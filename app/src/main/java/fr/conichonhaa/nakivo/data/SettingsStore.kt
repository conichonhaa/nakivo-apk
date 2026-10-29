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

    // ------------------------------------------------- mode Interface web

    /** Mode choisi à l'accueil : interface web (toutes licences) ou API (Enterprise Plus). */
    var mode: String
        get() = prefs.getString(KEY_MODE, MODE_WEB) ?: MODE_WEB
        set(value) = prefs.edit().putString(KEY_MODE, value).apply()

    /** true quand l'utilisateur a ouvert l'interface web (on y revient directement au lancement). */
    var webActive: Boolean
        get() = prefs.getBoolean(KEY_WEB_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_WEB_ACTIVE, value).apply()

    /** Largeur virtuelle (px CSS) de mise en page de l'interface, réduite ensuite à l'écran. */
    var webWidth: Int
        get() = prefs.getInt(KEY_WEB_WIDTH, 900)
        set(value) = prefs.edit().putInt(KEY_WEB_WIDTH, value).apply()

    var webLandscape: Boolean
        get() = prefs.getBoolean(KEY_WEB_LANDSCAPE, false)
        set(value) = prefs.edit().putBoolean(KEY_WEB_LANDSCAPE, value).apply()

    var pin: String?
        get() = prefs.getString(KEY_PIN, null)
        set(value) = prefs.edit().putString(KEY_PIN, value).apply()

    fun saveUrl(url: String) {
        // Nouveau serveur : l'empreinte validée pour l'ancien ne vaut plus.
        if (prefs.getString(KEY_URL, null) != url) prefs.edit().remove(KEY_PIN).apply()
        prefs.edit().putString(KEY_URL, url).apply()
    }

    companion object {
        const val MODE_WEB = "web"
        const val MODE_API = "api"
        private const val KEY_MODE = "mode"
        private const val KEY_WEB_ACTIVE = "web_active"
        private const val KEY_WEB_WIDTH = "web_width"
        private const val KEY_WEB_LANDSCAPE = "web_landscape"
        const val DEFAULT_URL = "https://192.168.1.29:4443"
        private const val KEY_URL = "url"
        private const val KEY_USER = "user"
        private const val KEY_PASS = "pass"
        private const val KEY_PIN = "pin"
        private const val KEY_REFRESH = "refresh"
    }
}
