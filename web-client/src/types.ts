// Storage types matching Android client structure

export interface Contact {
    meshId: string;
    nickname: string;
    lastMessage?: string;
    lastMessageTime?: number;
    unreadCount: number;
}

export interface Message {
    id?: number;
    peerId: string;
    isIncoming: boolean;
    text: string;
    timestamp: number;
    status: 'sent' | 'received' | 'read';
}

export interface EncryptedMessage {
    ciphertext: string; // Base64
    nonce: string;      // Base64
    timestamp: number;
}

export interface ProtocolPayload {
    type: 'chat' | 'invite' | 'invite_ack' | 'l2_notify';
    content: string;
}

export interface Invite {
    type: 'invite';
    from: string;
    to: string;
    nickname?: string;
    timestamp: number;
    signature: string;
    parentInvite?: Invite;
}

export interface InviteAck {
    type: 'invite_ack';
    from: string;
    to: string;
    nickname?: string;
    inviteHash: string;
    timestamp: number;
    signature: string;
}

export interface SignalingMessage {
    type: string;
    payload?: string;
    user_id?: string;
    client_version?: string;
    to?: string;
}

export interface ServerMessage {
    type: 'server_message';
    from: string;
    to: string;
    payload: string; // Base64
}
