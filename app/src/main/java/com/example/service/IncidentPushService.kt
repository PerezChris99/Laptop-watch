package com.example.service

import com.example.security.SecureLaptopGateway
import com.example.util.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** An FCM data message is only a hint. On opening LaptopWatch, the phone must
 * verify the event via owner-signed HTTPS before it is recorded as a person.
 * Never treat a push payload alone as forensic evidence.
 */
class IncidentPushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (!PushEnrollment.optedIn(this)) return
        if (message.data["kind"] != "verified_person_hint") return
        val desktopId=message.data["desktop_id"] ?: return
        val eventId=message.data["event_id"]?.toLongOrNull() ?: return
        if (eventId<=0 || eventId>9_223_372_036_854_775_807L) return
        val gateway=runCatching { SecureLaptopGateway(this) }.getOrNull() ?: return
        if (gateway.pairedDesktopId() != desktopId) return
        NotificationHelper(this).showMotionAlertNotification(
            title="LaptopWatch — security event",
            message="Your paired laptop reported an event. Open the app to verify and inspect evidence.",
            isAwayMode=true
        )
    }

    @Suppress("DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Do not log/serialize the bearer registration token.
        PushEnrollment.enqueue(applicationContext)
    }

    override fun onRegistered(installationId: String) {
        super.onRegistered(installationId)
        PushEnrollment.enqueue(applicationContext)
    }

    override fun onDeletedMessages() {
        super.onDeletedMessages()
        if (!PushEnrollment.optedIn(this)) return
        NotificationHelper(this).showMotionAlertNotification(
            title="LaptopWatch — missed notifications possible",
            message="Some push events may be missing. Open LaptopWatch to sync the signed incident log.",
            isAwayMode=true
        )
    }
}
