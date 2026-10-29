package fr.conichonhaa.nakivo.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Paramètres de connexion, chiffrés avec une clé du Keystore Android. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = run {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nakivo_secure_prefs",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): ServerConfig? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val user = prefs.getString(KEY_USER, null) ?: return null
        val pass = prefs.getString(KEY_PASS, null) ?: return null
        return ServerConfig(url, user, pass, prefs.getString(KEY_PIN, null))
    }

    fun lastUrl(): String = prefs.getString(KEY_URL, null) ?: DEFAULT_URL
    fun lastUser(): String = prefs.getString(KEY_USER, null).orEmpty()

    fun save(config: ServerConfig) {
        prefs.edit()
            .putString(KEY_URL, config.baseUrl)
            .putString(KEY_USER, config.username)
            .putString(KEY_PASS, config.password)
            .putString(KEY_PIN, config.pinnedSha256)
            .apply()
    }

    /** Déconnexion : on garde l'URL et l'utilisateur, on oublie le mot de passe. */
    fun forgetPassword() {
        prefs.edit().remove(KEY_PASS).apply()
    }

    var refreshSeconds: Int
        get() = prefs.getInt(KEY_REFRESH, 60)
        set(value) = prefs.edit().putInt(KEY_REFRESH, value).apply()

    companion object {
        const val DEFAULT_URL = "https://192.168.1.29:4443"
        private const val KEY_URL = "url"
        private const val KEY_USER = "user"
        private const val KEY_PASS = "pass"
        private const val KEY_PIN = "pin"
        private const val KEY_REFRESH = "refresh"
    }
}
