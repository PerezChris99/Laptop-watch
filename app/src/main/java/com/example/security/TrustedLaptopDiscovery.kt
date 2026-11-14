package com.example.security

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * One-shot, user-initiated mDNS discovery. Never trusts mDNS alone:
 * a candidate must match the desktop ID and pinned TLS cert fingerprint
 * before its address may be used, then signed HTTPS verifies it again.
 */
internal class TrustedLaptopDiscovery(context: Context) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val type = "_laptopwatch._tcp."

    suspend fun find(pair: PairedLaptop, timeoutMs: Long = 7000): String? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<String?> { continuation ->
                val delivered = AtomicBoolean(false)
                lateinit var discovery: NsdManager.DiscoveryListener

                fun stop() {
                    runCatching { nsd.stopServiceDiscovery(discovery) }
                }

                fun finish(value: String?) {
                    if (delivered.compareAndSet(false, true)) {
                        stop()
                        if (continuation.isActive) continuation.resume(value)
                    }
                }
                discovery = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {}
                    override fun onServiceFound(info: NsdServiceInfo) {
                        if (!info.serviceType.startsWith("_laptopwatch._tcp")) return
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                val attrs = resolved.attributes
                                val desktop = attrs["id"]?.toString(Charsets.UTF_8)
                                val fp = attrs["fp"]?.toString(Charsets.UTF_8)
                                val version = attrs["v"]?.toString(Charsets.UTF_8)
                                val ip = (resolved.host as? Inet4Address)?.hostAddress
                                if (desktop == pair.desktopId && fp == pair.tlsFingerprint &&
                                    version == "1" && ip != null && PairingCrypto.isPrivateIPv4(ip) &&
                                    resolved.port == pair.port) {
                                    finish(ip)
                                }
                            }
                        })
                    }
                    override fun onServiceLost(info: NsdServiceInfo) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { finish(null) }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { finish(null) }
                }
                try {
                    nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery)
                } catch (ex: Exception) {
                    finish(null)
                }
                continuation.invokeOnCancellation { stop() }
            }
        }
}
