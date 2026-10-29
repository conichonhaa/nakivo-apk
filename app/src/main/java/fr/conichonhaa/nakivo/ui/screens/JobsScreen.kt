package fr.conichonhaa.nakivo.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.conichonhaa.nakivo.data.JobInfo
import fr.conichonhaa.nakivo.data.Tone
import fr.conichonhaa.nakivo.ui.AppViewModel
import fr.conichonhaa.nakivo.ui.ErrorBanner
import fr.conichonhaa.nakivo.ui.StatusChip
import fr.conichonhaa.nakivo.ui.UiState
import fr.conichonhaa.nakivo.ui.theme.toneColor
import fr.conichonhaa.nakivo.util.Format

private enum class JobFilter(val label: String, val test: (JobInfo) -> Boolean) {
    ALL("Tous", { true }),
    FAILED("Échecs", { it.lastRunFailed }),
    RUNNING("En cours", { it.isRunning }),
    DISABLED("Désactivés", { !it.isEnabled }),
}

@Composable
fun JobsScreen(state: UiState, vm: AppViewModel, contentPadding: PaddingValues) {
    var filter by rememberSaveable { mutableStateOf(JobFilter.ALL) }
    val visible = state.jobs.filter(filter.test)

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.error?.let { item { ErrorBanner(it) } }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(JobFilter.entries.toList()) { f ->
                    val count = state.jobs.count(f.test)
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text("${f.label} ($count)") },
                    )
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                Text(
                    if (state.jobs.isEmpty()) "Aucun job chargé." else "Aucun job dans cette catégorie.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(visible, key = { it.id }) { job -> JobCard(job) { vm.openJob(job.id) } }
    }
}

@Composable
private fun JobCard(job: JobInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        job.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            job.jobType?.let { Format.label(it) },
                            job.hvType?.let { Format.label(it) },
                            if (!job.isEnabled) "désactivé" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (job.isRunning) StatusChip(job.crState, Tone.RUNNING, "En cours")
                else StatusChip(job.lrState)
            }

            if (job.isRunning) {
                Spacer(Modifier.height(10.dp))
                val p = job.crProgress
                if (p != null && p in 0..100) {
                    LinearProgressIndicator(
                        progress = { p / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = toneColor(Tone.RUNNING),
                    )
                    Text("$p %", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = toneColor(Tone.RUNNING))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append("Dernière exécution : ")
                    append(Format.relative(job.lrFinishDate ?: job.lrDate))
                    job.lrDurationMs?.let { append(" · ").append(Format.duration(it)) }
                    val ok = job.lrVmOk
                    val ko = job.lrVmFailed
                    if (ok != null || ko != null) append(" · ${ok ?: 0} OK / ${ko ?: 0} KO")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
