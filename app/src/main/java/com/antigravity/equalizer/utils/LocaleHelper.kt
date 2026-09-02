package com.antigravity.equalizer.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {

    private const val PREFS_NAME = "equalizer_locale_prefs"
    private const val KEY_LANGUAGE = "selected_language"

    fun getSelectedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "system") ?: "system"
    }

    fun setSelectedLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
        applyLocale(context, languageCode)
    }

    fun applyLocale(context: Context, languageCode: String): Context {
        val locale = when (languageCode) {
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "en" -> Locale.ENGLISH
            else -> Locale.getDefault()
        }

        Locale.setDefault(locale)

        val resources = context.resources
        val config = Configuration(resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)

        return context.createConfigurationContext(config)
    }
}
