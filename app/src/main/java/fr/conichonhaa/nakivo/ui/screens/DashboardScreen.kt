package fr.conichonhaa.nakivo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.conichonhaa.nakivo.data.JobInfo
import fr.conichonhaa.nakivo.data.Tone
import fr.conichonhaa.nakivo.ui.AppViewModel
import fr.conichonhaa.nakivo.ui.Dot
import fr.conichonhaa.nakivo.ui.ErrorBanner
import fr.conichonhaa.nakivo.ui.SectionTitle
import fr.conichonhaa.nakivo.ui.StatRow
import fr.conichonhaa.nakivo.ui.StatTile
import fr.conichonhaa.nakivo.ui.StatusChip
import fr.conichonhaa.nakivo.ui.UiState
import fr.conichonhaa.nakivo.ui.UsageBar
import fr.conichonhaa.nakivo.ui.theme.toneColor
import fr.conichonhaa.nakivo.util.Format

@Composable
fun DashboardScreen(state: UiState, vm: AppViewModel, contentPadding: PaddingValues) {
    val o = state.overview
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
    ) {
        state.error?.let { item { ErrorBanner(it, Modifier.padding(bottom = 12.dp)) } }

        if (o == null) {
            item {
                Text(
                    if (state.refreshing) "Chargement…" else "Aucune donnée pour le moment. Tire vers le bas pour actualiser.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            return@LazyColumn
        }

        val failedJobs = state.jobs.filter { it.lastRunFailed }
        val runningJobs = state.jobs.filter { it.isRunning }
        val failedCount = maxOf(o.lastFailed, failedJobs.size)
        val runningCount = maxOf(o.jobsRunning, runningJobs.size)

        item { HealthCard(failedCount, runningCount, state.lastUpdate) }

        item {
            Spacer(Modifier.height(12.dp))
            StatRow {
                StatTile("Jobs", "${o.jobIds.size}", Tone.NEUTRAL, Modifier.weight(1f))
                StatTile("Réussis", "${o.lastOk}", Tone.GOOD, Modifier.weight(1f))
                StatTile("Échecs", "$failedCount", if (failedCount > 0) Tone.BAD else Tone.NEUTRAL, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            StatRow {
                StatTile("En cours", "$runningCount", if (runningCount > 0) Tone.RUNNING else Tone.NEUTRAL, Modifier.weight(1f))
                StatTile("VM protégées", "${o.vmCount}", Tone.NEUTRAL, Modifier.weight(1f))
                StatTile("Données source", Format.bytes(o.sourcesSize), Tone.NEUTRAL, Modifier.weight(1f))
            }
        }

        val attention = (runningJobs + failedJobs).distinctBy { it.id }
        if (attention.isNotEmpty()) {
            item { SectionTitle("À surveiller") }
            items(attention, key = { "att-${it.id}" }) { job ->
                CompactJobRow(job) { vm.openJob(job.id) }
            }
        }

        val recent = state.jobs.filter { it.lrDate != null }.sortedByDescending { it.lrDate }.take(5)
        if (recent.isNotEmpty()) {
            item { SectionTitle("Dernières exécutions") }
            items(recent, key = { "rec-${it.id}" }) { job ->
                CompactJobRow(job) { vm.openJob(job.id) }
            }
        }

        if (state.repositories.isNotEmpty() || o.storages.isNotEmpty()) {
            item { SectionTitle("Stockage") }
            if (state.repositories.isNotEmpty()) {
                items(state.repositories) { r ->
                    CapacityRow(r.name, r.size - r.free, r.size, r.state)
                }
            } else {
                items(o.storages) { s ->
                    CapacityRow(s.name, s.size - s.free, if (s.infinite) 0 else s.size, s.state)
                }
            }
        }

        if (o.transporters.isNotEmpty()) {
            item { SectionTitle("Transporters") }
            items(o.transporters) { t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dot(toneColor(Tone.of(t.state)), 10)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${Format.label(t.state)} · charge ${t.currentLoad}/${t.maxLoad}" + if (t.isAuto) " · auto" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthCard(failed: Int, running: Int, lastUpdate: Long?) {
    val (tone, title) = when {
        failed > 0 -> Tone.BAD to if (failed == 1) "1 job en échec" else "$failed jobs en échec"
        running > 0 -> Tone.RUNNING to if (running == 1) "1 job en cours" else "$running jobs en cours"
        else -> Tone.GOOD to "Toutes les sauvegardes sont OK"
    }
    val color = toneColor(tone)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(color, 14)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color)
                Text(
                    lastUpdate?.let { "Mis à jour à ${Format.time(it)}" } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun CompactJobRow(job: JobInfo, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(job.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (job.isRunning) "En cours" + (job.crProgress?.let { " · $it %" } ?: "")
                else Format.relative(job.lrFinishDate ?: job.lrDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (job.isRunning) StatusChip(job.crState, Tone.RUNNING, "En cours")
        else StatusChip(job.lrState)
    }
}

@Composable
private fun CapacityRow(name: String, used: Long, total: Long, state: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(toneColor(Tone.of(state)))
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (total > 0) "${Format.bytes(used)} / ${Format.bytes(total)}" else Format.bytes(used),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (total > 0) UsageBar(Format.percent(used, total))
    }
}
