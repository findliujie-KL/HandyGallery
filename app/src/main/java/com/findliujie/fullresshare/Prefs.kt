package com.findliujie.fullresshare

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class ThemeMode { System, Light, Dark }

/** The app's own language choice, applied by wrapping each activity's base context. */
object AppLocale {
    const val PREFS = "gallery"
    const val KEY = "language"

    /** Language tags offered in Settings; empty means follow the system. */
    val choices = listOf("", "en", "zh")

    fun wrap(base: Context): Context {
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(config)
    }
}
