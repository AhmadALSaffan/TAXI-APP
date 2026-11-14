package com.taxiapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.taxiapp.R
import com.taxiapp.ui.ride.TripTrackingActivity

class TaxiAppMessagingService : FirebaseMessagingService() {

    companion object {
        private const val CHANNEL_ID   = "trip_channel"
        private const val CHANNEL_NAME = "Trip Notifications"
        private val TRIP_EVENTS = setOf(
            "DRIVER_ACCEPTED", "DRIVER_ARRIVED", "TRIP_COMPLETED", "TRIP_CANCELLED"
        )
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance()
            .getReference("users/$uid/fcmToken")
            .setValue(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title  = message.notification?.title ?: message.data["title"] ?: return
        val body   = message.notification?.body  ?: message.data["body"]  ?: return
        val tripId = message.data["trip_id"]      ?: return
        val type   = message.data["type"]         ?: ""
        val isDriver = message.data["role"] == "driver"

        if (type in TRIP_EVENTS) {
            showTripNotification(title, body, tripId, isDriver)
        }
    }

    private fun showTripNotification(title: String, body: String, tripId: String, isDriver: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply { enableVibration(true) }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, TripTrackingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(TripTrackingActivity.EXTRA_TRIP_ID, tripId)
            putExtra(TripTrackingActivity.EXTRA_IS_DRIVER, isDriver)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            tripId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_mark)
            .setColor(getColor(R.color.amber))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // One notification per trip; later events replace earlier ones.
        manager.notify(tripId.hashCode(), notification)
    }
}