package com.mesh.client.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.mesh.client.data.EncryptedMessage
import org.webrtc.*

class WebRtcManager(
    private val context: Context,
    private val webSocketService: WebSocketService,
    private val myMeshId: String
) {

    interface Listener {
        fun onP2PMessageReceived(fromMeshId: String, message: EncryptedMessage)
        fun onP2PConnectionStateChange(peerId: String, isConnected: Boolean)
    }

    private val gson = Gson()
    private val factory: PeerConnectionFactory
    
    // Map peerId -> PeerConnection
    private val peers = mutableMapOf<String, PeerConnection>()
    // Map peerId -> DataChannel
    private val dataChannels = mutableMapOf<String, DataChannel>()
    
    var listener: Listener? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    )

    fun connectToPeer(peerId: String) {
        if (peers.containsKey(peerId)) return // Already connecting/connected

        val pcProcessor = PeerProcessor(peerId)
        val pc = factory.createPeerConnection(iceServers, pcProcessor) ?: return
        peers[peerId] = pc
        pcProcessor.pc = pc

        // IF we are initiating, create DataChannel
        // Simplification: We assume "connectToPeer" is called when we want to SEND message and no P2P exists.
        // We act as Offerer.
        val dcInit = DataChannel.Init()
        val dc = pc.createDataChannel("mesh-chat", dcInit)
        setupDataChannel(peerId, dc)
        
        pc.createOffer(pcProcessor, MediaConstraints())
    }

    fun handleSignaling(fromMeshId: String, type: String, payload: String?) {
        if (payload == null) return

        // If we received an Offer from someone we don't know, create PC
        if (type == "offer") {
            if (!peers.containsKey(fromMeshId)) {
                val pcProcessor = PeerProcessor(fromMeshId)
                val pc = factory.createPeerConnection(iceServers, pcProcessor) ?: return
                peers[fromMeshId] = pc
                pcProcessor.pc = pc
            }
        }
        
        val pc = peers[fromMeshId] ?: return
        val processor = pc // The observer is also the processor logic container in this simplified structure, 
                          // but actually we need to retrieve the generic observer attached.
                          // Refactoring: The PC observer is needed.
                          // Let's use `PeerProcessor` as the observer instance.
                          // Implementation detail: `peers` stores PC. We need to find the observer associated?
                          // Actually, we can just look at `peers` map. The `PeerProcessor` handles the callbacks.
                          // But to call `setRemoteDescription`, we need the PC instance.
        
        // We need the specific processor instance if we want to invoke createAnswer callbacks?
        // Let's just create generic SessionDescription for setRemote.
        
        when (type) {
            "offer" -> {
                val sdp = SessionDescription(SessionDescription.Type.OFFER, payload)
                pc.setRemoteDescription(PeerProcessor(fromMeshId).also { it.pc = pc }, sdp)
                // PeerProcessor (Observer) SetRemoteDescriptionObserver called -> createAnswer
                pc.createAnswer(PeerProcessor(fromMeshId).also { it.pc = pc }, MediaConstraints())
            }
            "answer" -> {
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, payload)
                pc.setRemoteDescription(PeerProcessor(fromMeshId).also { it.pc = pc }, sdp)
            }
            "ice" -> {
                // Parse candidate
                // Assuming payload is JSON of IceCandidate
                val candidate = gson.fromJson(payload, IceCandidateModel::class.java)
                pc.addIceCandidate(IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate))
            }
        }
    }

    fun sendP2PMessage(peerId: String, message: EncryptedMessage): Boolean {
        val dc = dataChannels[peerId]
        if (dc != null && dc.state() == DataChannel.State.OPEN) {
            val json = gson.toJson(message)
            val buffer = DataChannel.Buffer(
                java.nio.ByteBuffer.wrap(json.toByteArray()),
                false // binary? No, we send string json bytes
            )
            return dc.send(buffer)
        }
        return false
    }

    fun isConnected(peerId: String): Boolean {
        val dc = dataChannels[peerId]
        return dc != null && dc.state() == DataChannel.State.OPEN
    }
    
    // Setup DC observer
    private fun setupDataChannel(peerId: String, dc: DataChannel) {
        dataChannels[peerId] = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(p0: Long) {}
            override fun onStateChange() {
                val state = dc.state()
                Log.d(TAG, "DataChannel $peerId state: $state")
                listener?.onP2PConnectionStateChange(peerId, state == DataChannel.State.OPEN)
                if (state == DataChannel.State.CLOSED) {
                    dataChannels.remove(peerId)
                    peers.remove(peerId) // Cleanup?
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                val text = String(bytes)
                val msg = gson.fromJson(text, EncryptedMessage::class.java)
                listener?.onP2PMessageReceived(peerId, msg)
            }
        })
    }

    // Inner class handling PC events
    private inner class PeerProcessor(val peerId: String) : PeerConnection.Observer, SdpObserver {
        var pc: PeerConnection? = null

        // SdpObserver
        override fun onCreateSuccess(sdp: SessionDescription) {
            pc?.setLocalDescription(this, sdp)
            val typeStr = if (sdp.type == SessionDescription.Type.OFFER) "offer" else "answer"
            webSocketService.sendSignaling(peerId, typeStr, sdp.description)
        }
        override fun onSetSuccess() {}
        override fun onCreateFailure(s: String?) {}
        override fun onSetFailure(s: String?) {}

        // PeerConnection.Observer
        override fun onIceCandidate(candidate: IceCandidate) {
            val model = IceCandidateModel(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            webSocketService.sendSignaling(peerId, "ice", gson.toJson(model))
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onDataChannel(dc: DataChannel) {
            Log.d(TAG, "New DataChannel from $peerId")
            setupDataChannel(peerId, dc)
        }
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(b: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    }
    
    // Helper model for ICE JSON
    data class IceCandidateModel(val sdpMid: String, val sdpMLineIndex: Int, val candidate: String)

    companion object {
        private const val TAG = "WebRtcManager"
    }
}
