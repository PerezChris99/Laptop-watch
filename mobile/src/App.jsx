import 'react-native-get-random-values';
import 'react-native-url-polyfill/auto';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { RTCView } from 'react-native-webrtc';
import Video from 'react-native-video';
import { downloadEvidence, exportEvidenceLocal, deleteEvidenceLocal, purgePrivateEvidence } from './net/evidence.js';
import { signedRelayJson, normalizedRelayUrl } from './net/signedRelay.js';
import { pushAvailable, registerPush, unregisterPush, pushEnrollmentStatus, pushStatusMessage } from './notifications/push.js';
import { QrScanner } from './pairing/QrScanner.jsx';
import { rediscoverPairedLaptop } from './discovery/findPaired.js';
import { exportIncidents } from './reports/export.js';
import { askNotificationPermission, askNearbyPermission, askMicrophonePermission } from './permissions/access.js';
import { startForegroundConnection, stopForegroundConnection } from './background/bridge.js';
import { checkNotifications, RESULTS } from 'react-native-permissions';
import { startLiveView } from './media/live.js';
import { locationStatus } from './location/labels.js';
import { cacheLastSeen, cacheEvidence, cacheAlerts, clearCachedAlerts, loadAlerts, recordPrivateEvidence, forgetPrivateEvidence, getPreference, setPreference, loadLastSeen, deleteDesktopCache } from './storage/db.js';
import { ActivityIndicator, Alert, AppState, Pressable, SafeAreaView, ScrollView, StyleSheet, Text, TextInput, View, Image, Platform } from 'react-native';
import { tokens } from './design/tokens.js';
import { PaperCard, SectionHeader, StatusPill, PrimaryButton, SecondaryButton, DestructiveButton, ConsentNotice, Toast, EmptyState, LastSeenBadge, EvidenceThumb } from './components/Paper.jsx';
import { loadIdentity, saveIdentity, clearIdentity } from './storage/identity.js';
import { pinnedTransport } from './net/pinnedTransport.js';
import { SignedClient } from './net/SignedClient.js';
import { requestPairing, verifyApproval } from './pairing/pair.js';

const { colors: c } = tokens;
const tabs = ['Home', 'Pair', 'Live', 'Evidence', 'Alerts', 'Location', 'Settings'];
const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: c.paper },
  header: { backgroundColor: c.whiteSmoke, padding: 20, borderBottomWidth: 1, borderBottomColor: c.hairline },
  brand: { fontFamily: 'serif', fontSize: 25, color: c.ink, fontWeight: '600' },
  kicker: { fontSize: 10, letterSpacing: 1, fontWeight: '700', color: c.blue },
  nav: { flexDirection: 'row', paddingHorizontal: 12, paddingVertical: 12, gap: 8 },
  tab: { paddingVertical: 8, paddingHorizontal: 13, borderRadius: 10, borderWidth: 1, borderColor: c.hairline, backgroundColor: c.surface },
  tabActive: { borderColor: c.gold },
  tabText: { color: c.blueDeep, fontSize: 13, fontWeight: '600' },
  content: { padding: 18, paddingBottom: 42 },
  body: { color: c.inkMuted, fontSize: 14, lineHeight: 23, marginBottom: 9 },
  label: { color: c.ink, fontSize: 13, fontWeight: '700', marginBottom: 6 },
  metric: { color: c.ink, fontSize: 18, fontWeight: '600' },
});

function readableError(error) { return error?.message || 'Request failed safely'; }

export default function App() {
  const [consented, setConsented] = useState(false);
  const [consentReady, setConsentReady] = useState(false);
  const [scannerVisible, setScannerVisible] = useState(false);
  const [backgroundOptIn, setBackgroundOptIn] = useState(false);
  const [backgroundActive, setBackgroundActive] = useState(false);
  const foregroundStarted = useRef(false);
  const [audioListen, setAudioListen] = useState(false);
  const [talkbackEnabled, setTalkbackEnabled] = useState(false);
  const [recordingRequested, setRecordingRequested] = useState(false);
  const [warningText, setWarningText] = useState('');
  const [relayInput, setRelayInput] = useState('');
  const [relayUrl, setRelayUrl] = useState('');
  const [pushEnabled, setPushEnabled] = useState(false);
  const [pushEnrolled, setPushEnrolled] = useState(false);
  const [autoSnapshot, setAutoSnapshot] = useState(true);
  const [autoAlarm, setAutoAlarm] = useState(false);
  const [autoRecord, setAutoRecord] = useState(false);
  const [locationSource, setLocationSource] = useState('unavailable');
  const [tab, setTab] = useState('Home');
  const [paired, setPaired] = useState(null);
  const [qr, setQr] = useState('');
  const [status, setStatus] = useState(null);
  const [connected, setConnected] = useState(false);
  const [location, setLocation] = useState(null);
  const [locationHistory, setLocationHistory] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [evidence, setEvidence] = useState([]);
  const [localMedia, setLocalMedia] = useState({});
  const [selectedMedia, setSelectedMedia] = useState(null);
  const [liveStream, setLiveStream] = useState(null);
  const liveSession = useRef(null);
  const [lastFixFresh, setLastFixFresh] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');
  const [transportAvailable, setTransportAvailable] = useState(false);
  const afterId = useRef(0);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    loadIdentity().then(setPaired).catch(() => setError('Secure pairing storage could not be read'));
    Promise.all([
      getPreference('owner_consent_v1'), getPreference('background_opt_in'),
      getPreference('relay_https_url'), getPreference('push_opt_in'),
      getPreference('person_auto_snapshot'), getPreference('person_auto_alarm'),
      getPreference('person_auto_record'),
    ]).then(([consent, fgs, relay, push, snap, alarm, record]) => {
      if (!mounted.current) return;
      setConsented(consent === '1');
      setBackgroundOptIn(fgs === '1');
      setRelayUrl(relay || '');
      setRelayInput(relay || '');
      setPushEnrolled(push === '1');
      setAutoSnapshot(snap !== '0');
      setAutoAlarm(alarm === '1');
      setAutoRecord(record === '1');
      setConsentReady(true);
    })
      .catch(() => { if (mounted.current) { setConsentReady(true); setError('Private consent storage unavailable. Controls remain disabled.'); } });
    pushAvailable().then((isAvailable) => {
      if (mounted.current) setPushEnabled(isAvailable);
    }).catch(() => {});
    try { pinnedTransport(); setTransportAvailable(true); }
    catch (_) { setTransportAvailable(false); }
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    if (!paired?.desktopId) { setLocation(null); return; }
    loadLastSeen(paired.desktopId).then((cached) => {
      if (cached) { setLocation(cached); setLocationSource('locally cached'); setLastFixFresh(false); }
    }).catch(() => {});
    loadAlerts(paired.desktopId).then((cached) => {
      if (mounted.current && Array.isArray(cached)) setAlerts(cached);
    }).catch(() => {});
  }, [paired?.desktopId]);

  // Live media is an explicit foreground-only action. Leaving the screen or
  // putting the app in the background terminates the receiver immediately.
  useEffect(() => {
    if (tab === 'Live') return;
    const session = liveSession.current;
    liveSession.current = null;
    if (session) { session.stop().catch(() => {}); setLiveStream(null); }
  }, [tab]);

  useEffect(() => {
    const subscription = AppState.addEventListener('change', (nextState) => {
      if (nextState !== 'active' && liveSession.current) {
        const session = liveSession.current;
        liveSession.current = null;
        session.stop().catch(() => {});
        setLiveStream(null);
      }
    });
    return () => subscription.remove();
  }, []);

  // Native foreground polling is an explicit owner opt-in and has no command
  // outbox. It survives a hidden JS screen only while the native service can
  // authenticate the paired armed desktop. Native stop on remote disarm.
  useEffect(() => {
    let alive = true;
    const sync = async () => {
      if (!backgroundOptIn || !paired?.approved || (status && !status.motion_armed)) {
        if (foregroundStarted.current) {
          foregroundStarted.current = false;
          await stopForegroundConnection().catch(() => {});
          if (alive) setBackgroundActive(false);
        }
        return;
      }
      if (!status?.motion_armed || !connected || foregroundStarted.current) return;
      try {
        const permission = await checkNotifications();
        const granted = permission.status === RESULTS.GRANTED || permission.status === RESULTS.LIMITED;
        if (!granted || !alive) return;
        await startForegroundConnection({
          ownerOptedIn: true, pairedApproved: true, armed: true, notificationsGranted: true,
        }, paired);
        if (!alive) { await stopForegroundConnection(); return; }
        foregroundStarted.current = true;
        setBackgroundActive(true);
      } catch (_) {
        if (alive) setError('Native background connection unavailable; app still works while open.');
      }
    };
    sync();
    return () => { alive = false; };
  }, [backgroundOptIn, paired, status?.motion_armed, connected]);

  const client = useCallback(() => {
    if (!paired?.approved) throw new Error('Phone must be approved on the laptop');
    return new SignedClient(paired, pinnedTransport());
  }, [paired]);

  const refresh = useCallback(async () => {
    if (!paired?.approved || AppState.currentState !== 'active') {
      if (mounted.current) setConnected(false);
      return;
    }
    try {
      if (!transportAvailable) throw new Error('Signed LAN transport unavailable');
      const response = await client().status();
      if (!mounted.current) return;
      if (response.online !== true) throw new Error('Invalid desktop status');
      setStatus(response); setConnected(true);
      try {
        const fix = await client().lastSeen();
        if (mounted.current && fix && typeof fix === 'object') {
          if (Number.isFinite(fix.latitude) && Number.isFinite(fix.longitude) &&
              Number.isFinite(fix.accuracy_meters) &&
              Number.isSafeInteger(fix.observed_at_ms)) {
            setLocation(fix);
            setLocationSource('direct laptop');
            setLastFixFresh(false); // Server always reports historical, not a live GNSS stream.
            cacheLastSeen(paired.desktopId, fix).catch(() => {});
          }
        }
      } catch (_) {
        // Retain previous location as LAST SEEN, never LIVE.
        if (mounted.current) setLastFixFresh(false);
      }
      try {
        const result = await client().incidents(afterId.current);
        if (mounted.current && Array.isArray(result.events)) {
          setAlerts((old) => {
            const merged = new Map([...old, ...result.events].map((item) => [item.id, item]));
            return [...merged.values()].sort((a,b) => a.id - b.id).slice(-100);
          });
          cacheAlerts(paired.desktopId, result.events).catch(() => {});
          if (Number.isSafeInteger(result.next_after_id)) afterId.current = result.next_after_id;
        }
      } catch (_) { /* Optional guard/relay not configured */ }
    } catch (_) {
      if (mounted.current) { setConnected(false); setStatus(null); setLastFixFresh(false); }
      // The separately configured owner relay reports historical events only.
      // Never interpret its result as LAN connectivity or execute actions.
      if (relayUrl) {
        try {
          const remoteFix = await signedRelayJson(paired, relayUrl, 'GET', '/api/location/latest');
          if (mounted.current && Number.isFinite(remoteFix.latitude) &&
              Number.isFinite(remoteFix.longitude) &&
              Number.isFinite(remoteFix.accuracy_meters) &&
              Number.isSafeInteger(remoteFix.observed_at_ms)) {
            setLocation(remoteFix);
            setLocationSource('encrypted owner relay, LAST SEEN');
            setLastFixFresh(false);
            cacheLastSeen(paired.desktopId, remoteFix).catch(() => {});
          }
        } catch (_) { /* Unavailable relay never means a live location. */ }
        try {
          const remoteEvents = await signedRelayJson(paired, relayUrl,
            'POST', '/api/incidents/query', { after_id: afterId.current, limit: 100 });
          if (mounted.current && Array.isArray(remoteEvents.events)) {
            setAlerts((old) => {
              const merged = new Map([...old, ...remoteEvents.events].map((item) => [item.id, item]));
              return [...merged.values()].sort((a,b) => a.id-b.id).slice(-100);
            });
            cacheAlerts(paired.desktopId, remoteEvents.events).catch(() => {});
            if (Number.isSafeInteger(remoteEvents.next_after_id))
              afterId.current = remoteEvents.next_after_id;
          }
        } catch (_) { /* No offline command queue or synthetic notification. */ }
      }
    }
  }, [paired, transportAvailable, client, relayUrl]);

  useEffect(() => {
    refresh();
    const interval = setInterval(refresh, 9000);
    return () => clearInterval(interval);
  }, [refresh]);

  useEffect(() => {
    if (tab !== 'Evidence' || !connected) return;
    let current = true;
    client().evidenceIndex().then((result) => {
      if (current && Array.isArray(result.items)) {
        setEvidence(result.items);
        for (const item of result.items) cacheEvidence(item).catch(() => {});
      }
    }).catch(() => { if (current) setEvidence([]); });
    return () => { current = false; };
  }, [tab, connected, client]);

  useEffect(() => {
    if (tab !== 'Location' || !connected) return;
    let current = true;
    client().locationHistory().then((response) => {
      if (current && Array.isArray(response.fixes)) {
        setLocationHistory(response.fixes.filter((fix) =>
          Number.isFinite(fix.latitude) && Number.isFinite(fix.longitude) &&
          Number.isFinite(fix.accuracy_meters) &&
          Number.isSafeInteger(fix.observed_at_ms)).slice(0,100));
      }
    }).catch(() => { if (current) setLocationHistory([]); });
    return () => { current = false; };
  }, [tab, connected, client]);

  const perform = async (work) => {
    if (busy) return;
    setError(''); setNotice(''); setBusy(true);
    try { await work(); } catch (e) { setError(readableError(e)); }
    finally { setBusy(false); }
  };

  const pairNow = () => perform(async () => {
    if (paired) throw new Error('Remove the previous pairing first');
    const result = await requestPairing(qr.trim(), pinnedTransport());
    await saveIdentity(result);
    setPaired(result); setQr('');
    setNotice('Pair request sent. Approve this exact phone on your laptop, then verify.');
  });

  const approveNow = () => perform(async () => {
    if (!paired) throw new Error('No pairing request');
    const confirmed = await verifyApproval(paired, pinnedTransport());
    await saveIdentity(confirmed);
    setPaired(confirmed); setNotice('Approved by the laptop. Signed status is now available.');
    refresh();
  });

  const confirm = (label, operation) => {
    if (!connected || busy) { setError('Laptop is offline. Action not queued.'); return; }
    Alert.alert('Confirm ' + label, 'Execute on your paired laptop now?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Execute', style: label === 'Lock' ? 'destructive' : 'default', onPress: () => perform(async () => {
        // Re-read signed status so a stale UI never authorizes an offline command.
        await client().status();
        await client().action(operation);
        if (operation === 'recordStart') setRecordingRequested(true);
        if (operation === 'recordStop') setRecordingRequested(false);
        setNotice(label + ' accepted by the desktop gateway.');
        refresh();
      }) },
    ]);
  };

  const startLive = () => perform(async () => {
    if (liveSession.current || !connected || !status?.motion_armed) {
      throw new Error('Arm an online laptop before requesting its camera');
    }
    if (talkbackEnabled && !await askMicrophonePermission()) {
      throw new Error('Phone microphone permission is required for two-way intercom');
    }
    const session = await startLiveView(client(), setLiveStream, {
      includeAudio: audioListen || talkbackEnabled, talkback: talkbackEnabled,
    });
    liveSession.current = session;
    setNotice(talkbackEnabled ? 'Encrypted video and permission-gated two-way audio requested.' : audioListen ? 'Encrypted video with explicit laptop audio listening requested.' : 'Encrypted silent video requested.');
  });

  const stopLive = () => perform(async () => {
    const session = liveSession.current;
    liveSession.current = null;
    setLiveStream(null);
    if (session) await session.stop();
  });

  const toggleArmed = () => {
    if (!connected || busy) { setError('Laptop offline. Arm/disarm not queued.'); return; }
    const next = !status?.motion_armed;
    Alert.alert(next ? 'Arm monitoring?' : 'Disarm monitoring?',
      'Visible camera monitoring requires lawful notice and permission. Audio remains off.', [
        { text: 'Cancel', style: 'cancel' },
        { text: 'Confirm', onPress: () => perform(async () => {
          await client().status();
          await client().arm(next);
          if (!next) {
            setRecordingRequested(false);
            if (liveSession.current) { await liveSession.current.stop(); liveSession.current = null; setLiveStream(null); }
            foregroundStarted.current = false;
            await stopForegroundConnection().catch(() => {});
            setBackgroundActive(false);
          }
          await refresh();
        }) },
      ]);
  };

  const screen = () => {
    if (tab === 'Pair') return <PaperCard><SectionHeader title="Pair your laptop" subtitle="Scan the owner-displayed QR in person. Only an approved device can control the laptop." />
      {!transportAvailable && <ConsentNotice>Native certificate pinning has not been installed. Pairing is disabled; unpinned networking is never used.</ConsentNotice>}
      {paired ? <>
        <StatusPill state={paired.approved ? (connected ? 'Online' : 'Offline') : 'Not configured'} />
        <Text style={styles.body}>Desktop ID: {paired.desktopId}</Text>
        {!paired.approved && <SecondaryButton title="Check desktop approval" onPress={approveNow} disabled={busy || !transportAvailable} />}
        {paired.approved && <SecondaryButton title="Find paired laptop on this Wi-Fi"
          disabled={busy || Platform.OS !== 'android'}
          onPress={() => perform(async () => {
            if (!await askNearbyPermission()) throw new Error('Nearby Wi-Fi permission denied');
            const updated = await rediscoverPairedLaptop(paired);
            await saveIdentity(updated);
            setPaired(updated);
            setNotice('Paired laptop located. Existing certificate and signed identity verified.');
            refresh();
          })} />}
      </> : <>
        <SecondaryButton title={scannerVisible ? 'Close QR scanner' : 'Scan laptop QR'} onPress={() => setScannerVisible(!scannerVisible)} disabled={busy || !transportAvailable} />
        {scannerVisible ? <QrScanner onAccepted={(value) => { setQr(value); setScannerVisible(false); }} /> : null}
        {qr ? <Text style={styles.body}>Signed invitation scanned. Complete pairing before it expires.</Text> : null}
        <PrimaryButton title="Request secure pairing" onPress={pairNow} disabled={!transportAvailable || busy || qr.length < 40} />
      </>}
    </PaperCard>;

    if (tab === 'Home') return <>
      <SectionHeader title="Your laptop, under your control." subtitle="Only real paired-device responses determine status. Offline commands are never saved." />
      <PaperCard>
        <StatusPill state={!connected ? 'Offline' : status?.motion_armed ? 'Armed' : 'Disarmed'} />
        <Text style={styles.body}>{connected ? 'Signed connection to your paired laptop.' : 'No verified live connection.'}</Text>
        <PrimaryButton title={status?.motion_armed ? 'Disarm laptop' : 'Arm laptop'} disabled={!connected || busy} onPress={toggleArmed} />
      </PaperCard>
      <PaperCard><SectionHeader title="Immediate actions" subtitle="Your laptop must be awake and reachable. Lock, alarm and capture are never queued." />
        <SecondaryButton title="Lock laptop" disabled={!connected || busy} onPress={() => confirm('Lock', 'lock')} />
        <SecondaryButton title="Sound alarm" disabled={!connected || busy} onPress={() => confirm('Alarm', 'alarm')} />
        <TextInput value={warningText} onChangeText={setWarningText} maxLength={400}
          accessibilityLabel="Text warning to speak aloud on the laptop"
          placeholder="Optional spoken warning (1–400 characters)"
          style={{ color: c.ink, padding: 10, backgroundColor: c.surface,
            borderColor: c.hairline, borderWidth: 1, borderRadius: 8, marginVertical: 8 }} />
        <SecondaryButton title="Speak warning on laptop"
          disabled={!connected || busy || !warningText.trim()}
          onPress={() => Alert.alert('Speak warning aloud?', 'This is audible on your paired laptop. Send only when legally appropriate.', [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Speak', onPress: () => perform(async () => {
              await client().status(); await client().warn(warningText);
              setWarningText(''); setNotice('Spoken warning accepted by laptop.');
            }) },
          ])} />
        <SecondaryButton title="Take photo" disabled={!connected || !status?.motion_armed || busy} onPress={() => confirm('Photo', 'photo')} />
        <SecondaryButton title="Record a clip" disabled={!connected || !status?.motion_armed || busy} onPress={() => confirm('Record', 'recordStart')} />
        <DestructiveButton title="Stop recording" disabled={!connected || busy} onPress={() => confirm('Stop recording', 'recordStop')} />
      </PaperCard>
      <PaperCard><SectionHeader title="Location" />
        <LastSeenBadge time={location ? new Date(location.observed_at_ms || 0).toLocaleString() : 'No verified fix'} />
        <Text style={styles.body}>Cached positions are never labeled LIVE. A real GNSS fix is required.</Text>
      </PaperCard>
    </>;

    if (tab === 'Live') return <PaperCard><SectionHeader title="Live view" subtitle="Encrypted WebRTC from your paired laptop; no phone camera access." />
      {!liveStream && <EmptyState message="No verified live stream. Only a deliberate request can open the armed laptop camera." />}
      {liveStream ? <RTCView streamURL={liveStream.toURL()} style={{ height: 240, backgroundColor: c.ink }} objectFit="contain" /> : null}
      {!liveSession.current ?
        <PrimaryButton title={audioListen ? 'Request live video + consented laptop audio' : 'Request silent live video'}
          disabled={!connected || !status?.motion_armed || busy} onPress={startLive} /> :
        <DestructiveButton title="End live session" disabled={busy} onPress={stopLive} />}
      <SecondaryButton title={talkbackEnabled ? 'Disable two-way intercom' : 'Enable two-way intercom'}
        disabled={busy || !!liveSession.current || !connected}
        onPress={() => {
          if (talkbackEnabled) { setTalkbackEnabled(false); return; }
          Alert.alert('Authorize two-way intercom?', 'The phone microphone will transmit to your laptop only during the visible live session. You must have consent from affected people. Stop live view to end it.', [
            { text: 'Cancel', style: 'cancel' },
            { text: 'I have permission', onPress: () => setTalkbackEnabled(true) },
          ]);
        }} />
      <SecondaryButton title={audioListen ? 'Disable laptop microphone audio' : 'Enable optional laptop audio'}
        disabled={busy || !!liveSession.current}
        onPress={() => {
          if (audioListen) { setAudioListen(false); setPreference('mic_listen_opt_in', '0').catch(() => {}); return; }
          Alert.alert('Enable listening?', 'This accesses the laptop microphone. Confirm all affected people were informed and you are legally permitted to listen. Your phone microphone remains disabled.', [
            { text: 'Cancel', style: 'cancel' },
            { text: 'I have permission', onPress: () => {
              setPreference('mic_listen_opt_in', '1').then(() => setAudioListen(true))
                .catch(() => setError('Could not store microphone listening consent'));
            } },
          ]);
        }} />
      <SecondaryButton title="Take a still photo" disabled={!connected || !status?.motion_armed || busy}
        onPress={() => confirm('Photo', 'photo')} />
      <ConsentNotice>Both microphones are off by default. Listening or two-way intercom requires explicit consent. Leaving Live or backgrounding the app tears down the media and handset microphone.</ConsentNotice>
    </PaperCard>;

    if (tab === 'Evidence') return <PaperCard><SectionHeader title="Evidence vault" subtitle="Signed inventory from the paired laptop; no synthetic media." />
      {!connected ? <EmptyState message="Laptop offline; vault inventory cannot be refreshed." /> :
        evidence.length === 0 ? <EmptyState message="No complete, authenticated evidence items returned." /> :
        evidence.map((item) => <View key={item.kind + '-' + item.id}
          style={{ paddingVertical: 10, borderBottomWidth: 1, borderColor: c.hairline }}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: 15 }}>
            <EvidenceThumb kind={item.kind} />
            <View style={{ flex: 1 }}>
              <Text style={styles.metric}>{item.kind === 'photo' ? 'Photo' : 'Video'}</Text>
              <Text style={styles.body}>{item.size_bytes} bytes · {new Date(item.modified_at_ms).toLocaleString()}</Text>
            </View>
          </View>
          <SecondaryButton title={localMedia[item.id] ? 'View private copy' : 'Download signed private copy'}
            disabled={busy || !connected}
            onPress={() => perform(async () => {
              let file = localMedia[item.id];
              if (!file) {
                file = await downloadEvidence(client(), item);
                setLocalMedia((old) => ({ ...old, [item.id]: file }));
                recordPrivateEvidence(file.kind, file.id, file.uri).catch(() => {});
              }
              setSelectedMedia(file);
            })} />
        </View>)}
      {selectedMedia && <View style={{ paddingVertical: 14 }}>
        <Text style={styles.metric}>Private {selectedMedia.kind} preview</Text>
        {selectedMedia.kind === 'photo' ?
          <Image source={{ uri: selectedMedia.uri }} resizeMode="contain" style={{ width: '100%', height: 260 }} /> :
          <Video source={{ uri: selectedMedia.uri }} style={{ width: '100%', height: 240 }}
            controls paused resizeMode="contain" />}
        <SecondaryButton title="Export via system picker" disabled={busy}
          onPress={() => Alert.alert('Export private evidence?', 'The chosen destination or app will receive a copy. Confirm you have permission to share the people and information shown.', [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Choose destination', onPress: () => perform(async () => {
              await exportEvidenceLocal(selectedMedia);
              setNotice('Evidence exported only to your chosen destination.');
            }) },
          ])} />
        <DestructiveButton title="Delete phone copy" disabled={busy}
          onPress={() => Alert.alert('Delete phone copy?', 'This cannot be undone. Your laptop original is NOT deleted.', [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Delete local copy', style: 'destructive', onPress: () => perform(async () => {
              await deleteEvidenceLocal(selectedMedia);
              await forgetPrivateEvidence(selectedMedia.kind, selectedMedia.id);
              setLocalMedia((prior) => {
                const next = { ...prior }; delete next[selectedMedia.id]; return next;
              });
              setSelectedMedia(null); setNotice('Only the phone copy was removed.');
            }) },
          ])} />
        <SecondaryButton title="Close preview" onPress={() => setSelectedMedia(null)} />
      </View>}
      <ConsentNotice>Files remain in app-private storage. Sharing is disabled until export permission, redaction and owner policy are separately verified.</ConsentNotice>
    </PaperCard>;

    if (tab === 'Alerts') return <PaperCard><SectionHeader title="Verified person alerts" />
      <Text style={styles.body}>Events are metadata from the signed desktop or registered relay. Push notices alone are not proof of detection.</Text>
      {!connected && <StatusPill state="Offline" />}
      {alerts.length === 0 ? <EmptyState message="No authenticated incident events received. Remote push is not configured in this JS client." /> :
        alerts.slice().reverse().map((item, i) => <View key={String(item.id || i)} style={{ borderBottomWidth: 1, borderColor: c.hairline, paddingVertical: 12 }}>
          <Text style={styles.metric}>Person verification event #{item.id}</Text>
          <Text style={styles.body}>Confidence: {Number(item.confidence || 0).toFixed(2)}</Text>
        </View>)}
      <SecondaryButton title="Export signed incident metadata as CSV"
        disabled={busy || alerts.length === 0}
        onPress={() => Alert.alert('Export incident report?', 'Only event metadata is included. Use a secure destination and do not share private information without permission.', [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Choose destination', onPress: () => perform(async () => {
            await exportIncidents(alerts.slice(-100), 'csv');
            setNotice('Owner-controlled incident report export completed.');
          }) },
        ])} />
      <SecondaryButton title="Export incident summary as text"
        disabled={busy || alerts.length === 0}
        onPress={() => perform(async () => {
          await exportIncidents(alerts.slice(-100), 'text');
          setNotice('Incident report shared only through the selected system action.');
        })} />
      <DestructiveButton title="Clear laptop incidents" disabled={!connected || busy || alerts.length === 0}
        onPress={() => Alert.alert('Clear incident metadata?', 'This clears the laptop incident list. Stored photographs and recordings are not deleted.', [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Clear', style: 'destructive', onPress: () => perform(async () => {
            await client().status(); await client().clearIncidents();
            await clearCachedAlerts(paired.desktopId);
             setAlerts([]); afterId.current = 0; setNotice('Laptop incident metadata cleared.');
          }) },
        ])} />
    </PaperCard>;

    if (tab === 'Location') {
      const flag = locationStatus(location, Date.now(), lastFixFresh && connected);
      return <PaperCard><SectionHeader title="Laptop location" />
        <Text style={{ color: flag.live ? c.success : c.inkMuted, fontWeight: '700', marginBottom: 8 }}>{flag.label}</Text>
        <LastSeenBadge time={location ? new Date(location.observed_at_ms || 0).toLocaleString() : 'Not available'} />
        <Text style={styles.body}>Source: {locationSource}. This is historical until a newly verified GNSS reading is fetched.</Text>
        <SecondaryButton title="Request real laptop GNSS fix" disabled={!connected || busy}
          onPress={() => perform(async () => {
            const fix = await client().refreshLocation();
            if (!Number.isFinite(fix.latitude) || !Number.isFinite(fix.longitude) ||
                !Number.isFinite(fix.accuracy_meters) ||
                !Number.isSafeInteger(fix.observed_at_ms)) throw new Error('Invalid GNSS response');
            setLocation(fix); setLastFixFresh(fix.is_live_fix === true);
            setLocationSource('direct laptop on-demand GNSS fix');
            await cacheLastSeen(paired.desktopId, fix);
          })} />
        {location ? <Text style={styles.body}>Latitude {location.latitude} · Longitude {location.longitude} · ±{location.accuracy_meters} m</Text> : null}
        <Text style={styles.body}>A real GNSS receiver is required. Cached results are always LAST SEEN and are never a promise of live tracking.</Text>
        <SectionHeader title="Real GNSS history" subtitle="Only hardware-reported locations; most recent first, up to 100." />
        {locationHistory.length === 0 ? <Text style={styles.body}>No verified historical fixes loaded.</Text> :
          locationHistory.map((fix) => <View key={String(fix.event_id)}
            style={{ paddingVertical: 10, borderBottomWidth: 1, borderColor: c.hairline }}>
            <Text style={styles.metric}>LAST SEEN · {new Date(fix.observed_at_ms).toLocaleString()}</Text>
            <Text style={styles.body}>{fix.latitude.toFixed(5)}, {fix.longitude.toFixed(5)} · ±{fix.accuracy_meters} m</Text>
          </View>)}
      </PaperCard>;
    }

    return <PaperCard><SectionHeader title="Settings & privacy" subtitle="Your device, your consent and your security settings." />
      <Text style={styles.body}>Native TLS pinning: {transportAvailable ? 'Bridge present, device testing pending' : 'Not installed'}</Text>
      <Text style={styles.body}>Push: {pushEnabled ? (pushEnrolled ? 'owner-enrolled' : 'available, not enrolled') : 'not provisioned'}. Native background connection: {backgroundActive ? 'requested; native receipt pending' : 'off'}.</Text>
      <SecondaryButton title={backgroundOptIn ? 'Turn off background paired connection' : 'Enable optional armed background connection'}
        disabled={busy || !paired?.approved}
        onPress={() => {
          if (backgroundOptIn) {
            perform(async () => {
              await setPreference('background_opt_in', '0'); setBackgroundOptIn(false);
              foregroundStarted.current = false; await stopForegroundConnection(); setBackgroundActive(false);
            });
            return;
          }
          Alert.alert('Keep a signed connection while armed?', 'Android will show an ongoing notification and use battery. It cannot protect a sleeping or powered-off laptop. No camera or mic is started by this permission.', [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Enable', onPress: () => perform(async () => {
              if (!await askNotificationPermission()) throw new Error('Notification permission denied; background disabled');
              await setPreference('background_opt_in', '1'); setBackgroundOptIn(true);
            }) },
          ]);
        }} />
      <PaperCard>
        <SectionHeader title="Verified person response" subtitle="Requires a locally installed SHA-256 verified person model on the laptop." />
        <Text style={styles.body}>Detector: {status?.person_detector_configured ? 'model configured' : 'not configured or laptop offline'}. No alerts are inferred without real verified events.</Text>
        <SecondaryButton title={autoSnapshot ? 'Automatic evidence photo: enabled' : 'Automatic evidence photo: disabled'}
          disabled={!connected || busy || !status?.person_detector_configured}
          onPress={() => setAutoSnapshot((value) => !value)} />
        <SecondaryButton title={autoAlarm ? 'Automatic audible alarm: enabled' : 'Automatic audible alarm: disabled'}
          disabled={!connected || busy || !status?.person_detector_configured}
          onPress={() => setAutoAlarm((value) => !value)} />
        <SecondaryButton title={autoRecord ? 'Automatic video record: enabled' : 'Automatic video record: disabled'}
          disabled={!connected || busy || !status?.person_detector_configured}
          onPress={() => {
            if (autoRecord) { setAutoRecord(false); return; }
            Alert.alert('Authorize automatic video?', 'This can record other people when a verified person is detected. Obtain lawful notice/consent and install the approved detector model before enabling.', [
              { text: 'Cancel', style: 'cancel' },
              { text: 'I have permission', onPress: () => setAutoRecord(true) },
            ]);
          }} />
        <PrimaryButton title="Apply monitor policy on laptop"
          disabled={!connected || busy || !status?.person_detector_configured}
          onPress={() => perform(async () => {
            await client().status();
            await client().configurePersonMonitor({
              auto_snapshot: autoSnapshot, auto_alarm: autoAlarm, auto_record: autoRecord,
            });
            await Promise.all([
              setPreference('person_auto_snapshot', autoSnapshot ? '1' : '0'),
              setPreference('person_auto_alarm', autoAlarm ? '1' : '0'),
              setPreference('person_auto_record', autoRecord ? '1' : '0'),
            ]);
            setNotice('Signed detector response policy accepted by paired laptop.');
          })} />
      </PaperCard>
      <PaperCard>
        <SectionHeader title="Optional owner HTTPS relay" subtitle="Remote last-seen and event metadata are encrypted and separately signed. No remote action queue." />
        <TextInput style={{ backgroundColor: c.surface, borderWidth: 1, borderColor: c.hairline,
          borderRadius: 8, color: c.ink, padding: 12, marginBottom: 8 }}
          value={relayInput} onChangeText={setRelayInput} autoCapitalize="none"
          keyboardType="url" accessibilityLabel="Owner relay HTTPS URL"
          placeholder="https://relay.example.com" />
        <SecondaryButton title="Save relay URL" disabled={busy}
          onPress={() => perform(async () => {
            const address = relayInput.trim();
            const normalized = address ? normalizedRelayUrl(address) : '';
            await setPreference('relay_https_url', normalized);
            setRelayUrl(normalized); setRelayInput(normalized);
            setNotice(normalized ? 'Owner relay configured locally. Enrollment is still required on the relay.' : 'Relay disabled locally.');
          })} />
        <Text style={styles.body}>Firebase: {pushEnabled ? 'available, opt-in only' : 'not provisioned on this build'}. {pushStatusMessage()}</Text>
        <SecondaryButton title={pushEnrolled ? 'Request FCM unregistration' : 'Register optional incident notifications'}
          disabled={!pushEnabled || !paired?.approved || !relayUrl || busy}
          onPress={() => Alert.alert(pushEnrolled ? 'Disable push?' : 'Enable incident notifications?', 'Only signed event metadata may trigger generic notifications. Firebase is not an action channel and incident details require a signed query.', [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Confirm', onPress: () => perform(async () => {
              if (pushEnrolled) {
                await unregisterPush(paired, relayUrl);
                await setPreference('push_opt_in','0'); setPushEnrolled(false);
                setNotice('Remote push registration revoked.');
              } else {
                if (!await askNotificationPermission()) throw new Error('Notification permission denied');
                await registerPush(paired, relayUrl);
                await setPreference('push_opt_in','1'); setPushEnrolled(true);
                setNotice('Push registration accepted by the owner relay.');
              }
            }) },
          ])} />
        <SecondaryButton title="Check signed push enrollment" disabled={!pushEnabled || !paired?.approved || !relayUrl || busy}
          onPress={() => perform(async () => {
            const remote = await pushEnrollmentStatus(paired, relayUrl);
            setNotice('Signed relay enrollment: ' + (remote.registered === true ? 'registered' : 'not registered'));
          })} />
      </PaperCard>
      <Text style={styles.body}>No analytics. No covert recording. Audio and automatic video are off by default.</Text>
      <DestructiveButton title="Forget this paired phone" disabled={!paired || busy}
        onPress={() => Alert.alert('Revoke this local pairing?', 'This removes local credentials. Revoke the device on the desktop too.', [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Forget locally', style: 'destructive', onPress: () => perform(async () => {
            if (pushEnrolled && relayUrl) {
              try { await unregisterPush(paired, relayUrl); }
              catch (_) {
                throw new Error('Remote push revocation failed. Reconnect to your owner relay and disable push before forgetting this device.');
              }
            }
            await setPreference('push_opt_in','0');
            foregroundStarted.current = false; await stopForegroundConnection().catch(() => {});
            setBackgroundOptIn(false); setBackgroundActive(false);
            await setPreference('background_opt_in', '0');
            await purgePrivateEvidence();
             if (paired?.desktopId) await deleteDesktopCache(paired.desktopId);
            await clearIdentity(); setPaired(null); setStatus(null); setConnected(false);
            setLocation(null); setLocationHistory([]); setLastFixFresh(false); setAlerts([]); setEvidence([]); setLocalMedia({}); setSelectedMedia(null);
            setAudioListen(false); setTalkbackEnabled(false);
            if (liveSession.current) { await liveSession.current.stop(); liveSession.current = null; }
            setLiveStream(null); afterId.current = 0;
          }) },
        ])} />
    </PaperCard>;
  };

  if (!consentReady) return <SafeAreaView style={styles.root}><View style={styles.content}>
    <ActivityIndicator color={c.blue} accessibilityLabel="Opening protected consent settings" />
  </View></SafeAreaView>;

  if (!consented) return <SafeAreaView style={styles.root}><ScrollView contentContainerStyle={styles.content}>
    <Text style={styles.kicker}>LAPTOPWATCH · PERSONAL SECURITY</Text>
    <SectionHeader title="Protection with permission." subtitle="Visible, consent-based monitoring of a device you own and control." />
    <ConsentNotice>Photographs and video can capture other people. Inform everyone affected and comply with local privacy law. Microphone recording is off by default. No hidden camera activity or offline action queue.</ConsentNotice>
    <PaperCard><Text style={styles.body}>Pair your phone in person. Every remote action must be signed and accepted by the awake laptop. The owner can stop monitoring and revoke access.</Text>
      <PrimaryButton title="I understand and agree" onPress={() => {
        setPreference('owner_consent_v1', '1').then(() => setConsented(true))
          .catch(() => setError('Could not securely record consent; controls remain disabled'));
      }} />
      <Toast message={error} error />
    </PaperCard>
  </ScrollView></SafeAreaView>;

  return <SafeAreaView style={styles.root}>
    <View style={styles.header}><Text style={styles.kicker}>OWNER CONTROL · SIGNED REQUESTS</Text><Text style={styles.brand}>LaptopWatch</Text></View>
    <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ flexGrow: 0 }} contentContainerStyle={styles.nav}>
      {tabs.map((name) => <Pressable key={name} accessibilityRole="tab" accessibilityState={{ selected: tab === name }}
        style={[styles.tab, tab === name && styles.tabActive]} onPress={() => setTab(name)}>
        <Text style={styles.tabText}>{name}</Text>
      </Pressable>)}
    </ScrollView>
    <ScrollView contentContainerStyle={styles.content}>
      <Toast message={error} error />
      <Toast message={notice} />
      {busy && <ActivityIndicator color={c.blue} accessibilityLabel="Waiting for signed desktop response" />}
      {screen()}
    </ScrollView>
  </SafeAreaView>;
}
