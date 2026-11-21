package com.example.freelocalplayer.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.freelocalplayer.data.ConfirmedRetiredMediaRebind
import com.example.freelocalplayer.ui.RetiredMediaRelinkDialog

/**
 * Available ONLY in the separate QA build type (applicationId ends with .qa).
 * It renders the real production relink dialog against fake, in-memory records.
 * No LibraryStore, Room, MediaStore query or SAF I/O is ever invoked here.
 */
class RelinkQaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.example.freelocalplayer.qa") {
            "QA_DIALOG_MUST_NEVER_RUN_IN_PRODUCTION_PACKAGE"
        }
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RelinkQaScreen()
                }
            }
        }
    }
}

@Composable
private fun RelinkQaScreen() {
    var catalog by remember { mutableStateOf(RelinkQaFixture.create()) }
    var dialogOpen by remember { mutableStateOf(false) }
    var fakeZipChecked by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(
            "Готово: один архивный UUID, один подходящий новый UUID и " +
                "один кандидат с неверным размером."
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "FreeLocalPlayer — QA 0-B.3.4",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            "ИЗОЛИРОВАННАЯ СИМУЛЯЦИЯ. Все треки, UUID и метаданные вымышленные. " +
                "Реальные MediaStore-файлы не открываются. Кнопка ZIP только " +
                "имитирует успешный экспорт; архив не создаётся. " +
                "Ни LibraryStore, ни основные настройки плеера не изменяются.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "Пакет QA: com.example.freelocalplayer.qa. " +
                "Рабочий пакет: com.example.freelocalplayer. " +
                "Их внутренние каталоги Android изолированы.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(
            onClick = { dialogOpen = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Открыть настоящий диалог восстановления")
        }
        OutlinedButton(
            onClick = {
                catalog = RelinkQaFixture.create()
                fakeZipChecked = false
                dialogOpen = false
                status = "Симуляция сброшена; реальные данные не затронуты."
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сбросить синтетический сценарий")
        }
        Text("Состояние: $status")
        Text("Синтетический ZIP разрешён: $fakeZipChecked")
        Text("Архивных UUID: ${catalog.retiredMedia.size}")
        Text("Живых синтетических треков: ${catalog.media.size}")
        Text(
            "Проверка UX: выбери архивную запись, затем неверный размер; " +
                "после этого подходящего кандидата. До отметки подтверждения " +
                "и имитации ZIP кнопка восстановления должна оставаться неактивной.",
            style = MaterialTheme.typography.bodySmall
        )
        if (dialogOpen) {
            RetiredMediaRelinkDialog(
                library = catalog,
                backupVerifiedForCurrentSnapshot = fakeZipChecked,
                busy = false,
                onExportBackup = {
                    // Deliberate UI simulation only, no SAF picker or file writes.
                    fakeZipChecked = true
                    status = "Синтетический ZIP: условие только смоделировано."
                },
                onConfirmed = { oldUuid, candidateUuid ->
                    if (!fakeZipChecked) {
                        status = "Отказ: симуляция ZIP не выполнена."
                    } else {
                        val result = runCatching {
                            ConfirmedRetiredMediaRebind.confirmed(
                                state = catalog,
                                oldUuid = oldUuid,
                                candidateUuid = candidateUuid,
                                userConfirmed = true
                            )
                        }
                        result.onSuccess { revised ->
                            catalog = revised
                            fakeZipChecked = false
                            dialogOpen = false
                            val oldLive = revised.media.any { it.id == RelinkQaFixture.OLD_UUID }
                            val newAbsent = revised.media.none { it.id == RelinkQaFixture.NEW_UUID }
                            val lyricsRetained = revised.media
                                .firstOrNull { it.id == RelinkQaFixture.OLD_UUID }
                                ?.lyrics == RelinkQaFixture.SAVED_LYRICS
                            val playlistRetained = revised.playlists
                                .firstOrNull { it.id == RelinkQaFixture.PLAYLIST_ID }
                                ?.mediaIds?.contains(RelinkQaFixture.OLD_UUID) == true
                            status = if (
                                oldLive && newAbsent &&
                                lyricsRetained && playlistRetained &&
                                revised.retiredMedia.isEmpty()
                            ) {
                                "SIMULATED_RELINK=PASS: UUID, текст и плейлист."
                            } else {
                                "SIMULATED_RELINK=FAILED: инварианты не совпали."
                            }
                        }.onFailure { error ->
                            status = "Симулированная операция отклонена: " +
                                (error.message ?: "неизвестная ошибка")
                        }
                    }
                },
                onDismiss = { dialogOpen = false }
            )
        }
    }
}
