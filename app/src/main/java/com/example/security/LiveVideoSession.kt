package com.example.security

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.io.IOException

/**
 * Encrypted WebRTC video receiver for a signed, explicitly armed paired laptop.
 * The phone's camera and microphone are never opened by this video-only path.
 * WAN use requires separately deployed authenticated signaling + TURN.
 */
class LiveVideoSession(
    context: Context,
    private val secureGateway: SecureLaptopGateway
) {
    private val app = context.applicationContext
    private val _track = MutableStateFlow<VideoTrack?>(null)
    val track: StateFlow<VideoTrack?> = _track
    private val _state = MutableStateFlow("STOPPED")
    val state: StateFlow<String> = _state

    private var eglBase: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var gathering = CompletableDeferred<Unit>()

    fun eglContext(): EglBase.Context? = eglBase?.eglBaseContext

    private fun createFactory() {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions
                .builder(app).createInitializationOptions()
        )
        eglBase = EglBase.create()
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    private fun observer() = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            _state.value = when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> "CONNECTED"
                PeerConnection.IceConnectionState.DISCONNECTED -> "RECONNECTING"
                PeerConnection.IceConnectionState.FAILED -> "FAILED"
                PeerConnection.IceConnectionState.CLOSED -> "STOPPED"
                else -> "CONNECTING"
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) {
                gathering.complete(Unit)
            }
        }
        override fun onIceCandidate(candidate: IceCandidate) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
        override fun onAddStream(stream: MediaStream) {}
        override fun onRemoveStream(stream: MediaStream) {}
        override fun onDataChannel(channel: DataChannel) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            (receiver.track() as? VideoTrack)?.let { _track.value = it }
        }
        override fun onTrack(transceiver: RtpTransceiver) {
            (transceiver.receiver.track() as? VideoTrack)?.let { _track.value = it }
        }
    }

    private suspend fun createOffer(pc: PeerConnection): SessionDescription {
        val result = CompletableDeferred<SessionDescription>()
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) { result.complete(desc) }
            override fun onCreateFailure(message: String) {
                result.completeExceptionally(IOException(message))
            }
            override fun onSetSuccess() {}
            override fun onSetFailure(message: String) {}
        }, MediaConstraints())
        return withTimeout(6000) { result.await() }
    }

    private suspend fun setDescription(
        pc: PeerConnection, sdp: SessionDescription, local: Boolean
    ) {
        val result = CompletableDeferred<Unit>()
        val callback = object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {}
            override fun onCreateFailure(message: String) {}
            override fun onSetSuccess() { result.complete(Unit) }
            override fun onSetFailure(message: String) {
                result.completeExceptionally(IOException(message))
            }
        }
        if (local) pc.setLocalDescription(callback, sdp)
        else pc.setRemoteDescription(callback, sdp)
        withTimeout(7000) { result.await() }
    }

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            require(secureGateway.isApproved()) { "A laptop pairing must be approved first" }
            require(secureGateway.status().getOrThrow().optBoolean("motion_armed", false)) {
                "Arm the laptop camera first"
            }
            closeLocal()
            createFactory()
            gathering = CompletableDeferred()
            val config = PeerConnection.RTCConfiguration(emptyList<PeerConnection.IceServer>())
            config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            val current = factory!!.createPeerConnection(config, observer())
                ?: throw IOException("Unable to create WebRTC peer")
            peer = current
            current.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.RECV_ONLY
                )
            )
            _state.value = "CONNECTING"
            val offer = createOffer(current)
            setDescription(current, offer, true)
            if (current.iceGatheringState() != PeerConnection.IceGatheringState.COMPLETE) {
                withTimeout(8000) { gathering.await() }
            }
            val localSdp = current.localDescription?.description
                ?: throw IOException("No ICE-ready SDP offer")
            val reply = secureGateway.json(
                "POST", "/api/webrtc/offer",
                JSONObject().put("sdp", localSdp).put("type", "offer")
                    .put("include_audio", false)
            ).getOrThrow()
            require(reply.getString("type") == "answer") { "Invalid RTC answer" }
            setDescription(
                current, SessionDescription(SessionDescription.Type.ANSWER,
                    reply.getString("sdp")), false
            )
        }.onFailure { err ->
            _state.value = "FAILED"
            closeLocal()
        }
    }

    suspend fun restartIce(): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            // This requires the signed HTTP control path to remain reachable.
            // A full WAN signaling relay is necessary for mobile network handoff.
            val current = peer ?: throw IOException("No active peer")
            gathering = CompletableDeferred()
            current.restartIce()
            val offer = createOffer(current)
            setDescription(current, offer, true)
            if (current.iceGatheringState() != PeerConnection.IceGatheringState.COMPLETE) {
                withTimeout(8000) { gathering.await() }
            }
            val reply = secureGateway.json(
                "POST", "/api/webrtc/offer",
                JSONObject().put("sdp",
                    current.localDescription?.description ?: error("Missing restart SDP"))
                    .put("type", "offer").put("include_audio", false)
            ).getOrThrow()
            setDescription(current,
                SessionDescription(SessionDescription.Type.ANSWER, reply.getString("sdp")), false)
        }.onFailure { _state.value = "FAILED" }
    }

    suspend fun disconnect(): Unit = withContext(Dispatchers.Main) {
        runCatching { secureGateway.json("POST", "/api/webrtc/stop") }
        closeLocal()
        _state.value = "STOPPED"
    }

    private fun closeLocal() {
        _track.value = null
        peer?.close()
        peer?.dispose()
        peer = null
    }

    fun release() {
        closeLocal()
        factory?.dispose()
        factory = null
        eglBase?.release()
        eglBase = null
        _state.value = "STOPPED"
    }
}
