package com.iagodf.argos.lock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.iagodf.argos.data.Apps
import com.iagodf.argos.data.tr

/**
 * Argos Setup lives inside Argos.apk (assets/argos-setup.apk). it is only installed when you ask for it,
 * does the adb part from the phone itself and is removed again when it is done
 */
object SetupApp {
    const val PKG = "com.iagodf.argos.setup"

    /** wireless debugging, and with it Argos Setup, needs Android 11 */
    val supported: Boolean get() = Build.VERSION.SDK_INT >= 30

    fun installed(ctx: Context) = Apps.installed(ctx, PKG)

    /** false when Android first wants you to let Argos install apps (that page is opened) */
    fun open(ctx: Context): Boolean {
        if (installed(ctx)) { launch(ctx); return true }
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        Thread { runCatching { install(ctx.applicationContext) }.onFailure { say(ctx, tr("No se ha podido instalar Argos Setup", "Argos Setup could not be installed")) } }.start()
        return true
    }

    private fun install(ctx: Context) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(PKG) }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            ctx.assets.open("argos-setup.apk").use { input ->
                s.openWrite("argos-setup", 0, -1).use { out -> input.copyTo(out); s.fsync(out) }
            }
            s.commit(status(ctx, 0, "install"))
        }
    }

    fun launch(ctx: Context) {
        ctx.packageManager.getLaunchIntentForPackage(PKG)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** back from the setup: wireless debugging off, and the helper gone (device owner may remove it without asking) */
    fun cleanUp(ctx: Context) {
        if (Lock.canWriteSettings(ctx)) runCatching { Settings.Global.putInt(ctx.contentResolver, "adb_wifi_enabled", 0) }
        if (installed(ctx) && Lock.isOwner(ctx)) runCatching { ctx.packageManager.packageInstaller.uninstall(PKG, status(ctx, 1, "uninstall")) }
    }

    private fun status(ctx: Context, code: Int, action: String) = android.app.PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, SetupStatus::class.java).setAction(action),
        android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    ).intentSender

    fun say(ctx: Context, msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }
}

/** what Android answers to the install: show its confirmation, then open Argos Setup */
class SetupStatus : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else i.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> if (i.action == "install") SetupApp.launch(ctx)
            else -> if (i.action == "install") SetupApp.say(ctx, tr("No se ha instalado Argos Setup", "Argos Setup was not installed"))
        }
    }
}
