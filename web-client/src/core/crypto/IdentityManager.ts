import * as ed25519 from '@noble/ed25519';
import * as bip39 from '@scure/bip39';
import { wordlist } from '@scure/bip39/wordlists/english.js';

const ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';

export class IdentityManager {
    private static readonly STORAGE_KEY = 'mesh_identity';
    private static readonly NICKNAME_KEY = 'mesh_nickname';

    private cachedPrivateKey: Uint8Array | null = null;
    private cachedPublicKey: Uint8Array | null = null;
    private cachedMeshId: string | null = null;

    async hasIdentity(): Promise<boolean> {
        return !!localStorage.getItem(IdentityManager.STORAGE_KEY);
    }

    async getMeshId(): Promise<string | null> {
        if (this.cachedMeshId) return this.cachedMeshId;

        const keyPair = await this.getOrCreateIdentity();
        if (!keyPair) return null;

        this.cachedMeshId = this.publicKeyToBase58(keyPair.publicKey);
        return this.cachedMeshId;
    }

    async getPrivateKey(): Promise<Uint8Array | null> {
        if (this.cachedPrivateKey) return this.cachedPrivateKey;

        const keyPair = await this.getOrCreateIdentity();
        return keyPair?.privateKey || null;
    }

    async getPublicKey(): Promise<Uint8Array | null> {
        if (this.cachedPublicKey) return this.cachedPublicKey;

        const keyPair = await this.getOrCreateIdentity();
        return keyPair?.publicKey || null;
    }

    private async getOrCreateIdentity(): Promise<{ privateKey: Uint8Array; publicKey: Uint8Array } | null> {
        const stored = localStorage.getItem(IdentityManager.STORAGE_KEY);

        if (!stored) return null;

        try {
            const data = JSON.parse(stored);
            const seed = this.hexToBytes(data.seed);

            this.cachedPrivateKey = seed;
            this.cachedPublicKey = await ed25519.getPublicKeyAsync(seed);

            return {
                privateKey: this.cachedPrivateKey,
                publicKey: this.cachedPublicKey,
            };
        } catch (error) {
            console.error('Failed to load identity:', error);
            return null;
        }
    }

    async createIdentity(): Promise<string> {
        const mnemonic = bip39.generateMnemonic(wordlist);
        return this.createFromMnemonic(mnemonic);
    }

    async createFromMnemonic(mnemonic: string): Promise<string> {
        if (!bip39.validateMnemonic(mnemonic, wordlist)) {
            throw new Error('Invalid mnemonic phrase');
        }

        const seed = bip39.mnemonicToSeedSync(mnemonic);
        const privateKey = seed.slice(0, 32); // Ed25519 uses 32-byte seed

        const publicKey = await ed25519.getPublicKeyAsync(privateKey);
        const meshId = this.publicKeyToBase58(publicKey);

        // Store identity
        localStorage.setItem(IdentityManager.STORAGE_KEY, JSON.stringify({
            seed: this.bytesToHex(privateKey),
            mnemonic,
        }));

        this.cachedPrivateKey = privateKey;
        this.cachedPublicKey = publicKey;
        this.cachedMeshId = meshId;

        return mnemonic;
    }

    async restoreFromMnemonic(mnemonic: string): Promise<void> {
        await this.createFromMnemonic(mnemonic);
    }

    exportMnemonic(): string | null {
        const stored = localStorage.getItem(IdentityManager.STORAGE_KEY);
        if (!stored) return null;

        try {
            const data = JSON.parse(stored);
            return data.mnemonic || null;
        } catch {
            return null;
        }
    }

    exportSeedHex(): string | null {
        const stored = localStorage.getItem(IdentityManager.STORAGE_KEY);
        if (!stored) return null;

        try {
            const data = JSON.parse(stored);
            return data.seed || null;
        } catch {
            return null;
        }
    }

    setLocalNickname(nickname: string): void {
        localStorage.setItem(IdentityManager.NICKNAME_KEY, nickname);
    }

    getLocalNickname(): string {
        return localStorage.getItem(IdentityManager.NICKNAME_KEY) || 'User';
    }

    clearIdentity(): void {
        localStorage.removeItem(IdentityManager.STORAGE_KEY);
        localStorage.removeItem(IdentityManager.NICKNAME_KEY);
        this.cachedPrivateKey = null;
        this.cachedPublicKey = null;
        this.cachedMeshId = null;
    }

    // Base58 encoding (matching Android implementation)
    private publicKeyToBase58(publicKey: Uint8Array): string {
        return this.encodeBase58(publicKey);
    }

    private encodeBase58(bytes: Uint8Array): string {
        if (bytes.length === 0) return '';

        // Convert bytes to bigint
        let num = 0n;
        for (const byte of bytes) {
            num = num * 256n + BigInt(byte);
        }

        // Convert to base58
        let result = '';
        while (num > 0n) {
            const remainder = Number(num % 58n);
            result = ALPHABET[remainder] + result;
            num = num / 58n;
        }

        // Add leading '1's for leading zero bytes
        for (const byte of bytes) {
            if (byte === 0) result = '1' + result;
            else break;
        }

        return result;
    }

    decodeBase58(str: string): Uint8Array {
        if (str.length === 0) return new Uint8Array(0);

        let num = 0n;
        for (const char of str) {
            const index = ALPHABET.indexOf(char);
            if (index === -1) throw new Error('Invalid Base58 character');
            num = num * 58n + BigInt(index);
        }

        const bytes = new Uint8Array(32);
        let pos = 31;
        while (num > 0n && pos >= 0) {
            bytes[pos--] = Number(num % 256n);
            num = num / 256n;
        }

        // Handle leading zeros encoded as '1'
        let zeros = 0;
        for (const char of str) {
            if (char === '1') zeros++;
            else break;
        }

        // If we have leading zeros, they should be at the start of the 32-byte array.
        // However, standard Base58 decoding usually returns a variable length array.
        // In Mesh, we expect 32 bytes.

        return bytes;
    }

    private bytesToHex(bytes: Uint8Array): string {
        return Array.from(bytes)
            .map(b => b.toString(16).padStart(2, '0'))
            .join('');
    }

    private hexToBytes(hex: string): Uint8Array {
        const bytes: number[] = [];
        for (let i = 0; i < hex.length; i += 2) {
            bytes.push(parseInt(hex.substr(i, 2), 16));
        }
        return new Uint8Array(bytes);
    }
}

export const identityManager = new IdentityManager();
