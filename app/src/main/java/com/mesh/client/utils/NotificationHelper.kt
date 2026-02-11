package com.mesh.client.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mesh.client.MainActivity
import com.mesh.client.R

class NotificationHelper(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID = "MESH_CHANNEL_ID_V2"
        const val CHANNEL_NAME = "Mesh Messages"
        const val CHANNEL_DESC = "Notifications for incoming messages"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(title: String, body: String) {
        // Create intent to open app when tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round) // Fallback/Default
                .setContentTitle(title)
                .setContentText(body)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(context)) {
                // notificationId is a unique int for each notification that you must define
                notify(System.currentTimeMillis().toInt(), builder.build())
            }
        } catch (e: SecurityException) {
            // Permission might be revoked or not granted
        }
    }

    fun showCallNotification(title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            // Add extra to hint nav? Not strictly needed if ViewModel state handles it.
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 
            1, // Request code 1 for calls
            intent, 
            PendingIntent.FLAG_IMMUTABLE
        )
        
        // Full Screen Intent requires high priority and FSI permission
        try {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingIntent, true) // RAW FSI
                .setContentIntent(pendingIntent) // Standard click
                .setAutoCancel(false)
                .setOngoing(true) // Call notifications persist until handled
            
            with(NotificationManagerCompat.from(context)) {
                notify(999, builder.build()) // Fixed ID for incoming call? Or unique. Using fixed for single call support.
            }
        } catch (e: SecurityException) {
             // FSI permission might be missing
        }
    }
}
