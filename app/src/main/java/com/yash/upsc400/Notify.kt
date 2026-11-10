package com.yash.upsc400

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.util.Calendar

object Notifier {
    private const val TIMER_CHANNEL = "timer"
    private const val REMIND_CHANNEL = "reminder"
    private const val TIMER_ID = 1001
    private const val REMIND_ID = 1002

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(TIMER_CHANNEL, "Study timer", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(REMIND_CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    private fun openApp(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Ongoing notification with a system-driven chronometer, so no service or in-memory counter is needed. */
    @SuppressLint("MissingPermission")
    fun showTimer(ctx: Context, title: String, text: String, elapsedBeforeMs: Long) {
        try {
            ensureChannels(ctx)
            val n = NotificationCompat.Builder(ctx, TIMER_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle(title).setContentText(text)
                .setOngoing(true).setOnlyAlertOnce(true).setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - elapsedBeforeMs)
                .setContentIntent(openApp(ctx)).build()
            NotificationManagerCompat.from(ctx).notify(TIMER_ID, n)
        } catch (e: SecurityException) {
            // notification permission not granted: the timer itself still works
        }
    }

    fun cancelTimer(ctx: Context) { NotificationManagerCompat.from(ctx).cancel(TIMER_ID) }

    @SuppressLint("MissingPermission")
    fun showReminder(ctx: Context) {
        try {
            ensureChannels(ctx)
            val n = NotificationCompat.Builder(ctx, REMIND_CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("UPSC 400").setContentText("Open today's slots and start a timer.")
                .setAutoCancel(true).setContentIntent(openApp(ctx)).build()
            NotificationManagerCompat.from(ctx).notify(REMIND_ID, n)
        } catch (e: SecurityException) {
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { Notifier.showReminder(context) }
}

object Reminder {
    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 7, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun schedule(ctx: Context, hour: Int, minute: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pending(ctx))
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
    }
}
