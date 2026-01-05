import Dexie, { Table } from 'dexie';
import { Contact, Message } from '../../types';

export interface ContactEntity {
    meshId: string;
    nickname: string;
}

export interface MessageEntity {
    id?: number;
    peerId: string;
    isIncoming: boolean;
    text: string;
    timestamp: number;
    status: 'sent' | 'received' | 'read';
}

export class MeshDatabase extends Dexie {
    contacts!: Table<ContactEntity, string>;
    messages!: Table<MessageEntity, number>;

    constructor() {
        super('mesh_db');

        this.version(1).stores({
            contacts: 'meshId, nickname',
            messages: '++id, peerId, timestamp, status',
        });
    }

    async getContactsWithPreview(): Promise<Contact[]> {
        const contacts = await this.contacts.toArray();

        const contactsWithPreview = await Promise.all(
            contacts.map(async (contact) => {
                // Get last message for this contact
                const lastMessage = await this.messages
                    .where('peerId')
                    .equals(contact.meshId)
                    .reverse()
                    .sortBy('timestamp');

                const last = lastMessage[0];

                // Count unread messages
                const unreadCount = await this.messages
                    .where('peerId')
                    .equals(contact.meshId)
                    .and(msg => msg.isIncoming && msg.status !== 'read')
                    .count();

                return {
                    meshId: contact.meshId,
                    nickname: contact.nickname,
                    lastMessage: last?.text,
                    lastMessageTime: last?.timestamp,
                    unreadCount,
                };
            })
        );

        // Sort by last message time (most recent first)
        return contactsWithPreview.sort((a, b) => {
            const aTime = a.lastMessageTime || 0;
            const bTime = b.lastMessageTime || 0;
            return bTime - aTime;
        });
    }

    async getMessagesForPeer(peerId: string): Promise<Message[]> {
        const messages = await this.messages
            .where('peerId')
            .equals(peerId)
            .sortBy('timestamp');

        return messages;
    }

    async markMessagesAsRead(peerId: string): Promise<void> {
        await this.messages
            .where('peerId')
            .equals(peerId)
            .modify({ status: 'read' });
    }

    async insertMessage(message: MessageEntity): Promise<number> {
        return await this.messages.add(message);
    }

    async insertContact(contact: ContactEntity): Promise<void> {
        await this.contacts.put(contact);
    }

    async getContact(meshId: string): Promise<ContactEntity | undefined> {
        return await this.contacts.get(meshId);
    }

    async deleteContact(meshId: string): Promise<void> {
        await this.contacts.delete(meshId);
        await this.messages.where('peerId').equals(meshId).delete();
    }
}

export const db = new MeshDatabase();
