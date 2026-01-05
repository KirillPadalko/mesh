package com.mesh.client.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mesh.client.data.protocol.Invite
import com.mesh.client.data.protocol.InviteAck
import com.mesh.client.data.protocol.L2Notify

class MeshGraphManager(context: Context) {

    private val prefs = context.getSharedPreferences("mesh_graph_v1", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Key Constants
    private val KEY_L1_CONTACTS = "l1_contacts" // Set<MeshID>
    private val KEY_L2_CONNECTIONS = "l2_connections" // Map<L1_MeshID, Set<L2_MeshID>>
    private val KEY_RECEIVED_INVITES = "received_invites" // Map<MeshID, Invite> (From whom -> Invite)
    private val KEY_PROCESSED_HASHES = "processed_hashes" // Set<String> (Anti-replay)

    // In-memory cache
    private val l1Contacts = mutableSetOf<String>()
    private val l2Connections = mutableMapOf<String, MutableSet<String>>()
    private val processedHashes = mutableSetOf<String>()
    private val receivedInvitesMap = mutableMapOf<String, Invite>()
    
    // Event listeners (matching web client functionality)
    interface GraphChangeListener {
        fun onContactUpdate(meshId: String)
        fun onL2Update(via: String, child: String)
    }
    
    private val listeners = mutableListOf<GraphChangeListener>()

    init {
        loadGraph()
    }
    
    fun addListener(listener: GraphChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }
    
    fun removeListener(listener: GraphChangeListener) {
        listeners.remove(listener)
    }
    
    private fun notifyContactUpdate(meshId: String) {
        listeners.forEach { it.onContactUpdate(meshId) }
    }
    
    private fun notifyL2Update(via: String, child: String) {
        listeners.forEach { it.onL2Update(via, child) }
    }

    private fun loadGraph() {
        // Load L1
        val l1Json = prefs.getString(KEY_L1_CONTACTS, "[]")
        l1Contacts.addAll(gson.fromJson(l1Json, object : TypeToken<Set<String>>() {}.type))

        // Load L2
        val l2Json = prefs.getString(KEY_L2_CONNECTIONS, "{}")
        l2Connections.putAll(gson.fromJson(l2Json, object : TypeToken<Map<String, MutableSet<String>>>() {}.type))
        
        // Load Received Invites
        val invitesJson = prefs.getString(KEY_RECEIVED_INVITES, "{}")
        receivedInvitesMap.putAll(gson.fromJson(invitesJson, object : TypeToken<Map<String, Invite>>() {}.type))

        // Load Hashes
        val hashesJson = prefs.getString(KEY_PROCESSED_HASHES, "[]")
        processedHashes.addAll(gson.fromJson(hashesJson, object : TypeToken<Set<String>>() {}.type))
    }

    private fun saveGraph() {
        prefs.edit()
            .putString(KEY_L1_CONTACTS, gson.toJson(l1Contacts))
            .putString(KEY_L2_CONNECTIONS, gson.toJson(l2Connections))
            .putString(KEY_RECEIVED_INVITES, gson.toJson(receivedInvitesMap))
            .putString(KEY_PROCESSED_HASHES, gson.toJson(processedHashes))
            .apply()
    }
    
    // --- Score ---

    fun getMeshScore(): Double {
        val l1Count = l1Contacts.size
        val l2Count = l2Connections.values.sumOf { it.size }
        return l1Count + (0.3 * l2Count)
    }

    fun getMeshScoreDetails(): Pair<Int, Int> {
         val l1Count = l1Contacts.size
         val l2Count = l2Connections.values.sumOf { it.size }
         return Pair(l1Count, l2Count)
    }

    // Expose for UI Visualization
    fun getL1Connections(): Set<String> = l1Contacts.toSet()
    fun getL2Connections(): Map<String, Set<String>> = l2Connections.toMap()


    // --- Actions ---

    fun addL1Connection(meshId: String) {
        if (l1Contacts.add(meshId)) {
            Log.d("MeshGraph", "Adding L1 contact ${meshId.take(8)}")
            saveGraph()
            val newScore = getMeshScore()
            Log.d("MeshGraph", "New meshScore: $newScore")
            notifyContactUpdate(meshId)
            Log.d("MeshGraph", "Emitted 'contact-update' event")
        } else {
            Log.d("MeshGraph", "Contact ${meshId.take(8)} already exists, skipping")
        }
    }

    fun addL2Connection(viaL1: String, childL2: String) {
        if (!l1Contacts.contains(viaL1)) return // Only track L2 from valid L1
        
        val children = l2Connections.getOrPut(viaL1) { mutableSetOf() }
        if (children.add(childL2)) {
            saveGraph()
            notifyL2Update(viaL1, childL2)
        }
    }

    fun storeReceivedInvite(invite: Invite) {
         receivedInvitesMap[invite.from] = invite
         saveGraph()
    }

    fun getInviteFrom(meshId: String): Invite? {
        return receivedInvitesMap[meshId]
    }

    fun markHashProcessed(hash: String): Boolean {
        if (processedHashes.contains(hash)) return false
        processedHashes.add(hash)
        saveGraph()
        return true
    }
    
    fun isHashProcessed(hash: String): Boolean = processedHashes.contains(hash)

    fun removeConnection(meshId: String) {
        var changed = false
        // 1. Remove Direct Connection (L1) -> Reduces score by 1.0
        if (l1Contacts.remove(meshId)) {
            changed = true
        }
        
        // 2. Remove Downstream Connections (L2) provided by this peer -> Reduces score by 0.3 * count
        if (l2Connections.containsKey(meshId)) {
            l2Connections.remove(meshId)
            changed = true
        }

        if (changed) {
            saveGraph()
        }
    }
}
