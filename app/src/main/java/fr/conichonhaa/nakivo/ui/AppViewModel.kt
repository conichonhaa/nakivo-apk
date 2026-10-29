package fr.conichonhaa.nakivo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.conichonhaa.nakivo.data.JobInfo
import fr.conichonhaa.nakivo.data.NakivoClient
import fr.conichonhaa.nakivo.data.NakivoException
import fr.conichonhaa.nakivo.data.Overview
import fr.conichonhaa.nakivo.data.RepositoryInfo
import fr.conichonhaa.nakivo.data.ServerConfig
import fr.conichonhaa.nakivo.data.SettingsStore
import fr.conichonhaa.nakivo.data.UntrustedCertificateException
import fr.conichonhaa.nakivo.data.parseJobs
import fr.conichonhaa.nakivo.data.parseOverview
import fr.conichonhaa.nakivo.data.parseRepositories
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class Tab { DASHBOARD, JOBS, STORAGE }

data class PendingCert(
    val config: ServerConfig,
    val fingerprint: String,
    val subject: String,
    val validUntil: String,
)

data class UiState(
    val loggedIn: Boolean = false,
    val tab: Tab = Tab.DASHBOARD,
    // Écran de connexion
    val formUrl: String = SettingsStore.DEFAULT_URL,
    val formUser: String = "",
    val formPassword: String = "",
    val connecting: Boolean = false,
    val loginError: String? = null,
    val pendingCert: PendingCert? = null,
    // Données
    val refreshing: Boolean = false,
    val error: String? = null,
    val overview: Overview? = null,
    val jobs: List<JobInfo> = emptyList(),
    val repositories: List<RepositoryInfo> = emptyList(),
    val repositoriesError: String? = null,
    val lastUpdate: Long? = null,
    // Navigation secondaire
    val selectedJobId: Int? = null,
    val showDebug: Boolean = false,
    val debugOutput: String = "",
    val debugRunning: Boolean = false,
    val refreshSeconds: Int = 60,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private var client: NakivoClient? = null
    private var refreshJob: Job? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val saved = store.load()
        _state.update {
            it.copy(
                formUrl = store.lastUrl(),
                formUser = store.lastUser(),
                refreshSeconds = store.refreshSeconds,
            )
        }
        if (saved != null) {
            client = NakivoClient(saved)
            _state.update { it.copy(loggedIn = true) }
        }
    }

    // ------------------------------------------------------------ connexion

    fun onUrlChange(v: String) = _state.update { it.copy(formUrl = v, loginError = null) }
    fun onUserChange(v: String) = _state.update { it.copy(formUser = v, loginError = null) }
    fun onPasswordChange(v: String) = _state.update { it.copy(formPassword = v, loginError = null) }

    fun connect() {
        val s = _state.value
        val url = normalizeUrl(s.formUrl)
        if (url.isBlank() || s.formUser.isBlank() || s.formPassword.isEmpty()) {
            _state.update { it.copy(loginError = "Renseigne l'adresse, l'utilisateur et le mot de passe.") }
            return
        }
        // On réutilise l'empreinte déjà validée si le serveur n'a pas changé.
        val previous = store.load()
        val pin = previous?.pinnedSha256?.takeIf { previous.baseUrl == url }
        tryLogin(ServerConfig(url, s.formUser.trim(), s.formPassword, pin))
    }

    fun trustPendingCert() {
        val p = _state.value.pendingCert ?: return
        _state.update { it.copy(pendingCert = null) }
        tryLogin(p.config.copy(pinnedSha256 = p.fingerprint))
    }

    fun rejectPendingCert() = _state.update {
        it.copy(pendingCert = null, loginError = "Certificat refusé : connexion annulée.")
    }

    private fun tryLogin(config: ServerConfig) {
        _state.update { it.copy(connecting = true, loginError = null) }
        viewModelScope.launch {
            val c = NakivoClient(config)
            try {
                c.login()
                store.save(config)
                client = c
                _state.update {
                    it.copy(
                        connecting = false, loggedIn = true, formPassword = "",
                        formUrl = config.baseUrl, error = null, tab = Tab.DASHBOARD,
                    )
                }
                refresh()
            } catch (e: UntrustedCertificateException) {
                _state.update {
                    it.copy(
                        connecting = false,
                        pendingCert = PendingCert(config, e.fingerprint, e.subject, e.validUntil),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(connecting = false, loginError = e.readable()) }
            }
        }
    }

    fun logout() {
        refreshJob?.cancel()
        store.forgetPassword()
        client = null
        _state.update {
            UiState(
                formUrl = store.lastUrl(),
                formUser = store.lastUser(),
                refreshSeconds = store.refreshSeconds,
            )
        }
    }

    // --------------------------------------------------------------- données

    fun refresh(silent: Boolean = false) {
        val c = client ?: return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            if (!silent) _state.update { it.copy(refreshing = true) }
            try {
                val reposDeferred = async { runCatching { parseRepositories(c.getRepositories()) } }
                val overview = parseOverview(c.getOverview())
                val jobs = if (overview.jobIds.isEmpty()) emptyList()
                else parseJobs(c.getJobs(overview.jobIds))
                val repos = reposDeferred.await()
                _state.update {
                    it.copy(
                        refreshing = false,
                        error = null,
                        overview = overview,
                        jobs = jobs.sortedWith(jobOrder),
                        repositories = repos.getOrDefault(it.repositories),
                        repositoriesError = repos.exceptionOrNull()?.readable(),
                        lastUpdate = System.currentTimeMillis(),
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(refreshing = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(refreshing = false, error = e.readable()) }
            }
        }
    }

    fun setRefreshSeconds(sec: Int) {
        store.refreshSeconds = sec
        _state.update { it.copy(refreshSeconds = sec) }
    }

    // ------------------------------------------------------------ navigation

    fun selectTab(tab: Tab) = _state.update { it.copy(tab = tab) }
    fun openJob(id: Int) = _state.update { it.copy(selectedJobId = id) }
    fun closeJob() = _state.update { it.copy(selectedJobId = null) }
    fun openDebug() = _state.update { it.copy(showDebug = true) }
    fun closeDebug() = _state.update { it.copy(showDebug = false) }

    /** Écran diagnostic : affiche la réponse JSON brute d'un appel. */
    fun runDebug(kind: String) {
        val c = client ?: return
        _state.update { it.copy(debugRunning = true, debugOutput = "Appel en cours…") }
        viewModelScope.launch {
            val out = try {
                val resp: JSONObject = when (kind) {
                    "overview" -> c.getOverview()
                    "jobs" -> {
                        val ids = _state.value.overview?.jobIds?.takeIf { it.isNotEmpty() }
                            ?: parseOverview(c.getOverview()).jobIds
                        c.getJobs(ids.take(5))
                    }
                    "repos" -> c.getRepositories()
                    else -> {
                        c.login()
                        JSONObject().put("login", "OK")
                    }
                }
                resp.toString(2)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "ERREUR : ${e.readable()}"
            }
            _state.update { it.copy(debugRunning = false, debugOutput = out) }
        }
    }

    // --------------------------------------------------------------- helpers

    private val jobOrder = compareByDescending<JobInfo> { it.isRunning }
        .thenByDescending { it.lastRunFailed }
        .thenByDescending { it.lrDate ?: 0L }

    private fun Throwable.readable(): String = when (this) {
        is NakivoException -> message ?: "Erreur"
        else -> "${javaClass.simpleName} : ${message ?: ""}".trim()
    }

    companion object {
        fun normalizeUrl(input: String): String {
            var u = input.trim().trimEnd('/')
            if (u.isEmpty()) return u
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
            // Ajoute le port NAKIVO par défaut si aucun port n'est précisé.
            val hostPart = u.substringAfter("://").substringBefore('/')
            if (!hostPart.contains(':')) {
                val scheme = u.substringBefore("://")
                val rest = u.substringAfter("://").removePrefix(hostPart)
                u = "$scheme://$hostPart:4443$rest"
            }
            return u
        }
    }
}
