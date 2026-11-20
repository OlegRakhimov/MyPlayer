package com.example.freelocalplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.freelocalplayer.data.ConfirmedRetiredMediaRebind
import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.RetiredMedia

/**
 * 0-B.3: opt-in preview. Displays existing live scan results, not filesystem
 * guesses. The Activity re-verifies the evidence and durable ZIP before commit.
 */
@Composable
internal fun RetiredMediaRelinkDialog(
    library: LibraryState,
    backupVerifiedForCurrentSnapshot: Boolean,
    busy: Boolean,
    onExportBackup: () -> Unit,
    onConfirmed: (oldUuid: String, candidateUuid: String) -> Unit,
    onDismiss: () -> Unit
) {
    var oldId by remember { mutableStateOf<String?>(null) }
    var newId by remember { mutableStateOf<String?>(null) }
    var oldQuery by remember { mutableStateOf("") }
    var newQuery by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }

    val old = library.retiredMedia.firstOrNull { it.entry.id == oldId }
    val fresh = library.media.firstOrNull { it.id == newId }
    val decision = if (old != null && fresh != null) {
        ConfirmedRetiredMediaRebind.preview(library, old.entry.id, fresh.id)
    } else null
    val ready = decision == ConfirmedRetiredMediaRebind.Refusal.NONE

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Восстановить связь трека") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = 530.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Выбери запись из архива и найденный после переиндексации файл. " +
                        "Приложение не определяет, одинаковая ли это музыка: проверь соответствие сам.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text("1. Старая запись", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = oldQuery,
                    onValueChange = { oldQuery = it },
                    label = { Text("Найти удалённую запись") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                val oldOptions = library.retiredMedia.filter {
                    oldQuery.isBlank() ||
                        it.entry.title.contains(oldQuery, ignoreCase = true)
                }
                Text("Записей: " + oldOptions.size, style = MaterialTheme.typography.labelSmall)
                oldOptions.take(8).forEach { record: RetiredMedia ->
                    TextButton(
                        onClick = {
                            oldId = record.entry.id
                            acknowledged = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            (if (record.entry.id == oldId) "✓ " else "") + record.entry.title,
                            maxLines = 2
                        )
                    }
                }
                if (oldOptions.size > 8) {
                    Text("Уточни поиск, чтобы увидеть другие записи.",
                        style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
                Text("2. Файл, уже найденный на телефоне", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = newQuery,
                    onValueChange = { newQuery = it },
                    label = { Text("Найти новый файл") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                val newOptions = library.media.asSequence()
                    .filter {
                        (old == null || it.isVideo == old.entry.isVideo) &&
                            (newQuery.isBlank() ||
                                it.title.contains(newQuery, ignoreCase = true))
                    }
                    .take(30).toList()
                newOptions.forEach { entry: MediaEntry ->
                    TextButton(
                        onClick = {
                            newId = entry.id
                            acknowledged = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            (if (entry.id == newId) "✓ " else "") + entry.title,
                            maxLines = 2
                        )
                    }
                }
                if (newOptions.size >= 30) {
                    Text("Показаны первые 30 записей. Уточни поиск.",
                        style = MaterialTheme.typography.labelSmall)
                }
                HorizontalDivider()
                if (old != null && fresh != null) {
                    Text(
                        "Архив: " + old.entry.title + "\nНовый файл: " + fresh.title +
                            "\nРазмер: " + old.entry.sizeBytes + " / " + fresh.sizeBytes +
                            " байт\nДлительность: " + old.entry.durationMs + " / " +
                            fresh.durationMs + " мс\nСтарый UUID будет сохранён. " +
                            "Собственные данные нового UUID переноситься не будут.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (ready) "Первичная проверка пройдена; проверка файлов и привязок будет перед записью."
                        else "Восстановление заблокировано: " + refusalText(decision),
                        color = if (ready) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    if (backupVerifiedForCurrentSnapshot)
                        "Проверенный ZIP экспортирован для текущей версии медиатеки."
                    else "Перед восстановлением экспортируй проверенную ZIP-копию текущей медиатеки.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = onExportBackup,
                    enabled = !busy
                ) { Text("Экспортировать резервный ZIP") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = acknowledged,
                        onCheckedChange = { acknowledged = it },
                        enabled = !busy && ready
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "Я лично проверил, что это тот же файл, и подтверждаю замену временного UUID.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val previousId = oldId
                    val candidateId = newId
                    if (previousId != null && candidateId != null) {
                        onConfirmed(previousId, candidateId)
                    }
                },
                enabled = !busy && ready && acknowledged &&
                    backupVerifiedForCurrentSnapshot
            ) { Text("Подтвердить восстановление") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") }
        }
    )
}

private fun refusalText(reason: ConfirmedRetiredMediaRebind.Refusal?): String = when (reason) {
    null -> "выбери обе записи"
    ConfirmedRetiredMediaRebind.Refusal.NONE -> "готово"
    ConfirmedRetiredMediaRebind.Refusal.MEDIA_TYPE_MISMATCH -> "разный тип медиа"
    ConfirmedRetiredMediaRebind.Refusal.EVIDENCE_UNAVAILABLE -> "недостаточно метаданных или URI"
    ConfirmedRetiredMediaRebind.Refusal.SIZE_MISMATCH -> "разный размер"
    ConfirmedRetiredMediaRebind.Refusal.DURATION_MISMATCH -> "разная длительность"
    ConfirmedRetiredMediaRebind.Refusal.CANDIDATE_HAS_LYRICS -> "у нового файла есть свой текст"
    ConfirmedRetiredMediaRebind.Refusal.CANDIDATE_IN_PLAYLIST -> "новый файл находится в плейлисте"
    ConfirmedRetiredMediaRebind.Refusal.CANDIDATE_URI_COLLISION -> "URI уже используется"
    ConfirmedRetiredMediaRebind.Refusal.CANDIDATE_SOURCE_COLLISION -> "ID источника уже используется"
    else -> "конфликт идентификаторов"
}
