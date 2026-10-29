package fr.conichonhaa.nakivo.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Format {
    private val locale = Locale.FRANCE

    fun bytes(value: Long): String {
        if (value <= 0) return "0 o"
        val units = listOf("o", "Ko", "Mo", "Go", "To", "Po")
        var v = value.toDouble()
        var i = 0
        while (v >= 1024 && i < units.lastIndex) {
            v /= 1024
            i++
        }
        return if (v >= 100 || i == 0) String.format(locale, "%.0f %s", v, units[i])
        else String.format(locale, "%.1f %s", v, units[i])
    }

    fun duration(ms: Long?): String {
        if (ms == null || ms <= 0) return "—"
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            h > 0 -> "${h} h ${"%02d".format(m)} min"
            m > 0 -> "${m} min ${"%02d".format(sec)} s"
            else -> "${sec} s"
        }
    }

    fun dateTime(epochMs: Long?): String {
        if (epochMs == null) return "—"
        return SimpleDateFormat("dd/MM/yyyy HH:mm", locale).format(Date(epochMs))
    }

    fun time(epochMs: Long): String = SimpleDateFormat("HH:mm:ss", locale).format(Date(epochMs))

    /** « il y a 3 h », « dans 20 min »… */
    fun relative(epochMs: Long?, now: Long = System.currentTimeMillis()): String {
        if (epochMs == null) return "jamais"
        val diff = now - epochMs
        val past = diff >= 0
        val min = abs(diff) / 60_000
        val text = when {
            min < 1 -> return "à l'instant"
            min < 60 -> "$min min"
            min < 60 * 24 -> "${min / 60} h"
            else -> "${min / (60 * 24)} j"
        }
        return if (past) "il y a $text" else "dans $text"
    }

    fun percent(part: Long, total: Long): Float =
        if (total <= 0) 0f else (part.toFloat() / total).coerceIn(0f, 1f)

    /** Libellés français des valeurs NAKIVO les plus courantes ; sinon valeur brute lisible. */
    fun label(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        val map = mapOf(
            "OK" to "OK",
            "SUCCEEDED" to "Réussi",
            "SUCCESS" to "Réussi",
            "SUCCESSFUL" to "Réussi",
            "COMPLETED" to "Terminé",
            "FAILED" to "Échec",
            "FAILURE" to "Échec",
            "ERROR" to "Erreur",
            "STOPPED" to "Arrêté",
            "SKIPPED" to "Ignoré",
            "RUNNING" to "En cours",
            "IN_PROGRESS" to "En cours",
            "WAITING" to "En attente",
            "NONE" to "Inactif",
            "IDLE" to "Inactif",
            "WARNING" to "Avertissement",
            "GREEN" to "OK",
            "YELLOW" to "Avertissement",
            "RED" to "Erreur",
            "UPDATING" to "Mise à jour",
            "SELF_HEALING" to "Auto-réparation",
            "SPACE_RECLAIM" to "Récupération d'espace",
            "VERIFY_BACKUPS" to "Vérification",
            "ONLINE" to "En ligne",
            "OFFLINE" to "Hors ligne",
            "VMWARE" to "VMware",
            "HYPERV" to "Hyper-V",
            "BACKUP" to "Sauvegarde",
            "REPLICATION" to "Réplication",
            "BACKUP_COPY" to "Copie de sauvegarde",
        )
        val key = raw.uppercase()
        return map[key] ?: raw.replace('_', ' ').lowercase(locale).replaceFirstChar { it.titlecase(locale) }
    }
}
