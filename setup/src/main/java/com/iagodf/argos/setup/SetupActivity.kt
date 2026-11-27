package com.iagodf.argos.setup

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * the one screen of Argos Setup: the steps Android asks of you (accounts, developer options, wi-fi,
 * wireless debugging), then the pairing code goes in the notification and the rest happens on its own
 */
class SetupActivity : Activity() {
    private val ink = Color.rgb(0x15, 0x13, 0x1d)
    private val muted = Color.rgb(0x8d, 0x89, 0xa0)
    private val blue = Color.rgb(0x4a, 0x5c, 0xff)
    private val coral = Color.rgb(0xff, 0x5c, 0x6c)
    private lateinit var root: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.rgb(0xf6, 0xf5, 0xfb)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(40)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(0xf6, 0xf5, 0xfb)); fitsSystemWindows = true; addView(root) })
    }

    override fun onResume() {
        super.onResume()
        Progress.listener = { draw() }
        draw()
    }

    override fun onPause() {
        Progress.listener = null
        super.onPause()
    }

    private fun draw() {
        root.removeAllViews()
        text("Argos Setup", 30, Typeface.BOLD, ink)
        text(
            tr("Hace a Argos inquebrantable sin ordenador. Al terminar, esta app se borra sola.",
                "Makes Argos unbreakable without a computer. When it is done, this app removes itself."),
            15, Typeface.NORMAL, muted, top = 6,
        )
        note(tr("Hazlo en la Wi-Fi de tu casa, no en una pública. No hace falta internet, solo la red.",
            "Do it on your home Wi-Fi, not a public one. No internet is needed, only the network."))

        val dev = Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val wifi = getSystemService(ConnectivityManager::class.java).let { cm ->
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        val wireless = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1

        step(1, tr("Quita todas tus cuentas", "Remove all your accounts"),
            tr("Ajustes → Cuentas: Google, Outlook, Samsung, WhatsApp… y cualquier perfil de trabajo o segundo espacio. Ten a mano las contraseñas: al acabar las vuelves a añadir y se sincroniza todo.",
                "Settings → Accounts: Google, Outlook, Samsung, WhatsApp… and any work profile or second space. Keep the passwords at hand: afterwards you add them back and everything syncs again."),
            null, tr("Abrir cuentas", "Open accounts")) { startActivity(Intent(Settings.ACTION_SYNC_SETTINGS)) }
        step(2, tr("Opciones de desarrollador", "Developer options"),
            tr("En Información del teléfono, toca 7 veces \"Número de compilación\".", "In About phone, tap \"Build number\" 7 times."),
            dev, tr("Abrir", "Open")) { startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) }
        step(3, tr("Conéctate a tu Wi-Fi", "Connect to your Wi-Fi"),
            tr("La depuración inalámbrica solo funciona con Wi-Fi.", "Wireless debugging only works on Wi-Fi."),
            wifi, tr("Abrir", "Open")) { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        step(4, tr("Activa la depuración inalámbrica", "Turn on wireless debugging"),
            tr("Opciones de desarrollador → Depuración inalámbrica → actívala.", "Developer options → Wireless debugging → turn it on."),
            wireless, tr("Abrir", "Open")) { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
        step(5, tr("Vincula con el código", "Pair with the code"),
            tr("Toca Empezar. Te llevo a las opciones de desarrollador: entra en Depuración inalámbrica y toca \"Vincular dispositivo con código de vinculación\". Baja las notificaciones y escribe el código de 6 cifras en la de Argos Setup. Lo demás lo hace sola.",
                "Tap Start. You go to developer options: open Wireless debugging and tap \"Pair device with pairing code\". Pull the notifications down and type the 6 digit code in the Argos Setup one. It does the rest on its own."),
            null, null) {}

        val p = Progress.stage
        if (p != Progress.Stage.Idle) {
            val color = when (p) { Progress.Stage.Failed -> coral; Progress.Stage.Done -> blue; else -> ink }
            card(color = Color.WHITE).addView(textView(Progress.text, 15, Typeface.NORMAL, color))
        }
        when (p) {
            Progress.Stage.Failed -> button(tr("Reintentar", "Retry")) { SetupService.start(this, if (Progress.paired) SetupService.ACTION_RETRY else null) }
            Progress.Stage.Done -> button(tr("Abrir Argos", "Open Argos")) {
                packageManager.getLaunchIntentForPackage(SetupService.ARGOS)?.let { startActivity(it) }
            }
            Progress.Stage.Working -> Unit
            else -> button(tr("Empezar", "Start"), enabled = dev && wifi) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
                SetupService.start(this)
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            }
        }
    }

    // ---------- small view helpers, the look of Argos ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun textView(s: String, sp: Int, style: Int, color: Int) = TextView(this).apply {
        text = s; setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.toFloat()); setTypeface(Typeface.DEFAULT, style); setTextColor(color)
        setLineSpacing(0f, 1.25f)
    }

    private fun text(s: String, sp: Int, style: Int, color: Int, top: Int = 0) =
        root.addView(textView(s, sp, style, color), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })

    private fun card(color: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(color) }
        setPadding(dp(16), dp(14), dp(16), dp(14))
        root.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun note(s: String) = card(Color.rgb(0xee, 0xf0, 0xff)).addView(textView(s, 13, Typeface.NORMAL, Color.rgb(0x3c, 0x3a, 0x5a)))

    private fun step(n: Int, title: String, sub: String, ok: Boolean?, action: String?, open: () -> Unit) {
        val c = card(Color.WHITE)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = if (ok == true) "✓" else "$n"; gravity = Gravity.CENTER; setTextColor(if (ok == true) Color.WHITE else muted)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (ok == true) blue else Color.rgb(0xf0, 0xee, 0xf6)) }
        }, LinearLayout.LayoutParams(dp(30), dp(30)))
        row.addView(textView(title, 16, Typeface.BOLD, ink), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) })
        if (action != null && ok != true) row.addView(pill(action, open))
        c.addView(row)
        c.addView(textView(sub, 13, Typeface.NORMAL, muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun pill(s: String, click: () -> Unit) = TextView(this).apply {
        text = s; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(ink) }
        setPadding(dp(14), dp(8), dp(14), dp(8))
        setOnClickListener { click() }
    }

    private fun button(s: String, enabled: Boolean = true, click: () -> Unit) {
        val v = TextView(this).apply {
            text = s; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(ink) }
            alpha = if (enabled) 1f else .4f
            if (enabled) setOnClickListener { click() }
        }
        root.addView(v, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(20) })
        if (!enabled) text(tr("Primero los pasos 2 y 3.", "Do steps 2 and 3 first."), 12, Typeface.NORMAL, muted, top = 8)
    }
}
