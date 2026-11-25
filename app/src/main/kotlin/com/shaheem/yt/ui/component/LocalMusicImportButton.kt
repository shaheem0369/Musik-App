package com.shaheem.yt.ui.component

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.shaheem.yt.LocalDatabase
import com.shaheem.yt.R
import com.shaheem.yt.utils.LocalMusicImporter
import kotlinx.coroutines.launch

@Composable
fun LocalMusicImportButton(modifier: Modifier = Modifier, onImported: () -> Unit = {}) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty() && !importing) {
            importing = true
            scope.launch {
                try {
                    val result = LocalMusicImporter.import(context.applicationContext, database, uris)
                    Toast.makeText(context, context.getString(
                        R.string.local_music_import_result, result.added, result.duplicates, result.failed,
                    ), Toast.LENGTH_LONG).show()
                    if (result.added > 0 || result.duplicates > 0) onImported()
                } finally {
                    importing = false
                }
            }
        }
    }
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        TextButton(
            enabled = !importing,
            onClick = {
                try {
                    // Some providers label DSD/AIFF files as application/octet-stream.
                    // Validate actual audio streams after selection instead of hiding these files.
                    picker.launch(arrayOf("*/*"))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.local_music_picker_unavailable, Toast.LENGTH_LONG).show()
                }
            },
        ) {
            Text(stringResource(if (importing) R.string.importing_local_music else R.string.import_local_music))
        }
        Text(
            text = stringResource(R.string.local_music_import_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
