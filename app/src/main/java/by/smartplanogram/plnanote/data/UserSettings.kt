package by.smartplanogram.plnanote.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Быстрые настройки пользователя (SharedPreferences).
 * Тема и язык применяются через AppCompatDelegate (синхронно, без Room).
 */
class UserSettings private constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun themeMode(): String = prefs.getString(KEY_THEME, "dark") ?: "dark"

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME, mode).apply()
    }

    fun languageCode(): String = prefs.getString(KEY_LANG, "ru") ?: "ru"

    fun setLanguageCode(code: String) {
        prefs.edit().putString(KEY_LANG, code).apply()
    }

    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_LOGGED_IN, false)

    fun loginId(): String = prefs.getString(KEY_LOGIN_ID, "").orEmpty()

    /** null = гость (подпись берётся из локализованных ресурсов). */
    fun displayName(): String? {
        if (!isLoggedIn()) return null
        val custom = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty().trim()
        if (custom.isNotEmpty()) return custom
        val id = loginId()
        return if (id.length > 12) id.takeLast(4).let { "•••$it" } else id
    }

    fun setDisplayName(name: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, name.trim()).apply()
    }

    /**
     * Вход по телефону или спецкоду. Пароль не нужен.
     */
    fun login(raw: String): Boolean {
        val cleaned = raw.trim()
        if (!isValidLogin(cleaned)) return false
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, true)
            .putString(KEY_LOGIN_ID, cleaned)
            .apply()
        return true
    }

    fun logout() {
        prefs.edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .remove(KEY_LOGIN_ID)
            .remove(KEY_DISPLAY_NAME)
            .apply()
    }

    fun applyThemeNow() {
        val mode = when (themeMode()) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

    /** Применяет язык ко всему приложению (AppCompat сам пересоздаёт Activity). */
    fun applyLanguageNow() {
        val target = LocaleListCompat.forLanguageTags(languageCode())
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != target.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(target)
        }
    }

    companion object {
        private const val PREFS = "plna_user_settings"
        private const val KEY_THEME = "theme"
        private const val KEY_LANG = "lang"
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_LOGIN_ID = "login_id"
        private const val KEY_DISPLAY_NAME = "display_name"

        /** Демо-коды + любой телефон ≥ 9 цифр. */
        private val INVITE_CODES = setOf("PLNA", "GIPPO", "1234", "NOTE")

        fun isValidLogin(raw: String): Boolean {
            val v = raw.trim()
            if (v.length < 4) return false
            if (INVITE_CODES.any { it.equals(v, ignoreCase = true) }) return true
            val digits = v.filter { it.isDigit() }
            return digits.length >= 9
        }

        @Volatile private var instance: UserSettings? = null

        fun get(context: Context): UserSettings {
            return instance ?: synchronized(this) {
                instance ?: UserSettings(context).also { instance = it }
            }
        }
    }
}
