package com.iagodf.argos.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * app language: english by default, spanish from settings.
 * texts live in the code as tr("español", "english") so the service overlays use the same thing
 */
object Lang {
    var en by mutableStateOf(true); private set

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("argos", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        en = prefs(ctx).getString("lang", "en") != "es"
    }

    fun set(ctx: Context, english: Boolean) {
        en = english
        prefs(ctx).edit().putString("lang", if (english) "en" else "es").apply()
    }

    val locale: Locale get() = if (en) Locale.ENGLISH else Locale.forLanguageTag("es")
}

fun tr(es: String, en: String): String = if (Lang.en) en else es
