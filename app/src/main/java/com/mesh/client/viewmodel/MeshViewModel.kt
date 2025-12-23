package com.mesh.client.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mesh.client.crypto.CryptoManager
import com.mesh.client.data.LocalStorage
import com.mesh.client.data.MeshGraphManager
import com.mesh.client.identity.IdentityManager
import com.mesh.client.network.WebSocketService
import com.mesh.client.network.WebRtcManager
import com.mesh.client.transport.ChatTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeshViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    
    // Core Components
    val identityManager = IdentityManager(context)
    val graphManager = MeshGraphManager(context)
    
    // Lazy initialization for network components since they need Identity to be ready
    private var _chatTransport: ChatTransport? = null
    val chatTransport: ChatTransport? get() = _chatTransport

    // UI State
    private val _messages = MutableStateFlow<List<LocalStorage.StoredMessage>>(emptyList())
    val messages: StateFlow<List<LocalStorage.StoredMessage>> = _messages.asStateFlow()
    
    private val _contacts = MutableStateFlow<List<LocalStorage.Contact>>(emptyList())
    val contacts: StateFlow<List<LocalStorage.Contact>> = _contacts.asStateFlow()
    
    // Connection Status: Map<PeerID, IsP2P>
    private val _p2pStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val p2pStatus: StateFlow<Map<String, Boolean>> = _p2pStatus.asStateFlow()
    
    private val _meshId = MutableStateFlow<String?>(null)
    val meshId: StateFlow<String?> = _meshId.asStateFlow()
    
    private val _meshScore = MutableStateFlow<Double>(0.0)
    val meshScore: StateFlow<Double> = _meshScore.asStateFlow()

    // Graph Visualization Data
    private val _l1Items = MutableStateFlow<Set<String>>(emptySet())
    val l1Items: StateFlow<Set<String>> = _l1Items.asStateFlow()
    
    private val _l2Items = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val l2Items: StateFlow<Map<String, Set<String>>> = _l2Items.asStateFlow()

    init {
        checkIdentity()
    }

    fun checkIdentity() {
        try {
             if (identityManager.hasIdentity()) {
                 _meshId.value = identityManager.getMeshId()
                 _meshScore.value = graphManager.getMeshScore()
                 _l1Items.value = graphManager.getL1Connections()
                 _l2Items.value = graphManager.getL2Connections()
                 startServices()
             } else {
                 _meshId.value = null
             }
        } catch (e: Exception) {
            Log.e("MeshViewModel", "Identity check failed", e)
            _meshId.value = null
        }
    }
    
    fun getSeedPhrase(): String {
        return identityManager.exportMnemonic() ?: identityManager.exportSeedHex()
    }
    
    fun createIdentity() {
        identityManager.getIdentityKeyPair()
        _meshId.value = identityManager.getMeshId()
        startServices()
    }
    
    fun createFromMnemonic(mnemonic: String) {
        identityManager.createFromMnemonic(mnemonic)
        _meshId.value = identityManager.getMeshId()
        startServices()
    }
    
    fun restoreIdentity(seedHex: String) {
        identityManager.restoreIdentity(seedHex)
        _meshId.value = identityManager.getMeshId()
        startServices()
    }
    
    fun restoreFromMnemonic(mnemonic: String) {
        identityManager.restoreFromMnemonic(mnemonic)
        _meshId.value = identityManager.getMeshId()
        startServices()
    }

    private fun startServices() {
        val myId = _meshId.value ?: return
        
        // Init Services
        val wsService = WebSocketService("ws://10.0.2.2:8080", myId) // Localhost emulator
        val rtcManager = WebRtcManager(context, wsService, myId)
        val cryptoManager = CryptoManager(identityManager)
        
        val transport = ChatTransport(cryptoManager, rtcManager, wsService, myId)
        _chatTransport = transport
        
        // Listeners
        transport.messageListener = object : ChatTransport.MessageListener {
            override fun onMessageReceived(fromMeshId: String, text: String, timestamp: Long) {
                 LocalStorage.saveMessage(fromMeshId, true, text, timestamp)
                 refreshMessages(fromMeshId)
                 ensureContact(fromMeshId)
            }

            override fun onMessageStatusChanged(peerId: String, isP2P: Boolean) {
                val newMap = _p2pStatus.value.toMutableMap()
                newMap[peerId] = isP2P
                _p2pStatus.value = newMap
            }
        }
        
        wsService.connect()
        refreshContacts()
    }
    
    fun sendMessage(toPeerId: String, text: String) {
        _chatTransport?.sendMessage(toPeerId, text)
        LocalStorage.saveMessage(toPeerId, false, text, System.currentTimeMillis())
        refreshMessages(toPeerId)
        ensureContact(toPeerId)
    }
    
    fun loadMessages(peerId: String) {
        refreshMessages(peerId)
    }
    
    private fun refreshMessages(peerId: String) {
        val msgs = LocalStorage.getMessages(peerId)
        _messages.value = msgs
    }
    
    private fun refreshContacts() {
        // LocalStorage contacts API needed
        // For MVP we just use the map. Assuming LocalStorage exposes values or we add a method.
        // Let's rely on ensuring contacts for now or add get method.
        // Quick update to LocalStorage: add getContacts()
        // Or access map directly if open? No, it's private.
        // I'll add getContacts() to LocalStorage in next step or use simple workaround.
    }
    
    private fun ensureContact(peerId: String) {
        if (LocalStorage.getContact(peerId) == null) {
            LocalStorage.saveContact(peerId, "User ${peerId.take(4)}")
            _contacts.value = _contacts.value + LocalStorage.Contact(peerId, "User ${peerId.take(4)}")
        }
    }
    
    fun addContact(peerId: String, nickname: String) {
        LocalStorage.saveContact(peerId, nickname)
        // Refresh flow
        val current = _contacts.value
        if (current.none { it.meshId == peerId }) {
            _contacts.value = current + LocalStorage.Contact(peerId, nickname)
        }
    }
    
    fun handleInvite(peerId: String) {
        val myId = _meshId.value ?: return
        if (peerId == myId) return // Cannot invite self
        
        // Add as contact if not exists
        if (contacts.value.none { it.meshId == peerId }) {
            addContact(peerId, "User ${peerId.take(4)}")
        }
    }

    
    fun getSignalLevel(score: Double): Int {
        return when {
            score >= 50.0 -> 5
            score >= 25.0 -> 4
            score >= 10.0 -> 3
            score >= 3.0 -> 2
            score >= 1.0 -> 1
            else -> 0
        }
    }
}

