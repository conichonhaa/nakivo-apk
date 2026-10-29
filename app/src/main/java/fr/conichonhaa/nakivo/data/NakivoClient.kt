package fr.conichonhaa.nakivo.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException

data class ServerConfig(
    val baseUrl: String,
    val username: String,
    val password: String,
    val pinnedSha256: String?,
)

open class NakivoException(message: String) : Exception(message)

class NakivoApiException(message: String, val authProblem: Boolean = false) : NakivoException(message)

class UntrustedCertificateException(
    val fingerprint: String,
    val subject: String,
    val validUntil: String,
) : NakivoException("Certificat du serveur non reconnu")

private class MemoryCookieJar : CookieJar {
    private val cookies = mutableMapOf<String, Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { this.cookies[it.name] = it }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        this.cookies.values.removeAll { it.expiresAt < now }
        return this.cookies.values.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() = cookies.clear()
}

/**
 * Client de l'API JSON-RPC de NAKIVO Backup & Replication.
 * Toutes les requêtes sont des POST vers <url>/c/router.
 */
class NakivoClient(val config: ServerConfig) {

    private val json = "application/json; charset=utf-8".toMediaType()
    private val tid = AtomicInteger(1)
    private val cookieJar = MemoryCookieJar()
    private val trustManager = PinningTrustManager(config.pinnedSha256)
    private val loginMutex = Mutex()

    @Volatile
    private var loggedIn = false

    private val endpoint: String = config.baseUrl.trimEnd('/') + "/c/router"

    private val http: OkHttpClient = run {
        val ssl = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .sslSocketFactory(ssl.socketFactory, trustManager)
            .hostnameVerifier { host, session ->
                // Certificat épinglé : on accepte même si le nom (souvent une IP) ne correspond pas.
                val pin = config.pinnedSha256
                val leaf = runCatching { session.peerCertificates.firstOrNull() as? X509Certificate }.getOrNull()
                if (pin != null && leaf != null && CertUtils.sha256(leaf) == pin) {
                    true
                } else {
                    HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session)
                }
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ---------------------------------------------------------------- API

    /** Connexion. Lève une exception explicite en cas d'échec. */
    suspend fun login() {
        loginMutex.withLock {
            cookieJar.clear()
            loggedIn = false
            val data = JSONArray().put(config.username).put(config.password).put(true)
            val resp = rawCall("AuthenticationManagement", "login", data)
            val d = resp.optJSONObject("data")
            if (d != null) {
                val result = d.str("result")
                val reason = d.str("reason")
                val ok = result == null || result.uppercase() in setOf("SUCCESS", "OK", "TRUE", "LOGGED_IN")
                if (!ok) {
                    val canTry = d.optJSONObject("canTry")
                    val wait = canTry?.optInt("waitTimeLeft", 0) ?: 0
                    val extra = if (canTry != null && !canTry.optBoolean("isPossible", true) && wait > 0)
                        " (nouvel essai possible dans ${wait}s)" else ""
                    throw NakivoApiException(
                        "Connexion refusée : ${reason ?: result}$extra",
                        authProblem = true,
                    )
                }
            }
            loggedIn = true
        }
    }

    /** Vue d'ensemble : groupe racine (tous les jobs, transporters, stockages). */
    suspend fun getOverview(): JSONObject = call(
        "JobSummaryManagement", "getGroupInfo",
        JSONArray().put(JSONArray().put(JSONObject.NULL)).put(0).put(true),
    )

    /** Détail des jobs (dernière exécution incluse). */
    suspend fun getJobs(ids: List<Int>): JSONObject = call(
        "JobSummaryManagement", "getJobInfo",
        JSONArray().put(JSONArray(ids)).put(0).put(JSONObject.NULL),
    )

    /** Liste des Backup Repositories. */
    suspend fun getRepositories(): JSONObject = call(
        "BackupManagement", "getBackupRepositories",
        JSONArray().put(
            JSONObject().put(
                "filter",
                JSONObject()
                    .put("start", 0)
                    .put("count", 200)
                    .put("sort", "NAME")
                    .put("sortAsc", true),
            ),
        ),
    )

    /** Appel générique (écran de diagnostic). */
    suspend fun call(action: String, method: String, data: Any): JSONObject {
        if (!loggedIn) login()
        return try {
            rawCall(action, method, data)
        } catch (e: NakivoApiException) {
            if (!e.authProblem) throw e
            // Session expirée : on se reconnecte une fois puis on rejoue.
            loggedIn = false
            login()
            rawCall(action, method, data)
        }
    }

    // ---------------------------------------------------------- plumbing

    private suspend fun rawCall(action: String, method: String, data: Any): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("action", action)
                .put("method", method)
                .put("data", data)
                .put("type", "rpc")
                .put("tid", tid.getAndIncrement())
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .post(body.toString().toRequestBody(json))
                .build()
            try {
                http.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.code == 401 || resp.code == 403) {
                        throw NakivoApiException("HTTP ${resp.code} : accès refusé", authProblem = true)
                    }
                    if (!resp.isSuccessful) {
                        throw NakivoApiException("HTTP ${resp.code} : ${text.take(300)}")
                    }
                    val obj = try {
                        JSONObject(text)
                    } catch (e: JSONException) {
                        throw NakivoApiException("Réponse inattendue (pas du JSON) : ${text.take(300)}")
                    }
                    val message = obj.str("message")
                    if (message != null) {
                        val cause = obj.str("cause")
                        val full = listOfNotNull(message, cause).joinToString(" — ")
                        throw NakivoApiException(full, authProblem = looksLikeAuth(full))
                    }
                    obj
                }
            } catch (e: SSLException) {
                throw mapSsl(e)
            } catch (e: IOException) {
                throw NakivoException("Serveur injoignable : ${e.message ?: e.javaClass.simpleName}")
            }
        }

    private fun mapSsl(e: SSLException): Exception {
        val seen = trustManager.lastSeen
        if (seen != null) {
            val fp = CertUtils.sha256(seen)
            if (fp != config.pinnedSha256) {
                return UntrustedCertificateException(
                    fingerprint = fp,
                    subject = seen.subjectX500Principal.name,
                    validUntil = DateFormat.getDateInstance(DateFormat.MEDIUM).format(seen.notAfter),
                )
            }
        }
        return NakivoException("Erreur TLS : ${e.message ?: e.javaClass.simpleName}")
    }

    private fun looksLikeAuth(msg: String): Boolean =
        Regex("(?i)(login|log in|logged|auth|session|unauthori|not permitted|credential)").containsMatchIn(msg)
}

/** Chaîne ou null si absente / JSON null. */
fun JSONObject.str(name: String): String? =
    if (has(name) && !isNull(name)) optString(name).takeIf { it.isNotEmpty() } else null
