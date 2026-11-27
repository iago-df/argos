package com.iagodf.argos.setup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AdbMdns
import io.github.muntashirakon.adb.android.AndroidUtils
import java.util.Locale

/** spanish if the phone is in spanish, english otherwise */
fun tr(es: String, en: String) = if (Locale.getDefault().language == "es") es else en

/** what the setup is doing, for the screen */
object Progress {
    enum class Stage { Idle, Waiting, Code, Working, Failed, Done }

    @Volatile var stage = Stage.Idle; private set
    @Volatile var text = ""; private set
    /** pairing worked once, so a retry can skip the code */
    @Volatile var paired = false
    private val main = Handler(Looper.getMainLooper())
    var listener: (() -> Unit)? = null

    fun set(s: Stage, t: String) { stage = s; text = t; main.post { listener?.invoke() } }
}

/**
 * waits for the pairing screen of wireless debugging, takes the six digit code from its notification,
 * pairs, connects and runs the two commands. then it opens Argos and removes itself
 */
class SetupService : Service() {
    private var mdns: AdbMdns? = null
    @Volatile private var pairingPort = -1
    @Volatile private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground(tr("Argos Setup está esperando", "Argos Setup is waiting"), withReply = false)
        when (intent?.action) {
            ACTION_CODE -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString()?.filter { it.isDigit() }.orEmpty()
                if (code.length == 6) work { pairAndRun(code) } else ask(tr("Escribe los 6 números del código", "Type the 6 digits of the code"))
            }
            ACTION_RETRY -> work { run() }
            ACTION_STOP -> { stop(); return START_NOT_STICKY }
            else -> discover()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { mdns?.stop() }
        super.onDestroy()
    }

    private fun stop() {
        runCatching { mdns?.stop() }
        mdns = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun work(block: () -> Unit) {
        if (busy) return
        busy = true
        Thread {
            try { block() } catch (t: Throwable) {
                fail(tr("Algo ha fallado: ", "Something failed: ") + (t.message ?: t.javaClass.simpleName))
            } finally { busy = false }
        }.start()
    }

    /** the pairing screen announces itself on the network of the phone: that gives the port */
    private fun discover() {
        Progress.set(Progress.Stage.Waiting, tr(
            "En Depuración inalámbrica, toca \"Vincular dispositivo con código de vinculación\".",
            "In Wireless debugging, tap \"Pair device with pairing code\".",
        ))
        if (mdns != null) return
        mdns = AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { _, port ->
            if (port > 0 && port != pairingPort) {
                pairingPort = port
                ask(tr("Escribe aquí el código de 6 cifras que sale en Ajustes", "Type here the 6 digit code shown in Settings"))
            }
        }.also { it.start() }
    }

    private fun ask(msg: String) {
        Progress.set(Progress.Stage.Code, msg)
        foreground(msg, withReply = true)
    }

    private fun pairAndRun(code: String) {
        Progress.set(Progress.Stage.Working, tr("Vinculando…", "Pairing…"))
        foreground(tr("Vinculando…", "Pairing…"), withReply = false)
        val adb = Adb.get(this)
        val port = pairingPort
        val ok = runCatching { adb.pair("127.0.0.1", port, code) }.getOrDefault(false) ||
            runCatching { adb.pair(AndroidUtils.getHostIpAddress(this), port, code) }.getOrDefault(false)
        if (!ok) {
            ask(tr("El código no vale o ha caducado. Vuelve a tocar \"Vincular con código\" y escribe el nuevo.",
                "The code did not work or expired. Tap \"Pair with code\" again and type the new one."))
            return
        }
        Progress.paired = true
        runCatching { mdns?.stop() }; mdns = null
        run()
    }

    /** connects and runs the two commands. it can be repeated without pairing again */
    private fun run() {
        Progress.set(Progress.Stage.Working, tr("Conectando…", "Connecting…"))
        foreground(tr("Conectando…", "Connecting…"), withReply = false)
        val adb = Adb.get(this)
        // right after pairing, android takes a moment to trust the new key: the first tries can be turned down
        var connected = adb.isConnected
        var tries = 0
        while (!connected && tries < 6) {
            tries++
            Thread.sleep(if (tries == 1) 1500 else 2000)
            connected = try {
                runCatching { adb.disconnect() }
                adb.connectTls(this, 10_000)
            } catch (e: AdbPairingRequiredException) {
                if (tries < 6) false else {
                    Progress.paired = false
                    pairingPort = -1
                    discover()
                    ask(tr("Hay que vincular otra vez: toca \"Vincular con código\".", "It needs pairing again: tap \"Pair with code\"."))
                    return
                }
            } catch (e: Exception) { false }
        }
        if (!connected) {
            fail(tr("No he podido conectar con la depuración inalámbrica. Comprueba que sigue activada y toca Reintentar.",
                "Could not connect to wireless debugging. Check it is still on and tap Retry."))
            return
        }

        Progress.set(Progress.Stage.Working, tr("Haciendo a Argos propietario del dispositivo…", "Making Argos device owner…"))
        val said = adb.run("dpm set-device-owner $ADMIN 2>&1")
        val owner = said.contains("Success") || adb.run("dpm list-owners 2>&1").contains(ARGOS) ||
            adb.run("dumpsys device_policy | grep -A2 'Device Owner'").contains(ARGOS)
        adb.run("pm grant $ARGOS android.permission.WRITE_SECURE_SETTINGS 2>&1")
        val settings = adb.run("dumpsys package $ARGOS | grep WRITE_SECURE_SETTINGS").contains("granted=true")

        if (!owner) {
            val accounts = Regex("""Account \{name=([^,]+), type=([^}]+)}""").findAll(adb.run("dumpsys account"))
                .map { "${it.groupValues[1]} (${it.groupValues[2]})" }.distinct().toList()
            fail(
                when {
                    accounts.isNotEmpty() -> tr("Aún quedan cuentas en el móvil. Quítalas en Ajustes → Cuentas y toca Reintentar:\n",
                        "There are still accounts on the phone. Remove them in Settings → Accounts and tap Retry:\n") + accounts.joinToString("\n")
                    said.contains("user", true) -> tr("Hay otro usuario o perfil (de trabajo, carpeta segura, segundo espacio). Quítalo y toca Reintentar.",
                        "There is another user or profile (work profile, secure folder, second space). Remove it and tap Retry.")
                    else -> tr("Android ha dicho: ", "Android said: ") + said
                }
            )
            return
        }

        Progress.set(Progress.Stage.Done, if (settings) tr("Hecho. Argos es inquebrantable.", "Done. Argos is unbreakable.")
            else tr("Hecho. Argos es propietario del dispositivo.", "Done. Argos is device owner."))
        foreground(tr("Hecho. Ya puedes volver a añadir tus cuentas.", "Done. You can add your accounts back."), withReply = false)
        // back to Argos, then this app goes away and wireless debugging is switched off
        adb.run("am start -n $ARGOS/.MainActivity --ez from_setup true")
        adb.run("nohup sh -c 'sleep 4; pm uninstall $SETUP; settings put global adb_wifi_enabled 0' >/dev/null 2>&1 &")
        Adb.forget(this)
        stop()
    }

    private fun fail(msg: String) {
        Progress.set(Progress.Stage.Failed, msg)
        foreground(msg.lineSequence().first(), withReply = false)
    }

    private fun foreground(text: String, withReply: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Argos Setup", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(this, 0, Intent(this, SetupActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("Argos Setup").setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(!withReply)
        if (withReply) {
            val input = RemoteInput.Builder(KEY_CODE).setLabel(tr("Código de 6 cifras", "6 digit code")).build()
            val reply = PendingIntent.getForegroundService(
                this, 1, Intent(this, SetupService::class.java).setAction(ACTION_CODE),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(Notification.Action.Builder(null, tr("Escribir código", "Type code"), reply).addRemoteInput(input).build())
        }
        val n = b.build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID, n)
    }

    companion object {
        const val ARGOS = "com.iagodf.argos"
        const val SETUP = "com.iagodf.argos.setup"
        const val ADMIN = "$ARGOS/.lock.ArgosAdmin"
        const val ACTION_CODE = "code"
        const val ACTION_RETRY = "retry"
        const val ACTION_STOP = "stop"
        private const val KEY_CODE = "code"
        private const val CHANNEL = "setup"
        private const val ID = 7

        fun start(ctx: Context, action: String? = null) =
            ctx.startForegroundService(Intent(ctx, SetupService::class.java).setAction(action))
    }
}
