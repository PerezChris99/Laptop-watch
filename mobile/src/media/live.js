// @ts-check
/** Receiver-only WebRTC: laptop is the ONLY source of video/media.
 * Media and signalling are disabled when signed TLS/pairing are unavailable.
 */
import { RTCPeerConnection, RTCSessionDescription } from 'react-native-webrtc';

/** @typedef {{peer:RTCPeerConnection,stream:object,stop:()=>Promise<void>}} LiveSession */

/**
 * Start video only from a currently armed laptop after a signed status check.
 * The microphone stays disabled. ICE uses LAN host candidates only until the
 * owner configures an approved TURN server in both clients.
 *
 * @param {import('../net/SignedClient.js').SignedClient} client
 * @param {(stream:object|null)=>void} onStream
 * @returns {Promise<LiveSession>}
 */
export async function startLiveView(client, onStream) {
  const status = await client.status();
  if (!status.online || !status.motion_armed) {
    throw new Error('The verified laptop is not armed or online');
  }
  const peer = new RTCPeerConnection({ iceServers: [] });
  let stream = null;
  let stopped = false;
  try {
    peer.addTransceiver('video', { direction: 'recvonly' });
    peer.ontrack = (event) => {
      const media = event.streams?.[0];
      if (!stopped && event.track?.kind === 'video' && media) {
        stream = media;
        onStream(media);
      }
    };
    const offer = await peer.createOffer();
    await peer.setLocalDescription(offer);
    // The Python server accepts a single SDP request, not trickle ICE.
    // Wait for the local ICE gathering to finish; never send a partial offer.
    if (peer.iceGatheringState !== 'complete') {
      await new Promise((resolve, reject) => {
        const timer = setTimeout(() => {
          peer.removeEventListener('icegatheringstatechange', change);
          reject(new Error('Timed out gathering LAN ICE candidates'));
        }, 10_000);
        const change = () => {
          if (peer.iceGatheringState === 'complete') {
            clearTimeout(timer);
            peer.removeEventListener('icegatheringstatechange', change);
            resolve();
          }
        };
        peer.addEventListener('icegatheringstatechange', change);
        change();
      });
    }
    const current = peer.localDescription;
    if (!current?.sdp || current.sdp.length < 40 || !current.sdp.includes('m=video')) {
      throw new Error('No usable WebRTC video offer');
    }
    const answer = await client.json('POST', '/api/webrtc/offer', {
      sdp: current.sdp, type: 'offer', include_audio: false,
    });
    if (answer.type !== 'answer' || typeof answer.sdp !== 'string' ||
        answer.media_encryption !== 'DTLS-SRTP') {
      throw new Error('Invalid or unencrypted WebRTC answer');
    }
    await peer.setRemoteDescription(new RTCSessionDescription({ type: 'answer', sdp: answer.sdp }));
    return {
      peer,
      get stream() { return stream; },
      stop: async () => {
        stopped = true;
        onStream(null);
        peer.close();
        // Clean up the desktop-owned peer, but never cache/retry if offline.
        await client.json('POST', '/api/webrtc/stop').catch(() => {});
      },
    };
  } catch (error) {
    stopped = true;
    peer.close();
    onStream(null);
    throw error;
  }
}
