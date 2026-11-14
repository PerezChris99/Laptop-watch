import 'react-native-get-random-values';
import 'react-native-url-polyfill/auto';
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { RTCView } from 'react-native-webrtc';
import { startLiveView } from './media/live.js';
import { locationStatus } from './location/labels.js';
import { cacheLastSeen, cacheEvidence } from './storage/db.js';
import { ActivityIndicator, Alert, AppState, Pressable, SafeAreaView, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
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
  field: { backgroundColor: c.surface, borderWidth: 1, borderColor: c.hairline, borderRadius: 10, padding: 13, fontSize: 14, color: c.ink, marginBottom: 12, minHeight: 50 },
  label: { color: c.ink, fontSize: 13, fontWeight: '700', marginBottom: 6 },
  metric: { color: c.ink, fontSize: 18, fontWeight: '600' },
});

function readableError(error) { return error?.message || 'Request failed safely'; }

export default function App() {
  const [consented, setConsented] = useState(false);
  const [tab, setTab] = useState('Home');
  const [paired, setPaired] = useState(null);
  const [qr, setQr] = useState('');
  const [status, setStatus] = useState(null);
  const [connected, setConnected] = useState(false);
  const [location, setLocation] = useState(null);
  const [alerts, setAlerts] = useState([]);
  const [evidence, setEvidence] = useState([]);
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
    try { pinnedTransport(); setTransportAvailable(true); }
    catch (_) { setTransportAvailable(false); }
    return () => { mounted.current = false; };
  }, []);

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

  const client = useCallback(() => {
    if (!paired?.approved) throw new Error('Phone must be approved on the laptop');
    return new SignedClient(paired, pinnedTransport());
  }, [paired]);

  const refresh = useCallback(async () => {
    if (!paired?.approved || !transportAvailable || AppState.currentState !== 'active') {
      if (mounted.current) setConnected(false);
      return;
    }
    try {
      const response = await client().status();
      if (!mounted.current) return;
      if (response.online !== true) throw new Error('Invalid desktop status');
      setStatus(response); setConnected(true);
      try {
        const fix = await client().lastSeen();
        if (mounted.current && fix && typeof fix === 'object') {
          setLocation(fix);
          setLastFixFresh(true);
          cacheLastSeen(paired.desktopId, fix).catch(() => {});
        }
      } catch (_) {
        // Retain previous location as LAST SEEN, never LIVE.
        if (mounted.current) setLastFixFresh(false);
      }
      try {
        const result = await client().incidents(afterId.current);
        if (mounted.current && Array.isArray(result.events)) {
          setAlerts((old) => [...old, ...result.events].slice(-100));
          if (Number.isSafeInteger(result.next_after_id)) afterId.current = result.next_after_id;
        }
      } catch (_) { /* Optional guard/relay not configured */ }
    } catch (_) {
      if (mounted.current) { setConnected(false); setStatus(null); setLastFixFresh(false); }
    }
  }, [paired, transportAvailable, client]);

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
        setNotice(label + ' accepted by the desktop gateway.');
        refresh();
      }) },
    ]);
  };

  const startLive = () => perform(async () => {
    if (liveSession.current || !connected || !status?.motion_armed) {
      throw new Error('Arm an online laptop before requesting its camera');
    }
    const session = await startLiveView(client(), setLiveStream);
    liveSession.current = session;
    setNotice('Encrypted live receiver requested. Microphone is disabled.');
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
          await refresh();
        }) },
      ]);
  };

  const screen = () => {
    if (tab === 'Pair') return <PaperCard><SectionHeader title="Pair your laptop" subtitle="Scan the owner-displayed QR in person. Only an approved device can control the laptop." />
      {!transportAvailable && <ConsentNotice>Native certificate pinning has not been installed. Pairing is disabled; unpinned networking is never used.</ConsentNotice>}
      {paired ? <>
        <StatusPill state={paired.approved ? 'Online' : 'Not configured'} />
        <Text style={styles.body}>Desktop ID: {paired.desktopId}</Text>
        {!paired.approved && <SecondaryButton title="Check desktop approval" onPress={approveNow} disabled={busy || !transportAvailable} />}
      </> : <>
        <Text style={styles.label}>QR invitation (temporary text entry until camera scanner is integrated)</Text>
        <TextInput style={styles.field} value={qr} onChangeText={setQr} multiline autoCapitalize="none"
          placeholder="laptopwatch://pair?…" accessibilityLabel="Paste pairing invitation" />
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
        <PrimaryButton title="Request live video" disabled={!connected || !status?.motion_armed || busy} onPress={startLive} /> :
        <DestructiveButton title="End live session" disabled={busy} onPress={stopLive} />}
      <SecondaryButton title="Take a still photo" disabled={!connected || !status?.motion_armed || busy}
        onPress={() => confirm('Photo', 'photo')} />
      <ConsentNotice>Microphone disabled. Intercom must not activate until there is a verified, separate permission and consent flow.</ConsentNotice>
    </PaperCard>;

    if (tab === 'Evidence') return <PaperCard><SectionHeader title="Evidence vault" subtitle="Signed inventory from the paired laptop; no synthetic media." />
      {!connected ? <EmptyState message="Laptop offline; vault inventory cannot be refreshed." /> :
        evidence.length === 0 ? <EmptyState message="No complete, authenticated evidence items returned." /> :
        evidence.map((item) => <View key={item.kind + '-' + item.id}
          style={{ flexDirection: 'row', alignItems: 'center', gap: 15, paddingVertical: 10, borderBottomWidth: 1, borderColor: c.hairline }}>
          <EvidenceThumb kind={item.kind} />
          <View><Text style={styles.metric}>{item.kind === 'photo' ? 'Photo' : 'Video'}</Text>
            <Text style={styles.body}>{item.size_bytes} bytes · {new Date(item.modified_at_ms).toLocaleString()}</Text></View>
        </View>)}
      <ConsentNotice>Export, preview, secure app-private downloading and deletion still require device and privacy acceptance tests.</ConsentNotice>
    </PaperCard>;

    if (tab === 'Alerts') return <PaperCard><SectionHeader title="Verified person alerts" />
      {!connected && <StatusPill state="Offline" />}
      {alerts.length === 0 ? <EmptyState message="No authenticated incident events received. Remote push is not configured in this JS client." /> :
        alerts.slice().reverse().map((item, i) => <View key={String(item.id || i)} style={{ borderBottomWidth: 1, borderColor: c.hairline, paddingVertical: 12 }}>
          <Text style={styles.metric}>Person verification event #{item.id}</Text>
          <Text style={styles.body}>Confidence: {Number(item.confidence || 0).toFixed(2)}</Text>
        </View>)}
    </PaperCard>;

    if (tab === 'Location') {
      const flag = locationStatus(location, Date.now(), lastFixFresh && connected);
      return <PaperCard><SectionHeader title="Laptop location" />
        <Text style={{ color: flag.live ? c.success : c.inkMuted, fontWeight: '700', marginBottom: 8 }}>{flag.label}</Text>
        <LastSeenBadge time={location ? new Date(location.observed_at_ms || 0).toLocaleString() : 'Not available'} />
        {location ? <Text style={styles.body}>Latitude {location.latitude} · Longitude {location.longitude} · ±{location.accuracy_meters} m</Text> : null}
        <Text style={styles.body}>A real GNSS receiver is required. Cached results are always LAST SEEN and are never a promise of live tracking.</Text>
      </PaperCard>;
    }

    return <PaperCard><SectionHeader title="Settings & readiness" subtitle="Secure defaults — without a verified transport, control remains unavailable." />
      <Text style={styles.body}>Native TLS pinning: {transportAvailable ? 'Bridge present, device testing pending' : 'Not installed'}</Text>
      <Text style={styles.body}>Cloud push: not configured. Background service: not integrated. WebRTC: not integrated.</Text>
      <Text style={styles.body}>No analytics. No covert recording. No automatic audio capture.</Text>
      <DestructiveButton title="Forget this paired phone" disabled={!paired || busy}
        onPress={() => Alert.alert('Revoke this local pairing?', 'This removes local credentials. Revoke the device on the desktop too.', [
          { text: 'Cancel', style: 'cancel' },
          { text: 'Forget locally', style: 'destructive', onPress: () => perform(async () => {
            await clearIdentity(); setPaired(null); setStatus(null); setConnected(false);
            setLocation(null); setLastFixFresh(false); setAlerts([]); setEvidence([]);
            if (liveSession.current) { await liveSession.current.stop(); liveSession.current = null; }
            setLiveStream(null); afterId.current = 0;
          }) },
        ])} />
    </PaperCard>;
  };

  if (!consented) return <SafeAreaView style={styles.root}><ScrollView contentContainerStyle={styles.content}>
    <Text style={styles.kicker}>LAPTOPWATCH · PERSONAL SECURITY</Text>
    <SectionHeader title="Protection with permission." subtitle="Visible, consent-based monitoring of a device you own and control." />
    <ConsentNotice>Photographs and video can capture other people. Inform everyone affected and comply with local privacy law. Microphone recording is off by default. No hidden camera activity or offline action queue.</ConsentNotice>
    <PaperCard><Text style={styles.body}>Pair your phone in person. Every remote action must be signed and accepted by the awake laptop. The owner can stop monitoring and revoke access.</Text>
      <PrimaryButton title="I understand and agree" onPress={() => setConsented(true)} />
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
