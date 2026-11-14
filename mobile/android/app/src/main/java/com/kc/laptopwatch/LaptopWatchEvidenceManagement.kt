package com.kc.laptopwatch

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.ActivityEventListener
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.uimanager.ViewManager
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors

/** Explicit user export through ACTION_CREATE_DOCUMENT. Media is never
 * written into Downloads automatically, and no file:// URI is shared.
 */
class LaptopWatchEvidenceManagement(private val app: ReactApplicationContext)
    : ReactContextBaseJavaModule(app) {
    private val worker = Executors.newSingleThreadExecutor()
    private var pending: Promise? = null
    private var source: File? = null
    private var reportContent: String? = null
    private val requestCode = 0x5A21

    override fun getName() = "LaptopWatchEvidenceManagement"

    private fun verified(kind: String, id: String): File {
        require(kind in setOf("photo", "video") && id.matches(Regex("[a-f0-9]{32}")))
        val extension = if (kind == "photo") "jpg" else "mp4"
        val directory = File(app.filesDir, "evidence")
        val target = File(directory, "$kind-$id.$extension")
        require(directory.isDirectory && target.isFile && !Files.isSymbolicLink(target.toPath())) {
            "Private evidence not available"
        }
        return target
    }

    @ReactMethod
    fun deleteLocal(kind: String, id: String, promise: Promise) {
        worker.execute {
            try {
                val file = verified(kind, id)
                require(file.delete()) { "Local evidence could not be removed" }
                promise.resolve(true)
            } catch (_: Exception) {
                promise.reject("EVIDENCE_DELETE_FAILED", "Private evidence could not be removed")
            }
        }
    }

    /** Explicit unpair purge, restricted to locally downloaded evidence. */
    @ReactMethod
    fun deleteAllLocal(promise: Promise) {
        worker.execute {
            try {
                val directory = File(app.filesDir, "evidence")
                if (directory.exists()) {
                    require(directory.isDirectory && !Files.isSymbolicLink(directory.toPath()))
                    val items = directory.listFiles() ?: error("Cannot inspect private evidence")
                    val pattern = Regex("photo-[a-f0-9]{32}[.]jpg|video-[a-f0-9]{32}[.]mp4")
                    require(items.all {
                        it.isFile && !Files.isSymbolicLink(it.toPath()) && pattern.matches(it.name)
                    }) { "Unexpected private entry; refusing destructive purge" }
                    items.forEach { require(it.delete()) { "Cannot delete private evidence" } }
                    require(directory.delete()) { "Cannot delete private directory" }
                }
                promise.resolve(true)
            } catch (_: Exception) {
                promise.reject("EVIDENCE_PURGE_FAILED", "Private evidence was not fully purged")
            }
        }
    }

    @ReactMethod
    fun exportLocal(kind: String, id: String, promise: Promise) {
        app.runOnUiQueueThread {
            try {
                check(pending == null) { "A file export is already in progress" }
                val activity = app.currentActivity ?: error("Open the app to export evidence")
                val file = verified(kind, id)
                pending = promise
                source = file
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(if (kind == "photo") "image/jpeg" else "video/mp4")
                    .putExtra(Intent.EXTRA_TITLE, file.name)
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                activity.startActivityForResult(intent, requestCode)
            } catch (_: Exception) {
                pending = null; source = null
                promise.reject("EVIDENCE_EXPORT_FAILED", "Could not open a safe file-export picker")
            }
        }
    }

    @ReactMethod
    fun exportReport(format: String, content: String, promise: Promise) {
        app.runOnUiQueueThread {
            try {
                require(format in setOf("csv", "text") && content.length <= 131_072 &&
                        content.none { it == '\u0000' })
                check(pending == null) { "An export is already in progress" }
                val activity = app.currentActivity ?: error("Open the app to export a report")
                pending = promise
                source = null
                reportContent = content
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(if (format == "csv") "text/csv" else "text/plain")
                    .putExtra(Intent.EXTRA_TITLE,
                        if (format == "csv") "LaptopWatch-incidents.csv"
                        else "LaptopWatch-incidents.txt")
                    .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                activity.startActivityForResult(intent, requestCode)
            } catch (_: Exception) {
                pending = null; source = null; reportContent = null
                promise.reject("REPORT_EXPORT_FAILED", "Could not open a safe report picker")
            }
        }
    }

    private val listener = object : BaseActivityEventListener() {
        override fun onActivityResult(activity: Activity, code: Int, resultCode: Int, data: Intent?) {
            if (code != requestCode) return
            val callback = pending ?: return
            val file = source
            val report = reportContent
            pending = null; source = null; reportContent = null
            if (resultCode != Activity.RESULT_OK || data?.data == null ||
                (file == null && report == null)) {
                callback.reject("EVIDENCE_EXPORT_CANCELLED", "Evidence export was not completed")
                return
            }
            val destination = data.data!!
            worker.execute {
                try {
                    // Explicitly chosen SAF URI. Do not use a raw filesystem
                    // path from JS and do not permit export of any other file.
                    val sink = app.contentResolver.openOutputStream(destination, "w")
                        ?: error("No writable destination")
                    sink.use { output ->
                        if (file != null) file.inputStream().use { input ->
                            input.copyTo(output, 16_384)
                        }
                        else output.write((report ?: "").toByteArray(Charsets.UTF_8))
                    }
                    callback.resolve(true)
                } catch (_: Exception) {
                    callback.reject("EVIDENCE_EXPORT_FAILED", "Evidence export could not be completed")
                }
            }
        }
    }

    init { app.addActivityEventListener(listener) }
}

class LaptopWatchEvidenceManagementPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> =
        listOf(LaptopWatchEvidenceManagement(context))
    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> =
        emptyList()
}
