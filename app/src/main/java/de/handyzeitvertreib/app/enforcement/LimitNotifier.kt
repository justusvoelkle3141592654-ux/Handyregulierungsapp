package de.handyzeitvertreib.app.enforcement

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.handyzeitvertreib.app.R
import de.handyzeitvertreib.app.core.model.LimitKey
import de.handyzeitvertreib.app.regulation.RegulationActivity

/** Posts at most one quiet notification per limit and day (deduplicated by the caller). */
class LimitNotifier(
    private val context: Context,
) {
    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_limits),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notification_channel_limits_description) }
        manager.createNotificationChannel(channel)
    }

    fun notifyLimitReached(
        key: LimitKey,
        packageName: String?,
        title: String,
    ) {
        val granted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()
        val intent = RegulationActivity.intent(context, key, packageName)
        val pending =
            PendingIntent.getActivity(
                context,
                notificationId(key),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_hourglass)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.notification_limit_reached_text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(key), notification) }
    }

    companion object {
        const val CHANNEL_ID = "limits"

        fun notificationId(key: LimitKey): Int = 1000 + key.type.ordinal * 100_000 + (key.id % 100_000).toInt()
    }
}
