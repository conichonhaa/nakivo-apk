package fr.conichonhaa.nakivo.data

import org.json.JSONArray
import org.json.JSONObject

data class TransporterInfo(
    val name: String,
    val state: String?,
    val currentLoad: Int,
    val maxLoad: Int,
    val isAuto: Boolean,
)

data class StorageInfo(
    val name: String,
    val type: String?,
    val state: String?,
    val online: Boolean,
    val size: Long,
    val free: Long,
    val infinite: Boolean,
)

data class Overview(
    val name: String,
    val jobIds: List<Int>,
    val jobsEnabled: Int,
    val jobsRunning: Int,
    val vmsRunning: Int,
    val vmCount: Int,
    val sourcesSize: Long,
    val lastOk: Int,
    val lastFailed: Int,
    val lastStopped: Int,
    val lastSkipped: Int,
    val transporters: List<TransporterInfo>,
    val storages: List<StorageInfo>,
)

data class JobInfo(
    val id: Int,
    val name: String,
    val jobType: String?,
    val hvType: String?,
    val status: String?,
    val isEnabled: Boolean,
    val crState: String?,
    val crProgress: Int?,
    val lrState: String?,
    val lrDate: Long?,
    val lrFinishDate: Long?,
    val lrDurationMs: Long?,
    val lrVmOk: Int?,
    val lrVmFailed: Int?,
    val objects: List<String>,
    val rawJson: String,
) {
    val isRunning: Boolean
        get() = crState != null && Tone.of(crState) == Tone.RUNNING ||
            (crProgress != null && crProgress in 0..99 && crState != null && !crState.equals("NONE", true))

    val lastRunFailed: Boolean
        get() = Tone.of(lrState) == Tone.BAD || (lrVmFailed ?: 0) > 0
}

data class RepositoryInfo(
    val id: String,
    val name: String,
    val type: String?,
    val location: String?,
    val size: Long,
    val free: Long,
    val consumed: Long,
    val status: String?,
    val state: String?,
    val backupCount: Int?,
    val attached: Boolean,
    val consistent: Boolean,
)

/** Code couleur commun à tous les statuts NAKIVO. */
enum class Tone {
    GOOD, BAD, WARN, RUNNING, NEUTRAL;

    companion object {
        fun of(value: String?): Tone {
            val v = value?.uppercase() ?: return NEUTRAL
            return when {
                v.contains("FAIL") || v.contains("ERROR") || v == "RED" || v.contains("OFFLINE") ||
                    v.contains("INACCESSIBLE") || v.contains("UNAVAILABLE") -> BAD
                v.contains("RUN") || v.contains("PROGRESS") || v.contains("EXECUT") ||
                    v.contains("STARTING") || v.contains("UPDATING") || v.contains("VERIFY") ||
                    v.contains("RECLAIM") || v.contains("HEALING") -> RUNNING
                v.contains("WARN") || v.contains("STOP") || v.contains("SKIP") || v == "YELLOW" ||
                    v.contains("CANCEL") || v.contains("PARTIAL") -> WARN
                v == "OK" || v.contains("SUCCE") || v == "GREEN" || v.contains("COMPLET") ||
                    v == "ONLINE" || v == "AVAILABLE" || v == "IDLE" -> GOOD
                else -> NEUTRAL
            }
        }
    }
}

// ------------------------------------------------------------------ parsing

private fun JSONObject.intOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.longOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.ints(): List<Int> =
    if (this == null) emptyList() else (0 until length()).map { optInt(it) }

/** Les dates NAKIVO peuvent arriver en secondes ou en millisecondes. */
private fun normalizeEpoch(v: Long?): Long? = when {
    v == null || v <= 0 -> null
    v < 100_000_000_000L -> v * 1000
    else -> v
}

private fun children(resp: JSONObject): List<JSONObject> =
    resp.optJSONObject("data")?.optJSONArray("children").objects()

fun parseOverview(resp: JSONObject): Overview {
    val groups = children(resp)
    val transporters = linkedMapOf<String, TransporterInfo>()
    val storages = linkedMapOf<String, StorageInfo>()
    val jobIds = linkedSetOf<Int>()

    fun sum(name: String) = groups.sumOf { it.optInt(name, 0) }

    groups.forEach { g ->
        jobIds += g.optJSONArray("childJobIds").ints()
        g.optJSONArray("transporters").objects().forEach { t ->
            val key = t.str("vid") ?: t.optString("name")
            transporters[key] = TransporterInfo(
                name = t.optString("name", "?"),
                state = t.str("state"),
                currentLoad = t.optInt("currentTotalLoad", 0),
                maxLoad = t.optInt("maxLoadFactor", 0),
                isAuto = t.optBoolean("isAuto", false),
            )
        }
        g.optJSONArray("storages").objects().forEach { s ->
            val key = s.str("vid") ?: s.optString("name")
            storages[key] = StorageInfo(
                name = s.optString("name", "?"),
                type = s.str("type"),
                state = s.str("state"),
                online = s.optBoolean("online", true),
                size = s.optLong("size", 0),
                free = s.optLong("free", 0),
                infinite = s.optBoolean("infiniteSize", false),
            )
        }
    }

    return Overview(
        name = groups.singleOrNull()?.str("name") ?: "Tous les jobs",
        jobIds = jobIds.toList(),
        jobsEnabled = sum("jobCountEnabled"),
        jobsRunning = sum("crJobRunning"),
        vmsRunning = sum("crVmRunning"),
        vmCount = sum("vmCount"),
        sourcesSize = groups.sumOf { it.optLong("sourcesSize", 0) },
        lastOk = sum("lrJobOk"),
        lastFailed = sum("lrJobFailed"),
        lastStopped = sum("lrJobStopped"),
        lastSkipped = sum("lrJobSkipped"),
        transporters = transporters.values.toList(),
        storages = storages.values.toList(),
    )
}

fun parseJobs(resp: JSONObject): List<JobInfo> = children(resp).map { j ->
    JobInfo(
        id = j.optInt("id"),
        name = j.optString("name", "Job ${j.optInt("id")}"),
        jobType = j.str("jobType"),
        hvType = j.str("hvType"),
        status = j.str("status"),
        isEnabled = j.optBoolean("isEnabled", true),
        crState = j.str("crState"),
        crProgress = j.intOrNull("crProgress"),
        lrState = j.str("lrState"),
        lrDate = normalizeEpoch(j.longOrNull("lrDate")),
        lrFinishDate = normalizeEpoch(j.longOrNull("lrFinishDate")),
        lrDurationMs = j.longOrNull("lrDurationMs")?.takeIf { it > 0 },
        lrVmOk = j.intOrNull("lrVmOk"),
        lrVmFailed = j.intOrNull("lrVmFailed"),
        objects = j.optJSONArray("objects").objects().mapNotNull { it.str("name") },
        rawJson = j.toString(2),
    )
}

fun parseRepositories(resp: JSONObject): List<RepositoryInfo> = children(resp).map { r ->
    RepositoryInfo(
        id = r.optString("id", r.optString("name")),
        name = r.optString("name", "?"),
        type = r.str("type"),
        location = r.str("location") ?: r.str("path"),
        size = r.optLong("size", 0),
        free = r.optLong("free", 0),
        consumed = r.optLong("consumed", 0),
        status = r.str("status"),
        state = r.str("state"),
        backupCount = r.intOrNull("backupCount"),
        attached = r.optBoolean("attached", true),
        consistent = r.optBoolean("consistent", true),
    )
}
