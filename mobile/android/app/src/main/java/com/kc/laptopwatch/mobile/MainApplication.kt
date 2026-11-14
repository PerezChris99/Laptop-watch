package com.kc.laptopwatch.mobile

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.kc.laptopwatch.LaptopWatchPinnedPackage
import com.kc.laptopwatch.LaptopWatchForegroundPackage
import com.kc.laptopwatch.LaptopWatchEvidencePackage
import com.kc.laptopwatch.LaptopWatchRelayPackage
import com.kc.laptopwatch.LaptopWatchEvidenceManagementPackage
import com.kc.laptopwatch.LaptopWatchDiscoveryPackage

class MainApplication : Application(), ReactApplication {
    override val reactHost: ReactHost by lazy {
        getDefaultReactHost(
            context = applicationContext,
            packageList = PackageList(this).packages.apply {
                add(LaptopWatchPinnedPackage())
                add(LaptopWatchForegroundPackage())
                add(LaptopWatchEvidencePackage())
                add(LaptopWatchRelayPackage())
                add(LaptopWatchEvidenceManagementPackage())
                add(LaptopWatchDiscoveryPackage())
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        loadReactNative(this)
    }
}
