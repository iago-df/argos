package com.iagodf.argos.lock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import com.iagodf.argos.MainActivity
import com.iagodf.argos.R
import com.iagodf.argos.data.Lang
import com.iagodf.argos.data.Policy
import com.iagodf.argos.data.Store
import com.iagodf.argos.data.tr

/**
 * small foreground service that keeps Argos alive: it counts the deactivation days,
 * applies the soft changes when their wait is over and turns the blocking service back on
 */
class Guard : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            step()
            main.postDelayed(this, 60_000)
        }
    }
    private val watcher = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) { Lock.reviveService(this@Guard) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Lang.load(this)
        Store.init(this)
        val n = notification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(ID, n)
        contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, watcher)
        main.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        step()
        return START_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(tick)
        contentResolver.unregisterContentObserver(watcher)
        super.onDestroy()
    }

    private fun step() {
        Store.applyDue()
        Store.deactTick(SystemClock.elapsedRealtime(), Policy.now().focus)
        Lock.sync(this)
        Lock.reviveService(this)
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, tr("Argos vigilando", "Argos watching"), NotificationManager.IMPORTANCE_MIN))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(tr("Argos está contigo", "Argos has your back"))
            .setContentText(tr("Cuidando tu atención", "Keeping your attention yours"))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val ID = 7
        private const val CHANNEL = "guard"

        fun start(ctx: Context) {
            runCatching { ctx.startForegroundService(Intent(ctx, Guard::class.java)) }
        }
    }
}

/** starts the guard after a reboot or an update */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        Lock.sync(context)
        Lock.reviveService(context)
        Guard.start(context)
    }
}
