import { CryptoManager } from '../../core/crypto/CryptoManager';
import { IdentityManager } from '../../core/crypto/IdentityManager';
import { MeshGraphManager } from '../../core/mesh/MeshGraphManager';
import { Invite, InviteAck } from '../../types';

export class InviteManager {
    constructor(
        private identityManager: IdentityManager,
        private cryptoManager: CryptoManager,
        private graphManager: MeshGraphManager
    ) { }

    async createInvite(toPeerId: string): Promise<Invite> {
        const myMeshId = await this.identityManager.getMeshId();
        if (!myMeshId) throw new Error('No identity');

        const nickname = this.identityManager.getLocalNickname();
        const timestamp = Date.now();

        // Android format: "$from$to$timestamp$nickname"
        const signatureData = `${myMeshId}${toPeerId}${timestamp}${nickname || ''}`;
        const signature = await this.cryptoManager.signMessage(signatureData);

        const invite: Invite = {
            type: 'invite',
            from: myMeshId,
            to: toPeerId,
            nickname,
            timestamp,
            signature,
        };

        return invite;
    }

    async processInvite(invite: Invite): Promise<InviteAck | null> {
        const myMeshId = await this.identityManager.getMeshId();
        if (!myMeshId) return null;
        if (invite.to !== myMeshId) return null;

        // Verify signature
        // Android format: "$from$to$timestamp$nickname"
        const nickname = invite.nickname || '';
        const signatureData = `${invite.from}${invite.to}${invite.timestamp}${nickname}`;

        const isValid = await this.cryptoManager.verifySignature(
            signatureData,
            invite.signature,
            invite.from
        );

        if (!isValid) {
            console.error('Invalid invite signature');
            return null;
        }

        // Store invite
        this.graphManager.storeReceivedInvite(invite);

        // Create ACK
        const timestamp = Date.now();
        const myNickname = this.identityManager.getLocalNickname();
        const inviteHash = await this.hashInvite(invite);

        // Android format: "$myId$senderId$inviteHash$timestamp$myNickname"
        const ackData = `${myMeshId}${invite.from}${inviteHash}${timestamp}${myNickname || ''}`;

        const signature = await this.cryptoManager.signMessage(ackData);

        const ack: InviteAck = {
            type: 'invite_ack',
            from: myMeshId,
            to: invite.from,
            nickname: myNickname,
            inviteHash: inviteHash,
            timestamp,
            signature,
        };

        return ack;
    }

    async processInviteLink(meshId: string): Promise<boolean> {
        // Add as contact
        // In a real scenario, we might want to verify headers or fetch profile
        try {
            await this.graphManager.addL1Connection(meshId);
            return true;
        } catch (e) {
            console.error('Failed to process invite link', e);
            return false;
        }
    }

    async processInviteAck(ack: InviteAck): Promise<boolean> {
        const myMeshId = await this.identityManager.getMeshId();
        if (!myMeshId) return false;
        if (ack.to !== myMeshId) return false;

        // Verify signature
        // Android format: "$from$to$inviteHash$timestamp$nickname"
        const nickname = ack.nickname || '';
        const ackData = `${ack.from}${ack.to}${ack.inviteHash}${ack.timestamp}${nickname}`;

        const isValid = await this.cryptoManager.verifySignature(
            ackData,
            ack.signature,
            ack.from
        );

        if (!isValid) {
            console.error('Invalid ACK signature');
            return false;
        }

        return true;
    }

    private async hashInvite(invite: Invite): Promise<string> {
        // Android: uses SHA-256 hex hash of the JSON of Invite
        // We need to be careful with JSON stabilization. 
        // Android Gson likely produces a specific order.
        // Assuming alphabetical keys in JSON for both.
        const inviteData = {
            from: invite.from,
            to: invite.to,
            timestamp: invite.timestamp,
            nickname: invite.nickname || null,
            signature: invite.signature,
            type: 'invite'
        };

        // Sorting keys to ensure consistency
        const sortedKeys = Object.keys(inviteData).sort() as Array<keyof typeof inviteData>;
        const stabilized: any = {};
        sortedKeys.forEach(key => {
            stabilized[key] = inviteData[key];
        });

        const encoder = new TextEncoder();
        const data = encoder.encode(JSON.stringify(stabilized).replace(/,/g, ',').replace(/:/g, ':')); // Basic JSON
        return await this.cryptoManager.hash(data);
    }

    storePendingInvite(inviterMeshId: string, nickname?: string): void {
        localStorage.setItem('pending_invite', JSON.stringify({
            inviterMeshId,
            nickname,
        }));
    }

    getPendingInvite(): { inviterMeshId: string; nickname?: string } | null {
        const stored = localStorage.getItem('pending_invite');
        if (!stored) return null;

        try {
            return JSON.parse(stored);
        } catch {
            return null;
        }
    }

    clearPendingInvite(): void {
        localStorage.removeItem('pending_invite');
    }
}
