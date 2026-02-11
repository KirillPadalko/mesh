package com.mesh.client.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.mesh.client.data.EncryptedMessage
import com.google.gson.annotations.SerializedName
import org.webrtc.*

class WebRtcManager(
    private val context: Context,
    private val webSocketService: WebSocketService,
    private val myMeshId: String
) {

    interface Listener {
        fun onP2PMessageReceived(fromMeshId: String, message: EncryptedMessage)
        fun onP2PConnectionStateChange(peerId: String, isConnected: Boolean)
        fun onIncomingCall(peerId: String) {}
    }

    private open class SimpleSdpObserver(
        private val successCallback: (SessionDescription) -> Unit = {},
        private val setSuccessCallback: () -> Unit = {}
    ) : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription) = successCallback(sdp)
        override fun onSetSuccess() = setSuccessCallback()
        override fun onCreateFailure(s: String?) { Log.e("WebRtcManager", "SDP Create Failure: $s") }
        override fun onSetFailure(s: String?) { Log.e("WebRtcManager", "SDP Set Failure: $s") }
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
        // STUN servers for NAT discovery
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        
        // Free public TURN servers for NAT traversal (critical for audio calls)
        // OpenRelay - free TURN server
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:80")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:openrelay.metered.ca:443")
            .setUsername("openrelayproject")
            .setPassword("openrelayproject")
            .createIceServer(),
        
        // Metered TURN - free tier with good reliability
        PeerConnection.IceServer.builder("turn:a.relay.metered.ca:80")
            .setUsername("f3c3f3f5f3f5f3f5f3f5f3f5")
            .setPassword("f3c3f3f5f3f5f3f5f3f5f3f5")
            .createIceServer(),
        PeerConnection.IceServer.builder("turn:a.relay.metered.ca:443")
            .setUsername("f3c3f3f5f3f5f3f5f3f5f3f5")
            .setPassword("f3c3f3f5f3f5f3f5f3f5f3f5")
            .createIceServer()
    )

    private val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        keyType = PeerConnection.KeyType.ECDSA
        bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        iceCandidatePoolSize = 8 // Increased for better connectivity while avoiding candidate storm
    }

    fun connectToPeer(peerId: String) {
        val existingPc = peers[peerId]
        if (existingPc != null) {
            val state = existingPc.iceConnectionState()
            // Robust check: don't restart if it's already busy doing something useful
            if (state != PeerConnection.IceConnectionState.FAILED && 
                state != PeerConnection.IceConnectionState.CLOSED &&
                state != PeerConnection.IceConnectionState.DISCONNECTED) {
                Log.d(TAG, "connectToPeer: $peerId already active ($state), skipping")
                return
            }
            Log.d(TAG, "connectToPeer: Cleaning up unusable existing connection ($state) for $peerId")
            cleanupPeer(peerId)
        }
        
        Log.d(TAG, "connectToPeer: Initiating NEW connection to $peerId")
        val pcProcessor = PeerProcessor(peerId)
        val pc = factory.createPeerConnection(rtcConfig, pcProcessor) ?: return
        peers[peerId] = pc
        pcProcessor.pc = pc
        
        Log.d(TAG, "Creating DataChannel for $peerId")
        val dcInit = DataChannel.Init()
        val dc = pc.createDataChannel("mesh-chat", dcInit)
        setupDataChannel(peerId, dc)
    }

    fun restartIce(peerId: String) {
        Log.i(TAG, "Restarting ICE for $peerId due to connectivity issues")
        cleanupPeer(peerId)
        connectToPeer(peerId)
    }

    fun stopCall(peerId: String) {
        Log.d(TAG, "stopCall for $peerId")
        localAudioTrack?.setEnabled(false)
        val pc = peers[peerId] ?: return
        
        // Remove audio senders
        val senders = pc.senders
        for (sender in senders) {
            val track = sender.track()
            if (track != null && track.kind() == "audio") {
                pc.removeTrack(sender)
            }
        }
    }

    private fun cleanupPeer(peerId: String) {
        Log.d(TAG, "Cleaning up PeerConnection and DataChannel for $peerId")
        dataChannels.remove(peerId)?.unregisterObserver()
        peers.remove(peerId)?.dispose()
    }

    fun restartAllIce() {
        Log.i(TAG, "Restarting ALL ICE connections due to network change")
        val activePeers = peers.keys.toList()
        for (peerId in activePeers) {
            restartIce(peerId)
        }
    }

    private fun setupDataChannel(peerId: String, dc: DataChannel) {
        Log.d(TAG, "setupDataChannel (internal) for $peerId")
        dataChannels[peerId] = dc
        dc.registerObserver(DataChannelObserver(peerId, dc))
    }

    fun handleSignaling(fromMeshId: String, type: String, payload: String?) {
        Log.d(TAG, "handleSignaling from $fromMeshId: type=$type")
        if (payload == null) return

        val pc = peers[fromMeshId]
        val sigState = pc?.signalingState()
        
        // Glare handling
        if (type == "offer" || type == "webrtc_offer") {
            val isStable = pc == null || sigState == PeerConnection.SignalingState.STABLE
            val isImpolite = myMeshId > fromMeshId
            
            if (!isStable && isImpolite) {
                Log.w(TAG, "Glare detected. I am impolite, ignoring incoming offer from $fromMeshId (State: $sigState)")
                return
            }
            
            Log.d(TAG, "Received OFFER from $fromMeshId. My State: $sigState")
            
            // If we are polite and in glare, or if we have an unusable connection, recreate.
            // But if we are STABLE and already have a PC, we should probably just update it.
            val targetPc = if (!isStable && !isImpolite) {
                Log.d(TAG, "Glare: I am polite, resolving by recreating PC")
                cleanupPeer(fromMeshId)
                null
            } else if (pc != null && pc.iceConnectionState() == PeerConnection.IceConnectionState.FAILED) {
                cleanupPeer(fromMeshId)
                null
            } else {
                pc
            }

            val finalPc = if (targetPc == null) {
                val pcProcessor = PeerProcessor(fromMeshId)
                val newPc = factory.createPeerConnection(rtcConfig, pcProcessor) ?: return
                peers[fromMeshId] = newPc
                pcProcessor.pc = newPc
                newPc
            } else {
                targetPc
            }
            
            Log.d(TAG, "Setting Remote Description (OFFER) for $fromMeshId")
            finalPc.setRemoteDescription(SimpleSdpObserver(setSuccessCallback = {
                Log.d(TAG, "onSetSuccess for $fromMeshId. Creating Answer...")
                finalPc.createAnswer(SimpleSdpObserver(successCallback = { answer ->
                    Log.d(TAG, "onCreateSuccess: ANSWER for $fromMeshId. Setting Local Description...")
                    finalPc.setLocalDescription(SimpleSdpObserver(setSuccessCallback = {
                        webSocketService.sendSignaling(fromMeshId, "webrtc_answer", answer.description)
                    }), answer)
                }), MediaConstraints())
            }), SessionDescription(SessionDescription.Type.OFFER, payload))
            return
        }

        if (pc == null) {
            Log.w(TAG, "No PeerConnection for $fromMeshId to handle $type")
            return
        }

        when (type) {
            "answer", "webrtc_answer" -> {
                if (sigState != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
                    Log.w(TAG, "Ignoring ANSWER for $fromMeshId - My State is $sigState (expected HAVE_LOCAL_OFFER)")
                    return
                }
                Log.d(TAG, "Setting Remote Description (ANSWER) for $fromMeshId")
                pc.setRemoteDescription(SimpleSdpObserver(setSuccessCallback = {
                     Log.d(TAG, "onSetSuccess (ANSWER) for $fromMeshId")
                }), SessionDescription(SessionDescription.Type.ANSWER, payload))
            }
            "ice", "ice_candidate" -> {
                Log.d(TAG, "Adding ICE Candidate from remote for $fromMeshId")
                try {
                    val candidate = gson.fromJson(payload, IceCandidateModel::class.java)
                    pc.addIceCandidate(IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse/add ICE candidate", e)
                }
            }
        }
    }

    fun sendP2PMessage(peerId: String, message: EncryptedMessage): Boolean {
        val dc = dataChannels[peerId]
        if (dc != null && dc.state() == DataChannel.State.OPEN) {
            val json = gson.toJson(message)
            val buffer = DataChannel.Buffer(
                java.nio.ByteBuffer.wrap(json.toByteArray(Charsets.UTF_8)),
                false
            )
            return dc.send(buffer)
        }
        return false
    }

    fun isConnected(peerId: String): Boolean {
        val dc = dataChannels[peerId]
        return dc != null && dc.state() == DataChannel.State.OPEN
    }

    private inner class DataChannelObserver(val peerId: String, val dc: DataChannel) : DataChannel.Observer {
        override fun onBufferedAmountChange(p0: Long) {}
        override fun onStateChange() {
            val state = dc.state()
            Log.d(TAG, "DataChannel $peerId state: $state")
            listener?.onP2PConnectionStateChange(peerId, state == DataChannel.State.OPEN)
        }
        override fun onMessage(buffer: DataChannel.Buffer) {
            val bytes = ByteArray(buffer.data.remaining())
            buffer.data.get(bytes)
            val text = String(bytes, Charsets.UTF_8)
            try {
                val msg = gson.fromJson(text, EncryptedMessage::class.java)
                listener?.onP2PMessageReceived(peerId, msg)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse P2P Message", e)
            }
        }
    }

    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    
    fun initAudio() {
        if (localAudioTrack != null) return
        val mediaConstraints = MediaConstraints()
        localAudioSource = factory.createAudioSource(mediaConstraints)
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", localAudioSource)
    }

    fun startCall(peerId: String) {
        if (!peers.containsKey(peerId)) {
            connectToPeer(peerId)
        }
        val pc = peers[peerId] ?: return
        if (localAudioTrack == null) initAudio()
        localAudioTrack?.let { track ->
            val alreadyAdded = pc.senders.any { sender -> sender.track()?.id() == track.id() }
            if (!alreadyAdded) {
                try {
                    pc.addTrack(track, listOf("ARDAMS"))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add track: ${e.message}")
                }
            }
        }
    }

    private inner class PeerProcessor(val peerId: String) : PeerConnection.Observer, SdpObserver {
        var pc: PeerConnection? = null

        override fun onCreateSuccess(sdp: SessionDescription) {
            Log.d(TAG, "onCreateSuccess: ${sdp.type} for $peerId")
            pc?.setLocalDescription(this, sdp)
            val typeStr = if (sdp.type == SessionDescription.Type.OFFER) "webrtc_offer" else "webrtc_answer"
            webSocketService.sendSignaling(peerId, typeStr, sdp.description)
        }
        override fun onSetSuccess() { Log.d(TAG, "onSetSuccess for $peerId") }
        override fun onCreateFailure(s: String?) { Log.e(TAG, "onCreateFailure: $s") }
        override fun onSetFailure(s: String?) { Log.e(TAG, "SDP Set Failure: $s") }

        override fun onIceCandidate(candidate: IceCandidate) {
            Log.d(TAG, "onIceCandidate generated for $peerId: ${candidate.sdpMid}")
            val model = IceCandidateModel(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            webSocketService.sendSignaling(peerId, "ice_candidate", gson.toJson(model))
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onDataChannel(dc: DataChannel) {
            Log.d(TAG, "New Remote DataChannel from $peerId")
            setupDataChannel(peerId, dc)
        }
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
             Log.d(TAG, "SignalingState for $peerId changed to: $state")
        }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            Log.d(TAG, "ICE Connection Change for $peerId: $state")
            if (state == PeerConnection.IceConnectionState.FAILED || 
                state == PeerConnection.IceConnectionState.CLOSED) {
                
                Log.w(TAG, "P2P Connection terminal failure for $peerId ($state), scheduling cleanup")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    cleanupPeer(peerId)
                    listener?.onP2PConnectionStateChange(peerId, false)
                }
            } else if (state == PeerConnection.IceConnectionState.DISCONNECTED) {
                Log.i(TAG, "P2P Connection transiently DISCONNECTED for $peerId, waiting for potential recovery")
            }
        }
        override fun onIceConnectionReceivingChange(b: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onAddStream(stream: MediaStream?) {
             Log.d(TAG, "onAddStream from $peerId")
             if (stream?.audioTracks?.isNotEmpty() == true) {
                 val audioTrack = stream.audioTracks[0]
                 audioTrack.setEnabled(true)
                 // Audio playback is handled automatically by WebRTC native layer
                 // The track must be enabled and part of the stream
                 Log.i(TAG, "Audio track enabled for $peerId: ${audioTrack.id()}, state: ${audioTrack.state()}")
             } else {
                 Log.w(TAG, "No audio tracks in stream from $peerId")
             }
        }
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded for $peerId")
            pc?.createOffer(this, MediaConstraints())
        }
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
             Log.d(TAG, "onAddTrack from $peerId")
             // In Unified Plan, tracks are added via onAddTrack rather than onAddStream
             val track = receiver?.track()
             if (track != null && track.kind() == "audio") {
                 track.setEnabled(true)
                 Log.i(TAG, "Audio track added via onAddTrack for $peerId: ${track.id()}, enabled")
             }
        }
    }
    
    @androidx.annotation.Keep
    data class IceCandidateModel(
        @SerializedName("sdpMid") val sdpMid: String,
        @SerializedName("sdpMLineIndex") val sdpMLineIndex: Int,
        @SerializedName("candidate") val candidate: String
    )

    companion object {
        private const val TAG = "WebRtcManager"
    }
}
