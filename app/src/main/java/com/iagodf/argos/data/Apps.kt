package com.iagodf.argos.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager

data class AppInfo(val pkg: String, val label: String)

object Apps {
    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"

    val browsers = setOf(
        "com.android.chrome", "com.chrome.beta", "com.brave.browser", "org.mozilla.firefox", "org.mozilla.firefox_beta",
        "com.sec.android.app.sbrowser", "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native",
        "com.duckduckgo.mobile.android", "com.kiwibrowser.browser", "com.vivaldi.browser", "app.vanadium.browser",
    )

    private var webApps: Set<String>? = null

    /** the known browsers plus any app that opens web links */
    fun isBrowser(ctx: Context, pkg: String): Boolean {
        if (pkg in browsers) return true
        val set = webApps ?: runCatching {
            ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }.toSet() - setOf(INSTAGRAM, YOUTUBE)
        }.getOrDefault(emptySet()).also { webApps = it }
        return pkg in set
    }

    /** apps that are never sent home during focus: the phone itself has to keep working */
    fun alwaysAllowed(ctx: Context): Set<String> = buildSet {
        add(ctx.packageName)
        add("com.android.systemui")
        add("com.android.settings")
        add("android")
        home(ctx)?.let { add(it) }
        dialer(ctx)?.let { add(it) }
        keyboard(ctx)?.let { add(it) }
        add("com.google.android.dialer"); add("com.android.phone"); add("com.android.server.telecom")
        add("com.google.android.permissioncontroller"); add("com.android.permissioncontroller")
    }

    /** the apps the video shows as allowed: phone, messages, maps, camera and gallery if found */
    fun defaultAllowed(ctx: Context): Set<String> = buildSet {
        dialer(ctx)?.let { add(it) }
        runCatching { Telephony.Sms.getDefaultSmsPackage(ctx) }.getOrNull()?.let { add(it) }
        resolve(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=x")))?.let { add(it) }
        resolve(ctx, Intent(MediaStore.ACTION_IMAGE_CAPTURE))?.let { add(it) }
        listOf("com.whatsapp", "org.telegram.messenger", "com.google.android.apps.photos", "com.google.android.calendar")
            .filter { installed(ctx, it) }.forEach { add(it) }
    }

    private fun resolve(ctx: Context, i: Intent): String? = runCatching {
        ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()?.takeIf { it != "android" }

    fun home(ctx: Context): String? = resolve(ctx, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))

    fun dialer(ctx: Context): String? = runCatching {
        (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).defaultDialerPackage
    }.getOrNull()

    private fun keyboard(ctx: Context): String? = runCatching {
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
    }.getOrNull()

    fun installed(ctx: Context, pkg: String) = runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /** apps with an icon in the launcher, sorted by name */
    fun launchable(ctx: Context): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != ctx.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun label(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun icon(ctx: Context, pkg: String, px: Int): Bitmap? = runCatching {
        val d = ctx.packageManager.getApplicationIcon(pkg)
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, px, px)
        d.draw(Canvas(b))
        b
    }.getOrNull()
}
