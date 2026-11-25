package com.shaheem.yt.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import com.shaheem.yt.R
import com.shaheem.yt.constants.TogetherServerTokenKey
import com.shaheem.yt.constants.TogetherServerUrlKey
import com.shaheem.yt.together.TogetherOnlineEndpoint
import com.shaheem.yt.utils.dataStore
import kotlinx.coroutines.launch

@Composable
internal fun TogetherServerSettingsCard(serverUrl: String, serverToken: String, enabled: Boolean, compact: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    // Keep the access token out of saved-instance-state and the clipboard.
    var token by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!compact) {
                Text(stringResource(R.string.together_server_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.together_server_description))
            }
            Text(
                if (serverUrl.isBlank()) stringResource(R.string.together_server_missing) else serverUrl,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { url = serverUrl; token = serverToken; saveError = false; editing = true }, enabled = enabled) {
                Text(stringResource(R.string.together_server_configure))
            }
            if (!compact) Text(stringResource(R.string.together_server_note), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { if (!saving) { editing = false; token = "" } },
            title = { Text(stringResource(R.string.together_server_configure)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.together_server_hint))
                    OutlinedTextField(
                        value = url, onValueChange = { url = it }, singleLine = true,
                        label = { Text(stringResource(R.string.together_server_url)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        isError = url.isNotBlank() && TogetherOnlineEndpoint.normalizeBaseUrl(url) == null,
                    )
                    OutlinedTextField(
                        value = token, onValueChange = { token = it }, singleLine = true,
                        label = { Text(stringResource(R.string.together_server_token)) },
                        supportingText = { Text(stringResource(R.string.together_server_token_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (saveError) Text(stringResource(R.string.together_server_save_error), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(enabled = !saving && TogetherOnlineEndpoint.normalizeBaseUrl(url) != null && (token.isBlank() || token.trim().length >= 16),
                    onClick = {
                        scope.launch {
                            saving = true
                            try {
                                context.dataStore.edit {
                                    it[TogetherServerUrlKey] = requireNotNull(TogetherOnlineEndpoint.normalizeBaseUrl(url))
                                    it[TogetherServerTokenKey] = token.trim()
                                }
                                token = ""
                                editing = false
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                saveError = true
                            } finally { saving = false }
                        }
                    }) { Text(stringResource(R.string.together_server_save)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { editing = false; token = "" }) {
                    Text(stringResource(R.string.together_server_cancel))
                }
            },
        )
    }
}
