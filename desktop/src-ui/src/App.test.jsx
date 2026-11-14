import React from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import App from './App.jsx'

vi.mock('@tauri-apps/api/core', () => ({
  invoke: vi.fn(async (command) => {
    if (command === 'initialize_companion') return '{}'
    if (command === 'suggest_lan_ip') return '192.168.1.20'
    if (command === 'dashboard_snapshot') return JSON.stringify({ pending: [], devices: [], incidents: [] })
    if (command === 'evidence_files') return []
    if (command === 'companion_health') return { running: false, last_error: null }
    if (command === 'location_snapshot') return JSON.stringify({ last_seen: null })
    if (command === 'audit_history') return '[]'
    throw new Error('Unrecognized test IPC command: ' + command)
  }),
}))

vi.mock('@tauri-apps/plugin-autostart', () => ({
  isEnabled: vi.fn(async () => false),
  enable: vi.fn(async () => {}),
  disable: vi.fn(async () => {}),
}))

afterEach(() => cleanup())

describe('LaptopWatch desktop owner navigation', () => {
  it('renders a calm paper home without starting camera controls', async () => {
    render(<App />)
    expect(screen.getByText('Protect your laptop. Stay in control.')).toBeTruthy()
    expect(screen.getByText(/No silent background camera permission/)).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Pair a phone' })).toBeTruthy()
  })

  it('labels cached GNSS honestly and shows no fake coordinates', async () => {
    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: 'Location' }))
    expect(await screen.findByText(/No verified GNSS history/)).toBeTruthy()
    expect(screen.getByText('LAST SEEN · NOT LIVE')).toBeTruthy()
  })

  it('does not display unverified audit events', async () => {
    render(<App />)
    fireEvent.click(screen.getByRole('button', { name: 'Audit log' }))
    expect(await screen.findByText('No verified audit events available.')).toBeTruthy()
  })
})
