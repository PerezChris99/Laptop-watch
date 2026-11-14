package com.kc.laptopwatch

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/** Register in MainApplication only after the React Native Android scaffold
 * and its Gradle dependencies are created and tested on real devices. */
class LaptopWatchPinnedPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(LaptopWatchPinnedTransport(context))

    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
