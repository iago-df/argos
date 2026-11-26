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
import com.iagodf.argos.data.Apps
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
 * the unbreakable part. being device owner changes nothing on its own: it all starts when you lock Argos.
 * then Argos can not be uninstalled, force stopped or wiped, the phone can not boot in safe mode, the time
 * can not be changed and the apps you picked are paused. other apps keep working as always.
 * the only way out without waiting is a factory reset
 */
object Lock {
    /**
     * test builds: the lock can be switched off right away, to update or fix things while trying it.
     * the real thing only leaves through the 30 day request
     */
    const val TESTING = true

    const val ADB_OWNER = "adb shell dpm set-device-owner com.iagodf.argos/.lock.ArgosAdmin"
    const val ADB_SETTINGS = "adb shell pm grant com.iagodf.argos android.permission.WRITE_SECURE_SETTINGS"

    private val restrictions = listOf(
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_CONFIG_DATE_TIME,
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

    /** puts the device policy in line with the commitment and the paused apps */
    fun sync(ctx: Context) {
        if (!isOwner(ctx)) return
        // the guard ticks often; the policy only has to be written when something changed
        val want = if (Store.committed) "on|" + Store.lockApps().sorted().joinToString(",") else "off"
        if (synced == want) return
        if (Store.committed) apply(ctx) else release(ctx, keepOwner = true)
        synced = want
    }

    @Volatile private var synced: String? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("lock", Context.MODE_PRIVATE)

    private fun apply(ctx: Context) = runCatching {
        val d = dpm(ctx)
        val a = admin(ctx)
        d.setUninstallBlocked(a, ctx.packageName, true)
        restrictions.forEach { d.addUserRestriction(a, it) }
        if (Build.VERSION.SDK_INT >= 30) d.setAutoTimeEnabled(a, true) else @Suppress("DEPRECATION") d.setAutoTimeRequired(a, true)
        // only Argos is kept from being force stopped or wiped, not every app of the phone
        if (Build.VERSION.SDK_INT >= 30) d.setUserControlDisabledPackages(a, listOf(ctx.packageName))
        suspend(ctx, Store.lockApps() - Apps.essentials(ctx))
    }

    /** pauses exactly these apps (greyed out, they do not open) and lets go of the rest */
    private fun suspend(ctx: Context, want: Set<String>) {
        val d = dpm(ctx)
        val a = admin(ctx)
        val before = prefs(ctx).getStringSet("suspended", emptySet()).orEmpty()
        val off = (before - want).toTypedArray()
        if (off.isNotEmpty()) runCatching { d.setPackagesSuspended(a, off, false) }
        val on = want.filter { Apps.installed(ctx, it) }.toTypedArray()
        val failed = if (on.isNotEmpty()) runCatching { d.setPackagesSuspended(a, on, true).toSet() }.getOrDefault(on.toSet()) else emptySet()
        prefs(ctx).edit().putStringSet("suspended", on.toSet() - failed).apply()
    }

    /**
     * before locking, being device owner can be dropped right away, so Argos can be uninstalled like any app.
     * once locked it is not possible (the 30 day request, or [TESTING])
     */
    fun dropOwner(ctx: Context): Boolean {
        if (Store.committed && !TESTING) return false
        if (Store.committed) Store.uncommit()
        release(ctx, keepOwner = false)
        synced = null
        return !isOwner(ctx)
    }

    /** test builds only: switch the lock off now and keep device owner, so the commands are not needed again */
    fun unlockNow(ctx: Context) {
        if (!TESTING) return
        Store.uncommit()
        sync(ctx)
    }

    /** undoes everything. after a finished deactivation it also gives up device owner so the app can be removed */
    fun release(ctx: Context, keepOwner: Boolean = false) = runCatching {
        if (!isOwner(ctx)) return@runCatching
        val d = dpm(ctx)
        val a = admin(ctx)
        restrictions.forEach { d.clearUserRestriction(a, it) }
        // older versions of Argos set this one for every app
        d.clearUserRestriction(a, UserManager.DISALLOW_APPS_CONTROL)
        d.setUninstallBlocked(a, ctx.packageName, false)
        if (Build.VERSION.SDK_INT >= 30) d.setUserControlDisabledPackages(a, emptyList())
        suspend(ctx, emptySet())
        if (Build.VERSION.SDK_INT >= 30) d.setAutoTimeEnabled(a, false) else @Suppress("DEPRECATION") d.setAutoTimeRequired(a, false)
        if (!keepOwner) @Suppress("DEPRECATION") d.clearDeviceOwnerApp(ctx.packageName)
    }

    /** if the accessibility service was turned off, turn it back on (needs WRITE_SECURE_SETTINGS from adb) */
    fun reviveService(ctx: Context): Boolean {
        if (!Store.committed) return false
        return enableService(ctx)
    }

    /** turns the accessibility service on without going through settings, if adb granted WRITE_SECURE_SETTINGS */
    fun enableService(ctx: Context): Boolean {
        if (ArgosService.enabled(ctx) || !canWriteSettings(ctx)) return false
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
