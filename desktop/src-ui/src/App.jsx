import React, { useCallback, useEffect, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { enable, disable, isEnabled } from '@tauri-apps/plugin-autostart'
import { QRCodeSVG } from 'qrcode.react'

const formatError = (e) => typeof e === 'string' ? e : e?.message || 'Operation failed'

const panels = [
  { id: 'overview', title: 'Overview' },
  { id: 'pair', title: 'Pair a phone' },
  { id: 'devices', title: 'Paired devices' },
  { id: 'evidence', title: 'Evidence' },
  { id: 'settings', title: 'Settings' },
]

export default function App () {
  const [page, setPage] = useState('overview')
  const [ready, setReady] = useState(false)
  const [protecting, setProtecting] = useState(false)
  const [invite, setInvite] = useState(null)
  const [lanIP, setLanIP] = useState('')
  const [pending, setPending] = useState([])
  const [devices, setDevices] = useState([])
  const [evidence, setEvidence] = useState([])
  const [autostart, setAutostart] = useState(false)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  const execute = async (name, fn) => {
    setBusy(name); setError(''); setMessage('')
    try { return await fn() } catch (e) { setError(formatError(e)); return null } finally { setBusy('') }
  }
  const refresh = useCallback(async () => {
    try {
      const [p, d, e] = await Promise.all([
        invoke('pending_pairs'),
        invoke('paired_devices'),
        invoke('evidence_files'),
      ])
      setPending(JSON.parse(p)); setDevices(JSON.parse(d)); setEvidence(e)
    } catch (e) { /* transient startup or sidecar process state; never fake data */ }
  }, [])

  useEffect(() => {
    let active = true
    invoke('initialize_companion').then(async (identity) => {
      if (!active) return
      setReady(true)
      try { setLanIP(await invoke('suggest_lan_ip')) } catch (_) {}
      try { setAutostart(await isEnabled()) } catch (_) {}
      refresh()
    }).catch(e => { if (active) setError(formatError(e)) })
    const id = setInterval(() => { if (active) refresh() }, 4000)
    return () => { active = false; clearInterval(id) }
  }, [refresh])

  const createInvite = () => execute('pair', async () => {
    const response = await invoke('create_pair_invitation', { host: lanIP })
    const parsed = JSON.parse(response)
    setInvite(parsed)
    setMessage('Scan on Android; verify the laptop, then approve the phone below.')
    await invoke('start_companion', { allowLan: true })
    setProtecting(true)
  })

  const approve = (id) => execute('approve', async () => {
    await invoke('approve_pending', { requestId: id })
    setMessage('Phone approved. Android should now verify its signed status request.')
    refresh()
  })

  const revoke = (id) => execute('revoke', async () => {
    if (!window.confirm('Remove this phone’s remote access?')) return
    await invoke('revoke_device', { deviceId: id })
    setMessage('Access revoked.')
    refresh()
  })

  const stop = () => execute('stop', async () => {
    await invoke('stop_companion')
    setProtecting(false)
    setMessage('Desktop gateway stopped.')
  })

  const updateAutostart = (checked) => execute('autostart', async () => {
    if (checked) await enable()
    else await disable()
    setAutostart(await isEnabled())
  })

  return (
    <div className="app">
      <aside className="sidebar" aria-label="Main navigation">
        <div className="brand"><img src="/app_icon.jpg" alt="" /><div><strong>LaptopWatch</strong><small>SECURITY COMPANION</small></div></div>
        <nav>{panels.map(p => <button key={p.id} className={page === p.id ? 'active' : ''} onClick={() => setPage(p.id)}>{p.title}</button>)}</nav>
        <div className="sidebarFooter">Monitoring requires your consent. Visible camera activity only.</div>
      </aside>
      <main className="main" aria-live="polite">
        <div className="topline"><span className="eyebrow">LAPTOPWATCH / {page.toUpperCase()}</span><span className={'status ' + (protecting ? 'green' : '')}>{protecting ? 'LAN gateway launched' : 'Not monitoring'}</span></div>
        {error && <p role="alert" className="error">{error}</p>}
        {message && <p className="notice">{message}</p>}
        {!ready && !error && <p>Preparing a private desktop identity…</p>}
        {page === 'overview' && <>
          <h1>Protect your laptop. Stay in control.</h1>
          <p className="lede">LaptopWatch pairs your own workstation with Android. Approve each phone, control when the camera is armed, and retain evidence locally.</p>
          <div className="hero"><img src="/hero_banner.jpg" alt="LaptopWatch laptop and Android anti-theft concept" /></div>
          <div className="grid">
            <div className="panel"><h3>1. Pair</h3><p>Show a two-minute QR. The phone must prove its key and you approve on this laptop.</p><button disabled={!ready || !!busy} onClick={() => setPage('pair')}>Pair Android</button></div>
            <div className="panel"><h3>2. Arm</h3><p>Only a paired controller can arm, photograph, record or sound a deterrent. The webcam remains visible to the OS.</p><span className="muted">No silent background camera permission.</span></div>
            <div className="panel"><h3>3. Recover</h3><p>Review previous evidence. Location requires a real receiver; the last fix survives disconnection.</p><button disabled={!ready} onClick={() => setPage('evidence')}>Local evidence</button></div>
          </div>
          <h2>Desktop gateway</h2>
          <p>Pairing requires a running HTTPS gateway and a valid signed QR. To stop the listener and release resources, use Stop gateway or quit.</p>
          <button className="danger" disabled={!protecting || !!busy} onClick={stop}>Stop gateway</button>
        </>}
        {page === 'pair' && <>
          <h1>Pair an Android phone</h1>
          <p className="lede">On Android, tap Scan Laptop QR. This invitation expires after 120 seconds and requires a final approval on this screen. The TLS fingerprint is bound to the QR.</p>
          <div className="panel pairing">
            <label htmlFor="host">Laptop LAN IPv4 (detected; edit if incorrect)</label>
            <input id="host" value={lanIP} onChange={e => setLanIP(e.target.value)} placeholder="192.168.1.25" />
            <button className="primary" disabled={!ready || !!busy || !lanIP} onClick={createInvite}>Create pairing QR and start secure listener</button>
            {invite && <div className="qrcode"><QRCodeSVG value={invite.uri} size={234} bgColor="#FFFFFF" fgColor="#0B1B3D" marginSize={4} level="M" /><p>Keep this QR visible while the phone requests pairing.</p><small>Expiration: 120 seconds after generation. Rescan if expired.</small></div>}
          </div>
          <h2>Approve a phone</h2>
          {pending.length === 0 ? <p className="empty">No phone awaiting approval. Scan the QR from the Android app.</p> : pending.map(p => <div className="device" key={p.request_id}><div><strong>{p.label}</strong><small>Identity fingerprint: {p.public_key_sha256}</small></div><div className="actions"><button onClick={() => approve(p.request_id)} disabled={!!busy}>Approve</button><button className="secondary" onClick={() => execute('reject', () => invoke('reject_pending',{requestId:p.request_id}).then(refresh))}>Reject</button></div></div>)}
        </>}
        {page === 'devices' && <>
          <h1>Trusted phones</h1>
          <p className="lede">Each phone has its own key. Revoking one does not affect your other devices.</p>
          {devices.length === 0 ? <p className="empty">No paired phones. Pair one before enabling remote access.</p> : devices.map(d => <div className="device" key={d.id}><div><strong>{d.id}</strong><small>{d.revoked ? 'Revoked' : 'Paired and authorized'}</small></div>{!d.revoked && <button className="danger" disabled={!!busy} onClick={() => revoke(d.id)}>Revoke</button>}</div>)}
        </>}
        {page === 'evidence' && <>
          <h1>Local evidence</h1>
          <p className="lede">Photographs and recordings remain on this laptop. Only armed, authenticated actions can create new evidence. Files are not uploaded by default.</p>
          {evidence.length === 0 ? <p className="empty">No evidence files yet. Arm from a paired phone, then capture a photo or video.</p> : <ul className="files">{evidence.map(e => <li key={e.name}><strong>{e.name}</strong><span>{(e.size / 1048576).toFixed(2)} MB</span></li>)}</ul>}
        </>}
        {page === 'settings' && <>
          <h1>Safety &amp; startup</h1>
          <div className="panel"><div className="switchrow"><div><h3>Launch LaptopWatch at login</h3><p>Optional. Starting the app does not automatically arm your camera.</p></div><input type="checkbox" checked={autostart} onChange={e => updateAutostart(e.target.checked)} aria-label="Launch at login" /></div></div>
          <div className="panel"><h3>Privacy</h3><p>Only record where you are legally permitted. Camera hardware indicators stay on during capture; stop and disarm explicitly. Sleeping or powered-off laptops cannot stream live video or run ordinary GNSS polling.</p></div>
          <div className="panel"><h3>Release status</h3><p>This desktop frontend is under development and requires packaging, signing, installer tests, OS key-storage hardening and real-device verification.</p></div>
        </>}
        <footer>Built for private, consent-based laptop security. No stealth capture.</footer>
      </main>
    </div>
  )
}
