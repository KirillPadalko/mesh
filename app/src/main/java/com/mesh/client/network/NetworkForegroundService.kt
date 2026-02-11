
package com.mesh.client.network

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mesh.client.crypto.CryptoManager
import com.mesh.client.data.db.AppDatabase
import com.mesh.client.data.db.entities.ContactEntity
import com.mesh.client.data.db.entities.MessageEntity
import com.mesh.client.identity.IdentityManager
import com.mesh.client.transport.ChatTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager

class NetworkForegroundService : Service() {

    interface CallListener {
        fun onIncomingCall(peerId: String)
        fun onCallEnded()
    }

    interface P2PStatusListener {
        fun onP2PStatusChanged(peerId: String, isP2P: Boolean)
    }

    private val binder = LocalBinder()
    var callListener: CallListener? = null
    var p2pStatusListener: P2PStatusListener? = null
    var webSocketService: WebSocketService? = null
        private set
    var chatTransport: ChatTransport? = null
        private set
    
    // Scopes
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    // UI Tracking
    var activeChatPeerId: String? = null
    
    // Dependencies
    private lateinit var database: AppDatabase
    private lateinit var identityManager: IdentityManager
    private lateinit var graphManager: com.mesh.client.data.MeshGraphManager
    private lateinit var inviteManager: com.mesh.client.data.InviteManager
    
    // WakeLock to keep CPU running
    private var wakeLock: PowerManager.WakeLock? = null

    inner class LocalBinder : Binder() {
        fun getService(): NetworkForegroundService = this@NetworkForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        identityManager = IdentityManager(this)
        graphManager = com.mesh.client.data.MeshGraphManager(this)
        val signer = com.mesh.client.crypto.MeshSigner(identityManager)
        inviteManager = com.mesh.client.data.InviteManager(identityManager, signer, graphManager, database.contactDao())
        
        startNetworkMonitor()
        acquireWakeLock()
    }
    
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mesh:NetworkKeepAlive")
            wakeLock?.setReferenceCounted(false)
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire()
            Log.d("NetworkService", "WakeLock acquired")
        }
    }
    
    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d("NetworkService", "WakeLock released")
        }
    }

    private fun startNetworkMonitor() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                Log.i("NetworkService", "Network Available: $network")
                // Force Re-connect WebSocket 
                webSocketService?.reconnect()
                // Restart P2P connections to handle IP change
                chatTransport?.webRtcManager?.restartAllIce()
            }

            override fun onLost(network: android.net.Network) {
                Log.w("NetworkService", "Network Lost: $network")
            }
        })
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val meshId = intent?.getStringExtra("mesh_id")
        if (meshId != null) {
            startForegroundService()
            initStack(meshId)
        }
        return START_STICKY
    }

    private var currentMeshId: String? = null

    private fun initStack(meshId: String) {
        if (webSocketService != null && currentMeshId == meshId) {
             Log.d("NetworkService", "Stack already initialized for $meshId")
             // Ensure connected
             webSocketService?.connect()
             return
        }
        
        Log.d("NetworkService", "Initializing Stack for $meshId")
        
        // Teardown existing
        webSocketService?.disconnect()
        
        currentMeshId = meshId
        webSocketService = WebSocketService(com.mesh.client.BuildConfig.SERVER_URL + "/ws", meshId)
             
        // Initialize Transport Stack
        val rtcManager = WebRtcManager(this, webSocketService!!, meshId)
        val cryptoManager = CryptoManager(identityManager)
             
        chatTransport = ChatTransport(
            cryptoManager, 
            rtcManager, 
            webSocketService!!, 
            meshId,
            database.outgoingMessageDao(),
            serviceScope
        )
             
        setupListeners(meshId)
             
        webSocketService?.connect()
    }
    
    private fun setupListeners(myMeshId: String) {
        val transport = chatTransport ?: return
        val ws = webSocketService ?: return
        
        transport.messageListener = object : ChatTransport.MessageListener {
            override fun onMessageReceived(fromMeshId: String, text: String, timestamp: Long, transportType: String) {
                 serviceScope.launch {
                     // Ensure Contact First
                     ensureContact(fromMeshId)

                     // Save to DB
                     val msg = MessageEntity(
                         peerId = fromMeshId,
                         isIncoming = true,
                         text = text,
                         timestamp = System.currentTimeMillis(), // Use local time as per user request
                         status = "received",
                         transportType = transportType
                     )
                     database.messageDao().insertMessage(msg)

                     // Show Notification ONLY if chat with this user is NOT currently open
                     if (fromMeshId != activeChatPeerId) {
                         val contact = database.contactDao().getContact(fromMeshId)
                         val notificationHelper = com.mesh.client.utils.NotificationHelper(this@NetworkForegroundService)
                         val contactName = contact?.nickname ?: getString(com.mesh.client.R.string.user_prefix, fromMeshId.take(4))
                         notificationHelper.showNotification(contactName, text)
                     } else {
                         Log.d("NetworkService", "Skipping notification for active chat with $fromMeshId")
                     }
                 }
            }

            override fun onMessageStatusChanged(peerId: String, isP2P: Boolean) {
                Log.d("NetworkService", "P2P status changed for ${peerId.take(8)}: $isP2P")
                p2pStatusListener?.onP2PStatusChanged(peerId, isP2P)
            }
            
            override fun onInviteReceived(fromMeshId: String, inviteJson: String) {
                serviceScope.launch {
                    try {
                        val invite = com.google.gson.Gson().fromJson(inviteJson, com.mesh.client.data.protocol.Invite::class.java)
                        val ack = inviteManager.processInvite(invite)
                        if (ack != null) {
                            Log.i("NetworkService", "Auto-accepting invite from $fromMeshId")
                            val ackJson = com.google.gson.Gson().toJson(ack)
                            transport.sendInviteAck(fromMeshId, ackJson)
                            graphManager.addL1Connection(fromMeshId)
                            ensureContact(fromMeshId, invite.nickname)
                        }
                    } catch (e: Exception) {
                        Log.e("NetworkService", "Error processing invite", e)
                    }
                }
            }

            override fun onInviteAckReceived(fromMeshId: String, ackJson: String) {
               serviceScope.launch {
                   Log.i("NetworkService", "ACK from $fromMeshId")
                   try {
                       val ack = com.google.gson.Gson().fromJson(ackJson, com.mesh.client.data.protocol.InviteAck::class.java)
                       ensureContact(fromMeshId, ack.nickname)
                   } catch (e: Exception) {
                       ensureContact(fromMeshId)
                   }
                   graphManager.addL1Connection(fromMeshId)
               }
            }
            
            override fun onL2NotifyReceived(fromMeshId: String, notifyJson: String) {
                serviceScope.launch {
                    try {
                        val notify = com.google.gson.Gson().fromJson(notifyJson, com.mesh.client.data.protocol.L2Notify::class.java)
                        val isValid = inviteManager.processL2Notify(notify)
                        if (isValid) {
                            Log.i("NetworkService", "L2 connection discovered via ${fromMeshId.take(4)}")
                            
                            // Show notification for L2 discovery
                            val contact = database.contactDao().getContact(fromMeshId)
                            val viaContact = contact?.nickname ?: getString(com.mesh.client.R.string.user_prefix, fromMeshId.take(4))
                            val notificationHelper = com.mesh.client.utils.NotificationHelper(this@NetworkForegroundService)
                            notificationHelper.showNotification(
                                getString(com.mesh.client.R.string.new_l2_connection),
                                getString(com.mesh.client.R.string.discovered_via, viaContact)
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("NetworkService", "Error processing L2 notify", e)
                    }
                }
            }
            
            override fun onTransportError(message: String) {
                Log.e("NetworkService", "Transport Error: $message")
            }
            
            override fun onIncomingCall(peerId: String) {
                serviceScope.launch {
                     Log.i("NetworkService", "Incoming Call from $peerId")
                     callListener?.onIncomingCall(peerId)
                     
                     // Also show notification
                     val contact = database.contactDao().getContact(peerId)
                     val name = contact?.nickname ?: getString(com.mesh.client.R.string.user_prefix, peerId.take(4))
                     val notificationHelper = com.mesh.client.utils.NotificationHelper(this@NetworkForegroundService)
                     notificationHelper.showCallNotification(name, getString(com.mesh.client.R.string.incoming_call_notification))
                }
            }

            override fun onHangupReceived(peerId: String) {
                Log.i("NetworkService", "Hangup received for $peerId")
                callListener?.onCallEnded()
            }

            override fun onMessageDelivered(msgId: String) {
                Log.d("NetworkService", "Message $msgId confirmed via P2P ACK")
                // Here we could update DB status to "delivered_p2p" if needed
            }
        }
        
        ws.listener = object : WebSocketService.Listener {
            override fun onSignalingMessage(fromMeshId: String, type: String, payload: String?) {
                transport.onSignalingMessage(fromMeshId, type, payload)
            }
            override fun onEncryptedMessageReceived(fromMeshId: String, message: com.mesh.client.data.EncryptedMessage) {
                transport.onEncryptedMessageReceived(fromMeshId, message)
            }
            override fun onDeliveryAck(msgId: String, status: String) {
                transport.handleServerAck(msgId, status)
            }
            override fun onConnected() {
                transport.onConnected()
            }
            override fun onDisconnected() {
                transport.onDisconnected()
            }
            override fun onError(message: String) {
                Log.e("NetworkService", "WS Error: $message")
            }
        }
    }
    
    private suspend fun ensureContact(meshId: String, nickname: String? = null) {
        val existing = database.contactDao().getContact(meshId)
        val newNick = nickname ?: getString(com.mesh.client.R.string.user_prefix, meshId.take(4))

        if (existing == null) {
            database.contactDao().insertContact(
                ContactEntity(meshId = meshId, nickname = newNick)
            )
        } else {
             // Update if specific nickname provided and different
             if (nickname != null && existing.nickname != nickname) {
                 database.contactDao().insertContact(
                     existing.copy(nickname = nickname)
                 )
             }
        }
    }
    
    fun sendMessage(toPeerId: String, text: String, replyToId: Long? = null, replyToText: String? = null) {
        serviceScope.launch {
            val tc = chatTransport ?: return@launch
            val isP2p = tc.webRtcManager.isConnected(toPeerId)
            val transportType = if (isP2p) "p2p" else "server"

            tc.sendMessage(toPeerId, text)
            // Save outgoing
            database.messageDao().insertMessage(
                MessageEntity(
                    peerId = toPeerId,
                    isIncoming = false,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    status = "sent",
                    transportType = transportType,
                    replyToId = replyToId,
                    replyToText = replyToText
                )
            )
        }
    }

    fun startCall(toPeerId: String) {
        serviceScope.launch {
            chatTransport?.startCall(toPeerId)
        }
    }

    fun sendHangup(toPeerId: String) {
        serviceScope.launch {
            chatTransport?.sendHangup(toPeerId)
        }
    }
    
    fun sendInvite(toPeerId: String) {
        serviceScope.launch {
             val invite = inviteManager.createInvite(toPeerId)
             val inviteJson = com.google.gson.Gson().toJson(invite)
             chatTransport?.sendInvite(toPeerId, inviteJson)
             // Optimistic add
             graphManager.addL1Connection(toPeerId)
             ensureContact(toPeerId)
        }
    }

    private fun startForegroundService() {
        val channelId = "mesh_connection_service"
        val channelName = "Mesh Connection Service"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(com.mesh.client.R.string.app_name))
            .setContentText(getString(com.mesh.client.R.string.connected_to_mesh))
            .setSmallIcon(android.R.drawable.ic_dialog_info) 
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketService?.disconnect()
        serviceJob.cancel()
        releaseWakeLock()
    }
}

