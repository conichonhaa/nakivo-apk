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
import fr.conichonhaa.nakivo.data.RepositoryInfo
import fr.conichonhaa.nakivo.data.Tone
import fr.conichonhaa.nakivo.ui.ErrorBanner
import fr.conichonhaa.nakivo.ui.StatusChip
import fr.conichonhaa.nakivo.ui.UiState
import fr.conichonhaa.nakivo.ui.UsageBar
import fr.conichonhaa.nakivo.util.Format

@Composable
fun StorageScreen(state: UiState, contentPadding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.repositoriesError?.let { item { ErrorBanner("Repositories : $it") } }
        if (state.repositories.isEmpty()) {
            item {
                Text(
                    "Aucun repository chargé.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(state.repositories) { RepositoryCard(it) }
    }
}

@Composable
private fun RepositoryCard(r: RepositoryInfo) {
    val used = (r.size - r.free).coerceAtLeast(0)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        r.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(r.type?.let { Format.label(it) }, r.location).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusChip(r.state)
            }

            if (r.size > 0) {
                Spacer(Modifier.height(12.dp))
                UsageBar(Format.percent(used, r.size))
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(
                        "${Format.bytes(used)} utilisés sur ${Format.bytes(r.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${Format.bytes(r.free)} libres",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            val details = buildList {
                r.backupCount?.let { add("$it sauvegardes") }
                if (r.consumed > 0) add("données ${Format.bytes(r.consumed)}")
                r.status?.takeIf { !it.equals("NONE", true) }?.let { add(Format.label(it)) }
                if (!r.attached) add("détaché")
                if (!r.consistent) add("incohérent")
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!r.consistent || !r.attached) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (r.status != null && Tone.of(r.status) == Tone.RUNNING) {
                Spacer(Modifier.height(6.dp))
                StatusChip(r.status, Tone.RUNNING)
            }
        }
    }
}
