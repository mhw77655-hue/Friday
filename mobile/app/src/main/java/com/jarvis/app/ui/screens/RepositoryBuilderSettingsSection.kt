package com.jarvis.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jarvis.app.builder.GitHubTokenVault
import com.jarvis.app.ui.components.FridayPanel
import com.jarvis.app.ui.components.FridaySectionHeading
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType

@Composable
fun RepositoryBuilderSettingsSection() {
    val context = LocalContext.current.applicationContext
    val settings = remember(context) { GitHubTokenVault(context) }
    var token by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(settings.hasToken()) }
    var automatic by remember { mutableStateOf(settings.automaticRequestsEnabled() && configured) }
    var status by remember { mutableStateOf("") }

    FridayPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FridaySectionHeading(
                title = "PHONE-TO-REPOSITORY BUILDER",
                detail = if (configured) "CONNECTED" else "OPTIONAL CONNECTION"
            )
            Text(
                "Connect Friday to the trusted GitHub Actions publisher. The phone credential can dispatch this workflow only; it cannot push commits or write repository contents.",
                style = JarvisType.Body.copy(color = JarvisColors.TextSecondary)
            )
            Text(
                "Use a fine-grained token owned by mhw77655-hue, scoped to the Friday repository, with Actions: Read and write only. Do not grant Contents access. Friday encrypts it with Android Keystore and never includes it in a request.",
                style = JarvisType.Technical.copy(color = JarvisColors.TextMuted)
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Fine-grained GitHub token") },
                placeholder = { Text("github_pat_…") },
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        runCatching {
                            settings.saveToken(token)
                            configured = settings.hasToken()
                            token = ""
                            status = "Token encrypted in Android Keystore."
                        }.onFailure { failure ->
                            status = failure.message ?: "Could not save the token."
                        }
                    },
                    enabled = token.isNotBlank()
                ) {
                    Text("Save securely")
                }
                if (configured) {
                    OutlinedButton(onClick = {
                        settings.clear()
                        configured = false
                        automatic = false
                        token = ""
                        status = "Repository connection removed."
                    }) {
                        Text("Remove")
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Allow automatic development requests", style = JarvisType.Body)
                    Text(
                        "Friday may send bounded technical briefs to the repository workflow. Conversation history, memory stores and provider credentials are excluded.",
                        style = JarvisType.Technical.copy(color = JarvisColors.TextMuted)
                    )
                }
                Switch(
                    checked = automatic,
                    enabled = configured,
                    onCheckedChange = { enabled ->
                        runCatching {
                            settings.setAutomaticRequestsEnabled(enabled)
                            automatic = enabled
                            status = if (enabled) {
                                "Automatic repository requests enabled."
                            } else {
                                "Automatic repository requests paused."
                            }
                        }.onFailure { failure ->
                            status = failure.message ?: "Could not update consent."
                        }
                    }
                )
            }
            if (status.isNotBlank()) {
                Text(status, style = JarvisType.Technical.copy(color = JarvisColors.CoreIdle))
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
