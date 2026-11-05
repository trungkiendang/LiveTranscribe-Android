package com.kun.livetranslation.ui

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kun.livetranslation.LiveCaptionApp
import com.kun.livetranslation.ai.AiCatalog
import com.kun.livetranslation.ai.AiConfig
import com.kun.livetranslation.ai.AiProvider
import com.kun.livetranslation.ai.AiTestReport
import com.kun.livetranslation.ai.ModelOption
import com.kun.livetranslation.ui.l10n.LocalUiStrings
import kotlinx.coroutines.launch

/**
 * Cloud AI settings: pick a provider, enter the key, choose a model (recommended ones are
 * flagged, and the provider's live list can be fetched), and test the draft before saving.
 * Save only turns on after the current draft passed a test (or when clearing the key).
 */
@Composable
internal fun AiProviderCard(saved: AiConfig, onSave: (AiConfig) -> Unit) {
    val t = LocalUiStrings.current
    val api = (LocalContext.current.applicationContext as LiveCaptionApp).container.aiApi
    val scope = rememberCoroutineScope()

    var draft by remember(saved) { mutableStateOf(saved) }
    var testing by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<AiTestReport?>(null) }
    var testedDraft by remember { mutableStateOf<AiConfig?>(null) }
    var pickingStt by remember { mutableStateOf<Boolean?>(null) } // null = dialog closed

    fun edit(next: AiConfig) {
        draft = next
        report = null
        testedDraft = null
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel(t["Cloud AI provider"])

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AiProvider.entries.forEach { p ->
                    ChoiceChip(
                        label = p.label,
                        icon = Icons.Filled.Cloud,
                        selected = draft.provider == p,
                        onClick = {
                            edit(
                                AiConfig(
                                    provider = p, apiKey = draft.apiKey,
                                    model = AiCatalog.recommended(AiConfig(provider = p)).firstOrNull()?.id.orEmpty()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (draft.provider == AiProvider.OPENAI_COMPAT) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    AiCatalog.presets.forEach { preset ->
                        val selected = if (preset.baseUrl.isEmpty()) AiCatalog.presetFor(draft) == null
                        else AiCatalog.presetFor(draft) == preset
                        ChoiceChip(
                            label = preset.name,
                            icon = Icons.Filled.Settings,
                            selected = selected,
                            onClick = {
                                edit(
                                    draft.copy(
                                        baseUrl = preset.baseUrl,
                                        model = preset.models.firstOrNull()?.id.orEmpty(),
                                        sttModel = preset.sttModel
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = draft.baseUrl,
                    onValueChange = { edit(draft.copy(baseUrl = it)) },
                    label = { Text(t["Base URL"]) },
                    placeholder = { Text("https://host/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { edit(draft.copy(apiKey = it)) },
                label = { Text(t["API key"]) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            ModelField(
                label = t["Model"],
                value = draft.model,
                recommended = AiCatalog.recommended(draft).firstOrNull { it.recommended }?.id,
                onChange = { edit(draft.copy(model = it)) },
                onChoose = { pickingStt = false }
            )
            if (draft.provider == AiProvider.OPENAI_COMPAT && draft.canTranscribe) {
                ModelField(
                    label = t["Speech model"],
                    value = draft.sttModel,
                    recommended = AiCatalog.presetFor(draft)?.sttModel?.takeIf { it.isNotBlank() },
                    onChange = { edit(draft.copy(sttModel = it)) },
                    onChoose = { pickingStt = true }
                )
            }
            if (!draft.canTranscribe) {
                Text(
                    t["This provider can translate but not transcribe speech. Use Gemini or another OpenAI-compatible service for speech."],
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                t["Your key is stored only on this phone. Cloud AI sends your text or speech to the provider you choose."],
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            report?.let { r -> TestResult(r) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = !testing && draft.apiKey.isNotBlank() && draft.model.isNotBlank(),
                    onClick = {
                        testing = true
                        val snapshot = draft
                        scope.launch {
                            val result = api.test(snapshot)
                            report = result
                            testedDraft = if (result.ok) snapshot else null
                            testing = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (testing) t["Testing…"] else t["Test connection"]) }
                Button(
                    enabled = !testing && (testedDraft == draft || draft.apiKey.isBlank()),
                    onClick = { onSave(draft) },
                    modifier = Modifier.weight(1f)
                ) { Text(t["Save"]) }
            }
            if (draft.apiKey.isNotBlank() && testedDraft != draft && !testing) {
                Text(
                    t["Run a successful test to enable Save."],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    pickingStt?.let { forStt ->
        ModelPickerDialog(
            config = draft,
            onDismiss = { pickingStt = null },
            onPick = { id ->
                edit(if (forStt) draft.copy(sttModel = id) else draft.copy(model = id))
                pickingStt = null
            }
        )
    }
}

@Composable
private fun ModelField(label: String, value: String, recommended: String?, onChange: (String) -> Unit, onChoose: () -> Unit) {
    val t = LocalUiStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = onChoose, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text(t["Choose"])
            }
        }
        if (recommended != null && !value.equals(recommended, ignoreCase = true)) {
            Text(
                text = "${t["Recommended"]}: $recommended",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onChange(recommended) }
            )
        }
    }
}

@Composable
private fun TestResult(r: AiTestReport) {
    val t = LocalUiStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        r.translation?.let {
            Line(
                ok = it.isSuccess,
                text = it.fold(
                    { text -> "${t["Translation"]}: \"Hello, how are you?\" → $text" },
                    { e -> "${t["Translation"]}: ${e.message}" }
                )
            )
        }
        r.speech?.let {
            Line(
                ok = it.isSuccess,
                text = it.fold({ t["Speech recognition: OK"] }, { e -> "${t["Speech recognition"]}: ${e.message}" })
            )
        }
        Text("${r.millis} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Line(ok: Boolean, text: String) {
    Text(
        text = (if (ok) "✓ " else "✗ ") + text,
        style = MaterialTheme.typography.bodySmall,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
}

/** Recommended models first; "Fetch" loads the provider's own list. */
@Composable
private fun ModelPickerDialog(config: AiConfig, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val t = LocalUiStrings.current
    val api = (LocalContext.current.applicationContext as LiveCaptionApp).container.aiApi
    val scope = rememberCoroutineScope()
    val recommended = AiCatalog.recommended(config)
    var options by remember { mutableStateOf<List<ModelOption>>(AiCatalog.merge(emptyList(), recommended)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(t["Close"]) } },
        title = { Text(t["Choose a model"]) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !loading && config.apiKey.isNotBlank(),
                    onClick = {
                        loading = true
                        error = null
                        scope.launch {
                            api.listModels(config).fold(
                                { ids -> options = AiCatalog.merge(ids, recommended) },
                                { e -> error = e.message }
                            )
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (loading) t["Loading…"] else t["Fetch models from provider"]) }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (options.isEmpty()) {
                    Text(t["No models yet. Enter an API key and fetch the list."], style = MaterialTheme.typography.bodySmall)
                }
                Column(
                    Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    options.forEach { m ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(m.id) }.padding(vertical = 8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(m.id, style = MaterialTheme.typography.bodyMedium, fontWeight = if (m.recommended) FontWeight.Bold else FontWeight.Normal)
                                if (m.recommended) {
                                    Text(t["Recommended"], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (m.note.isNotBlank()) {
                                Text(m.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    )
}
