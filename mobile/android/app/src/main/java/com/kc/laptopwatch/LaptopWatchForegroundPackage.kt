package com.kc.laptopwatch

import android.content.Intent
import android.os.Build
import android.util.Base64
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.ViewManager
import java.util.UUID

class LaptopWatchForegroundConnection(private val app: ReactApplicationContext)
    : ReactContextBaseJavaModule(app) {
    override fun getName() = "LaptopWatchForegroundConnection"

    @ReactMethod
    fun start(args: ReadableMap, promise: Promise) {
        try {
            if (Build.VERSION.SDK_INT < 26) error("Unsupported Android SDK")
            val id = args.getString("deviceId") ?: error("No owner-approved device ID")
            UUID.fromString(id)
            val host = args.getString("host") ?: error("No paired LAN address")
            val octets = host.split(".").map {
                require(it.matches(Regex("0|[1-9][0-9]{0,2}"))) { "Invalid IP" }
                it.toInt().also { n -> require(n in 0..255) }
            }
            require(octets.size == 4 && (
                octets[0] == 10 || (octets[0] == 172 && octets[1] in 16..31) ||
                (octets[0] == 192 && octets[1] == 168)
            )) { "Remote public destination not permitted" }
            val pin = args.getString("tlsFingerprint") ?: error("No TLS pin")
            require(pin.matches(Regex("[0-9a-f]{64}"))) { "Invalid TLS pin" }
            val port = args.getInt("port")
            require(port in 1024..65535) { "Invalid port" }
            val encoded = args.getString("privateSeedB64") ?: error("Missing signed identity")
            val seed = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            require(seed.size == 32) { "Invalid signed identity" }
            require(LaptopWatchForegroundService.pendingSession == null) {
                "Foreground connection already started"
            }
            LaptopWatchForegroundService.pendingSession =
                LaptopWatchForegroundService.Session(host, port, pin, id, seed)
            val intent = Intent(app, LaptopWatchForegroundService::class.java)
            app.startForegroundService(intent)
            promise.resolve(true)
        } catch (_: Exception) {
            promise.reject("FGS_NOT_AVAILABLE", "Foreground connection cannot be started safely")
        }
    }

    @ReactMethod
    fun stop(promise: Promise) {
        try {
            app.stopService(Intent(app, LaptopWatchForegroundService::class.java))
            // The worker owns the key zeroization while exiting; never persist.
            promise.resolve(true)
        } catch (_: Exception) {
            promise.reject("FGS_STOP_FAILED", "Foreground service could not be stopped")
        }
    }
}

class LaptopWatchForegroundPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(LaptopWatchForegroundConnection(context))

    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
