import { CryptoManager } from '../crypto/CryptoManager';
import { WebRTCManager, WebRTCListener } from './WebRTCManager';
import { WebSocketService, WebSocketListener } from './WebSocketService';
import { EncryptedMessage, ProtocolPayload } from '../../types';

export interface ChatTransportListener {
    onMessageReceived(fromMeshId: string, text: string, timestamp: number): void;
    onMessageStatusChanged(peerId: string, isP2P: boolean): void;
    onInviteReceived(fromMeshId: string, inviteJson: string): void;
    onInviteAckReceived(fromMeshId: string, ackJson: string): void;
    onL2NotifyReceived(fromMeshId: string, notifyJson: string): void;
    onTransportError(message: string): void;
}

export class ChatTransport implements WebSocketListener, WebRTCListener {
    listener: ChatTransportListener | null = null;

    constructor(
        private cryptoManager: CryptoManager,
        private webRTCManager: WebRTCManager,
        private wsService: WebSocketService,
    ) {
        // Set up listeners
        this.wsService.listener = this;
        this.webRTCManager.listener = this;
    }

    async sendMessage(toPeerId: string, text: string): Promise<void> {
        const payload: ProtocolPayload = {
            type: 'chat',
            content: text,
        };
        await this.sendProtocolPayload(toPeerId, payload);
    }

    async sendInvite(toPeerId: string, inviteJson: string): Promise<void> {
        const payload: ProtocolPayload = {
            type: 'invite',
            content: inviteJson,
        };
        await this.sendProtocolPayload(toPeerId, payload);
    }

    async sendInviteAck(toPeerId: string, ackJson: string): Promise<void> {
        const payload: ProtocolPayload = {
            type: 'invite_ack',
            content: ackJson,
        };
        await this.sendProtocolPayload(toPeerId, payload);
    }

    private async sendProtocolPayload(peerId: string, payload: ProtocolPayload): Promise<void> {
        try {
            const payloadJson = JSON.stringify(payload);
            const encrypted = await this.cryptoManager.encryptMessage(payloadJson, peerId);

            // Try P2P first
            if (this.webRTCManager.isConnected(peerId)) {
                console.log(`Sending ${payload.type} via P2P to ${peerId}`);
                const sent = this.webRTCManager.sendP2PMessage(peerId, encrypted);

                if (!sent) {
                    console.warn('P2P send failed, falling back to server');
                    this.wsService.sendEncryptedMessage(peerId, encrypted);
                }
            } else {
                console.log(`Sending ${payload.type} via server to ${peerId}`);
                this.wsService.sendEncryptedMessage(peerId, encrypted);

                // Try to establish P2P for future messages
                this.webRTCManager.connectToPeer(peerId);
            }
        } catch (error) {
            console.error('Error sending protocol message:', error);
            this.listener?.onTransportError(`Failed to send message: ${error}`);
        }
    }

    // WebSocketListener implementation
    onSignalingMessage(fromMeshId: string, type: string, payload?: string): void {
        if (type === 'error') {
            this.listener?.onTransportError(payload || 'Unknown server error');
            return;
        }

        this.webRTCManager.handleSignaling(fromMeshId, type, payload);
    }

    onEncryptedMessageReceived(fromMeshId: string, message: EncryptedMessage): void {
        this.handleIncomingMessage(fromMeshId, message);
    }

    onConnected(): void {
        console.log('Transport connected');
    }

    onDisconnected(): void {
        console.log('Transport disconnected');
    }

    onError(message: string): void {
        this.listener?.onTransportError(message);
    }

    // WebRTCListener implementation
    onP2PMessageReceived(fromMeshId: string, message: EncryptedMessage): void {
        this.handleIncomingMessage(fromMeshId, message);
    }

    onP2PConnectionStateChange(peerId: string, isConnected: boolean): void {
        this.listener?.onMessageStatusChanged(peerId, isConnected);
    }

    private async handleIncomingMessage(fromMeshId: string, message: EncryptedMessage): Promise<void> {
        try {
            const decryptedJson = await this.cryptoManager.decryptMessage(message, fromMeshId);

            // Try to parse as ProtocolPayload
            try {
                const payload = JSON.parse(decryptedJson) as ProtocolPayload;

                switch (payload.type) {
                    case 'chat':
                        this.listener?.onMessageReceived(fromMeshId, payload.content, message.timestamp);
                        break;
                    case 'invite':
                        this.listener?.onInviteReceived(fromMeshId, payload.content);
                        break;
                    case 'invite_ack':
                        this.listener?.onInviteAckReceived(fromMeshId, payload.content);
                        break;
                    case 'l2_notify':
                        this.listener?.onL2NotifyReceived(fromMeshId, payload.content);
                        break;
                    default:
                        console.warn(`Unknown protocol type: ${payload.type}`);
                }
            } catch {
                // Backward compatibility: assume plain text chat
                this.listener?.onMessageReceived(fromMeshId, decryptedJson, message.timestamp);
            }
        } catch (error) {
            console.error(`Decryption failed from ${fromMeshId}:`, error);
        }
    }
}
