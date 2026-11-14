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

class MainApplication : Application(), ReactApplication {
    override val reactHost: ReactHost by lazy {
        getDefaultReactHost(
            context = applicationContext,
            packageList = PackageList(this).packages.apply {
                add(LaptopWatchPinnedPackage())
                add(LaptopWatchForegroundPackage())
                add(LaptopWatchEvidencePackage())
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        loadReactNative(this)
    }
}
