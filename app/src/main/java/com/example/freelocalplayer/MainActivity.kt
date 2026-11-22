package com.example.freelocalplayer

import android.content.ComponentName
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.freelocalplayer.backup.AppDataBackup
import com.example.freelocalplayer.data.LibraryState
import com.example.freelocalplayer.data.LibraryStore
import com.example.freelocalplayer.data.MediaEntry
import com.example.freelocalplayer.data.ConfirmedRetiredMediaRebind
import com.example.freelocalplayer.data.RelinkBackupReceipt
import com.example.freelocalplayer.media.DeviceMediaScanner
import com.example.freelocalplayer.media.MediaFormatSupport
import com.example.freelocalplayer.music.MusicDataRepository
import com.example.freelocalplayer.music.ChordCorrection
import com.example.freelocalplayer.music.AudioFileChordDetector
import com.example.freelocalplayer.playback.PlaybackService
import com.example.freelocalplayer.playback.PlaybackResumeStore
import com.example.freelocalplayer.ui.PlayerApp
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class MainActivity : ComponentActivity() {
    private lateinit var store: LibraryStore
    private lateinit var musicRepository: MusicDataRepository
    private val backupManager by lazy { AppDataBackup(this) }
    private var backupBusy by mutableStateOf(false)
    // A verified ZIP authorizes re-linking only while dependent data is unchanged.
    private var relinkBackupReceipt by mutableStateOf<RelinkBackupReceipt?>(null)
    private var sidecarWritesInFlight by mutableIntStateOf(0)
    private var relinkBusy by mutableStateOf(false)
    private var relinkSuccessCounter by mutableIntStateOf(0)
    private var musicRevision by mutableStateOf(0)
    private var analyzingMediaId by mutableStateOf<String?>(null)
    private var audioAnalysisPercent by mutableIntStateOf(0)
    private var audioAnalysisJob: Job? = null
    private val libraryPreferences by lazy { getSharedPreferences("player_shortcuts_v1", MODE_PRIVATE) }
    private var recentIds by mutableStateOf<List<String>>(emptyList())
    private var favoriteIds by mutableStateOf<Set<String>>(emptySet())
    private var launcherDestination by mutableStateOf("")
    private var launcherRequestId by mutableIntStateOf(0)
    private var pendingResumeShortcut = false

    private fun currentRelinkBackupIsValid(): Boolean =
        relinkBackupReceipt?.matches(
            library, musicRevision, recentIds, favoriteIds, sidecarWritesInFlight
        ) == true

    private companion object {
        const val SHORTCUT_RESUME = "com.example.freelocalplayer.RESUME"
        const val SHORTCUT_PLAYLISTS = "com.example.freelocalplayer.PLAYLISTS"
        const val SHORTCUT_RECENT = "com.example.freelocalplayer.RECENT"
        const val SHORTCUT_FAVORITES = "com.example.freelocalplayer.FAVORITES"
    }

    private var selectedMidiMediaId: String? = null
    private var selectedChordsMediaId: String? = null

    private val importMidiPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val mediaId = selectedMidiMediaId
        selectedMidiMediaId = null
        if (uri != null && mediaId != null) importMidiScore(mediaId, uri)
    }

    private val importChordsPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val mediaId = selectedChordsMediaId
        selectedChordsMediaId = null
        if (uri != null && mediaId != null) importChordChart(mediaId, uri)
    }
    private var library by mutableStateOf(LibraryState())
    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var selectedTextTrackId: String? = null
    private var scanning by mutableStateOf(false)
    private var deviceScanning by mutableStateOf(false)
    private var playerReady = false
    private var initialScanHandled = false
    private var lastDeviceScanAt: Long = 0L
    private val permissionPreferences by lazy { getSharedPreferences("media_discovery_v1", MODE_PRIVATE) }

    private val mediaPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (DeviceMediaScanner.canReadAny(this)) scanPhone(force = true)
        else toast("Доступ не предоставлен. Файлы можно добавить вручную.")
    }

    private val addFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) importFiles(uris)
    }

    private val addFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) importFolder(treeUri)
    }

    private val addLyricsFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val mediaId = selectedTextTrackId
        selectedTextTrackId = null
        if (uri != null && mediaId != null) importLyrics(mediaId, uri)
    }

    private val createBackupPicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) exportBackup(uri) }

    private val importBackupPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) inspectBackupForRestore(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (backupManager.hasInterruptedRestore()) {
            setContent { androidx.compose.material3.Text("Восстанавливаю данные после сбоя…") }
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        LibraryStore(applicationContext).awaitPendingWrites()
                        backupManager.recoverPendingRestoreIfAny()
                    }
                    toast("Данные возвращены из защитной копии после сбоя")
                    initializePlayer()
                } catch (error: Exception) {
                    toast("Защитный откат не удался, данные нельзя изменять: ${error.message}")
                    finish()
                }
            }
            return
        }
        initializePlayer()
    }

    private fun initializePlayer() {
        store = LibraryStore(applicationContext)
        musicRepository = MusicDataRepository(this)
        setContent { androidx.compose.material3.Text("Загружаю медиатеку…") }
        lifecycleScope.launch {
            try {
                // Include writes from a previous Activity instance before reading JSON.
                val loaded = withContext(Dispatchers.IO) {
                    store.awaitPendingWrites()
                    store.load()
                }
                library = loaded
                recentIds = libraryPreferences.getString("recent_ids", "")
            .orEmpty().split('|').filter { it.isNotBlank() }.distinct().take(50)
        favoriteIds = libraryPreferences.getStringSet("favorite_ids", emptySet())
            .orEmpty().toSet()
        handleLauncherShortcut(intent)

        connectPlayer()
        setContent {
            PlayerApp(
                library = library,
                controller = controller,
                scanning = scanning || deviceScanning,
                onScanPhone = { discoverDeviceMedia() },
                musicRepository = musicRepository,
                musicRevision = musicRevision,
                analyzingMediaId = analyzingMediaId,
                audioAnalysisPercent = audioAnalysisPercent,
                onAnalyzeChords = ::analyzeChords,
                onCancelAnalyze = { audioAnalysisJob?.cancel() },
                onDeleteAutoChords = { mediaId ->
                    if (!relinkBusy) {
                        sidecarWritesInFlight++
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    musicRepository.deleteAudioEstimate(mediaId)
                                }
                                musicRevision++
                            } catch (error: Exception) {
                                toast("Ошибка удаления анализа: " + error.message)
                            } finally {
                                sidecarWritesInFlight--
                            }
                        }
                    }
                },
                launcherDestination = launcherDestination,
                launcherRequestId = launcherRequestId,
                recentIds = recentIds,
                favoriteIds = favoriteIds,
                onToggleFavorite = ::toggleFavorite,
                onImportMidiScore = { mediaId ->
                    selectedMidiMediaId = mediaId
                    importMidiPicker.launch(arrayOf("audio/midi", "audio/x-midi", "application/octet-stream", "*/*"))
                },
                onImportChordChart = { mediaId ->
                    selectedChordsMediaId = mediaId
                    importChordsPicker.launch(arrayOf("text/plain", "*/*"))
                },
                onSaveChordChart = { mediaId, chart, onResult ->
                    writeMusicSidecarConfirmed(mediaId, onResult) {
                        "РЎРѕС…СЂР°РЅРµРЅРѕ Р°РєРєРѕСЂРґРѕРІ: ${musicRepository.saveChords(mediaId, chart)}"
                    }
                },
                onSaveChordCorrection = { mediaId, edit, onResult ->
                    writeMusicSidecarConfirmed(mediaId, onResult) {
                        musicRepository.saveChordCorrection(mediaId, edit)
                        "РСЃРїСЂР°РІР»РµРЅРёРµ Р°РєРєРѕСЂРґР° СЃРѕС…СЂР°РЅРµРЅРѕ"
                    }
                },
                onRemoveChordCorrection = { mediaId, chordSource, timeMs, onResult ->
                    writeMusicSidecarConfirmed(mediaId, onResult) {
                        musicRepository.removeChordCorrection(mediaId, chordSource, timeMs)
                        "РСЃРїСЂР°РІР»РµРЅРёРµ РѕС‚РјРµРЅРµРЅРѕ"
                    }
                },
                onMusicOffsetChange = { mediaId, value ->
                    if (!relinkBusy) {
                        musicRepository.saveOffset(mediaId, value)
                        musicRevision++
                    }
                },
                onDeleteMidiScore = { mediaId ->
                    if (!relinkBusy) {
                        sidecarWritesInFlight++
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    musicRepository.deleteScore(mediaId)
                                }
                                musicRevision++
                            } catch (error: Exception) {
                                toast("Ошибка удаления MIDI: " + error.message)
                            } finally {
                                sidecarWritesInFlight--
                            }
                        }
                    }
                },
                onImportFiles = { addFiles.launch(arrayOf("*/*")) },
                onImportFolder = { addFolder.launch(null) },
                onImportLyrics = { id ->
                    selectedTextTrackId = id
                    addLyricsFile.launch(arrayOf("*/*"))
                },
                onSaveLyrics = { id, text ->
                    changeLibrary(savedMessage = "Текст сохранён") { it.updateLyrics(id, text) }
                },
                onSaveLyricsConfirmed = ::saveLyricsWithReceipt,
                onPlay = ::play,
                onNewPlaylist = { name ->
                    changeLibrary(savedMessage = "Плейлист сохранён") { it.createPlaylist(name) }
                },
                onDeletePlaylist = { id ->
                    changeLibrary(savedMessage = "Плейлист сохранён") { it.deletePlaylist(id) }
                },
                onAddToPlaylist = { playlist, media ->
                    changeLibrary(savedMessage = "Плейлист сохранён") { it.addToPlaylist(playlist, media) }
                },
                onAddManyToPlaylist = { playlist, mediaIds ->
                    changeLibrary(savedMessage = "Плейлист сохранён") {
                        it.addManyToPlaylist(playlist, mediaIds)
                    }
                },
                onRemoveFromPlaylist = { playlist, media ->
                    changeLibrary(savedMessage = "Плейлист сохранён") {
                        it.removeFromPlaylist(playlist, media)
                    }
                },
                onMoveTrack = { playlist, media, direction ->
                    changeLibrary(savedMessage = "Плейлист сохранён") {
                        it.moveInPlaylist(playlist, media, direction)
                    }
                },
                onRemoveMedia = { id -> changeLibrary { it.removeMedia(id) } },
                onSpeedChange = { speed ->
                    store.setSpeed(speed)
                    controller?.setPlaybackSpeed(speed)
                },
                onShuffleChange = { enabled ->
                    store.setShuffle(enabled)
                    controller?.shuffleModeEnabled = enabled
                },
                onRepeatChange = { mode ->
                    store.setRepeatMode(mode)
                    controller?.repeatMode = mode
                },
                onExportBackup = {
                    if (!backupBusy && !relinkBusy) createBackupPicker.launch(
                        "FreeLocalPlayer-backup-${System.currentTimeMillis()}.zip"
                    )
                },
                onImportBackup = {
                    if (!backupBusy && !relinkBusy) importBackupPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                },
                backupBusy = backupBusy,
                relinkBackupReady = currentRelinkBackupIsValid(),
                relinkBusy = relinkBusy,
                relinkSuccessCounter = relinkSuccessCounter,
                onRelinkConfirmed = ::confirmManualRelink
            )
        }
                playerReady = true
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    scanAfterResumeIfReady()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Never allow a failed JSON load to become an empty, writable library.
                playerReady = false
                setContent {
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.material3.Text(
                            "Не удалось загрузить медиатеку. Данные не удалены. " +
                            "Не очищай приложение. Причина: ${error.message}"
                        )
                        androidx.compose.material3.Button(onClick = {
                            importBackupPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                        }) {
                            androidx.compose.material3.Text("Восстановить из резервного ZIP")
                        }
                    }
                }
            }
        }
    }

    private fun exportBackup(uri: Uri) {
        if (backupBusy || relinkBusy) return
        if (analyzingMediaId != null || scanning || deviceScanning ||
            sidecarWritesInFlight != 0) {
            toast("Заверши анализ, MIDI, аккорды и сканирование перед экспортом")
            return
        }
        backupBusy = true
        relinkBackupReceipt = null
        val receiptAtStart = RelinkBackupReceipt.capture(
            library, musicRevision, recentIds, favoriteIds
        )
        lifecycleScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    store.awaitPendingWrites()
                    backupManager.exportTo(uri)
                }
                if (receiptAtStart.matches(
                        library, musicRevision, recentIds, favoriteIds,
                        sidecarWritesInFlight
                    )) {
                    relinkBackupReceipt = receiptAtStart
                }
                toast("ZIP сохранён и проверен: ${summary.tracks} файлов медиатеки, ${summary.playlists} списков")
            } catch (error: Exception) {
                toast("Экспорт не подтверждён: ${error.message}")
            } finally {
                backupBusy = false
            }
        }
    }

    /**
     * Verify the currently indexed candidate, not merely stale metadata from
     * the time the list was built. A failed query or unreadable URI fails closed.
     * Called from Dispatchers.IO.
     */
    private fun verifyCurrentRelinkMedia(candidate: MediaEntry) {
        val uri = Uri.parse(candidate.uri)
        require(uri.scheme == "content" && uri.authority == "media") {
            "Ручное восстановление поддерживает только MediaStore URI"
        }
        val durationColumn = if (candidate.isVideo) {
            MediaStore.Video.Media.DURATION
        } else {
            MediaStore.Audio.Media.DURATION
        }
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            durationColumn
        )
        val valid = contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use false
            val title = cursor.getString(
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
            val size = cursor.getLong(
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
            val duration = cursor.getLong(cursor.getColumnIndexOrThrow(durationColumn))
            title == candidate.title &&
                size > 0L && size == candidate.sizeBytes &&
                duration > 0L && duration == candidate.durationMs
        } ?: false
        if (!valid) {
            throw IOException("Файл или метаданные изменились после сканирования. Обнови медиатеку.")
        }
        contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            if (descriptor.statSize > 0L && descriptor.statSize != candidate.sizeBytes) {
                throw IOException("Размер открытого файла отличается от данных сканирования")
            }
        } ?: throw IOException("Нет доступа к выбранному медиафайлу")
    }

    /**
     * Opt-in recovery: never writes before a verified SAF ZIP exists for this
     * exact in-memory library snapshot. No changes to candidate UUID assets.
     */
    private fun candidateHasPlaybackCheckpoint(mediaId: String): Boolean =
        PlaybackResumeStore.forApplication(applicationContext).load()?.mediaId == mediaId

    private fun candidateIsQueued(mediaId: String): Boolean {
        val player = controller ?: return false
        return (0 until player.mediaItemCount).any { index ->
            player.getMediaItemAt(index).mediaId == mediaId
        }
    }

    private fun confirmManualRelink(oldUuid: String, candidateUuid: String) {
        if (relinkBusy || backupBusy || scanning || deviceScanning ||
            analyzingMediaId != null || sidecarWritesInFlight != 0) {
            toast("Дождись окончания текущих операций перед восстановлением")
            return
        }
        val before = library
        if (!currentRelinkBackupIsValid()) {
            toast("Сначала экспортируй проверенный ZIP текущей медиатеки")
            return
        }
        val preview = ConfirmedRetiredMediaRebind.preview(before, oldUuid, candidateUuid)
        if (preview != ConfirmedRetiredMediaRebind.Refusal.NONE) {
            toast("Связь не подтверждена: " + preview.name)
            return
        }
        if (candidateUuid in recentIds || candidateUuid in favoriteIds ||
            candidateHasPlaybackCheckpoint(candidateUuid)) {
            toast("У нового UUID есть история или избранное; автоматическое слияние запрещено")
            return
        }
        // A running Media3 queue can still reference the candidate UUID.
        // No active playback/queue may be invalidated by an identity change.
        if (candidateIsQueued(candidateUuid)) {
            toast("Файл находится в очереди воспроизведения. Освободи очередь и повтори.")
            return
        }
        val candidate = before.media.firstOrNull { it.id == candidateUuid } ?: return
        relinkBusy = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    store.awaitPendingWrites()
                    if (musicRepository.hasStoredDataForUuid(candidateUuid)) {
                        throw IOException("У нового UUID есть MIDI, аккорды, исправления или смещение")
                    }
                    verifyCurrentRelinkMedia(candidate)
                }
                if (library !== before || !currentRelinkBackupIsValid()) {
                    throw IOException("Медиатека изменилась во время проверки; нужен новый ZIP")
                }
                if (candidateUuid in recentIds || candidateUuid in favoriteIds ||
                    candidateHasPlaybackCheckpoint(candidateUuid) ||
                    candidateIsQueued(candidateUuid)) {
                    throw IOException("У кандидата появились пользовательские привязки или очередь")
                }
                val updated = ConfirmedRetiredMediaRebind.confirmed(
                    before, oldUuid, candidateUuid, userConfirmed = true)
                // No optimistic UI success: only display relinked state after the
                // canonical JSON writer acknowledges its synchronous commit.
                store.saveConfirmed(updated) { outcome ->
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) {
                            if (outcome.isSuccess) {
                                library = updated
                                relinkBackupReceipt = null
                                relinkSuccessCounter++
                                toast("Старый UUID и пользовательские данные восстановлены")
                            } else {
                                toast("Связь не сохранена: " +
                                    outcome.exceptionOrNull()?.message)
                            }
                        }
                        relinkBusy = false
                    }
                }
            } catch (cancelled: CancellationException) {
                relinkBusy = false
                throw cancelled
            } catch (error: Exception) {
                relinkBusy = false
                toast("Восстановление остановлено без изменения библиотеки: " + error.message)
            }
        }
    }

    private fun inspectBackupForRestore(uri: Uri) {
        if (backupBusy || relinkBusy) return
        backupBusy = true
        lifecycleScope.launch {
            var staged: java.io.File? = null
            try {
                val (file, summary) = withContext(Dispatchers.IO) { backupManager.stageFromUri(uri) }
                staged = file
                val compatibility = if (summary.sourcePackage == packageName) "" else
                    "\nВ архиве другой пакет приложения: ${summary.sourcePackage}. " +
                    "Доступ к отдельным медиафайлам может потребоваться выдать снова."
                android.app.AlertDialog.Builder(this@MainActivity)
                    .setTitle("Проверенный резервный ZIP")
                    .setMessage("Файлов в каталоге: ${summary.tracks}; плейлистов: ${summary.playlists}; " +
                        "дополнительных MIDI/аккордовых файлов: ${summary.musicFiles}." +
                        "\nВсе контрольные суммы совпали." +
                        "\nВНИМАНИЕ: импорт ЗАМЕНИТ текущие данные, " +
                        "предварительно сохранив защитную копию внутри приложения." + compatibility)
                    .setNegativeButton("Отмена") { _, _ -> file.delete() }
                    .setPositiveButton("Восстановить") { _, _ -> restoreBackup(file) }
                    .setOnCancelListener { file.delete() }
                    .show()
                staged = null // Dialog owns the staged snapshot until user's decision.
            } catch (error: Exception) {
                toast("Архив отклонён без изменений: ${error.message}")
            } finally {
                staged?.delete()
                backupBusy = false
            }
        }
    }

    private fun restoreBackup(staged: java.io.File) {
        if (backupBusy || relinkBusy) { staged.delete(); return }
        if (analyzingMediaId != null || scanning || deviceScanning) {
            toast("Дождись завершения анализа и сканирования перед импортом")
            staged.delete()
            return
        }
        backupBusy = true
        controller?.pause()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    store.awaitPendingWrites()
                    backupManager.restoreVerified(staged)
                }
                controller?.clearMediaItems()
                toast("Данные восстановлены. Открываю обновлённую библиотеку")
                recreate()
            } catch (error: Exception) {
                toast("Импорт остановлен: ${error.message}. Проверь сообщения, не удаляй приложение")
            } finally {
                staged.delete()
                backupBusy = false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLauncherShortcut(intent)
    }

    private fun handleLauncherShortcut(shortcut: Intent?) {
        when (shortcut?.action) {
            SHORTCUT_RESUME -> {
                if (controller == null) pendingResumeShortcut = true
                else resumeFromShortcut()
            }
            SHORTCUT_PLAYLISTS -> navigateFromShortcut("playlists")
            SHORTCUT_RECENT -> navigateFromShortcut("recent")
            SHORTCUT_FAVORITES -> navigateFromShortcut("favorites")
        }
    }

    private fun navigateFromShortcut(destination: String) {
        launcherDestination = destination
        launcherRequestId++
    }

    private fun resumeFromShortcut() {
        val player = controller ?: run { pendingResumeShortcut = true; return }
        if (player.mediaItemCount > 0) {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
            return
        }
        val candidate = recentIds.firstOrNull { id -> library.media.any { it.id == id } }
        if (candidate == null) {
            toast("Нет недавно воспроизводившихся файлов")
            return
        }
        play(candidate, "all")
    }

    private fun toggleFavorite(mediaId: String) {
        if (relinkBusy) return
        favoriteIds = if (mediaId in favoriteIds) favoriteIds - mediaId else favoriteIds + mediaId
        libraryPreferences.edit().putStringSet("favorite_ids", favoriteIds.toSet()).apply()
    }

    override fun onPostResume() {
        super.onPostResume()
        scanAfterResumeIfReady()
    }

    private fun scanAfterResumeIfReady() {
        if (!playerReady || backupBusy || relinkBusy) return
        if (!initialScanHandled) {
            initialScanHandled = true
            if (DeviceMediaScanner.canReadAny(this)) {
                scanPhone(force = true)
            } else if (!permissionPreferences.getBoolean("asked_once", false)) {
                permissionPreferences.edit().putBoolean("asked_once", true).apply()
                mediaPermissions.launch(DeviceMediaScanner.requiredPermissions())
            }
        } else if (DeviceMediaScanner.canReadAny(this) &&
            SystemClock.elapsedRealtime() - lastDeviceScanAt > 60_000L
        ) {
            scanPhone(force = false)
        }
    }

    private fun discoverDeviceMedia() {
        if (DeviceMediaScanner.canReadAny(this)) {
            scanPhone(force = true)
        } else {
            permissionPreferences.edit().putBoolean("asked_once", true).apply()
            mediaPermissions.launch(DeviceMediaScanner.requiredPermissions())
        }
    }

    private fun scanPhone(force: Boolean) {
        if (backupBusy || relinkBusy || deviceScanning || !DeviceMediaScanner.canReadAny(this)) return
        if (!force && SystemClock.elapsedRealtime() - lastDeviceScanAt < 60_000L) return
        deviceScanning = true
        lastDeviceScanAt = SystemClock.elapsedRealtime()
        lifecycleScope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    DeviceMediaScanner.scan(this@MainActivity)
                }
                val additions = mergeScannedMedia(found)
                if (force || additions != 0) {
                    toast("Обнаружено: ${found.size}; новых: $additions")
                }
            } catch (error: Exception) {
                toast("Не удалось прочитать медиатеку: ${error.message}")
            } finally {
                deviceScanning = false
            }
        }
    }

    private fun persistLibrarySnapshot(next: LibraryState, onResult: ((Boolean) -> Unit)? = null) {
        // UI success is acknowledged only after SharedPreferences.commit() succeeds.
        // JSON serialization and all disk I/O remain on the dedicated writer thread.
        try {
            store.saveConfirmed(next) { outcome ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        if (outcome.isSuccess) {
                            onResult?.invoke(true)
                        } else {
                            val error = outcome.exceptionOrNull()
                            toast("Ошибка сохранения медиатеки: ${error?.message}. Правка не подтверждена.")
                            onResult?.invoke(false)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            toast("Ошибка постановки записи в очередь: ${error.message}")
            onResult?.invoke(false)
        }
    }

    private fun saveLyricsWithReceipt(id: String, text: String, onResult: (Boolean) -> Unit) {
        if (relinkBusy) {
            onResult(false)
            return
        }
        if (library.media.none { it.id == id }) {
            toast("Песня не найдена. Текст не сохранён")
            onResult(false)
            return
        }
        val next = library.updateLyrics(id, text)
        library = next
        // Even an unchanged retry is committed and acknowledged, rather than
        // incorrectly treating an in-memory state as a durable disk write.
        persistLibrarySnapshot(next) { persisted ->
            if (persisted) toast("Текст сохранён")
            onResult(persisted)
        }
    }

    private fun changeLibrary(
        savedMessage: String? = null,
        change: (LibraryState) -> LibraryState
    ) {
        if (relinkBusy) {
            toast("Идёт безопасное восстановление связи трека")
            return
        }
        val next = change(library)
        if (library != next) {
            library = next
            persistLibrarySnapshot(next) { persisted ->
                if (persisted && savedMessage != null) toast(savedMessage)
            }
        }
    }

    /** Merge thousands of scanner results off-main; retry if the user edited state meanwhile. */
    private suspend fun mergeScannedMedia(found: List<MediaEntry>): Int {
        if (relinkBusy) throw IOException("Восстановление связи выполняется. Повтори сканирование позже.")
        repeat(4) {
            val previous = library
            val next = withContext(Dispatchers.Default) { previous.addMedia(found) }
            // UI state is only changed after returning to the main dispatcher.
            if (library === previous) {
                if (previous != next) {
                    library = next
                    persistLibrarySnapshot(next)
                }
                return next.media.size - previous.media.size
            }
        }
        throw IOException("Медиатека была изменена во время сканирования. Повтори поиск файлов.")
    }

    private fun connectPlayer() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val connected = future.get()
                controller = connected
                if (pendingResumeShortcut) {
                    pendingResumeShortcut = false
                    resumeFromShortcut()
                }
                if (connected.mediaItemCount == 0) {
                    connected.setPlaybackSpeed(store.getSpeed())
                    connected.shuffleModeEnabled = store.getShuffle()
                    connected.repeatMode = store.getRepeatMode().coerceIn(
                        Player.REPEAT_MODE_OFF,
                        Player.REPEAT_MODE_ALL
                    )
                }
            } catch (exception: Exception) {
                Toast.makeText(this, "Ошибка подключения плеера: ${exception.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun play(entryId: String, source: String) {
        if (relinkBusy) return
        val player = controller ?: run {
            toast("Плеер ещё подключается")
            return
        }
        val byId = library.media.associateBy { it.id }
        val queue = when (source) {
            "recent" -> recentIds.mapNotNull(byId::get)
            "favorites" -> library.media.filter { it.id in favoriteIds }
            else -> library.tracksFor(source)
        }
        val index = queue.indexOfFirst { it.id == entryId }
        if (index < 0) return

        val sameQueue = player.mediaItemCount == queue.size && queue.indices.all { i ->
            player.getMediaItemAt(i).mediaId == queue[i].id
        }
        if (sameQueue) {
            player.seekToDefaultPosition(index)
        } else {
            val items = queue.map { entry ->
                val builder = MediaItem.Builder()
                    .setMediaId(entry.id)
                    .setUri(Uri.parse(entry.uri))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.title).build())
                // SAF content:// URIs may hide the filename and report a generic MIME type.
                // Give ExoPlayer an explicit WAV hint for such documents.
                if (MediaFormatSupport.isWave(entry.title)) builder.setMimeType("audio/wav")
                builder.build()
            }
            player.setMediaItems(items, index, C.TIME_UNSET)
            player.prepare()
        }
        player.play()
        recentIds = (listOf(entryId) + recentIds.filterNot { it == entryId }).take(50)
        libraryPreferences.edit().putString("recent_ids", recentIds.joinToString("|")).apply()
    }

    private fun persistReadPermission(uri: Uri): Boolean {
        return try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (exception: SecurityException) {
            // Don't silently keep inaccessible media that would break after restarting.
            false
        }
    }

    private fun importFiles(uris: List<Uri>) {
        val entries = mutableListOf<MediaEntry>()
        var unavailable = 0
        uris.forEach { uri ->
            if (!persistReadPermission(uri)) {
                unavailable++
                return@forEach
            }
            val name = getDisplayName(uri)
            val video = isVideo(name, contentResolver.getType(uri))
            if (video != null) entries += MediaEntry(uri = uri.toString(), title = name, isVideo = video)
        }
        val oldCount = library.media.size
        changeLibrary { it.addMedia(entries) }
        toast("Добавлено файлов: ${library.media.size - oldCount}" + if (unavailable > 0) "; без доступа: $unavailable" else "")
    }

    private fun importFolder(uri: Uri) {
        if (scanning) return
        if (!persistReadPermission(uri)) {
            toast("Нет постоянного доступа к выбранной папке")
            return
        }
        scanning = true
        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    val root = DocumentFile.fromTreeUri(this@MainActivity, uri)
                    val found = mutableListOf<MediaEntry>()
                    val directoryQueue = ArrayDeque<DocumentFile>()
                    if (root != null) directoryQueue.add(root)
                    var visited = 0
                    while (directoryQueue.isNotEmpty() && visited < 10000 && found.size < 5000) {
                        val directory = directoryQueue.removeFirst()
                        val children = try { directory.listFiles() } catch (_: Exception) { emptyArray() }
                        children.forEach { child ->
                            if (++visited > 10000 || found.size >= 5000) return@forEach
                            if (child.isDirectory) directoryQueue.add(child)
                            else if (child.isFile) {
                                val name = child.name ?: "Файл"
                                val video = isVideo(name, child.type)
                                if (video != null) found += MediaEntry(
                                    uri = child.uri.toString(), title = name, isVideo = video
                                )
                            }
                        }
                    }
                    found
                }
                val additions = mergeScannedMedia(results)
                toast("Из папки добавлено: $additions")
            } catch (exception: Exception) {
                toast("Не удалось открыть папку: ${exception.message}")
            } finally {
                scanning = false
            }
        }
    }

    private fun importLyrics(mediaId: String, uri: Uri) {
        lifecycleScope.launch {
            try {
                val name = getDisplayName(uri).lowercase()
                if (!name.endsWith(".txt") && !name.endsWith(".lrc")) {
                    toast("Для текста выбери файл .txt или .lrc")
                    return@launch
                }
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        val buffer = CharArray(8192)
                        val result = StringBuilder()
                        while (result.length <= 1_000_000) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            result.append(buffer, 0, count)
                        }
                        if (result.length > 1_000_000) throw IOException("Файл больше 1 МБ")
                        result.toString().removePrefix("\uFEFF")
                    } ?: throw IOException("Не получилось прочитать файл")
                }
                changeLibrary { it.updateLyrics(mediaId, text) }
                toast("Текст добавлен")
            } catch (exception: Exception) {
                toast("Ошибка чтения текста: ${exception.message}")
            }
        }
    }

    private fun analyzeChords(entry: MediaEntry) {
        if (relinkBusy) {
            toast("Дождись завершения восстановления связи трека")
            return
        }
        if (analyzingMediaId != null) {
            toast("Подожди завершения предыдущего анализа или отмени его")
            return
        }
        analyzingMediaId = entry.id
        audioAnalysisPercent = 0
        audioAnalysisJob = lifecycleScope.launch {
            try {
                val currentJob = currentCoroutineContext()
                val result = withContext(Dispatchers.IO) {
                    AudioFileChordDetector.analyze(
                        context = this@MainActivity,
                        uri = Uri.parse(entry.uri),
                        progress = { percent ->
                            if (percent % 5 == 0) runOnUiThread { audioAnalysisPercent = percent }
                        },
                        keepGoing = { currentJob.isActive }
                    ).also { cues -> musicRepository.saveAudioEstimate(entry.id, cues) }
                }
                musicRevision++
                toast("Оценка аккордов: ${result.size} временных отметок. Проверь результат на слух")
            } catch (_: CancellationException) {
                toast("Анализ отменён")
            } catch (error: Exception) {
                toast("Ошибка анализа: ${error.message}")
            } finally {
                analyzingMediaId = null
                audioAnalysisPercent = 0
                audioAnalysisJob = null
            }
        }
    }

    private fun importMidiScore(mediaId: String, uri: Uri) {
        if (relinkBusy) return
        sidecarWritesInFlight++
        lifecycleScope.launch {
            try {
                val score = withContext(Dispatchers.IO) { musicRepository.importMidi(mediaId, uri) }
                musicRevision++
                toast("MIDI: ${score.parts.size} партий, ${score.noteCount} нот")
            } catch (error: Exception) {
                toast("Ошибка MIDI: " + error.message)
            } finally {
                sidecarWritesInFlight--
            }
        }
    }

    private fun importChordChart(mediaId: String, uri: Uri) {
        if (relinkBusy) return
        sidecarWritesInFlight++
        lifecycleScope.launch {
            try {
                val count = withContext(Dispatchers.IO) { musicRepository.importChords(mediaId, uri) }
                musicRevision++
                toast("Добавлено аккордов: $count")
            } catch (error: Exception) {
                toast("Ошибка аккордов: " + error.message)
            } finally {
                sidecarWritesInFlight--
            }
        }
    }

    /**
     * Confirms a chord file change only after the actual atomic file operation
     * completed. The callback always runs on the main thread for live UI sessions.
     */
    private fun writeMusicSidecarConfirmed(
        mediaId: String,
        onResult: (Boolean) -> Unit,
        operation: () -> String
    ) {
        if (relinkBusy) {
            toast("Р”РѕР¶РґРёСЃСЊ Р·Р°РІРµСЂС€РµРЅРёСЏ РІРѕСЃСЃС‚Р°РЅРѕРІР»РµРЅРёСЏ СЃРІСЏР·Рё С‚СЂРµРєР°")
            onResult(false)
            return
        }
        if (library.media.none { it.id == mediaId }) {
            toast("РўСЂРµРє Р±РѕР»СЊС€Рµ РЅРµ РЅР°С…РѕРґРёС‚СЃСЏ РІ РјРµРґРёР°С‚РµРєРµ. РџСЂР°РІРєР° РЅРµ СЃРѕС…СЂР°РЅРµРЅР°")
            onResult(false)
            return
        }
        sidecarWritesInFlight++
        lifecycleScope.launch {
            val result: Result<String> = try {
                Result.success(withContext(Dispatchers.IO) { operation() })
            } catch (error: Exception) {
                Result.failure<String>(error)
            } finally {
                sidecarWritesInFlight--
            }
            result.onSuccess { message ->
                musicRevision++
                toast(message)
            }.onFailure { error ->
                toast("РћС€РёР±РєР° СЃРѕС…СЂР°РЅРµРЅРёСЏ РјСѓР·С‹РєР°Р»СЊРЅС‹С… РґР°РЅРЅС‹С…: ${error.message}")
            }
            onResult(result.isSuccess)
        }
    }

    private fun getDisplayName(uri: Uri): String {
        var result: String? = null
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor: Cursor ->
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0 && cursor.moveToFirst()) result = cursor.getString(col)
            }
        } catch (_: Exception) {
            // Some document providers do not support metadata queries.
        }
        return result?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "Без названия"
    }

    private fun isVideo(name: String, mime: String?): Boolean? =
        MediaFormatSupport.isVideo(name, mime)

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}
