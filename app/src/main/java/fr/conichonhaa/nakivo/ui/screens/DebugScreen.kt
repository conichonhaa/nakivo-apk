package fr.conichonhaa.nakivo.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.conichonhaa.nakivo.BuildConfig
import fr.conichonhaa.nakivo.ui.AppViewModel
import fr.conichonhaa.nakivo.ui.SectionTitle
import fr.conichonhaa.nakivo.ui.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DebugScreen(state: UiState, vm: AppViewModel, contentPadding: PaddingValues) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionTitle("Actualisation automatique")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Désactivée", 30 to "30 s", 60 to "1 min", 300 to "5 min").forEach { (sec, label) ->
                FilterChip(
                    selected = state.refreshSeconds == sec,
                    onClick = { vm.setRefreshSeconds(sec) },
                    label = { Text(label) },
                )
            }
        }

        SectionTitle("Diagnostic de l'API")
        Text(
            "Affiche la réponse brute du Director. Utile si un écran reste vide : copie le JSON et envoie-le pour ajuster l'app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            AssistChip(onClick = { vm.runDebug("login") }, label = { Text("Login") }, enabled = !state.debugRunning)
            AssistChip(onClick = { vm.runDebug("overview") }, label = { Text("getGroupInfo") }, enabled = !state.debugRunning)
            AssistChip(onClick = { vm.runDebug("jobs") }, label = { Text("getJobInfo (5)") }, enabled = !state.debugRunning)
            AssistChip(onClick = { vm.runDebug("repos") }, label = { Text("Repositories") }, enabled = !state.debugRunning)
            AssistChip(
                onClick = { clipboard.setText(AnnotatedString(state.debugOutput)) },
                label = { Text("Copier") },
                enabled = state.debugOutput.isNotEmpty(),
            )
        }

        if (state.debugOutput.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                SelectionContainer {
                    Text(
                        state.debugOutput.take(200_000),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        }

        SectionTitle("À propos")
        Text(
            "Version ${BuildConfig.VERSION_NAME} · serveur ${state.formUrl}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
