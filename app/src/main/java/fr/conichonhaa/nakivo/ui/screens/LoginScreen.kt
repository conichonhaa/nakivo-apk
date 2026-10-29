package fr.conichonhaa.nakivo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.conichonhaa.nakivo.ui.AppViewModel
import fr.conichonhaa.nakivo.ui.ErrorBanner
import fr.conichonhaa.nakivo.ui.UiState

@Composable
fun LoginScreen(state: UiState, vm: AppViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text("NAKIVO Dashboard", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Connexion au Director NAKIVO Backup & Replication",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = state.formUrl,
            onValueChange = vm::onUrlChange,
            label = { Text("Adresse du Director") },
            placeholder = { Text("https://192.168.1.29:4443") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.formUser,
            onValueChange = vm::onUserChange,
            label = { Text("Utilisateur") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.formPassword,
            onValueChange = vm::onPasswordChange,
            label = { Text("Mot de passe") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.connect() }),
            modifier = Modifier.fillMaxWidth(),
        )

        state.loginError?.let { ErrorBanner(it) }

        Button(
            onClick = vm::connect,
            enabled = !state.connecting,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.connecting) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text("Se connecter")
            }
        }

        Text(
            "Conseil : crée dans NAKIVO un utilisateur dédié avec un rôle en lecture seule. " +
                "Les identifiants sont chiffrés sur le téléphone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    state.pendingCert?.let { p ->
        AlertDialog(
            onDismissRequest = vm::rejectPendingCert,
            title = { Text("Certificat non reconnu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Le serveur présente un certificat auto-signé ou qui ne correspond pas à son adresse. " +
                            "Vérifie l'empreinte (dans le navigateur, sur la page du Director) avant de l'accepter.",
                    )
                    Text("Sujet : ${p.subject}", style = MaterialTheme.typography.bodySmall)
                    Text("Valide jusqu'au : ${p.validUntil}", style = MaterialTheme.typography.bodySmall)
                    Text("SHA-256 :", style = MaterialTheme.typography.labelMedium)
                    Text(
                        p.fingerprint,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::trustPendingCert) { Text("Faire confiance") } },
            dismissButton = { TextButton(onClick = vm::rejectPendingCert) { Text("Refuser") } },
        )
    }
}
