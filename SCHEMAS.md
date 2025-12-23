# Mesh Protocol Schemas

This document defines the strict data structures for the Mesh MVP P2P protocol.
We use **Protobuf (proto3)** as the interface definition language (IDL) for strictness and type safety, but the MVP can use **JSON** serialization if preferred for simplicity.

## 1. Common Types

```protobuf
syntax = "proto3";

package mesh.protocol;

// Represents a 32-byte public key (Mesh-ID)
message MeshId {
  string key_hex = 1; // Hex-encoded public key
}

// Represents a cryptographic signature
message Signature {
  string sig_hex = 1; // Hex-encoded signature
  int64 timestamp = 2; // Unix timestamp
}
```

---

## 2. Invite (L1)

Sent from **Inviter (A)** to **Invitee (B)**.

```protobuf
message Invite {
  // Who is inviting
  MeshId from = 1;
  
  // Who is being invited
  MeshId to = 2;
  
  // Creation time
  int64 timestamp = 3;
  
  // Signature of (from + to + timestamp) by 'from'
  string signature = 4;
  
  // Optional: If this is an L2 invite, it contains the parent invite
  Invite parent_invite = 5; 
}
```

### JSON Example
```json
{
  "type": "invite",
  "from": "0xABC...",
  "to": "0xDEF...",
  "timestamp": 1710000000,
  "signature": "sig_hex_string",
  "parent_invite": null
}
```

---

## 3. Invite Acknowledgement (ACK)

Sent from **Invitee (B)** back to **Inviter (A)** after accepting the invite.

```protobuf
message InviteAck {
  // Who accepted (B)
  MeshId from = 1;
  
  // Who sent the invite (A)
  MeshId to = 2;
  
  // Hash of the original Invite message (SHA-256)
  string invite_hash = 3;
  
  // Time of acceptance
  int64 timestamp = 4;
  
  // Signature of (from + to + invite_hash + timestamp) by 'from' (B)
  string signature = 5;
}
```

### JSON Example
```json
{
  "type": "invite_ack",
  "from": "0xDEF...",
  "to": "0xABC...",
  "invite_hash": "hash_of_invite_json",
  "timestamp": 1710000100,
  "signature": "sig_hex_string"
}
```

---

## 4. L2 Notification (L2 Notify)

Sent from **L1 (B)** to **Root (A)** to inform about a new L2 connection (C).

```protobuf
message L2Notify {
  // The new node (C)
  MeshId origin = 1;
  
  // The intermediary (B) - Sender of this message
  MeshId via = 2;
  
  // Proof chain
  ProofChain proof = 3;
  
  int64 timestamp = 4;
  
  // Signature of (origin + via + proof_hash + timestamp) by 'via' (B)
  string signature = 5;
}

message ProofChain {
  // The original invite A->B
  Invite invite_ab = 1;
  
  // The ACK B->A (Optional, keeping it light)
  // InviteAck ack_ba = 2; 
  
  // The second invite B->C
  Invite invite_bc = 3;
  
  // The ACK C->B
  InviteAck ack_cb = 4;
}
```

### JSON Example
```json
{
  "type": "l2_notify",
  "origin": "0xGHI...",
  "via": "0xDEF...",
  "proof": {
    "invite_ab": { ... },
    "invite_bc": { ... },
    "ack_cb": { ... }
  },
  "timestamp": 1710000300,
  "signature": "sig_hex_string"
}
```

---

## 5. Notes

1.  **Serialization**: For the MVP, fields should be canonicalized (sorted keys) before signing if using JSON.
2.  **Signatures**: Ed25519 is recommended for Mesh-IDs.
3.  **Validation**:
    *   `timestamp` must be within allowed drift (e.g. ±1 minute).
    *   `Invite.to` must match the receiver's Mesh-ID.
    *   `InviteAck.invite_hash` must match the locally stored invite.
