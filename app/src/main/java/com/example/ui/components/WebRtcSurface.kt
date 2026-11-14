package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/** Displays an authenticated DTLS-SRTP WebRTC video track.
 * Does not request the phone camera/microphone or create an HTTP image stream.
 */
@Composable
fun WebRtcSurface(
    videoTrack: VideoTrack,
    eglContext: EglBase.Context,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val renderer = remember(videoTrack) {
        SurfaceViewRenderer(context)
    }
    DisposableEffect(renderer, videoTrack, eglContext) {
        renderer.init(eglContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setMirror(false)
        videoTrack.addSink(renderer)
        onDispose {
            videoTrack.removeSink(renderer)
            renderer.release()
        }
    }
    AndroidView(factory = { renderer }, modifier = modifier.fillMaxSize())
}
