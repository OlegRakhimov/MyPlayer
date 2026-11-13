# FreeLocalPlayer — этап 0-C.2-R: проверка автономного ZIP-восстановления на Android

**Дата подготовки:** 13 ноября 2026 года. **Статус:** подготовлен код тестов и запускатель, но Android Gradle/Pixel_7 ещё не проверены. Не объявлять полную миграцию на Room выполненной.

## Цель и источник данных

Этап проверяет уже реализованный код `AppDataBackup` из 0-A (формат `FreeLocalPlayer-offline-backup`, версия 1), не меняя его, и проверяет сопоставимость восстановленного JSON с моделью Room из 0-C.2. Ввод пользователя: Room QA 0-C.2 на AVD Pixel_7 прошёл `ROOM_ANDROID_INSTRUMENTATION=PASS tests=4`, `ROOM_APP_SHADOW_OPEN=PASS`, `PHYSICAL_PHONE_CHANGED=NO` в 19:59, 13.11.2026.

В патче только новые **инструментальные тесты**, безопасный запускатель PowerShell и эта документация. `MainActivity`, `AppDataBackup`, `Room`, `LibraryStore`, `applicationId`, `versionCode=20`, `versionName=1.3.12`, debug-подпись и **все пользовательские данные** неизменны.

## Почему тесты изолированы

`BackupRestoreDeviceTest` использует `InstrumentationRegistry.getInstrumentation().targetContext` только как нижележащий Android Context. Новый `SandboxContext : ContextWrapper` перенаправляет:

- `filesDir` в новый случайный `files/flp-restore-qa-<UUID>/files`;
- `cacheDir` в соответствующий `cache` внутри этой случайной директории;
- обращения к пяти именам `SharedPreferences` — только к новым именам `__flp_restore_qa_<UUID>_<оригинальное имя>`;
- любые **другие** имена SharedPreferences блокируются `require(...)`.

Оригинальные `local_library_v1`, `player_shortcuts_v1`, `music_mode_offsets_v1`, `freelocalplayer_appearance`, `media_discovery_v1`, приватный `music_mode_v1` и постоянная база Room **не открываются и не заменяются**. Тесты не запускают `LibraryStore.load()` (иначе тот синхронизировал бы постоянную Room-базу). Вместо этого проверяют `ShadowRoomMapping.toRows(...).reconstruct()` без доступа к SQLite на диске. В `@After` тестовые prefs и файлы удаляются. При аварийной остановке могут остаться только тестовые префиксы; они не читаются плеером. В тестах не используется настоящий пользовательский ZIP, реальные названия файлов или тексты.

Сами тесты устанавливаются/выполняются **только на проверенном `Pixel_7`**; скрипт сверяет и serial `emulator-*`, и `ro.kernel.qemu=1`, и имя AVD. При запущенном другом AVD, например WorkTime117_QA, останавливается без установки. Скрипт не отправляет ни одной ADB-команды на телефон.

## Семь инструментальных тестов

1. `fullZipRoundTripRestoresUuidLyricsPlaylistsSettingsAndMusic`: экспорт настоящим `AppDataBackup.exportTo` через Android `ContentResolver` на тестовый `file://` URI; проверка ZIP, preview через `stageFromUri`, изменение всех пяти prefs и MIDI/аккордов, восстановление через `restoreVerified`; побайтная сверка всех сохранённых файлов, UUID, TXT/Enhanced LRC, списков, favorites, recent, offsets, разных типов настроек.
2. `checksumTamperIsRejectedWithoutChangingExistingData`: изменение `prefs.json` внутри ZIP при прежнем manifest; неверный SHA/размер отклоняются до записи.
3. `traversalEntryIsRejectedWithoutWritingOutsideSandbox`: добавление `../../outside-test.txt` к архиву и проверка отказа до записи.
4. `interruptedImportCanRecoverOriginalSnapshotAndClearMarker`: имитация журнала `flp-restore-in-progress` с действующим `flp-before-last-import.zip`; проверка восстановления исходных байтов и удаления журнала.
5. `missingSafetyArchivePreventsUnverifiedCrashRecovery`: при наличии журнала без защитной копии восстановление запрещено и текущие данные не изменяются.
6. `failedCommitDuringImportAutomaticallyRollsBackAllPreviousWrites`: test-only `SharedPreferences.Editor.commit()` один раз возвращает `false` на третьем хранилище **после** замены MIDI/аккордовых файлов и первых prefs; настоящий `restoreVerified` должен откатить все уже внесённые изменения до предшествующего снимка.
7. `restoredCatalogCanBeMappedToRoomWithoutChangingAnyUuid`: восстановленный JSON преобразуется в `LibraryState`/`ShadowRoomMapping` и обратно, с сохранением ID, TXT/LRC, playlist order и archived UUID. **Это не повторный тест настоящего SQLite**: реальный SQLite уже отдельно проверен четырьмя тестами 0-C.2-QA.

## Запуск

После применения пакета и успешной компиляции `:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --no-daemon`:

```powershell
$Project = 'C:\Users\Oleg\Documents\AndroidProjects\FreeLocalPlayer'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Project 'tools\Verify-FreeLocalPlayer-BackupRestore.ps1') -AvdName 'Pixel_7'
```

Ожидаются:

```text
BACKUP_DEVICE_IS_PIXEL_7_EMULATOR=PASS
GRADLE_QA_BUILD=PASS
EMULATOR_APKS_INSTALLED=PASS
BACKUP_RESTORE_INSTRUMENTATION=PASS tests=7
TEST_STORAGE_ISOLATED=PASS
PHYSICAL_PHONE_CHANGED=NO
RESULT=SUCCESS BACKUP_RESTORE_EMULATOR_QA_PASS
```

Логи: `<единственный проект>/.patch-backups/backup-restore-qa-0c2r-<дата>/report.txt` и `instrumentation.txt`. **Отчёт PASS допустим только по фактическому результату Android-инструментальных тестов**, а не после успешного Gradle или локальной компиляции Kotlin.

## Что этот этап НЕ доказывает

- Настоящий системный SAF `ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT` на физическом телефоне здесь не вызывается: используется `ContentResolver` с тестовым `file://` URI. Это упражняет фактический сериализатор/импортёр, но не проверяет внешний DocumentsProvider или persisted URI grants.
- Настоящий резервный ZIP пользователя всё ещё **не был восстановлен на отдельном устройстве**. Этот этап использует исключительно созданные тестом данные.
- Это не проверка полного процесса гибели приложения при записи на носитель, масштабирования на 20 000 треков или связи с изменившимся MediaStore ID.
- ZIP не шифруется. Прежде чем переключать основное чтение на Room в 0-C.3, обязательно проверить внешний пользовательский ZIP на тестовой установке, rollback при реальных сбоях и производительность; не удалять приложение с основного телефона.
