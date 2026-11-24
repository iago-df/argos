package com.iagodf.argos.lock

import android.Manifest
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import com.iagodf.argos.data.Store
import com.iagodf.argos.engine.ArgosService

/** needed to become device owner: adb shell dpm set-device-owner com.iagodf.argos/.lock.ArgosAdmin */
class ArgosAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Store.init(context)
        Lock.sync(context)
    }
}

/**
 * the unbreakable part. as device owner Argos can not be uninstalled, the phone can not boot in safe mode,
 * the time can not be changed and apps can not be force stopped or wiped from settings.
 * the only way out without waiting is a factory reset
 */
object Lock {
    const val ADB_OWNER = "adb shell dpm set-device-owner com.iagodf.argos/.lock.ArgosAdmin"
    const val ADB_SETTINGS = "adb shell pm grant com.iagodf.argos android.permission.WRITE_SECURE_SETTINGS"

    private val restrictions = listOf(
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_CONFIG_DATE_TIME,
        UserManager.DISALLOW_APPS_CONTROL,
        UserManager.DISALLOW_DEBUGGING_FEATURES,
        UserManager.DISALLOW_ADD_USER,
    )

    fun admin(ctx: Context) = ComponentName(ctx, ArgosAdmin::class.java)
    private fun dpm(ctx: Context) = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isOwner(ctx: Context) = dpm(ctx).isDeviceOwnerApp(ctx.packageName)

    fun canWriteSettings(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    /** fully unbreakable: committed, device owner and able to turn its own service back on */
    fun unbreakable(ctx: Context) = Store.committed && isOwner(ctx)

    /** puts the device policy in line with the commitment */
    fun sync(ctx: Context) {
        if (!isOwner(ctx)) return
        if (Store.committed) apply(ctx) else release(ctx, keepOwner = true)
    }

    private fun apply(ctx: Context) = runCatching {
        val d = dpm(ctx)
        val a = admin(ctx)
        d.setUninstallBlocked(a, ctx.packageName, true)
        restrictions.forEach { d.addUserRestriction(a, it) }
        if (Build.VERSION.SDK_INT >= 30) d.setAutoTimeEnabled(a, true) else @Suppress("DEPRECATION") d.setAutoTimeRequired(a, true)
    }

    /** undoes everything. after a finished deactivation it also gives up device owner so the app can be removed */
    fun release(ctx: Context, keepOwner: Boolean = false) = runCatching {
        if (!isOwner(ctx)) return@runCatching
        val d = dpm(ctx)
        val a = admin(ctx)
        restrictions.forEach { d.clearUserRestriction(a, it) }
        d.setUninstallBlocked(a, ctx.packageName, false)
        if (Build.VERSION.SDK_INT >= 30) d.setAutoTimeEnabled(a, false) else @Suppress("DEPRECATION") d.setAutoTimeRequired(a, false)
        if (!keepOwner) @Suppress("DEPRECATION") d.clearDeviceOwnerApp(ctx.packageName)
    }

    /** if the accessibility service was turned off, turn it back on (needs WRITE_SECURE_SETTINGS from adb) */
    fun reviveService(ctx: Context): Boolean {
        if (!Store.committed || ArgosService.enabled(ctx) || !canWriteSettings(ctx)) return false
        return runCatching {
            val cr = ctx.contentResolver
            val me = ArgosService.component(ctx).flattenToString()
            val cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val list = (cur.split(':').filter { it.isNotBlank() } + me).distinct().joinToString(":")
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list)
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        }.getOrDefault(false)
    }
}
