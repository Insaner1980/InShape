package com.emmah.inshape.feature.assistant

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.room.withTransaction
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.data.AssistantMemory
import com.emmah.inshape.data.JournalRepository
import com.emmah.inshape.ui.components.ActionRow
import com.emmah.inshape.ui.components.OutlineButton
import com.emmah.inshape.ui.components.TextField
import com.emmah.inshape.ui.requireUi

@Composable
internal fun AssistantMemories(memories: List<AssistantMemory>, vm: JournalViewModel, acknowledge: Boolean = false) {
    if (memories.isEmpty()) return
    Text(
        if (acknowledge) stringResource(R.string.next_use_reminders) else stringResource(R.string.assistant_memories),
        style = MaterialTheme.typography.titleMedium
    )
    memories.forEach { memory ->
        var edited by rememberSaveable(memory.id, memory.revision) { mutableStateOf<String?>(null) }
        val current = edited
        if (current == null) {
            Text(memory.text)
            if (memory.acknowledgedAt != null) Text(stringResource(R.string.acknowledged), style = MaterialTheme.typography.bodySmall)
            ActionRow {
                if (acknowledge) {
                    OutlineButton(stringResource(R.string.acknowledge), { vm.write { changeAssistantMemory(memory, seen = true) } })
                } else {
                    OutlineButton(stringResource(R.string.edit), { edited = memory.text })
                    OutlineButton(stringResource(R.string.remove), { vm.write { changeAssistantMemory(memory) } })
                }
            }
        } else {
            TextField(current, stringResource(R.string.memory), { edited = it.take(1000) })
            ActionRow {
                OutlineButton(stringResource(R.string.save_memory), enabled = current.isNotBlank(), onClick = {
                    vm.write {
                        changeAssistantMemory(memory, updatedText = current)
                        edited = null
                    }
                })
                OutlineButton(stringResource(R.string.cancel), { edited = null })
            }
        }
    }
}

private suspend fun JournalRepository.changeAssistantMemory(original: AssistantMemory, updatedText: String? = null, seen: Boolean = false) {
    db.withTransaction {
        val dao = db.journal()
        val current = dao.assistantMemories().find { it.id == original.id }
        requireUi(current == original, R.string.assistant_memory_changed)
        when {
            seen -> dao.put(original.copy(acknowledgedAt = clock.instant().toString(), revision = original.revision + 1))
            updatedText != null -> dao.put(original.copy(text = updatedText.trim(), revision = original.revision + 1))
            else -> dao.deleteAssistantMemory(original.id, original.revision)
        }
    }
}
