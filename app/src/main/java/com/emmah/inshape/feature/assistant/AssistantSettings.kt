package com.emmah.inshape.feature.assistant

import androidx.compose.ui.res.stringResource
import com.emmah.inshape.R
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.data.AssistantMemory
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.ConfirmDialog
import com.emmah.inshape.ui.components.ListCard
import com.emmah.inshape.ui.components.ListRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.theme.InShapeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AssistantSettings(
    credentials: AssistantCredentials?,
    gateway: AssistantGateway,
    vm: JournalViewModel,
    memories: List<AssistantMemory>
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<KeyStatus?>(null) }
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var feedback by rememberSaveable { mutableStateOf<String?>(null) }
    var isError by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var manage by rememberSaveable { mutableStateOf(false) }
    var usage by rememberSaveable { mutableStateOf(false) }
    var showMemories by rememberSaveable { mutableStateOf(false) }
    var confirmRemoval by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(credentials) { status = credentials?.status() }

    fun editKey() {
        feedback = null
        isError = false
        editing = true
    }

    Page(Modifier.imePadding()) {
        Text(stringResource(R.string.openai), style = MaterialTheme.typography.titleLarge)
        if (credentials == null) {
            Text(stringResource(R.string.assistant_unavailable), color = InShapeTheme.colors.textSecondary)
            return@Page
        }
        if (editing) {
            SecureCredentialEntry()
            OutlinedTextField(
                input,
                { input = it.take(1024) },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.api_key)) },
                supportingText = { Text(feedback ?: stringResource(R.string.stored_on_device)) },
                isError = isError,
                enabled = !saving,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)
            )
            ActionRow {
                PrimaryButton(stringResource(R.string.save_key), enabled = input.isNotBlank() && !saving, onClick = {
                    val key = input
                    input = ""
                    feedback = null
                    isError = false
                    saving = true
                    scope.launch {
                        try {
                            credentials.save(key)
                            status = credentials.status()
                            editing = false
                            manage = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            feedback = context.getString(R.string.key_save_failed)
                            isError = true
                        } finally {
                            saving = false
                        }
                    }
                })
                OutlineButton(stringResource(R.string.cancel), enabled = !saving, onClick = {
                    input = ""
                    feedback = null
                    isError = false
                    editing = false
                })
            }
            return@Page
        }
        val connection = when {
            testing -> stringResource(R.string.testing_connection)
            feedback != null -> feedback.orEmpty()
            status == KeyStatus.SAVED -> stringResource(R.string.api_key_saved)
            status == KeyStatus.UNREADABLE -> stringResource(R.string.reenter_key)
            status == KeyStatus.MISSING -> stringResource(R.string.add_api_key)
            else -> stringResource(R.string.checking_key)
        }
        Text(
            connection,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = if (isError) InShapeTheme.colors.error else InShapeTheme.colors.text,
            style = MaterialTheme.typography.titleMedium
        )
        if (status == KeyStatus.SAVED) {
            PrimaryButton(stringResource(R.string.test_connection), enabled = !testing && !saving, onClick = {
                feedback = null
                isError = false
                testing = true
                scope.launch {
                    try {
                        gateway.checkConnection()
                        feedback = context.getString(R.string.connected_openai)
                    } catch (failure: AssistantFailure) {
                        feedback = context.getString(failure.kind.message)
                        isError = true
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        feedback = context.getString(R.string.connection_test_failed)
                        isError = true
                    } finally {
                        testing = false
                    }
                }
            })
            Text(stringResource(R.string.test_credits), style = MaterialTheme.typography.bodySmall, color = InShapeTheme.colors.textSecondary)
        } else if (status != null) {
            PrimaryButton(stringResource(R.string.enter_key), onClick = ::editKey)
        }

        ListCard {
            if (status == KeyStatus.SAVED) item {
                ListRow(
                    stringResource(R.string.manage_api_key),
                    subtitle = stringResource(R.string.stored_on_device),
                    onClick = { manage = !manage }
                )
                if (manage) {
                    ActionRow {
                        OutlineButton(stringResource(R.string.replace_key), enabled = !testing && !saving, onClick = ::editKey)
                        OutlineButton(stringResource(R.string.remove_key), enabled = !testing && !saving, onClick = { confirmRemoval = true })
                    }
                }
            }
            item { AssistantVoiceSettings() }
            item { ListRow(stringResource(R.string.usage_details), onClick = { usage = !usage }) }
            if (memories.isNotEmpty()) {
                item { ListRow(stringResource(R.string.memories), onClick = { showMemories = !showMemories }) }
            }
        }
        if (usage) AssistantUsageDetails(vm.container.assistantUsage?.entries().orEmpty())
        if (showMemories) AssistantMemories(memories, vm)
        if (confirmRemoval) {
            ConfirmDialog(
                stringResource(R.string.remove_key_question),
                stringResource(R.string.remove_key_message),
                stringResource(R.string.remove_key),
                destructive = true,
                {
                    confirmRemoval = false
                    saving = true
                    scope.launch {
                        try {
                            credentials.remove()
                            status = credentials.status()
                            feedback = null
                            isError = false
                            manage = false
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            status = credentials.status()
                            feedback = context.getString(R.string.key_remove_failed)
                            isError = true
                        } finally {
                            saving = false
                        }
                    }
                },
                { confirmRemoval = false }
            )
        }
    }
}

@Composable
private fun AssistantUsageDetails(entries: List<UsageEntry>) {
    val links = LocalUriHandler.current
    OutlineButton(stringResource(R.string.open_usage_dashboard), { links.openUri("https://platform.openai.com/usage") })
    entries.take(5).forEach { entry ->
        val tokens = if (entry.input != null && entry.output != null) stringResource(R.string.usage_tokens, entry.input, entry.output)
        else stringResource(R.string.usage_unavailable)
        val operation = stringResource(when (entry.operation) {
            "Connection test" -> R.string.assistant_usage_connection
            "Text" -> R.string.assistant_usage_text
            "Image import" -> R.string.assistant_usage_images
            "Transcription" -> R.string.assistant_usage_transcription
            "Speech" -> R.string.assistant_usage_speech
            else -> R.string.assistant_usage_unknown
        })
        Text("$operation, ${entry.at.take(16).replace('T', ' ')} UTC\n${entry.model}\n$tokens",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SecureCredentialEntry() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        val flag = WindowManager.LayoutParams.FLAG_SECURE
        val inherited = window?.attributes?.flags?.and(flag) == flag
        window?.addFlags(flag)
        onDispose { if (!inherited) window?.clearFlags(flag) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext == this) null else baseContext.findActivity()
    else -> null
}
