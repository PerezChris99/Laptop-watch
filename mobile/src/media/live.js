// @ts-check
/** Owner-consented WebRTC: laptop is the only video source; optional duplex audio is explicit.
 * Media and signalling are disabled when signed TLS/pairing are unavailable.
 */
import { RTCPeerConnection, RTCSessionDescription, mediaDevices } from 'react-native-webrtc';

/** @typedef {{peer:RTCPeerConnection,stream:object,stop:()=>Promise<void>}} LiveSession */

/**
 * Start video only from a currently armed laptop after a signed status check.
 * The phone microphone stays disabled unless talkback is explicitly requested. ICE uses LAN host candidates until the
 * owner configures an approved TURN server in both clients.
 *
 * @param {import('../net/SignedClient.js').SignedClient} client
 * @param {(stream:object|null)=>void} onStream
 * @param {{includeAudio?:boolean,talkback?:boolean}} options
 * @returns {Promise<LiveSession>}
 */
export async function startLiveView(client, onStream, options = {}) {
  const status = await client.status();
  if (!status.online || !status.motion_armed) {
    throw new Error('The verified laptop is not armed or online');
  }
  const peer = new RTCPeerConnection({ iceServers: [] });
  let stream = null;
  let localMicrophone = null;
  let stopped = false;
  try {
    peer.addTransceiver('video', { direction: 'recvonly' });
    if (options.includeAudio === true || options.talkback === true) {
      // The handset microphone is not opened for receive-only sessions.
      const direction = options.talkback === true ? 'sendrecv' : 'recvonly';
      const transceiver = peer.addTransceiver('audio', { direction });
      if (options.talkback === true) {
        // Must follow explicit user confirmation AND an OS runtime permission
        // check in the UI. Never created by background service or alerts.
        localMicrophone = await mediaDevices.getUserMedia({
          audio: { echoCancellation: true, noiseSuppression: true },
          video: false,
        });
        const audioTrack = localMicrophone.getAudioTracks()[0];
        if (!audioTrack) throw new Error('No permitted phone microphone track');
        await transceiver.sender.replaceTrack(audioTrack);
      }
    }
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
      sdp: current.sdp, type: 'offer', include_audio: options.includeAudio === true || options.talkback === true,
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
        if (localMicrophone) localMicrophone.getTracks().forEach((track) => track.stop());
        peer.close();
        // Clean up the desktop-owned peer, but never cache/retry if offline.
        await client.json('POST', '/api/webrtc/stop').catch(() => {});
      },
    };
  } catch (error) {
    stopped = true;
    if (localMicrophone) localMicrophone.getTracks().forEach((track) => track.stop());
    peer.close();
    onStream(null);
    throw error;
  }
}
