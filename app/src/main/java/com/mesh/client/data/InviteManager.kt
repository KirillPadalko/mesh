package com.mesh.client.data

import com.mesh.client.crypto.MeshSigner
import com.mesh.client.data.protocol.Invite
import com.mesh.client.data.protocol.InviteAck
import com.mesh.client.data.protocol.L2Notify
import com.mesh.client.data.protocol.ProofChain
import com.mesh.client.identity.IdentityManager
import com.google.gson.Gson

class InviteManager(
    private val identityManager: IdentityManager,
    private val signer: MeshSigner,
    private val graphManager: MeshGraphManager
) {
    private val gson = Gson()
    private val MAX_SKEW_MS = 5 * 60 * 1000L // 5 minutes

    private fun validateTimestamp(ts: Long): Boolean {
        val now = System.currentTimeMillis()
        if (ts > now + MAX_SKEW_MS) return false // Future
        if (ts < now - MAX_SKEW_MS) return false // Too old
        return true
    }

    // --- 1. Create Invite (A -> B) ---
    fun createInvite(toMeshId: String): Invite {
        val myId = identityManager.getMeshId()
        val timestamp = System.currentTimeMillis()
        
        // Data to sign: from + to + timestamp
        // For MVP simplicity, we sign the concatenation or a robust structure.
        // SPEC says: "sig_A(from + to + timestamp)"
        val dataToSign = "$myId$toMeshId$timestamp".toByteArray()
        val signature = signer.sign(dataToSign)

        return Invite(
            from = myId,
            to = toMeshId,
            timestamp = timestamp,
            signature = signature
        )
    }

    // --- 2. Process Incoming Invite (B receives from A) ---
    // Returns the ACK if valid and accepted, null otherwise
    fun processInvite(invite: Invite): InviteAck? {
        val myId = identityManager.getMeshId()

        // 1. Basic Validation
        if (invite.to != myId) return null // Not for me
        if (!validateTimestamp(invite.timestamp)) return null

        
        // 2. Verify Signature
        val dataToVerify = "${invite.from}${invite.to}${invite.timestamp}".toByteArray()
        if (!signer.verify(dataToVerify, invite.signature, invite.from)) {
            return null // Invalid signature
        }

        // 3. Replay / Duplicate Check
        // Calculate hash of the invite to track it
        val inviteJson = gson.toJson(invite) // Canonicalization needed in real prod, assume simple here
        val inviteHash = signer.hash(inviteJson.toByteArray())

        if (graphManager.isHashProcessed(inviteHash)) {
            return null // Already processed
        }

        // 4. Accept Logic (First invite rule)
        // In this MVP, we might accept multiple connections, but only count unique ones for score?
        // SPEC: "B accepts only first invite" -> implies single parent?
        // But in a Mesh, you can have multiple peers.
        // Let's assume SPEC meant "B accepts specific invite only once".
        // If "First Invite" means "Single Parent", that restricts graph to a Tree.
        // Mesh usually implies multiple links.
        // "Mesh-Score = L1 + 0.3 * L2". If I invite 5 people, I get 5 * 1.
        // If I accept 5 invites, do I maximize connection?
        // Let's assume we can accept multiple invites (many-to-many graph).
        
        graphManager.markHashProcessed(inviteHash)
        graphManager.addL1Connection(invite.from) 
        graphManager.storeReceivedInvite(invite) // Store for potential L2 proof logic
        // Note: L1 scoring: A receives +1 L1 when receiving ACK. B receives +1 when?
        // Usually symmetric. B knows A is L1. A knows B is L1.
        // So B acts as "Client" here accepting.
        
        // 5. Generate ACK
        val timestamp = System.currentTimeMillis()
        val ackDataToSign = "$myId${invite.from}$inviteHash$timestamp".toByteArray()
        val signature = signer.sign(ackDataToSign)

        return InviteAck(
            from = myId,
            to = invite.from,
            inviteHash = inviteHash,
            timestamp = timestamp,
            signature = signature
        )
    }

    // --- 3. Process ACK (A receives from B) ---
    fun processInviteAck(ack: InviteAck): Boolean {
        val myId = identityManager.getMeshId()
        
        if (ack.to != myId) return false
        if (!validateTimestamp(ack.timestamp)) return false

        
        // Verify Signature
        val dataToVerify = "${ack.from}${ack.to}${ack.inviteHash}${ack.timestamp}".toByteArray()
        if (!signer.verify(dataToVerify, ack.signature, ack.from)) {
            return false
        }

        // Mark processed
        val ackHash = signer.hash(gson.toJson(ack).toByteArray())
        if (graphManager.isHashProcessed(ackHash)) return false
        graphManager.markHashProcessed(ackHash)

        // Success: Add L1
        graphManager.addL1Connection(ack.from)
        return true
    }

    // --- 4. Create L2 Notify (B -> A about C) ---
    // B calls this after establishing connection with C
    fun createL2Notify(targetRoot: String, inviteBC: Invite): L2Notify {
        val myId = identityManager.getMeshId()
        
        // 1. Get the invite A->B (proof that A invited me)
        val inviteAB = graphManager.getInviteFrom(targetRoot)
            ?: throw IllegalStateException("Cannot notify $targetRoot: No invite found from them.")
            
        // 2. Validate inviteBC (B->C) is from me
        if (inviteBC.from != myId) throw IllegalArgumentException("InviteBC must be from me")
        
        // 3. Construct Proof
        val proof = ProofChain(
            inviteAB = inviteAB,
            inviteBC = inviteBC
        )
        
        val timestamp = System.currentTimeMillis()
        val origin = inviteBC.to // C
        
        // 4. Sign
        // Sig(origin + via + hash(proof) + timestamp)
        val proofHash = signer.hash(gson.toJson(proof).toByteArray())
        val dataToSign = "$origin$myId$proofHash$timestamp".toByteArray()
        val signature = signer.sign(dataToSign)
        
        return L2Notify(
            origin = origin,
            via = myId,
            proof = proof,
            timestamp = timestamp,
            signature = signature
        )
    }

    // --- 5. Process L2 Notify (A receives from B about C) ---
    fun processL2Notify(notify: L2Notify): Boolean {
        // 1. Verify B's signature on Notify
        val dataToVerify = "${notify.origin}${notify.via}${signer.hash(gson.toJson(notify.proof).toByteArray())}${notify.timestamp}".toByteArray()
        if (!signer.verify(dataToVerify, notify.signature, notify.via)) return false
        if (!validateTimestamp(notify.timestamp)) return false

        
        // 2. Validate Proof
        // Proof contains Invite A->B and Invite B->C
        val proof = notify.proof
        
        // Check Invite A->B
        if (proof.inviteAB.from != identityManager.getMeshId()) return false // I didn't invite B
        if (proof.inviteAB.to != notify.via) return false // Not to B
        // Ideally verify signature of A->B (my own signature, or check local record)
        
        // Check Invite B->C
        if (proof.inviteBC.from != notify.via) return false
        if (proof.inviteBC.to != notify.origin) return false
        
        // Verify Invite B->C signature
        val bcData = "${proof.inviteBC.from}${proof.inviteBC.to}${proof.inviteBC.timestamp}".toByteArray()
        if (!signer.verify(bcData, proof.inviteBC.signature, proof.inviteBC.from)) return false
        
        // 3. Add L2
        graphManager.addL2Connection(notify.via, notify.origin)
        return true
    }
}
