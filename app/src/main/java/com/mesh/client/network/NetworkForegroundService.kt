
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

class NetworkForegroundService : Service() {

    private val binder = LocalBinder()
    var webSocketService: WebSocketService? = null
        private set
    var chatTransport: ChatTransport? = null
        private set
    
    // Scopes
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    // Dependencies
    private lateinit var database: AppDatabase
    private lateinit var identityManager: IdentityManager
    private lateinit var graphManager: com.mesh.client.data.MeshGraphManager
    private lateinit var inviteManager: com.mesh.client.data.InviteManager

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

    private fun initStack(meshId: String) {
        if (webSocketService == null || webSocketService?.disconnect() == Unit) {
             webSocketService = WebSocketService(com.mesh.client.BuildConfig.SERVER_URL + "/ws", meshId)
             
             // Initialize Transport Stack
             val rtcManager = WebRtcManager(this, webSocketService!!, meshId)
             val cryptoManager = CryptoManager(identityManager)
             
             chatTransport = ChatTransport(cryptoManager, rtcManager, webSocketService!!, meshId)
             
             setupListeners(meshId)
             
             webSocketService?.connect()
        }
    }
    
    private fun setupListeners(myMeshId: String) {
        val transport = chatTransport ?: return
        val ws = webSocketService ?: return
        
        transport.messageListener = object : ChatTransport.MessageListener {
            override fun onMessageReceived(fromMeshId: String, text: String, timestamp: Long) {
                 serviceScope.launch {
                     // Save to DB
                     val msg = MessageEntity(
                         peerId = fromMeshId,
                         isIncoming = true,
                         text = text,
                         timestamp = timestamp,
                         status = "received" // Default to received, will be marked read when opened
                     )
                     database.messageDao().insertMessage(msg)
                     
                     // Ensure Contact
                     ensureContact(fromMeshId)

                     // Show Notification
                     val contact = database.contactDao().getContact(fromMeshId)
                     val notificationHelper = com.mesh.client.utils.NotificationHelper(this@NetworkForegroundService)
                     val contactName = contact?.nickname ?: "User ${fromMeshId.take(4)}"
                     notificationHelper.showNotification(contactName, text)
                 }
            }

            override fun onMessageStatusChanged(peerId: String, isP2P: Boolean) {
                // Broadcast or Flow update? 
                // For now, UI polls or we use a SharedFlow singleton if needed.
                // Keeping it simple.
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
                            val viaContact = contact?.nickname ?: "User ${fromMeshId.take(4)}"
                            val notificationHelper = com.mesh.client.utils.NotificationHelper(this@NetworkForegroundService)
                            notificationHelper.showNotification("New L2 Connection", "Discovered via $viaContact")
                        }
                    } catch (e: Exception) {
                        Log.e("NetworkService", "Error processing L2 notify", e)
                    }
                }
            }
            
            override fun onTransportError(message: String) {
                Log.e("NetworkService", "Transport Error: $message")
            }
        }
        
        ws.listener = object : WebSocketService.Listener {
            override fun onSignalingMessage(fromMeshId: String, type: String, payload: String?) {
                transport.onSignalingMessage(fromMeshId, type, payload)
            }
            override fun onEncryptedMessageReceived(fromMeshId: String, message: com.mesh.client.data.EncryptedMessage) {
                transport.onEncryptedMessageReceived(fromMeshId, message)
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
        val newNick = nickname ?: "User ${meshId.take(4)}"

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
    
    fun sendMessage(toPeerId: String, text: String) {
        serviceScope.launch {
            chatTransport?.sendMessage(toPeerId, text)
            // Save outgoing
            database.messageDao().insertMessage(
                MessageEntity(
                    peerId = toPeerId,
                    isIncoming = false,
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    status = "sent"
                )
            )
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
            .setContentTitle("Mesh")
            .setContentText("Connected to Mesh network")
            .setSmallIcon(android.R.drawable.ic_dialog_info) 
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketService?.disconnect()
        serviceJob.cancel()
    }
}

