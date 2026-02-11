
package com.mesh.client.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mesh.client.MeshApplication
import com.mesh.client.data.InviteRedemptionManager
import com.mesh.client.data.LocalStorage
import com.mesh.client.data.MeshGraphManager
import com.mesh.client.data.db.AppDatabase
import com.mesh.client.identity.IdentityManager
import com.mesh.client.network.NetworkForegroundService
import com.mesh.client.network.WebRtcManager
import com.mesh.client.transport.ChatTransport
import com.mesh.client.utils.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MeshViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    
    // Core Components
    val identityManager = IdentityManager(context)
    val graphManager = MeshGraphManager(context)
    private val database = (application as MeshApplication).database
    // Helper wrapper for invite logic (still used for generating invites UI side if needed, though service handles receiving)
    private val inviteRedemptionManager = InviteRedemptionManager(context)
    private val notificationHelper = NotificationHelper(context)
    
    // Service Binding
    private var networkService: NetworkForegroundService? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as NetworkForegroundService.LocalBinder
            networkService = binder.getService()
            
            networkService?.callListener = object : NetworkForegroundService.CallListener {
                override fun onIncomingCall(peerId: String) {
                    Log.d("MeshViewModel", "Incoming call from $peerId")
                    if (_callState.value != CallState.IDLE) {
                        Log.d("MeshViewModel", "Already in call/calling, ignoring incoming call")
                        return
                    }
                    viewModelScope.launch {
                        _currentCallPeerId.value = peerId
                        _callState.value = CallState.INCOMING
                    }
                }

                override fun onCallEnded() {
                    Log.d("MeshViewModel", "Call ended by remote")
                    viewModelScope.launch {
                        _callState.value = CallState.IDLE
                        _currentCallPeerId.value = null
                    }
                }
            }
            
            _isConnected.value = true
            Log.d("MeshViewModel", "Service Bound")

        }

        override fun onServiceDisconnected(name: ComponentName?) {
             networkService = null
             _isConnected.value = false
             Log.d("MeshViewModel", "Service Disconnected")
        }
    }

    // UI State
    private val _messages = MutableStateFlow<List<LocalStorage.StoredMessage>>(emptyList())
    val messages: StateFlow<List<LocalStorage.StoredMessage>> = _messages.asStateFlow()
    
    private val _contacts = MutableStateFlow<List<LocalStorage.Contact>>(emptyList())
    val contacts: StateFlow<List<LocalStorage.Contact>> = _contacts.asStateFlow()
    
    private val _p2pStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val p2pStatus: StateFlow<Map<String, Boolean>> = _p2pStatus.asStateFlow()
    
    private val _meshId = MutableStateFlow<String?>(null)
    val meshId: StateFlow<String?> = _meshId.asStateFlow()
    
    private val _localNickname = MutableStateFlow("User")
    val localNickname: StateFlow<String> = _localNickname.asStateFlow()
    
    private val _meshScore = MutableStateFlow<Double>(0.0)
    val meshScore: StateFlow<Double> = _meshScore.asStateFlow()

    private val _l1Items = MutableStateFlow<Set<String>>(emptySet())
    val l1Items: StateFlow<Set<String>> = _l1Items.asStateFlow()
    
    private val _l2Items = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val l2Items: StateFlow<Map<String, Set<String>>> = _l2Items.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents: SharedFlow<String> = _errorEvents

    // Call State
    // Call State
    enum class CallState { IDLE, CALLING, INCOMING, CONNECTED }
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()
    
    private val _currentCallPeerId = MutableStateFlow<String?>(null)
    val currentCallPeerId: StateFlow<String?> = _currentCallPeerId.asStateFlow()
    
    private var callStartTime: Long = 0L

    init {
        checkIdentity()
        observeContacts()
        setupGraphListeners()
    }
    
    private fun setupGraphListeners() {
        graphManager.addListener(object : com.mesh.client.data.MeshGraphManager.GraphChangeListener {
            override fun onContactUpdate(meshId: String) {
                Log.d("MeshViewModel", "contact-update event received for ${meshId.take(8)}")
                val newScore = graphManager.getMeshScore()
                Log.d("MeshViewModel", "Updating meshScore to $newScore")
                _meshScore.value = newScore
                _l1Items.value = graphManager.getL1Connections()
            }
            
            override fun onL2Update(via: String, child: String) {
                Log.d("MeshViewModel", "L2 connection discovered via ${via.take(8)}")
                val newScore = graphManager.getMeshScore()
                _meshScore.value = newScore
                _l2Items.value = graphManager.getL2Connections()
            }
        })
    }

    fun checkIdentity() {
        try {
             if (identityManager.hasIdentity()) {
                 _meshId.value = identityManager.getMeshId()
                 _localNickname.value = identityManager.getLocalNickname()
                 _meshScore.value = graphManager.getMeshScore()
                 _l1Items.value = graphManager.getL1Connections()
                 _l2Items.value = graphManager.getL2Connections()
                 
                 startNetworkService()
                 
                 processPendingInvite()
                 
                 // Check for deferred deep link invite
                 inviteRedemptionManager.checkDeferredInvite(_meshId.value!!) { inviterId ->
                     Log.i("MeshViewModel", "Deferred invite found from $inviterId")
                     handleInvite(inviterId)
                 }
             } else {
                 _meshId.value = null
             }
        } catch (e: Exception) {
            Log.e("MeshViewModel", "Identity check failed", e)
            _meshId.value = null
        }
    }
    
    private fun startNetworkService() {
        val myId = _meshId.value ?: return
        val intent = Intent(context, NetworkForegroundService::class.java).apply {
            putExtra("mesh_id", myId)
        }
        // Start Foreground Service
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        // Bind
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun observeContacts() {
        viewModelScope.launch {
            database.contactDao().getContactsWithPreview().collect { entities ->
                _contacts.value = entities.map { 
                    LocalStorage.Contact(
                        meshId = it.contact.meshId, 
                        nickname = it.contact.nickname,
                        lastMessage = it.lastMessageText,
                        lastMessageTime = it.lastMessageTime,
                        unreadCount = it.unreadCount
                    ) 
                }
                // Also update graph UI potentially?
                _l1Items.value = graphManager.getL1Connections()
                _meshScore.value = graphManager.getMeshScore()
            }
        }
    }
    
    private var messageJob: Job? = null
    
    fun loadMessages(peerId: String) {
        networkService?.activeChatPeerId = peerId
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            // We do NOT mark as read here immediately. The UI will trigger it.
            
            database.messageDao().getMessagesForPeer(peerId).collect { entities ->
                _messages.value = entities.map { 
                    LocalStorage.StoredMessage(
                        it.id,
                        it.peerId, 
                        it.isIncoming, 
                        it.text, 
                        it.timestamp,
                        it.transportType,
                        it.reaction,
                        it.replyToId,
                        it.replyToText
                    ) 
                }
            }
        }
    }

    fun markAsRead(peerId: String) {
        viewModelScope.launch {
            database.messageDao().markMessagesAsRead(peerId)
        }
    }

    fun clearActiveChat() {
        networkService?.activeChatPeerId = null
    }

    fun updateP2PStatus(peerId: String, isP2P: Boolean) {
        val currentMap = _p2pStatus.value.toMutableMap()
        currentMap[peerId] = isP2P
        _p2pStatus.value = currentMap
        Log.d("MeshViewModel", "P2P status updated for ${peerId.take(8)}: $isP2P")
    }

    private fun refreshMessages(peerId: String) {
        // This will trigger the observer in loadMessages if it's active for this peerId
        // Or, if not active, it will just update the DB.
        // For now, we'll just rely on the DB observer.
        // If we need to force a refresh for the current chat, we'd need to re-collect.
        // For simplicity, let's assume the current chat's observer is always active.
    }

    private suspend fun ensureContact(peerId: String) {
        if (database.contactDao().getContact(peerId) == null) {
            database.contactDao().insertContact(
                com.mesh.client.data.db.entities.ContactEntity(peerId, "User ${peerId.take(4)}")
            )
        }
    }

    fun sendMessage(toPeerId: String, text: String, replyToId: Long? = null, replyToText: String? = null) {
        networkService?.sendMessage(toPeerId, text, replyToId, replyToText)
         // Optimistically update UI? The DB observer will handle it, but might be slight delay.
         // DB observer is fast enough.
    }

    fun reactToMessage(messageId: Long, emoji: String) {
        viewModelScope.launch {
             val msg = database.messageDao().getMessageById(messageId)
             if (msg != null) {
                 database.messageDao().insertMessage(msg.copy(reaction = emoji))
             }
        }
    }

    fun startCall(peerId: String) {
        if (_callState.value != CallState.IDLE) return
        
        _currentCallPeerId.value = peerId
        _callState.value = CallState.CONNECTED // Simplified: assume connected immediately for now as per previous step
        callStartTime = System.currentTimeMillis()
        
        networkService?.startCall(peerId)
    }
    
    fun endCall() {
        val peerId = _currentCallPeerId.value ?: return
        if (_callState.value == CallState.IDLE) return
        
        val durationMs = System.currentTimeMillis() - callStartTime
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val durationStr = String.format("%02d:%02d", minutes, seconds)
        
        val logMessage = "Audio call $durationStr"
        
        viewModelScope.launch {
            database.messageDao().insertMessage(
                 com.mesh.client.data.db.entities.MessageEntity(
                     peerId = peerId,
                     isIncoming = false, // outgoing call log
                     text = logMessage,
                     timestamp = System.currentTimeMillis(),
                     status = "call_log"
                 )
            )
        }

        _callState.value = CallState.IDLE
        _currentCallPeerId.value = null
        networkService?.sendHangup(peerId)
    }

    fun acceptCall() {
        val peerId = _currentCallPeerId.value ?: return
        if (_callState.value != CallState.INCOMING) return
        
        _callState.value = CallState.CONNECTED
        callStartTime = System.currentTimeMillis()
        
        // Start Audio
        networkService?.startCall(peerId)
    }

    fun declineCall() {
        val peerId = _currentCallPeerId.value
        _callState.value = CallState.IDLE
        _currentCallPeerId.value = null
        if (peerId != null) {
            networkService?.sendHangup(peerId)
        }
    }
    
    fun addContact(peerId: String, nickname: String) {
        viewModelScope.launch {
            // 1. Add to Graph (Increases Score)
            graphManager.addL1Connection(peerId)
            
            // 2. Add to DB if new
            if (database.contactDao().getContact(peerId) == null) {
                 database.contactDao().insertContact(
                     com.mesh.client.data.db.entities.ContactEntity(peerId, nickname)
                 )
            }

            // 3. Update State
            _meshScore.value = graphManager.getMeshScore()
            _l1Items.value = graphManager.getL1Connections()
        }
    }
    
    fun deleteContact(meshId: String) {
        viewModelScope.launch {
            // 1. Remove from Graph (Decreases Score)
            graphManager.removeConnection(meshId)
            
            // 2. Remove from DB
            database.contactDao().deleteContact(meshId)
            
            // 3. Update State (Flows should trigger automatically if observing graph/db)
            _meshScore.value = graphManager.getMeshScore()
            _l1Items.value = graphManager.getL1Connections()
            _l2Items.value = graphManager.getL2Connections()
        }
    }

    fun renameContact(meshId: String, newNickname: String) {
        viewModelScope.launch {
            database.contactDao().insertContact(
                com.mesh.client.data.db.entities.ContactEntity(meshId, newNickname)
            )
        }
    }
    
    fun handleInvite(inviterMeshId: String, nickname: String? = null) {
        val myId = _meshId.value
        if (myId == null) {
            inviteRedemptionManager.storePendingInvite(inviterMeshId, nickname)
            return
        }
        if (inviterMeshId == myId) return
        
        // Ensure Contact
        val contactName = if (!nickname.isNullOrBlank()) nickname else "User ${inviterMeshId.take(4)}"
        addContact(inviterMeshId, contactName)
        inviteRedemptionManager.markInviteProcessed(inviterMeshId)

        // Send via Service
        networkService?.sendInvite(inviterMeshId)
    }
    
    private fun processPendingInvite() {
        val pending = inviteRedemptionManager.getPendingInvite()
        if (pending != null) {
            val (inviterMeshId, nickname) = pending
            handleInvite(inviterMeshId, nickname)
            inviteRedemptionManager.clearPendingInvite()
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
    
    // Methods delegating to identity manager
    fun getSeedPhrase(): String = identityManager.exportMnemonic() ?: identityManager.exportSeedHex()
    
    fun createIdentity() {
        identityManager.getIdentityKeyPair()
        checkIdentity()
    }
    
    fun createFromMnemonic(mnemonic: String) {
        identityManager.createFromMnemonic(mnemonic)
        checkIdentity()
    }
    
    fun restoreIdentity(seedHex: String) {
        identityManager.restoreIdentity(seedHex)
        checkIdentity()
    }
    
    fun restoreFromMnemonic(mnemonic: String) {
        identityManager.restoreFromMnemonic(mnemonic)
        checkIdentity()
    }
    
    fun updateLocalNickname(newName: String) {
        identityManager.setLocalNickname(newName)
        _localNickname.value = newName
    }
}
