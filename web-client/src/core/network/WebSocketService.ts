import { SignalingMessage, ServerMessage, EncryptedMessage } from '../../types';


const CLIENT_VERSION = '0.1.0';

export interface WebSocketListener {
    onSignalingMessage(fromMeshId: string, type: string, payload?: string): void;
    onEncryptedMessageReceived(fromMeshId: string, message: EncryptedMessage): void;
    onConnected(): void;
    onDisconnected(): void;
    onError(message: string): void;
}

export class WebSocketService {
    private ws: WebSocket | null = null;
    private reconnectTimer: number | null = null;
    private pingInterval: number | null = null;
    private myMeshId: string | null = null;

    listener: WebSocketListener | null = null;


    connect(meshId: string): void {
        this.myMeshId = meshId;
        this.disconnect();

        try {
            // Dynamic URL generation based on current window location
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const host = window.location.host; // e.g. "mesh-online.org" or "localhost:3000"

            // If running on localhost/dev (usually port 3000 or 5173), connect to backend on 8000/8001
            // If running in production (served by backend), use relative path
            let wsUrl = `${protocol}//${host}/ws`;

            if (host.includes('localhost') || host.includes('127.0.0.1')) {
                // Determine port - assume default backend port 8001 for dev (matching docker-compose)
                wsUrl = 'ws://localhost:8001/ws';
            }

            this.ws = new WebSocket(wsUrl);

            this.ws.onopen = () => {
                console.log('WebSocket connected');
                this.sendAuth();
                this.listener?.onConnected();
                this.startPing();
            };

            this.ws.onmessage = (event) => {
                this.handleMessage(event.data);
            };

            this.ws.onclose = (event) => {
                console.log(`WebSocket disconnected. Code: ${event.code}, Reason: ${event.reason}`);
                this.listener?.onDisconnected();
                this.stopPing();
                this.scheduleReconnect();
            };

            this.ws.onerror = (error) => {
                console.error('WebSocket error details:', error);
                this.listener?.onError('WebSocket connection error');
            };
        } catch (error) {
            console.error('Failed to create WebSocket:', error);
            this.listener?.onError('Failed to connect to server: ' + error);
        }
    }

    disconnect(): void {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }

        this.stopPing();

        if (this.ws) {
            this.ws.close(1000, 'Client disconnecting');
            this.ws = null;
        }
    }

    private sendAuth(): void {
        if (!this.myMeshId) return;

        const authMessage: SignalingMessage = {
            type: 'auth',
            user_id: this.myMeshId,
            client_version: CLIENT_VERSION,
        };

        this.send(JSON.stringify(authMessage));
    }

    private handleMessage(data: string): void {
        if (data === 'ping' || data === 'pong') {
            return;
        }

        try {
            const msg = JSON.parse(data);

            if (msg.type === 'error') {
                // Special handling for recipient_offline:
                // This error is returned when we try to send P2P signaling (OFFER/ICE) to an offline user.
                // However, the actual chat message is sent in parallel via 'server_message', which
                // includes offline storage. So, we should NOT treat this as a fatal error for the user.
                if (msg.error === 'recipient_offline') {
                    console.warn('Recipient is offline - standard message stored on server, logic P2P skipped.');
                    return;
                }

                const errorMsg = msg.message || 'Server error';
                console.warn('Server error:', errorMsg);
                this.listener?.onError(errorMsg);
                return;
            }

            if (msg.type === 'server_message') {
                const serverMsg = msg as ServerMessage;
                // Decode Base64 payload
                const encryptedJson = atob(serverMsg.payload);
                const encryptedMsg = JSON.parse(encryptedJson) as EncryptedMessage;
                this.listener?.onEncryptedMessageReceived(serverMsg.from, encryptedMsg);
                return;
            }

            // Signaling messages (WebRTC offer/answer/ice)
            const sigMsg = msg as SignalingMessage;
            this.listener?.onSignalingMessage(
                sigMsg.user_id || 'unknown',
                sigMsg.type,
                sigMsg.payload
            );
        } catch (error) {
            console.error('Error parsing message:', error);
        }
    }

    sendSignaling(toMeshId: string, type: string, payload?: string): void {
        const msg = {
            to: toMeshId,
            type,
            payload,
        };
        this.send(JSON.stringify(msg));
    }

    sendEncryptedMessage(toMeshId: string, message: EncryptedMessage): void {
        if (!this.myMeshId) return;

        const msgJson = JSON.stringify(message);
        const payloadBase64 = btoa(msgJson);

        const serverMsg: ServerMessage = {
            type: 'server_message',
            from: this.myMeshId,
            to: toMeshId,
            payload: payloadBase64,
        };

        this.send(JSON.stringify(serverMsg));
    }

    private send(data: string): void {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(data);
        } else {
            console.warn('WebSocket not ready, message not sent');
        }
    }

    private startPing(): void {
        this.pingInterval = window.setInterval(() => {
            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                this.ws.send('ping');
            }
        }, 30000); // 30 seconds
    }

    private stopPing(): void {
        if (this.pingInterval) {
            clearInterval(this.pingInterval);
            this.pingInterval = null;
        }
    }

    private scheduleReconnect(): void {
        if (this.reconnectTimer) return;

        this.reconnectTimer = window.setTimeout(() => {
            console.log('Attempting to reconnect...');
            if (this.myMeshId) {
                this.connect(this.myMeshId);
            }
            this.reconnectTimer = null;
        }, 5000); // 5 seconds
    }
}
