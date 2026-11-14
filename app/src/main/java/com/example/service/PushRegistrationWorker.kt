package com.example.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.security.SecureLaptopGateway
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** FCM is optional and OFF until the owner explicitly opts into notifications.
 * Registration is retried on network changes; private phone signing keys never
 * leave AndroidKeyStore. No registration token is written to app logs.
 */
object PushEnrollment {
    private const val PREF = "laptopwatch_push_opt_in_v1"
    private const val KEY = "enabled"

    fun optedIn(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setOptIn(context: Context, enabled: Boolean) {
        check(context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY, enabled).commit())
        if (enabled) enqueue(context)
        else WorkManager.getInstance(context).cancelUniqueWork("laptopwatch_push_registration")
    }

    fun enqueue(context: Context) {
        if (!optedIn(context)) return
        val work = OneTimeWorkRequestBuilder<PushRegistrationWorker>()
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "laptopwatch_push_registration",
            ExistingWorkPolicy.REPLACE,
            work
        )
    }
}

class PushRegistrationWorker(
    context: Context, parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!PushEnrollment.optedIn(applicationContext)) return Result.success()
        if (FirebaseApp.getApps(applicationContext).isEmpty()) return Result.failure()
        val gateway=SecureLaptopGateway(applicationContext)
        if (!gateway.isApproved() || gateway.configuredRelayUrl().isBlank()) {
            // Owner must pair/provision before registering a push destination.
            return Result.failure()
        }
        return try {
            val token=withTimeout(12_000) {
                suspendCancellableCoroutine<String> { continuation ->
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                        if (!continuation.isActive) return@addOnCompleteListener
                        if (task.isSuccessful && task.result != null)
                            continuation.resume(task.result)
                        else continuation.resumeWithException(
                            task.exception ?: IllegalStateException("FCM registration unavailable"))
                    }
                }
            }
            val result=gateway.registerPushToken(token)
            if (result.isSuccess) Result.success()
            else if (runAttemptCount < 12) Result.retry()
            else Result.failure()
        } catch (_: Exception) {
            if (runAttemptCount < 12) Result.retry() else Result.failure()
        }
    }
}
