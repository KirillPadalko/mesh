import { EncryptedMessage } from '../../types';
import { WebSocketService } from './WebSocketService';

export interface WebRTCListener {
    onP2PMessageReceived(fromMeshId: string, message: EncryptedMessage): void;
    onP2PConnectionStateChange(peerId: string, isConnected: boolean): void;
}

interface PeerState {
    connection: RTCPeerConnection;
    dataChannel: RTCDataChannel | null;
    isConnected: boolean;
}

export class WebRTCManager {
    private peers = new Map<string, PeerState>();
    listener: WebRTCListener | null = null;

    constructor(
        private wsService: WebSocketService,
    ) {
    }

    connectToPeer(peerId: string): void {
        if (this.peers.has(peerId)) return;

        const pc = this.createPeerConnection(peerId);

        // Create data channel (initiator)
        const dc = pc.createDataChannel('mesh-data');
        this.setupDataChannel(peerId, dc);

        const state: PeerState = {
            connection: pc,
            dataChannel: dc,
            isConnected: false,
        };

        this.peers.set(peerId, state);

        // Create and send offer
        pc.createOffer()
            .then(offer => pc.setLocalDescription(offer))
            .then(() => {
                const sdp = pc.localDescription?.sdp;
                if (sdp) {
                    this.wsService.sendSignaling(peerId, 'webrtc_offer', sdp);
                }
            })
            .catch(error => console.error('Error creating offer:', error));
    }

    handleSignaling(fromMeshId: string, type: string, payload?: string): void {
        if (type === 'webrtc_offer') {
            this.handleOffer(fromMeshId, payload!);
        } else if (type === 'webrtc_answer') {
            this.handleAnswer(fromMeshId, payload!);
        } else if (type === 'webrtc_ice') {
            this.handleIceCandidate(fromMeshId, payload!);
        }
    }

    private handleOffer(fromMeshId: string, sdp: string): void {
        let state = this.peers.get(fromMeshId);

        if (!state) {
            const pc = this.createPeerConnection(fromMeshId);
            state = {
                connection: pc,
                dataChannel: null,
                isConnected: false,
            };
            this.peers.set(fromMeshId, state);

            // Responder: wait for data channel
            pc.ondatachannel = (event) => {
                state!.dataChannel = event.channel;
                this.setupDataChannel(fromMeshId, event.channel);
            };
        }

        const pc = state.connection;

        pc.setRemoteDescription({ type: 'offer', sdp })
            .then(() => pc.createAnswer())
            .then(answer => pc.setLocalDescription(answer))
            .then(() => {
                const answerSdp = pc.localDescription?.sdp;
                if (answerSdp) {
                    this.wsService.sendSignaling(fromMeshId, 'webrtc_answer', answerSdp);
                }
            })
            .catch(error => console.error('Error handling offer:', error));
    }

    private handleAnswer(fromMeshId: string, sdp: string): void {
        const state = this.peers.get(fromMeshId);
        if (!state) return;

        state.connection.setRemoteDescription({ type: 'answer', sdp })
            .catch(error => console.error('Error setting remote description:', error));
    }

    private handleIceCandidate(fromMeshId: string, candidateJson: string): void {
        const state = this.peers.get(fromMeshId);
        if (!state) return;

        try {
            const { candidate, sdpMLineIndex, sdpMid } = JSON.parse(candidateJson);
            const iceCandidate = new RTCIceCandidate({
                candidate,
                sdpMLineIndex,
                sdpMid,
            });

            state.connection.addIceCandidate(iceCandidate)
                .catch(error => console.error('Error adding ICE candidate:', error));
        } catch (error) {
            console.error('Error parsing ICE candidate:', error);
        }
    }

    private createPeerConnection(peerId: string): RTCPeerConnection {
        const config: RTCConfiguration = {
            iceServers: [
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' },
            ],
        };

        const pc = new RTCPeerConnection(config);

        pc.onicecandidate = (event) => {
            if (event.candidate) {
                const candidateJson = JSON.stringify({
                    candidate: event.candidate.candidate,
                    sdpMLineIndex: event.candidate.sdpMLineIndex,
                    sdpMid: event.candidate.sdpMid,
                });
                this.wsService.sendSignaling(peerId, 'webrtc_ice', candidateJson);
            }
        };

        pc.oniceconnectionstatechange = () => {
            console.log(`ICE connection state for ${peerId}: ${pc.iceConnectionState}`);
        };

        return pc;
    }

    private setupDataChannel(peerId: string, dc: RTCDataChannel): void {
        dc.onopen = () => {
            console.log(`Data channel opened for ${peerId}`);
            const state = this.peers.get(peerId);
            if (state) {
                state.isConnected = true;
                this.listener?.onP2PConnectionStateChange(peerId, true);
            }
        };

        dc.onclose = () => {
            console.log(`Data channel closed for ${peerId}`);
            const state = this.peers.get(peerId);
            if (state) {
                state.isConnected = false;
                this.listener?.onP2PConnectionStateChange(peerId, false);
            }
        };

        dc.onmessage = (event) => {
            try {
                const message = JSON.parse(event.data) as EncryptedMessage;
                this.listener?.onP2PMessageReceived(peerId, message);
            } catch (error) {
                console.error('Error parsing P2P message:', error);
            }
        };
    }

    sendP2PMessage(peerId: string, message: EncryptedMessage): boolean {
        const state = this.peers.get(peerId);

        if (!state || !state.isConnected || !state.dataChannel) {
            return false;
        }

        try {
            const data = JSON.stringify(message);
            state.dataChannel.send(data);
            return true;
        } catch (error) {
            console.error('Error sending P2P message:', error);
            return false;
        }
    }

    isConnected(peerId: string): boolean {
        const state = this.peers.get(peerId);
        return state?.isConnected || false;
    }

    disconnectPeer(peerId: string): void {
        const state = this.peers.get(peerId);
        if (!state) return;

        state.dataChannel?.close();
        state.connection.close();
        this.peers.delete(peerId);
    }
}
