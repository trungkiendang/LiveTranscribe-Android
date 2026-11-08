package com.kun.livetranslation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kun.livetranslation.LiveCaptionApp
import com.kun.livetranslation.speech.SherpaModelStore
import com.kun.livetranslation.ui.l10n.LocalUiStrings
import kotlinx.coroutines.launch

/** Switch plus per-language download rows for the high-accuracy on-device (Zipformer) models. */
@Composable
internal fun SherpaSection(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    val t = LocalUiStrings.current
    val store = (LocalContext.current.applicationContext as LiveCaptionApp).container.sherpaModels
    val installed by store.installed.collectAsState()
    val progress by store.progress.collectAsState()
    val errors by store.errors.collectAsState()
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(t["Accurate on-device (English, Chinese)"], style = MaterialTheme.typography.bodyMedium)
                Text(
                    t["Streaming Zipformer models. More accurate than the standard models and still offline."],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled) {
            SherpaModelStore.models.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${m.label} · ${m.sizeMb} MB", style = MaterialTheme.typography.bodySmall)
                        errors[m.id]?.let {
                            Text("✗ $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    val p = progress[m.id]
                    when {
                        p != null -> Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        m.id in installed -> OutlinedButton(onClick = { store.delete(m) }) { Text(t["Remove"]) }
                        else -> OutlinedButton(onClick = { scope.launch { store.download(m) } }) { Text(t["Download"]) }
                    }
                }
            }
        }
    }
}
