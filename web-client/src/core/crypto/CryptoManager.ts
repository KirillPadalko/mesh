import * as ed25519 from '@noble/ed25519';
import { sha256 } from '@noble/hashes/sha256';
import { sha512 } from '@noble/hashes/sha512';
import { hkdf } from '@noble/hashes/hkdf';
import { IdentityManager } from './IdentityManager';
import { EncryptedMessage } from '../../types';

export class CryptoManager {
    constructor(private identityManager: IdentityManager) { }

    async encryptMessage(plaintext: string, peerMeshId: string): Promise<EncryptedMessage> {
        const sessionKey = await this.deriveSessionKey(peerMeshId);

        // Generate random nonce (12 bytes for AES-GCM)
        const nonce = crypto.getRandomValues(new Uint8Array(12));

        // Encrypt using AES-GCM
        const encoder = new TextEncoder();
        const plaintextBytes = encoder.encode(plaintext);

        const key = await crypto.subtle.importKey(
            'raw',
            sessionKey as any,
            { name: 'AES-GCM' },
            false,
            ['encrypt']
        );

        const ciphertextBytes = await crypto.subtle.encrypt(
            {
                name: 'AES-GCM',
                iv: nonce,
                tagLength: 128,
            },
            key,
            plaintextBytes
        );

        return {
            ciphertext: this.bytesToBase64(new Uint8Array(ciphertextBytes)),
            nonce: this.bytesToBase64(nonce),
            timestamp: Date.now(),
        };
    }

    async decryptMessage(encryptedMessage: EncryptedMessage, peerMeshId: string): Promise<string> {
        const sessionKey = await this.deriveSessionKey(peerMeshId);

        try {
            const ciphertextBytes = this.base64ToBytes(encryptedMessage.ciphertext);
            const nonce = this.base64ToBytes(encryptedMessage.nonce);

            const key = await crypto.subtle.importKey(
                'raw',
                sessionKey as any,
                { name: 'AES-GCM' },
                false,
                ['decrypt']
            );

            const plaintextBytes = await crypto.subtle.decrypt(
                {
                    name: 'AES-GCM',
                    iv: nonce as any,
                    tagLength: 128,
                },
                key,
                ciphertextBytes as any
            );

            const decoder = new TextDecoder();
            return decoder.decode(plaintextBytes);
        } catch (error) {
            console.error('Core decrypt failure:', error);
            // Log hex of key (first 4 bytes) for debugging without exposing full key
            console.debug(`Session key prefix: ${this.bytesToHex(sessionKey.slice(0, 4))}`);
            throw error;
        }
    }

    async signMessage(message: string): Promise<string> {
        const privateKey = await this.identityManager.getPrivateKey();
        if (!privateKey) throw new Error('No identity available');

        const encoder = new TextEncoder();
        const messageBytes = encoder.encode(message);
        const signature = await ed25519.signAsync(messageBytes, privateKey);

        return this.bytesToHex(signature);
    }

    async hash(data: Uint8Array): Promise<string> {
        const hashBuffer = await crypto.subtle.digest('SHA-256', data as any);
        return this.bytesToHex(new Uint8Array(hashBuffer));
    }

    async verifySignature(message: string, signature: string, publicKeyBase58: string): Promise<boolean> {
        try {
            const publicKey = this.identityManager.decodeBase58(publicKeyBase58);
            const encoder = new TextEncoder();
            const messageBytes = encoder.encode(message);
            const signatureBytes = this.hexToBytes(signature);

            return await ed25519.verifyAsync(signatureBytes, messageBytes, publicKey);
        } catch (error) {
            console.error('Signature verification failed:', error);
            return false;
        }
    }

    private async deriveSessionKey(peerMeshId: string): Promise<Uint8Array> {
        const myPrivateKey = await this.identityManager.getPrivateKey();
        if (!myPrivateKey) throw new Error('No identity');

        // 1. Derive my X25519 Private Key from Ed25519 seed
        const seedHash = sha512(myPrivateKey);
        const myX25519Priv = seedHash.slice(0, 32);
        myX25519Priv[0] &= 248;
        myX25519Priv[31] &= 127;
        myX25519Priv[31] |= 64;

        // 2. Convert Peer Ed25519 Public Key to X25519 Public Key
        const peerEdPubKey = this.identityManager.decodeBase58(peerMeshId);
        const peerX25519Pub = this.convertEd25519ToX25519(peerEdPubKey);

        // 3. Use pure JS implementation of X25519 (Curve25519) to match Android
        // Web Crypto API has proven brittle for this specific curve on some platforms/contexts
        const sharedSecret = this.scalarMult(myX25519Priv, peerX25519Pub);

        // 4. HKDF (matching Android)
        // Salt: 32 bytes of zeros (explicit)
        // Info: "MESH_SESSION_V1" (explicit versioning)
        const salt = new Uint8Array(32);
        const info = new TextEncoder().encode("MESH_SESSION_V1");

        const sessionKey = hkdf(sha256, sharedSecret, salt, info, 32);

        // Debug logging
        console.debug(`[CRYPTO-DEBUG] My Priv: ${this.bytesToHex(myX25519Priv).substring(0, 8)}...`);
        console.debug(`[CRYPTO-DEBUG] Peer Pub: ${this.bytesToHex(peerX25519Pub).substring(0, 8)}...`);
        console.debug(`[CRYPTO-DEBUG] Shared Secret: ${this.bytesToHex(sharedSecret).substring(0, 8)}...`);
        console.debug(`[CRYPTO-DEBUG] Session Key: ${this.bytesToHex(sessionKey).substring(0, 8)}...`);

        return sessionKey;
    }

    private scalarMult(scalar: Uint8Array, u: Uint8Array): Uint8Array {
        const P = 2n ** 255n - 19n;
        const A24 = 121665n; // (486662 - 2) / 4 (RFC 7748)

        // Ensure u is within field
        const x_1 = this.decodeInt(u) % P;

        let x_2 = 1n;
        let z_2 = 0n;
        let x_3 = x_1;
        let z_3 = 1n;
        let swap = 0n;

        const k = this.decodeInt(scalar);

        for (let t = 254n; t >= 0n; t--) {
            const k_t = (k >> t) & 1n;
            swap ^= k_t;

            if (swap) {
                [x_2, x_3] = [x_3, x_2];
                [z_2, z_3] = [z_3, z_2];
            }
            swap = k_t;

            const A = (x_2 + z_2) % P;
            const AA = (A * A) % P;
            const B = (x_2 - z_2 + P) % P;
            const BB = (B * B) % P;
            const E = (AA - BB + P) % P;
            const C = (x_3 + z_3) % P;
            const D = (x_3 - z_3 + P) % P;
            const DA = (D * A) % P;
            const CB = (C * B) % P;

            x_3 = ((DA + CB) * (DA + CB)) % P;
            z_3 = (x_1 * (((DA - CB + P) % P) * ((DA - CB + P) % P))) % P;

            x_2 = (AA * BB) % P;
            z_2 = (E * (AA + A24 * E)) % P;
        }

        if (swap) {
            [x_2, x_3] = [x_3, x_2];
            [z_2, z_3] = [z_3, z_2];
        }

        const invZ2 = this.modInverse(z_2, P);
        const x = (x_2 * invZ2) % P;

        return this.encodeInt(x);
    }

    private decodeInt(bytes: Uint8Array): bigint {
        let res = 0n;
        // Handle potentially shorter than 32 bytes due to Base58 decoding
        const len = Math.min(bytes.length, 32);
        for (let i = 0; i < len; i++) {
            res |= BigInt(bytes[i]) << (8n * BigInt(i));
        }
        return res;
    }

    private encodeInt(n: bigint): Uint8Array {
        const res = new Uint8Array(32);
        for (let i = 0; i < 32; i++) {
            res[i] = Number((n >> (8n * BigInt(i))) & 0xffn);
        }
        return res;
    }

    private convertEd25519ToX25519(edPubKey: Uint8Array): Uint8Array {
        const p = 2n ** 255n - 19n;

        // Ensure we handle keys that might be slightly different length due to Base58
        const yBytes = new Uint8Array(32);
        const copyLen = Math.min(edPubKey.length, 32);
        yBytes.set(edPubKey.slice(-copyLen), 32 - copyLen);

        // Ed25519 public key in bytes is the y coordinate + sign of x
        // The last byte contains the sign of x in MSB
        yBytes[31] &= 0x7F; // clear sign bit

        let y = 0n;
        for (let i = 0; i < 32; i++) {
            y += BigInt(yBytes[i]) << (8n * BigInt(i));
        }

        const num = (1n + y) % p;
        let den = (1n - y) % p;
        if (den < 0n) den += p;
        const u = (num * this.modInverse(den, p)) % p;

        return this.encodeInt(u);
    }

    private modInverse(a: bigint, m: bigint): bigint {
        let [g, x] = this.extendedGCD(a, m);
        if (g !== 1n) throw new Error('Modular inverse does not exist');
        return (x % m + m) % m;
    }

    private extendedGCD(a: bigint, b: bigint): [bigint, bigint, bigint] {
        if (a === 0n) return [b, 0n, 1n];
        let [g, x, y] = this.extendedGCD(b % a, a);
        return [g, y - (b / a) * x, x];
    }

    private bytesToBase64(bytes: Uint8Array): string {
        const binString = Array.from(bytes, (byte) =>
            String.fromCodePoint(byte),
        ).join("");
        return btoa(binString);
    }

    private base64ToBytes(base64: string): Uint8Array {
        const binString = atob(base64);
        return Uint8Array.from(binString, (m) => m.codePointAt(0)!);
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
