import { Invite } from '../../types';

interface MeshGraph {
    l1Contacts: Set<string>;
    l2Connections: Map<string, Set<string>>;
    processedHashes: Set<string>;
    receivedInvites: Map<string, Invite>;
}

export class MeshGraphManager {
    private static readonly STORAGE_KEY = 'mesh_graph_v1';
    private graph: MeshGraph;
    private listeners: Record<string, ((data: any) => void)[]> = {};

    constructor() {
        this.graph = this.loadGraph();
    }

    private loadGraph(): MeshGraph {
        const stored = localStorage.getItem(MeshGraphManager.STORAGE_KEY);

        if (!stored) {
            return {
                l1Contacts: new Set(),
                l2Connections: new Map(),
                processedHashes: new Set(),
                receivedInvites: new Map(),
            };
        }

        try {
            const data = JSON.parse(stored);
            return {
                l1Contacts: new Set(data.l1Contacts || []),
                l2Connections: new Map(
                    Object.entries(data.l2Connections || {}).map(([key, value]) => [
                        key,
                        new Set(value as string[]),
                    ])
                ),
                processedHashes: new Set(data.processedHashes || []),
                receivedInvites: new Map(Object.entries(data.receivedInvites || {})),
            };
        } catch (error) {
            console.error('Failed to load mesh graph:', error);
            return {
                l1Contacts: new Set(),
                l2Connections: new Map(),
                processedHashes: new Set(),
                receivedInvites: new Map(),
            };
        }
    }

    private saveGraph(): void {
        const data = {
            l1Contacts: Array.from(this.graph.l1Contacts),
            l2Connections: Object.fromEntries(
                Array.from(this.graph.l2Connections.entries()).map(([key, value]) => [
                    key,
                    Array.from(value),
                ])
            ),
            processedHashes: Array.from(this.graph.processedHashes),
            receivedInvites: Object.fromEntries(this.graph.receivedInvites),
        };

        localStorage.setItem(MeshGraphManager.STORAGE_KEY, JSON.stringify(data));
    }

    getMeshScore(): number {
        const l1Count = this.graph.l1Contacts.size;
        const l2Count = Array.from(this.graph.l2Connections.values()).reduce(
            (sum, set) => sum + set.size,
            0
        );
        return l1Count + 0.3 * l2Count;
    }

    getMeshScoreDetails(): { l1: number; l2: number } {
        const l1 = this.graph.l1Contacts.size;
        const l2 = Array.from(this.graph.l2Connections.values()).reduce(
            (sum, set) => sum + set.size,
            0
        );
        return { l1, l2 };
    }

    getL1Connections(): Set<string> {
        return new Set(this.graph.l1Contacts);
    }

    getL2Connections(): Map<string, Set<string>> {
        return new Map(
            Array.from(this.graph.l2Connections.entries()).map(([key, value]) => [
                key,
                new Set(value),
            ])
        );
    }

    addL1Connection(meshId: string): void {
        if (this.graph.l1Contacts.has(meshId)) {
            console.debug(`[MeshGraph] Contact ${meshId.substring(0, 8)} already exists, skipping`);
            return;
        }

        console.debug(`[MeshGraph] Adding L1 contact ${meshId.substring(0, 8)}`);
        this.graph.l1Contacts.add(meshId);
        this.saveGraph();

        const newScore = this.getMeshScore();
        console.debug(`[MeshGraph] New meshScore: ${newScore}`);

        this.emit('contact-update', { meshId });
        console.debug(`[MeshGraph] Emitted 'contact-update' event`);
    }

    addL2Connection(viaL1: string, childL2: string): void {
        if (!this.graph.l1Contacts.has(viaL1)) return;

        if (!this.graph.l2Connections.has(viaL1)) {
            this.graph.l2Connections.set(viaL1, new Set());
        }

        const children = this.graph.l2Connections.get(viaL1)!;
        if (children.has(childL2)) return;

        children.add(childL2);
        this.saveGraph();
        this.emit('l2-update', { via: viaL1, child: childL2 });
    }

    on(event: string, callback: (data: any) => void) {
        if (!this.listeners[event]) this.listeners[event] = [];
        this.listeners[event].push(callback);
    }

    private emit(event: string, data: any) {
        if (this.listeners[event]) {
            this.listeners[event].forEach(cb => cb(data));
        }
    }

    removeConnection(meshId: string): void {
        let changed = false;

        // Remove L1 connection
        if (this.graph.l1Contacts.has(meshId)) {
            this.graph.l1Contacts.delete(meshId);
            changed = true;
        }

        // Remove L2 connections provided by this peer
        if (this.graph.l2Connections.has(meshId)) {
            this.graph.l2Connections.delete(meshId);
            changed = true;
        }

        if (changed) {
            this.saveGraph();
        }
    }

    storeReceivedInvite(invite: Invite): void {
        this.graph.receivedInvites.set(invite.from, invite);
        this.saveGraph();
    }

    getInviteFrom(meshId: string): Invite | undefined {
        return this.graph.receivedInvites.get(meshId);
    }

    markHashProcessed(hash: string): boolean {
        if (this.graph.processedHashes.has(hash)) return false;

        this.graph.processedHashes.add(hash);
        this.saveGraph();
        return true;
    }

    isHashProcessed(hash: string): boolean {
        return this.graph.processedHashes.has(hash);
    }
}

export const meshGraphManager = new MeshGraphManager();
