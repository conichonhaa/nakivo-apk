package fr.conichonhaa.nakivo.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.conichonhaa.nakivo.data.JobInfo
import fr.conichonhaa.nakivo.data.Tone
import fr.conichonhaa.nakivo.ui.KeyValue
import fr.conichonhaa.nakivo.ui.SectionTitle
import fr.conichonhaa.nakivo.ui.StatusChip
import fr.conichonhaa.nakivo.util.Format

@Composable
fun JobDetailScreen(job: JobInfo, contentPadding: PaddingValues) {
    var showRaw by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row {
            if (job.isRunning) StatusChip(job.crState, Tone.RUNNING, "En cours")
            else StatusChip(job.lrState)
            Spacer(Modifier.width(8.dp))
            if (!job.isEnabled) StatusChip("DISABLED", Tone.WARN, "Désactivé")
        }

        SectionTitle("Dernière exécution")
        KeyValue("Résultat", Format.label(job.lrState))
        KeyValue("Début", Format.dateTime(job.lrDate))
        KeyValue("Fin", Format.dateTime(job.lrFinishDate))
        KeyValue("Durée", Format.duration(job.lrDurationMs))
        KeyValue("Objets OK", job.lrVmOk?.toString() ?: "—")
        KeyValue("Objets en échec", job.lrVmFailed?.toString() ?: "—")

        if (job.isRunning) {
            SectionTitle("Exécution en cours")
            KeyValue("État", Format.label(job.crState))
            KeyValue("Progression", job.crProgress?.let { "$it %" } ?: "—")
        }

        SectionTitle("Job")
        KeyValue("Type", Format.label(job.jobType))
        KeyValue("Plateforme", Format.label(job.hvType))
        KeyValue("Statut", Format.label(job.status))
        KeyValue("Identifiant", job.id.toString())

        if (job.objects.isNotEmpty()) {
            SectionTitle("Objets protégés (${job.objects.size})")
            job.objects.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
            }
        }

        SectionTitle("Données brutes")
        TextButton(onClick = { showRaw = !showRaw }) {
            Text(if (showRaw) "Masquer le JSON" else "Afficher le JSON renvoyé par l'API")
        }
        if (showRaw) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                SelectionContainer {
                    Text(
                        job.rawJson,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}
