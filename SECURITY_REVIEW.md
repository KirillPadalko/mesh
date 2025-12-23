# Mesh Protocol — Attack Surface Review (MVP)

This document analyzes potential attack vectors on the Mesh Invite & Score protocol and describes mitigations.

## 1. Sybil Attacks (Fake Accounts)

**Attack**: User generates 1000 keypairs and invites them all to boost L1 score.
**Mitigation**:
*   **L2 Limit**: Score is primarily driven by L2 connections (0.3 weight), but L1 is the base.
*   **Economic Barrier (Implicit)**: In a pure P2P mesh without a central server, "fake" nodes don't stay online or exchange data.
*   **MVP Acceptance**: We accept that a user can generate a high *local* L1 score on their own device. However, other nodes in the network won't see these "ghosts" unless they physically connect/handshake.
*   **Future**: Require Proof-of-Work or biometric binding for "verified" nodes.

## 2. Replay Attacks

**Attack**: Attacker intercepts a valid `InviteAck` from B -> A and replays it later or to another node.
**Mitigation**:
*   `InviteAck` contains the **hash of the specific Invite**.
*   `Invite` contains `to: mesh_id_A`.
*   Signatures cover these fields.
*   An `Invite` is valid only for a specific unique timestamp/ID pair.
*   **Check**: Nodes must store `seen_invite_hashes` to prevent processing the same invite twice.

## 3. L2 Spoofing (The "Rich Uncle" Attack)

**Attack**: B tells A: "I invited C, D, E..." but C, D, E don't exist.
**Mitigation**:
*   **Proof Chain**: The `L2Notify` message MUST contain the `Invite` from B->C *signed by B* AND the `InviteAck` from C->B (optional in MVP schema, but critical for security).
*   **Correction**: The current MVP Schema `L2Notify` includes `ProofChain` with `Invite` (B->C). It *should* ideally include C's signature (the generic `ProofChain` has `ack_cb`).
*   **Strict Rule**: A does NOT award L2 points unless the `proof` contains a valid cryptographic signature from C.

## 4. Circular Boosting

**Attack**: A -> B -> C -> A. Infinite nesting?
**Mitigation**:
*   **Max Depth = 2**: A only cares about B (L1) and C (L2). D (L3) gives 0 points.
*   **Duplicate Check**: If C tries to invite A, A sees it's already in its own graph (local check).
*   **Global**: Without a global graph consensus, cycles are locally practically irrelevant because the score stops at L2.

## 5. Metadata Leakage

**Attack**: B broadcast `L2Notify` to everyone.
**Mitigation**:
*   `L2Notify` is **Unicast** (B -> A only).
*   Only the direct inviter needs to know about the L2 connection.
*   Privacy is preserved relative to the rest of the network.

## 6. Score Inflation (The "Spam Invite" Attack)

**Attack**: A sends 1000 Invites to random IDs.
**Mitigation**:
*   Score is only counted on **ACK**.
*   A cannot force B to ACK without B's private key logic.

## 7. Implementation Traps (TODOs for Devs)

1.  **Race Conditions**: B receives invites from A and C simultaneously. B must pick *one* parent (first come first served) or implementation defined. *Spec says: "B accepts only first invite".*
2.  **Clock Skew**: `timestamp` validation needs a forgiving window (e.g., +/- 5 mins) but must reject very old replays.
3.  **Key Storage**: Private keys must be in secure storage (Android Keystore).

---

## Conclusion
For MVP, the primary risk is **local Sybil generation** (one user simulating a network on one machine). Since there are no financial rewards yet, this risk is acceptable. The protocol is mathematically secure against spoofing remote identities.
