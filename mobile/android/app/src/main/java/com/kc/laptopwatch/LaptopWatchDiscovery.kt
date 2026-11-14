package com.kc.laptopwatch

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.ViewManager
import java.net.Inet4Address
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owner-initiated mDNS candidate lookup, not authorization.
 * JavaScript MUST revalidate every returned IP with the already pinned
 * TLS certificate AND freshly signed /api/status before persisting it.
 */
class LaptopWatchDiscovery(private val app: ReactApplicationContext)
    : ReactContextBaseJavaModule(app) {
    override fun getName() = "LaptopWatchDiscovery"
    private val handler = Handler(Looper.getMainLooper())

    @ReactMethod
    fun find(params: ReadableMap, promise: Promise) {
        try {
            val desktopId = params.getString("desktopId") ?: error("No paired laptop ID")
            UUID.fromString(desktopId)
            val fingerprint = params.getString("tlsFingerprint") ?: error("No TLS pin")
            require(fingerprint.matches(Regex("[a-f0-9]{64}")))
            val port = params.getInt("port")
            require(port in 1024..65535)
            val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
            val once = AtomicBoolean(false)
            lateinit var listener: NsdManager.DiscoveryListener
            fun stop(value: String?) {
                if (!once.compareAndSet(false, true)) return
                try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {}
                if (value != null) promise.resolve(value)
                else promise.reject("DISCOVERY_UNAVAILABLE", "No matching paired laptop advertised on this LAN")
            }
            listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { stop(null) }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { stop(null) }
                override fun onServiceFound(info: NsdServiceInfo) {
                    if (!info.serviceType.startsWith("_laptopwatch._tcp")) return
                    @Suppress("DEPRECATION")
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(result: NsdServiceInfo) {
                            if (once.get()) return
                            val attrs = result.attributes
                            val identity = attrs["id"]?.toString(Charsets.UTF_8)
                            val hash = attrs["fp"]?.toString(Charsets.UTF_8)
                            val version = attrs["v"]?.toString(Charsets.UTF_8)
                            val ipv4 = (result.host as? Inet4Address)?.hostAddress ?: return
                            val bytes = ipv4.split(".").mapNotNull { it.toIntOrNull() }
                            if (bytes.size != 4 || bytes.any { it !in 0..255 }) return
                            val privateNetwork = bytes[0] == 10 ||
                                (bytes[0] == 172 && bytes[1] in 16..31) ||
                                (bytes[0] == 192 && bytes[1] == 168)
                            if (privateNetwork && identity == desktopId && hash == fingerprint &&
                                version == "1" && result.port == port) stop(ipv4)
                        }
                    })
                }
            }
            handler.post {
                try {
                    nsd.discoverServices("_laptopwatch._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
                    handler.postDelayed({ stop(null) }, 7_000)
                } catch (_: Exception) { stop(null) }
            }
        } catch (_: Exception) {
            promise.reject("DISCOVERY_INVALID", "Paired laptop discovery could not start")
        }
    }
}

class LaptopWatchDiscoveryPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(LaptopWatchDiscovery(context))
    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
