# FreeLocalPlayer — единая дорожная карта проекта

**Редакция 4 — актуализация 16 ноября 2026 года: 0-C.3-A (1/1) и 0-C.3-B (3/3) подтверждены журналами Motorola, 0-C.3-C пользователь подтвердил функционально без детального отчёта; постоянный Room-primary не включён, JSON остаётся каноническим.**

Этот документ — **основная дорожная карта**. Исходная редакция v2 пользователя сохранена **ниже без удаления**; её статусы относятся к прежнему состоянию. **Раздел «Архитектурный стоп-кран P0» имеет высший приоритет**, затем текущая сводка v3–v10 и только потом архивный дизайн-план. Любые изменения должны сохранять уже работающие функции приложения.

- [Критический аудит архитектуры и восстановление данных](ARCHITECTURE_STABILIZATION_AUDIT_2026-11-12.md) — проверенные замечания, зависимости, порядок исправления, тестовые критерии, бюджет производительности.
- [GitHub issue #2](https://github.com/OlegRakhimov/MyPlayer/issues/2) — **P0: сохранность данных, стабильная идентичность, хранилище и release-подпись**.
- [Modern Fusion — дизайн-концепция](MODERN_FUSION_DESIGN.md) — спецификация выбранных пяти направлений дизайна.
- [Modern Fusion — реализованный этап A](MODERN_FUSION_IMPLEMENTATION.md) — подробности v3/v4.
- [Modern Fusion — медиатека v5](MODERN_FUSION_LIBRARY_V5.md) — подробности этапа медиатеки.
- [Modern Fusion — музыкальный экран v6](MODERN_FUSION_MUSIC_V6.md) — управление и LRC.
- [GitHub issue #1](https://github.com/OlegRakhimov/MyPlayer/issues/1) — *рабочая задача*, а не вторая дорожная карта.

## Архитектурный стоп-кран P0 — обязательное условие следующего функционального релиза

**0-E.1 — СБОРКА, ОБНОВЛЕНИЕ APK И ИЗОЛИРОВАННАЯ ANDROID QA PASS НА MOTOROLA (19.11.2026, 20:28).** В GitHub `main` проверен коммит [`56b82eaa373ec11e149d2fdc9ec72e6fe2d3a965`](https://github.com/OlegRakhimov/MyPlayer/commit/56b82eaa373ec11e149d2fdc9ec72e6fe2d3a965) с семью файлами: `PlaybackResumePolicy.kt`, `PlaybackResumeStore.kt`, изменение `PlaybackService.kt` и `AndroidManifest.xml`, семь JVM тестов, шесть инструментальных Android тестов и `docs/PLAYBACK_0E1_MEDIA_BUTTON_RESUMPTION.md`. Журнал пользователя: `GRADLE_BUILD_AND_UNIT_TESTS=PASS`, `VERIFIED_PHYSICAL_MOTOROLA=PASS model=motorola edge 60 pro`, `EMULATOR_USED=NO`, `PLAYER_PROCESS_RUNNING=NO`, `APK_SIGNING_CERTIFICATES_MATCH=PASS`, `MAIN_APP_UPDATE_IN_PLACE=PASS`, `TEST_APK_INSTALL=PASS`, `RESUME_CHECKPOINT_ANDROID_TESTS=PASS tests=6`, `CANONICAL_XML_UNCHANGED=PASS`, `ROOM_PRIMARY_STORAGE=DISABLED_JSON_CANONICAL`, `GITHUB_PUSH=PASS REMOTE_HASH_VERIFIED=YES`, `RESULT=SUCCESS MOTOROLA_0E1_MEDIA_BUTTON_RESUME_QA_AND_PUBLISH`. `local_library_v1.xml` SHA-256 ДО и ПОСЛЕ: `A475B859E84AB4262C08C2FBBF472300066F0EC382623EE2932E5C86FEC9EB11`. **Границы подтверждения:** Media3 media-button resumption реализован, а автоматические тесты покрыли хранилище последней локальной композиции/позиции, но не фактическое нажатие Bluetooth-гарнитуры или Android системного плеера после остановки сервиса. Эти ручные сценарии и проверка отсутствия автозапуска музыки пока ожидаются. Пока восстанавливается ОДНА композиция, без полной очереди/repeat/shuffle; последние ~5 секунд могут не сохраниться. `0-C.5.2` отдельно остаётся ОТКРЫТЫМ до фактических замеров stage/commit/clear на Motorola; отсутствие журнала нельзя заменять предположением PASS. JSON остаётся каноническим, Room-primary выключен.

**0-C.5.2 — ПОДГОТОВЛЕНО, ТЕЛЕФОННАЯ QA ОЖИДАЕТСЯ.** Добавлен тест [`LibraryJournalStageCostDeviceTest.kt`](https://github.com/OlegRakhimov/MyPlayer/blob/main/app/src/androidTest/java/com/example/freelocalplayer/data/LibraryJournalStageCostDeviceTest.kt) с тремя нагрузками (630, 5000, 20000 синтетических треков), тремя прогревочными проходами и 24 измерениями на ветвь; порядок прямой/журнальной записи чередуется, отдельно замеряются `LibraryPendingWriteJournal.stage`, `SharedPreferences.commit`, `LibraryPendingWriteJournal.clear` и полная последовательность. Документ [`docs/STABILIZATION_0C52_JOURNAL_STAGE_BREAKDOWN.md`](https://github.com/OlegRakhimov/MyPlayer/blob/main/docs/STABILIZATION_0C52_JOURNAL_STAGE_BREAKDOWN.md). Тест использует уникальные `__flp_0c52_*` QA prefs/папку, не вызывает `LibraryStore` и не меняет пользовательский JSON/Room. Только тестовый APK на Motorola, производственный APK неизменён. Скрипт запуска фиксирует *текущий* SHA-256 XML до/после — не сравнивает его с прежней резервной копией после ручных изменений. Реальные измерения, Android Gradle и QA для этого нового теста ещё **не подтверждены**. Порог производительности пока не установлен.


**0-C.5 — СИНТЕТИЧЕСКИЙ БЕНЧМАРК ЖУРНАЛА, ПРИНЯТО КАК ИЗМЕРЕНИЕ (Motorola Edge 60 Pro, 19.11.2026, 17:46).** Код теста и инструкция опубликованы в [`2c40af0488de81d95578ca5a6f1d039a50f569c9`](https://github.com/OlegRakhimov/MyPlayer/commit/2c40af0488de81d95578ca5a6f1d039a50f569c9); основное приложение не переустанавливалось (`MAIN_APP_APK_INSTALL=NO`, `MAIN_APP_APK_UNCHANGED=PASS`). Журнал пользователя: `GRADLE_BUILD_AND_UNIT_TESTS=PASS`, `VERIFIED_PHYSICAL_MOTOROLA=PASS`, `EMULATOR_USED=NO`, `APK_SIGNING_CERTIFICATES_MATCH=PASS`, `TEST_APK_ONLY_INSTALL=PASS`, `SYNTHETIC_JOURNAL_BENCHMARKS=PASS sizes=630,5000,20000`, `CANONICAL_XML_UNCHANGED=PASS`, `GITHUB_PUSH=PASS REMOTE_HASH_VERIFIED=YES`, `RESULT=SUCCESS MOTOROLA_0C5_PERFORMANCE_QA_AND_PUBLISH`. SHA-256 настоящего XML после тестов `A475B859E84AB4262C08C2FBBF472300066F0EC382623EE2932E5C86FEC9EB11` (совпадает с базовым). **Измерения дисковой части** (5 повторов, JSON построен заранее, без Room/Compose/сериализации/сканирования): 630 треков / 154 080 байт: direct p50 5.715 мс, журнал p50 15.486 мс, журнал p95 19.897 мс, замедление 2.710x; 5 000 / 1 210 140 байт: direct p50 32.614 мс, журнал p50 93.790 мс, p95 101.226 мс, 2.876x; 20 000 / 4 880 140 байт: direct p50 121.139 мс, журнал p50 341.398 мс, p95 394.460 мс, 2.818x. Замер подтверждает стоимость дополнительной синхронной записи на диске, но не задержку интерфейса: writer фоновый. В тесте ближайший ранговый p95 при N=5 фактически максимальное измерение; прямые измерения выполнялись до journaled, порядок не рандомизирован. `PERFORMANCE_REGRESSION_THRESHOLD=NOT_SET_RESULTS_ARE_MEASUREMENTS`, поэтому PASS означает корректность тестов, **не** производительность для релиза. Следующие действия: ручная QA текстов TXT/LRC/Enhanced LRC и плейлистов с холодным запуском, свежий SAF ZIP после ручных изменений; перед оптимизацией — раздельное профилирование stage / canonical commit / cleanup, прогрев и больше повторов, без изменений пользовательской базы и без эмулятора. Документ `docs/STABILIZATION_0C5_MOTOROLA_PERFORMANCE_AND_UI_QA.md`.


**0-C.4.2 — ПРИНЯТО ПО АВТОМАТИЧЕСКИМ ТЕСТАМ на физическом Motorola Edge 60 Pro (18.11.2026, 21:05).** В `main` опубликован коммит [`ffe171c705f832befc3813c7590e4cee730c5d01`](https://github.com/OlegRakhimov/MyPlayer/commit/ffe171c705f832befc3813c7590e4cee730c5d01): `LibraryPendingWriteJournal` (приложение-приватный `AtomicFile`, SHA-256 исходного и целевого JSON, строгая проверка восстановления), `PendingLibraryRecoveryPolicy`, подключение в `LibraryStore`, 6 JVM и 6 Android тестов, документация `docs/STABILIZATION_0C42_CRASH_RECOVERY_JOURNAL.md`. Журнал пользователя: `GRADLE_BUILD_AND_UNIT_TESTS=PASS`, `VERIFIED_PHYSICAL_MOTOROLA=PASS`, `EMULATOR_USED=NO`, `PLAYER_PROCESS_RUNNING=NO`, `APK_SIGNING_CERTIFICATES_MATCH=PASS`, `CANONICAL_XML_VERIFIED_BACKUP_BASELINE=PASS`, `MAIN_APP_UPDATE_IN_PLACE=PASS`, `TEST_APK_INSTALL=PASS`, `LIBRARY_JOURNAL_ANDROID_TESTS=PASS tests=6`, `CANONICAL_XML_UNCHANGED=PASS`, `ROOM_PRIMARY_STORAGE=DISABLED_JSON_CANONICAL`, `GITHUB_PUSH=PASS REMOTE_HASH_VERIFIED=YES`, `RESULT=SUCCESS MOTOROLA_0C42_JOURNAL_QA_AND_PUBLISH`. SHA-256 настоящего `local_library_v1.xml` до и после: `A475B859E84AB4262C08C2FBBF472300066F0EC382623EE2932E5C86FEC9EB11`. **Ограничения:** проверки выполнялись в отдельных QA prefs/каталогах и эмулировали прерванную запись; не тестировали настоящую аварийную остановку приложения с незавершённым `commit`. Осталось окно потери изменений до завершения записи журнала; при несовпадающем SHA-256 база не переписывается, загрузка останавливается для ручного разбора. Полная ручная UI-приёмка редакторов LRC/Enhanced LRC, плейлистов и сохранения после повторного открытия не предоставлена. Следующий приоритет — не повторять изолированные тесты, а выполнить безопасную проверку основных пользовательских сценариев и оценить задержку двойной записи на большой медиатеке; нельзя создавать искусственные повреждения настоящего JSON или выполнять `pm clear`/`uninstall`.


**0-C.4.1 — ПРИНЯТО ПО АВТОМАТИЧЕСКИМ ТЕСТАМ на Motorola Edge 60 Pro (18.11.2026, 20:24, без эмулятора).** Код опубликован в коммите [`f412ad941c1144c915f77934e82c029f03580fa7`](https://github.com/OlegRakhimov/MyPlayer/commit/f412ad941c1144c915f77934e82c029f03580fa7), `GITHUB_PUSH=PASS REMOTE_HASH_VERIFIED=YES`. Изменены 7 файлов: подтверждаемое сохранение меток строки LRC, слова Enhanced LRC и синхронизации TXT→LRC, сохранение черновика при сбое, пять Kotlin unit-тестов и три инструментальных Android-теста. Журнал пользователя: `GRADLE_BUILD_AND_UNIT_TESTS=PASS` (70 задач, 17 выполнено), `VERIFIED_PHYSICAL_MOTOROLA=PASS`, `EMULATOR_USED=NO`, `APK_SIGNING_CERTIFICATES_MATCH=PASS`, `CANONICAL_XML_VERIFIED_BACKUP_BASELINE=PASS`, `MAIN_APP_UPDATE_IN_PLACE=PASS`, `TEST_APK_INSTALL=PASS`, `LRC_TIMING_RECEIPTS_ANDROID_TESTS=PASS tests=3`, `CANONICAL_XML_UNCHANGED=PASS`, `ROOM_PRIMARY_STORAGE=DISABLED_JSON_CANONICAL`, `RESULT=SUCCESS MOTOROLA_0C41_LRC_TIMING_QA_AND_PUBLISH`. Реальный канонический XML имел идентичный SHA-256 до и после установки/QA: `A475B859E84AB4262C08C2FBBF472300066F0EC382623EE2932E5C86FEC9EB11`. **Ограничения:** ручная UI-приёмка всех трёх диалогов и сохранения/повторной загрузки ещё не предоставлена; процессная гибель *до* `commit()` всё ещё может потерять последнюю неподтверждённую правку. JSON остаётся основным хранилищем, Room-primary не активирован. Следующие приоритеты: безопасная проверка настоящего UI на Motorola, затем устранение риска незафиксированной правки и недопущение ложных подтверждений при последующей записи/коалесцировании. Эмулятор запрещён в текущем QA.


**0-C.4 — ПОДТВЕРЖДЁННЫЕ ЗАПИСИ, РЕАЛИЗОВАНО И QA PASS на Motorola Edge 60 Pro (18.11.2026, 17:40).** Коммит `f35f77195fd7b3f84dc508bface54dda24dc718b` опубликован с `GITHUB_PUSH=PASS REMOTE_HASH_VERIFIED=YES`. Журнал пользователя: `GRADLE_BUILD_AND_UNIT_TESTS=PASS`, `VERIFIED_PHYSICAL_MOTOROLA=PASS`, `EMULATOR_USED=NO`, `APK_SIGNING_CERTIFICATES_MATCH=PASS`, `CANONICAL_XML_VERIFIED_BACKUP_BASELINE=PASS`, `MAIN_APP_UPDATE_IN_PLACE=PASS`, `TEST_APK_INSTALL=PASS`, `CONFIRMED_WRITES_ANDROID_TESTS=PASS tests=3`, `CANONICAL_XML_UNCHANGED=PASS`, `ROOM_PRIMARY_STORAGE=DISABLED_JSON_CANONICAL`, `RESULT=SUCCESS MOTOROLA_0C4_CONFIRMED_SAVE_QA_AND_PUBLISH`. `local_library_v1.xml` до/после: `A475B859E84AB4262C08C2FBBF472300066F0EC382623EE2932E5C86FEC9EB11`. Новая логика `LatestSnapshotWriter` подтверждает успешную запись только после `SharedPreferences.commit()` и правильно отправляет receipts после коалесцирования быстрых изменений; четыре новых JVM-теста + три instrumented-теста QA-предпочтений на реальном телефоне. Полноэкранный редактор TXT/LRC теперь ожидает результат и при ошибке оставляет черновик открытым. Изменения плейлистов показывают подтверждение по завершении записи. **Ограничения:** пользовательская функциональная проверка реального редактора/плейлистов в UI отдельно не предоставлена; мгновенная гибель процесса *до commit* по-прежнему не гарантирует сохранность правки; редакторы LRC/Enhanced LRC внутри музыкального режима ещё закрывают свои диалоги без подтверждённого commit, см. `docs/STABILIZATION_0C4_CONFIRMED_WRITES.md`. Следующий подэтап: перенести подтверждение на редактирование тайминга TXT/LRC/Enhanced LRC, не меняя источники данных или Media3. Не повторять уже завершённые E1–E3 и 0-C.4 без нового конкретного риска.


**0-C.3-E3 — ПРИНЯТО: два настоящих холодных запуска FreeLocalPlayer на Motorola Edge 60 Pro (17.11.2026, 20:56).** По журналу пользователя: `MOTOROLA_E3_PREFLIGHT=PASS`, `EXTERNAL_SAF_BACKUP_SHA256=PASS`, `VERIFIED_PHYSICAL_MOTOROLA=PASS`, `EMULATOR_USED=NO`, `PLAYER_CLOSED_BEFORE_QA=PASS`, `STRICT_0C3D_AUDIT=PASS` (630 треков / 3 плейлиста / 10 SHA-256 записей архива, подпись APK совпадает), `COLD_START_JSON_DEFAULT=PASS cycle=1`, `COLD_START_JSON_DEFAULT=PASS cycle=2`, `CANONICAL_XML_UNCHANGED=PASS`, `APP_APK_INSTALL=NO`, `TEST_APK_INSTALL=NO`, `ROOM_PRIMARY_STORAGE=DISABLED_JSON_CANONICAL`, итог `RESULT=SUCCESS MOTOROLA_JSON_COLDSTART_E3_PASS cycles=2`. SHA-256 `local_library_v1.xml` до, после каждого запуска и после всего опыта одинаковый: `A475B859E84AB4262C08C2FBBF472300066F0EC382623EE2932E5C86FEC9EB11`. Отчёт сохранён в `.patch-backups/phone-coldstart-qa-0c3e3-20261117-205602`. **Границы проверки:** процесс останавливался контролируемо без незавершённых пользовательских правок; не подтверждена устойчивость при аварийной остановке *во время* pending disk write и не включался Room-primary. Весь E3 выполнен без эмулятора. Перейти к следующему P0 этапу без опасных манипуляций настоящим пользовательским JSON; избегать бесконечного повторения уже пройденных QA.


**0-C.3-E1 + 0-C.3-E2 — ПРИНЯТЫ на физическом Motorola Edge 60 Pro (17.11.2026, 20:35; без эмулятора).** Этап E1: 10/10 инструментальных тестов изолированных отказов SQLite и JSON-fallback PASS (17.11 в 18:05), `CANONICAL_XML_UNCHANGED=PASS`, `MAIN_APP_APK_REINSTALLED=NO`. Коммит опубликован `a5401c6c17ef397ab2379a2501a8e0a16481f194`, `GITHUB_PUSH=PASS REMOTE_HASH_VERIFIED=YES`, `PHONE_USER_DATA_CHANGED=NO`; тестовые исходники трёх файлов проходят SHA-256 проверки. Этап E2: `BACKUP_RESTORE_E2_INSTRUMENTATION=PASS tests=7`, `STRICT_0C3D_BACKUP_AUDIT=PASS`, `EXTERNAL_SAF_BACKUP_SHA256=PASS`, `APK_SIGNING_CERT_MATCH=PASS`, `TEST_APK_ONLY_INSTALL=PASS`, `REAL_USER_BACKUP_IMPORT=NO`, `MAIN_APP_APK_REINSTALLED=NO`, `EMULATOR_USED=NO`; весь E2 запущен только на Motorola. SHA-256 `local_library_v1.xml` до и после E2 одинаковый: `A475B859E84AB4262C08C2FBBF472300066F0EC382623EE2932E5C86FEC9EB11`. Результат `SUCCESS MOTOROLA_BACKUP_RESTORE_E2_PASS`, отчёт `.patch-backups/phone-backup-restore-qa-0c3e2-20261117-203441`. Резервный ZIP `FreeLocalPlayer-backup-1794858646872.zip`, SHA-256 `F239ABC8EC4771B76E01275B24383F4255418D592D6C0A7B0B2E1D458A5E313B`, ранее проверен на 630 треков, 3 списка. **Границы доказательств:** это испытания изолированных отказов и тестового восстановления; реальная гибель процесса при незавершённой записи, долговременный Room-primary и полная UI-приёмка не проверены. JSON остаётся каноническим. Дальше 0-C.3-E3: контролируемый холодный запуск на физическом телефоне с сохранением SHA-256 JSON; без вмешательства в данные и без эмулятора.


**0-C.3-D — ПРОВЕРЕНО на Motorola Edge 60 Pro (16.11.2026, 20:51; Android QA + сравнение двух SAF ZIP).** Пользователь представил журнал: `GRADLE_BUILD=PASS`, `POST_BUILD_APK_SIGNING_CERT_MATCH=PASS`, `APP_UPDATE_IN_PLACE=PASS`, `TEST_APK_INSTALL=PASS`, `ANDROID_CANONICAL_JSON_GUARD=PASS tests=10`, `REAL_CATALOG_CHECK=READ_ONLY`, `RESULT=SUCCESS JSON_GUARD_MOTOROLA_QA_PASS`. Отчёт: `.patch-backups/json-guard-phone-qa-20261116-203700`. До установки: ZIP `FreeLocalPlayer-backup-1794857126558.zip` SHA-256 `0311864BDAFEF67E42CE2FCF598B37575F8662EC39B27ADC39E899632D94CF76`; после установки: `FreeLocalPlayer-backup-1794858646872.zip` SHA-256 `F239ABC8EC4771B76E01275B24383F4255418D592D6C0A7B0B2E1D458A5E313B`. Оба ZIP: `BACKUP_CATALOG_COUNTS=PASS tracks=630 playlists=3`, `BACKUP_MANIFEST_AND_ENTRY_SHA256=PASS entries=10`, `APK_SIGNING_CERT_MATCH=PASS`. Сравнение: `CATALOG_COUNTS_PRESERVED=PASS`, `CANONICAL_LIBRARY_UNCHANGED=PASS` (сырой JSON посимвольно совпал), `MUSIC_BACKUP_ENTRIES_UNCHANGED=PASS` (совпали имена и SHA-256 вложений music), `RESULT=SUCCESS POST_QA_BACKUP_COMPARISON_PASS`. Это не доказывает работоспособность каждого элемента UI или эксперимент с настоящей гибелью процесса. Исправлен локальный PS5.1 обработчик `adb pull` (stderr прогресса не трактуется как ошибка), запасная копия runner `.patch-backups/Verify-FreeLocalPlayer-PhoneJsonGuard-0C3D-20261116-203456.ps1.bak`; локальное изменение скрипта пока не заявлено как опубликованное в GitHub. **Статус: 0-C.3-D технически принят по инструментальным тестам и сравнению резервных копий; визуальная UI-приёмка отдельно.** `ROOM_PRIMARY_STORAGE=DISABLED_JSON_CANONICAL`. Следующий этап 0-C.3-E: только изолированное моделирование отказов JSON/Room, перезапуска и rollback; запрещено повреждать реальные данные Motorola, делать `pm clear`, `uninstall` или включать постоянный Room-primary.


**0-C.3-D — УСТАНОВКА И GITHUB PASS; Motorola QA ЕЩЁ ОЖИДАЕТСЯ (16.11.2026, 18:50).** После двух сбоев предзапуска PowerShell пользователь установил исправленный ZIP `FreeLocalPlayer_0C3D_Canonical_JSON_Safety_PS51_v3.zip` и сообщил `BUILD SUCCESSFUL in 1m 15s`, 70 tasks (15 executed, 55 up-to-date), `GRADLE_BUILD_AND_TESTS=PASS`, `ROOM_SCHEMA_V1_EXPORTED=PASS count=1`, `LOCAL_COMMIT=3c9903aeece55dfc73eb4718b38bfef8b063419b`, `GITHUB_PUSH=PASS REMOTE_HASH_VERIFIED=YES HEAD=3c9903a...`, `RESULT=SUCCESS CANONICAL_GUARD_PATCH_PUSHED_PHONE_QA_PENDING`, `PATCH_INSTALL=PASS`. GitHub main независимо проверен: HEAD `3c9903a...` (`fix(data): fail closed on incomplete canonical JSON before Room migration`), исходники и телефонный runner `tools/Verify-FreeLocalPlayer-PhoneJsonGuard-0C3D.ps1` присутствуют. Новый код `CanonicalLibraryJsonDecoder` и `CanonicalLibraryStateGuard` установлен, отстроен Gradle, но **реальные Android instrumented tests на Motorola ещё не выполнены**. Для QA: сначала выполнить `-Mode Audit` с актуальным SAF ZIP при наличии (`-BackupFile`), затем `-Mode Run` на проверенной Motorola Edge 60 Pro. Скрипт проверяет содержимое backup ZIP+SHA256 манифеста, код, идентичность устройства и сертификаты app/test APK, обновляет через `adb install -r`, не выполняет `pm clear`/`uninstall`, запускает **10 Android-тестов**, в том числе read-only проверку настоящего JSON. До запуска свежий SAF ZIP на ПК желателен; исторический ZIP 14.11 доступен как резерв, но может не содержать последних изменений. `ROOM_PRIMARY_CUTOVER=DISABLED_JSON_PRIMARY_RETAINED`: JSON остаётся каноническим. **Не считать 0-C.3-D целиком принятым без Android QA.**



**0-C.3-D — второй preflight завершился `PropertyNotFoundStrict` (16.11.2026, 17:58); опубликован третий исправленный архив, Windows QA ещё не выполнена.** Пользователь проверил ZIP SHA (`PATCH_SHA256=PASS`), но `-VerifyOnly` исправленного v2 пакета остановился на строке 77 `Say ('PATCH_PAYLOAD_SHA256=PASS files=' + $items.Length)` с «Не удается найти свойство Length». Причина подтверждена реальным содержимым ZIP: `$items` — `System.Collections.Generic.List[object]`, у него есть `.Count`, а `.Length` нет при `Set-StrictMode -Version Latest`. Никаких записей в проект до CheckProject/Gradle не выполнено. Создан **`FreeLocalPlayer_0C3D_Canonical_JSON_Safety_PS51_v3.zip`** (20 657 байт), SHA-256 **`43F7F5F443E719391E295D6ED217A968842463409AE1FFA3C676BE48F4FA220B`**. В установщике исправлены `$items.Count`, согласованы Count для существующих массивов, добавлена проверка дубликатов manifest paths. В телефонном QA-скрипте устранён ненадёжный подсчёт `manifest.entries`, медиазаписей и плейлистов на PowerShell 5.1, добавлена fail-closed проверка записей свежего SAF ZIP. **Kotlin/Android payload и docs без изменений байтов**; 6/6 SHA-256 manifest PASS, ZIP CRC PASS, статические инварианты PS скрипта PASS. **Реальное исполнение PowerShell 5.1 НЕ проверено**, поэтому статус 0-C.3-D остаётся **PENDING**, JSON primary/Room shadow без изменений. Следующий шаг: SHA v3, распаковка только в `.patch-backups`, `-VerifyOnly` нового установщика, затем установка и Gradle только при PASS. Не использовать два старых ZIP и не ослаблять контроль манифеста.



**0-C.3-D — исправление установщика PS5.1 после `PATCH_FILE_COUNT_CHANGED` (16.11.2026, 17:53), повторная установка ещё не проверена.** Пользователь сообщил `PATCH_SHA256=PASS`, затем `RESULT=FAILED PATCH_FILE_COUNT_CHANGED` на `-VerifyOnly`. Непосредственно распакован предыдущий ZIP: манифест содержит **ровно 6** файлов, и все 6 payload-файлов в архиве присутствуют. Причина в `CheckPayload` установщика: PowerShell 5.1 может представить JSON-массив как один объект в `@(ConvertFrom-Json ...)`, поэтому проверка `.Length` ошибочно отвергает корректный пакет. Подготовлен новый отдельный архив **`FreeLocalPlayer_0C3D_Canonical_JSON_Safety_PS51_Fixed.zip`** SHA-256 **`F030B528833BD9F4D7F8A9450C39A0B9878CDC0F5C99E6749013677F408A5743`** (20 011 байт). Установщик теперь явно разворачивает вложенные JSON-массивы в `List[object]` и проверяет `.Count`. **Шесть payload-файлов не менялись**, SHA-256 каждого проверен, ZIP CRC PASS. Ошибка произошла до любых изменений проекта, следовательно предшествующий `0-C.3-D` не применён. Исправленный пакет ещё не запускался на Windows; не отмечать как PASS до отчёта. Использовать только новый архив и его SHA, не обходить контроль числа файлов и не менять исходники вручную.

**0-C.3-D — ПОДГОТОВЛЕН (16.11.2026), ещё не установлен на Windows/телефоне: строгая проверка канонического JSON перед будущим Room-primary.** Готов ZIP `FreeLocalPlayer_0C3D_Canonical_JSON_Safety.zip` размер 19 665 байт, SHA-256 `8244AF83BF3379A9F4A53015F672931C22D6919374EBA469A40D2D6ACB9548CB`. В актуальном `LibraryStore` выявлен реальный риск: `optJSONArray` + `mapNotNull` могут пропускать неверные записи из синтаксически правильного, но повреждённого JSON, а последующее `save` может перезаписать сокращённую медиатеку. Патч заменяет загрузку на `CanonicalLibraryJsonDecoder` (fail-closed; пропуск 0 записей; только отсутствующий `library` на первичной установке означает пустую библиотеку), добавляет `CanonicalLibraryStateGuard` для проверки UUID live+retired, URI, плейлистов и мест до сохранения. Отсутствующий `retiredMedia` у старых сохранений поддерживается, существующие данные/ID не переписываются. Добавлены 10 Kotlin unit-тестов и 10 Android instrumented-тестов, включая read-only проверку реальной медиатеки и SHA-256 JSON до/после. Windows установщик сверяет Git SHA `LibraryStore.kt=1d2f77d5af5940c916ff08454e619fea5ed54605`, исходный commit `26bad940...`, проверяет payload, использует общую блокировку 5-минутной GitHub-синхронизации, создаёт `.patch-backups`, выполняет Gradle и Git push только после PASS, откат исходников при ошибке ДО коммита. Motorola runner `Audit/Run` сверяет физическое устройство, подпись установленного и обоих новых APK, реальную ZIP-копию (`manifest.json` + SHA-256 файлов, непустую медиатеку), выполняет только `adb install -r` и чтение без очистки, `pm clear`/`uninstall`; допускает `-BackupFile` со свежим архивом, а исторический ZIP 14.11 маркирует как **устаревший**. **Проверено в текущей среде:** 10/10 чистых Kotlin-тестов с модельными заглушками, компиляция нового Kotlin и AndroidTest с API-заглушками, контрольные суммы и CRC ZIP 6/6, статический анализ установщика. **Не проверено:** настоящий Gradle/PowerShell 5.1/Android на Windows/Motorola, фактический SHA-256 живого JSON, реальный отзыв пользовательского UI. Room schema v1, пакет, версия 1.3.12(20), ZIP-формат, подпись, UUID не меняются, JSON остаётся каноническим; постоянный Room-primary **не включается**. Следующие ворота 0-C.3-E: process-death, отказ дисковых записей/SQLite, восстановление из свежего ZIP и только потом реальное долговременное переключение. Не заявлять 0-C.3-D PASS до журнала пользователя.



**0-C.3-C — пользователь подтвердил успешное испытание и нормальную работу на Motorola (16.11.2026, 16:50); подробный отчёт Gradle / instrumentation / cold-start НЕ приложен.** Исходники этапа действительно опубликованы в GitHub `main`, HEAD [`26bad940`](https://github.com/OlegRakhimov/MyPlayer/commit/26bad940167c1d01c3ee491bc46e35a76ce77040) (`test(room): add 180-second fail-closed real Room read rollout QA on Motorola`). Внедрены `RoomReadRolloutWindow.kt`, ограничение эксперимента `room_verified_read_0c3b=true` + `room_verified_read_expires_0c3c_ms` **максимум 180 секунд**, защита от отсутствующей/просроченной метки и аварийная возможность возврата к JSON через `-Mode Recover`. Текущая `LibraryStore` **сначала читает канонический JSON**, Room включается исключительно на действующий короткий срок и только после подтверждённого полного совпадения со снимком JSON; запись остаётся JSON-first. Пользователь сообщил дословно: «все прошло, все работает». Это позволяет отметить **пользовательское функциональное подтверждение 0-C.3-C**, но **не даёт оснований фиксировать** `REAL_ROOM_READ_COLD_START=PASS`, `JSON_ROLLBACK_COLD_START=PASS`, контрольный SHA-256 исходного каталога или конкретный результат всех трёх инструментальных тестов без их фактических строк. Предыдущая полноценная 0-C.3-B Android QA 3/3 PASS подтверждена отдельным журналом. При следующем доступе к логам отчёт 0-C.3-C ожидается внутри `.patch-backups`; не требуется повторно обновлять APK только для документирования. **Постоянный Room-primary НЕ включён**, флаг короткого испытания должен истекать; JSON остаётся каноническим. Следующее решение — **0-C.3-D**, разработка и отдельная верификация долговременного обратимого хранения/включения Room, отказоустойчивости, контрольных сумм и восстановления; перед реальным переключением нужен свежий SAF ZIP вне телефона и подтверждение отказного отката. Не менять UUID, `applicationId`, подпись, JSON/ZIP формат и не удалять данные.



**0-C.3-B — ПРИНЯТО на Motorola Edge 60 Pro: все три instrumented-теста защищённого Room-read с JSON fallback PASS (15.11.2026, 10:54).** По журналу пользователя: `BUILD SUCCESSFUL in 23s` (70/70 up-to-date), `GRADLE_BUILD=PASS`, `POST_BUILD_APK_SIGNING_CERT_MATCH=PASS`, `VERIFIED_PHYSICAL_PHONE=PASS manufacturer=motorola model=motorola edge 60 pro`, `PHONE_INSTALLED_VERSION=1.3.12(20)`, `APP_UPDATE_IN_PLACE=PASS`, `TEST_APK_INSTALL=PASS`, `PHONE_APP_VERSION_AFTER_INSTALL=PASS 1.3.12(20)`. Инструментальные тесты: `actualPhoneCatalogIsVerifiedWithoutRepairOrFlagChanges=PASS` (реальная медиатека, read-only), `staleAndWrongSignatureInMemoryMustReturnExactJson=PASS` (изоляция in-memory), `readFailureFallsBackWithoutTouchingPersistentStorage=PASS` (изоляция in-memory). Итог: `REAL_JSON_ROOM_GUARDED_READ=PASS tests=3`, `RESULT=SUCCESS GUARDED_ROOM_PHONE_3_TESTS_PASS`, `GUARDED_ROOM_PHONE_TESTS=PASS`. Отчёт пользователя: `.patch-backups/room-guarded-read-phone-qa-20261115-105342`. Исходный патч опубликован в коммите [`bb023e77`](https://github.com/OlegRakhimov/MyPlayer/commit/bb023e77f89e7ee2e2fad1e1575a7b3cb81b6b78), `GRADLE_BUILD_AND_TESTS=PASS`, `ROOM_SCHEMA_V1_EXPORTED=PASS`. **Данные сохранены посредством обновления APK поверх существующего; `uninstall`/`pm clear` не выполнялись.** Флаг `room_verified_read_0c3b` остаётся **OFF по умолчанию** (`ROOM_READ_ROLLOUT_FLAG=DISABLED_DEFAULT`), `JSON_PRIMARY=RETAINED`; **это не проверка включённого Room-primary в обычной работе**. `MOTOROLA_5000_20000_BENCHMARK=SKIPPED_SAFETY`. Следующий этап **0-C.3-C**: отдельное обратимое, ограниченное включение Room read с проверкой фактического источника загрузки, холодного старта, сохранений/перезапуска и process-death, а также rollback при SQLite/устаревшем stamp. Перед этим необходим актуальный резервный ZIP вне телефона; не менять `applicationId`, подпись, формат JSON/SAF и UUID. Полный лог строгого SAF ZIP Compare ранее не был передан, поэтому не помечать его формально PASS без исходного отчёта.



**0-C.3-A — ПРИНЯТА проверка настоящей библиотеки JSON↔Room на Motorola Edge 60 Pro (14.11.2026, 23:08); Room-primary по-прежнему ОТКЛЮЧЁН.** По журналу пользователя: `BUILD SUCCESSFUL in 19s`, 70 задач up-to-date, `GRADLE_BUILD=PASS`, `POST_BUILD_APK_SIGNING_CERT_MATCH=PASS`, `VERIFIED_PHYSICAL_PHONE=PASS manufacturer=motorola model=motorola edge 60 pro`, штатное обновление поверх установленной версии `APP_UPDATE_IN_PLACE=PASS`, `TEST_APK_INSTALL=PASS`, версия после установки `1.3.12(20)`, `REAL_LIBRARY_READ_ONLY_TEST=PASS tests=1`, `RESULT=SUCCESS SAFE_PHONE_JSON_ROOM_GATE_PASS`, `MOTOROLA_PHONE_QA_COMPLETE=PASS`. Отчёт на ПК: `.patch-backups/room-phone-qa-20261114-230808`. Исходники QA [коммит `886a25b`](https://github.com/OlegRakhimov/MyPlayer/commit/886a25b4ce62d03e1bea9c173df7d04ab5d9a46e). Код теста `RoomPrimaryPreflightDeviceTest#importedCanonicalJsonMatchesExistingRoomShadowReadOnly` проверяет существующий canonical JSON и Room через `RoomPrimaryCutoverGate.evaluate` без вызова `LibraryStore.load()` и автоматической синхронизации. Таким образом, **для состояния медиатеки во время этого запуска соответствие подтверждено**. Это не доказывает устойчивость при отключении питания, гибели процесса, конкурентных записях и повторном восстановлении ZIP. `LOAD_5000_20000_ON_PHONE=SKIPPED_SAFETY`: нагрузочные сценарии 5k/20k на телефоне не запускались. В предыдущем этапе пользователь сообщил «всё отлично» после SAF Compare, однако его итоговый построчный журнал не предоставлен — не объявлять строгое сравнение исходного и реэкспортированного архива документально подтверждённым. **Дальше 0-C.3-B: разработать контролируемый fail-closed Room-read / JSON-fallback с проверенным откатом и тестами, но не менять `applicationId`, schema v1, JSON формат, UUID или удалять данные. До включения: свежий SAF ZIP, process-death / stale Room / ошибки SQLite / последняя запись и повторный запуск, performance.**



**0-C.3-A — подготовлен защищённый этап проверки готовности Room-primary, переключение НЕ выполнено (14.11.2026).** ZIP `FreeLocalPlayer_Stage_0C3A_RoomPrimaryGate.zip` SHA-256 `43BD79DF0A555D689405C8C9199D1FB39D171B106922D35025A139C880B4BDD8`. Пять новых файлов: чистая fail-closed проверка `RoomPrimaryCutoverGate` сравнивает исходный JSON со всеми SQLite-строками, schemaVersion, stamp counts и SHA256 подписью без записи; 8 Kotlin unit-тестов; 3 Android instrumentation — реальный JSON↔Room без вызова `LibraryStore.load()` и отдельно синтетические 5000/20000; только Pixel_7 QA runner; техописание. **Локально:** 8/8 Kotlin unit-тестов PASS на синтетических типах/Room-моделях; Kotlin синтаксис AndroidTest проверен с API-заглушками; SHA и CRC ZIP PASS; PS-установщик и ограничения AVD проверены статически. **НЕ проверены:** реальный Gradle/Android runtime, результаты 628 реальных записей в Room, нагрузка 5k/20k, скрипты Windows PowerShell 5.1.** Установщик проверяет чистоту Git (кроме известного untracked QA-инструмента), fast-forward origin/main, lock автосинхронизации, хеши, Gradle, откат до commit; пушит только после PASS. QA runner применяет `adb install -r` *обоих APK только на проверенном Pixel_7*, без `pm clear`/`uninstall`; пользовательские данные эмулятора должны сохраниться. Android приложение **по-прежнему читает и пишет JSON**, новый Gate не вызывается им при обычной работе; версия остаётся 1.3.12(20). **0-C.3 Room-primary заблокирован** до достоверных результатов; пользователь сообщил «всё отлично» после предыдущего `Compare`, но полный лог семантического сравнения пока не предоставлен.



**0-C.2-S — НАСТОЯЩИЙ ZIP прошёл проверку на ПК и передан в `Pixel_7`; системный SAF импорт/экспорт ещё НЕ выполнен (14.11.2026, 17:12).** По журналу пользователя: Windows PowerShell v4 `SelfTest` 5/5 PASS; `REAL_ZIP_CRC_MANIFEST_SHA256=PASS`; исходный ZIP SHA-256 `4833b926802e164c05661774eed449083128f64c8707025f6ac9b1576dcad95b`, 628 треков, 3 плейлиста, 0 архивных треков, 8 файлов MIDI/аккордов; установлено приложение `com.example.freelocalplayer` v1.3.12(20); строго идентифицирован `PIXEL_7_VERIFIED=emulator-5554`; `EMULATOR_IMPORT_ZIP_SHA256=PASS`, `RESULT=READY_FOR_MANUAL_SAF_RESTORE`, `REAL_BACKUP_PREPARE=PASS`, `PHYSICAL_PHONE_CHANGED=NO`. Сессия: `.patch-backups/real-zip-qa-20261114-171242-4833b926`; импортный файл Downloads `FLP-IMPORT-20261114-171242-4833b926.zip`, имя для SAF-экспорта после восстановления `FLP-RESTORED-20261114-171242-4833b926.zip`. Следующие действия исключительно в Android UI `Pixel_7`: Backup/Restore > Restore ZIP > выбрать импортный файл > подтвердить замену текущих данных ЭМУЛЯТОРА после проверки предварительного состава 628/3/8, затем экспорт ZIP в Downloads под заданным именем. После этого запустить `tools/Verify-FreeLocalPlayer-RealBackup.ps1 -Mode Compare -AvdName Pixel_7` для строгого сопоставления исходного и реэкспортированного ZIP. **Пока подтверждены только целостность, доставка на эмулятор и версия APK — не подтверждены факт восстановления, сопоставление JSON/файлов, работоспособность аудио без оригинальных медиа и Room-primary**. Приложение и телефон не изменялись инструментом; 0-C.3 заблокирован.



**0-C.2-R — семь Android-тестов восстановления ZIP пройдены при повторной диагностике (13.11.2026, Pixel_7), исходный crash не объяснён.** Исходники QA: [`4b88f0d`](https://github.com/OlegRakhimov/MyPlayer/commit/4b88f0d7a5ac11446457b824ef69909b7217e4c8); Gradle и установка двух тестовых APK ранее PASS. Первоначальный запуск 20:21 остановился на пятом тесте `restoredCatalogCanBeMappedToRoomWithoutChangingAnyUuid`: после четырёх PASS Android Instrumentation сообщил `shortMsg=Process crashed`, без stack trace в `instrumentation.txt`. В повторной диагностике 20:33 на проверенном AVD `Pixel_7` **тот же пятый тест отдельно PASS, затем весь набор из семи тестов PASS** (`RESULT=PASS_ALL_7_BACKUP_TESTS`). Отчёты пользователя: `.patch-backups/backup-crash-diagnostic-20261113-203301/{single-test-5,full-suite-7}-{instrumentation,logcat}.txt`. Это подтверждает успешный повтор, **но не объясняет исходный процессный сбой и не доказывает отсутствие нестабильности**. Выполненные тесты работают на синтетических данных с изолированными prefs/files и `file://` ContentResolver, не на настоящем пользовательском ZIP и не через системный SAF picker. JSON по-прежнему основной источник. До перевода на Room (0-C.3) остаётся обязательным проверенное восстановление настоящего ZIP на отдельной тестовой установке, анализ прежнего crash-logcat при воспроизводимости, проверка перезапуска/сопоставления данных и производительность. Основной телефон не изменён этим тестовым запуском.



**0-C.2-QA — ПРИНЯТЫ реальные инструментальные тесты Room на Android-эмуляторе (13.11.2026, Pixel_7).** Код QA опубликован в [`d5e8abf`](https://github.com/OlegRakhimov/MyPlayer/commit/d5e8abf841c36e441707a065a51422e8d888305b); первоначальная остановка из-за отсутствующего `sdk_gphone16k_x86_64` устранена выбором существующего `Pixel_7` через `-AvdName Pixel_7`. Журнал пользователя: `BUILD SUCCESSFUL in 23s`, 70 задач `up-to-date`, `GRADLE_QA_BUILD=PASS`, SHA-256 обоих APK зафиксированы, две установки APK на эмулятор `Success`, `EMULATOR_APKS_INSTALLED=PASS`, `ROOM_ANDROID_INSTRUMENTATION=PASS tests=4`, запуск `MainActivity`, `ROOM_APP_SHADOW_OPEN=PASS`, `PHYSICAL_PHONE_CHANGED=NO`, `RESULT=SUCCESS ROOM_EMULATOR_QA_PASS`, `ROOM_QA_COMPLETE=PASS` (19:59). **Закрыта только проверка синхронизации/схемы Room на эмуляторе.** Приложение всё ещё использует JSON как источник истины; незакрыты SAF ZIP восстановление/откат на тестовой установке, сопоставление импортированных пользовательских данных с Room, performance 5k–20k, аппаратная проверка 0-C.2 и отказоустойчивость при гибели процесса. Переключение источника истины в 0-C.3 не разрешено до этих ворот.



**0-C.2 — Room shadow: Android Gradle, модульные тесты, экспорт схемы и GitHub PASS (13.11.2026), APK и device-test ещё не подтверждены.** Исходники [коммита `f2c641a`](https://github.com/OlegRakhimov/MyPlayer/commit/f2c641a04ba4f4f4f73d928cb41fd0976e3348f2) сверены по GitHub. Пользователь сообщил: `BUILD SUCCESSFUL in 3m 44s`, 70/70 задач выполнено, `GRADLE_BUILD_AND_TESTS=PASS`, `ROOM_SCHEMA_V1_EXPORTED=PASS count=1`, `GITHUB_PUSH=PASS REMOTE_HASH_VERIFIED=YES`, `RESULT=SUCCESS PATCH_INSTALLED_AND_PUSHED`. Room 2.8.5/KSP добавлены, schema `app/schemas/.../1.json` опубликована; `versionCode=20`, `versionName=1.3.12`, прежний `applicationId`. JSON остаётся основным источником, Room только транзакционная проверяемая копия. **Не подтверждены:** `adb install -r` на физическом Android, запуск `ShadowRoomMigrationDeviceTest` (in-memory SQLite) на эмуляторе, лог `FLP_ROOM_MIRROR`, сравнение данных, тест SAF ZIP-импорта/отката и производительность 5k–20k. Пока не переключать чтение на Room, этап P0 не закрывать.



**0-B.1 — опубликовано, пользователь сообщил «всё хорошо» (12.11.2026).** Исходники [коммита `232d932`](https://github.com/OlegRakhimov/MyPlayer/commit/232d93239db36786a6f8724aa10ba8e01376ff9e) проверены в GitHub; `versionName=1.3.10`, `versionCode=18`, прежний `applicationId`. При удалении трека сохраняется архивная запись со старым UUID, текстом, метаданными и позициями в плейлистах; при однозначном повторном обнаружении восстанавливаются связи, а неоднозначное совпадение не используется. Локально 22/22 Kotlin unit-теста PASS. **Не предоставлены:** отдельный лог Gradle и сценарий удаления/повторного добавления на тестовом телефоне. Полная переиндексация с новым MediaStore ID не решена автоматически.

**0-C.1 — Android Gradle, GitHub и установка APK PASS (13.11.2026), сценарии сохранности требуют QA.** Опубликовано в коммите [`a205546`](https://github.com/OlegRakhimov/MyPlayer/commit/a2055460fee2914ac725413dcdc14c55a1540f2b); исходники `1.3.11` / code `19`, без смены `applicationId`, UUID и JSON. По журналу пользователя: `BUILD SUCCESSFUL in 1m 7s`, 41 задача (14 выполнено, 27 up-to-date), `GRADLE_BUILD_AND_TESTS=PASS`, `LOCAL_COMMIT=a205546...`, `GITHUB_PUSH=PASS REMOTE_HASH_VERIFIED=YES`, `RESULT=SUCCESS PATCH_INSTALLED_AND_PUSHED`, `Performing Streamed Install / Success / ANDROID_INSTALL=PASS`. Перенесены сериализация/запись полной библиотеки из главного потока, фоновое объединение результатов сканирования, барьер очереди перед SAF ZIP; 7/7 локальных тестов writer PASS. **Ещё не подтверждены** реальный экспорт ZIP после быстрой правки, поворот/перезапуск без потерь, аварийное завершение, проверка на большой медиатеке и восстановление из ZIP на отдельной тестовой установке. Room отсутствует; мгновенное убийство процесса до фонового commit по-прежнему может потерять последнюю правку. [Матрица проверки](STABILIZATION_0C1_ASYNC_LIBRARY.md).



**0-A SAF ZIP — Gradle и GitHub PASS (12.11.2026), на телефоне не подтверждено.** Опубликовано в коммите [`a5e55e6`](https://github.com/OlegRakhimov/MyPlayer/commit/a5e55e66d621ae020111557433ed7169448fc3fb): экспорт через SAF, проверка ZIP и хешей, подтверждаемый импорт с внутренней защитной копией и аварийным откатом, семь unit-тестов правил ZIP. Журнал пользователя: `BUILD SUCCESSFUL in 1m 28s`, 41 задача (17 выполнено, 24 актуальны), `GRADLE_BUILD_AND_TESTS=PASS`, `GITHUB_PUSH=PASS`, `RESULT=SUCCESS PATCH_INSTALLED_AND_PUSHED`. В коде `versionName=1.3.9`, `versionCode=17`, `applicationId=com.example.freelocalplayer` **не изменён**. [Инструкция и матрица проверки](STABILIZATION_0A_SAF_BACKUP.md) опубликованы. **Ещё требуется:** установить APK поверх старого без удаления данных, экспортировать ZIP на телефоне, проверить копию архива на ПК, выполнить импорт/откат на отдельной тестовой установке. До этого 0-A НЕ закрывать.


По подтверждённому аудиту 12.11.2026 дальнейшие UI-патчи (включая подготовленный, **но не установленный v10**) **не имеют приоритета над защитой пользовательских данных**. Ссылка на проверенные факты, исключения и тесты: [аудит](ARCHITECTURE_STABILIZATION_AUDIT_2026-11-12.md), [задача #2](https://github.com/OlegRakhimov/MyPlayer/issues/2).

**Порядок работ:**

1. **P0-04, сначала экспорт/импорт:** SAF ZIP (TXT/LRC, аккорды, MIDI, списки, алиасы, настройки) с восстановлением на отдельной тестовой установке; без удаления старого APK.
2. **P0-01, стабильные ID:** сохранить существующие UUID, добавить устойчивые псевдонимы и миграцию связей, не заменяя все идентификаторы механически значением MediaStore ID или хешем имени.
3. **P0-02, фоновые операции:** убрать полную JSON-сериализацию и обработку большой медиатеки из главного потока; вынести Regex; затем Room с миграционными тестами и атомарным восстановлением.
4. **P0-05, Android identity и release:** выбрать окончательный applicationId вместо `com.example.*` только после проверенного переноса данных между двумя разными пакетами; постоянная release-подпись/keystore, R8 и versionCode.
5. **P0-03, долговременный анализ:** ViewModel + фоновые средства выполнения, подтверждённое поведение при повороте/сворачивании/смерти процесса.
6. **P1, затем:** MediaSession resumption, большая очередь, фильтры MediaStore, доступность и performance baseline, видео; P2 — улучшение качества распознавания только после эталонной выборки и метрик.

**Статус на 12.11.2026:** документирование рисков **завершено**; перечисленные P0-механизмы **ещё не реализованы и не проверены на телефоне**. Текущие исходники `applicationId=com.example.freelocalplayer`, `versionCode=20`, `versionName=1.3.12` (0-C.2, коммит `f2c641a`; APK 0-C.2 ещё не подтверждён на устройстве). Все Android-сборки и миграции делаются в единственном проекте `C:\Users\Oleg\Documents\AndroidProjects\FreeLocalPlayer`, с резервными копиями внутри него. Запрещены потеря старых данных, удаление приложения перед бэкапом и `git reset --hard`.

**Промежуточная приёмка:** сохранять факт источника отдельно для кода, Gradle, тестов миграции, установки/обновления на телефоне и восстановления тестового ZIP; статус P0 не поднимать до зелёного результата последнего шага. Метрики (cold-start p50/p95, CPU/RSS/батарея при анализе 4 мин, скан 1000/5000/20000, jank) должны иметь baseline и устройство; предложенные целевые бюджеты в аудите не являются измеренными значениями.

---

## Актуализация после v5: что подтверждено

| Этап | Изменение | Подтверждение | Дальнейшее действие |
|---|---|---|---|
| Исходники и синхронизация | Импорт текущего FreeLocalPlayer в `MyPlayer/main`, Windows GitHub Sync | Первоначальная публикация и последующие push подтверждены; задача раз в 5 минут установлена | Отдельно проверять журнал регулярного выполнения планировщика; не обещать синхронизацию при выключенном ПК или без интернета |
| v3 — UI foundation | Studio Dark / Soft Light / Violet Glass, сохранение выбора, уменьшение анимаций, скруглённые формы, главный Play/Pause, полупрозрачные панели | Gradle сборка и unit-тесты прошли, GitHub commit `6acfcb5` | Проверка всех тем и основных сценариев на телефоне |
| v4 — контраст | Цвета важных значков, вторичных надписей, вкладок, мини-плеера | Gradle сборка и тесты прошли, GitHub commit `ffd84be`; пользователь сообщил, что на телефоне «всё норм» | Продолжить регрессионную проверку на других экранах |
| v5 — библиотека | Компактная панель импорта, поиск с очисткой, сортировка и фильтр TXT/LRC, более свободные строки, действия через меню, предыдущий трек в мини-плеере | Gradle сборка и unit-тесты прошли, GitHub commit `c00e594` | Проверить обновление на физическом Android, сохранность пользовательских данных и UX |
| v6 — музыкальный экран | Предыдущая/следующая, Play/Pause и очередь доступны в музыкальном режиме; переключатель автопрокрутки LRC; согласование цветов текста/аккордов | `BUILD SUCCESSFUL`, `GRADLE_BUILD_AND_TESTS=PASS`; GitHub commit `ac19a6b`; пользователь 11.11.2026 сообщил «отлично все работает» после проверки интерфейса | Полная регрессия TXT/LRC, MIDI, A–B и сохранённых данных остаётся отдельным контролем |

| v7 — настройка меток LRC | Редактирование времени отдельной строки, ±0,1/0,5 с и привязка к текущей позиции; защита меток слов Enhanced LRC | **Опубликовано:** коммит `caf24f8` от 11.11.2026; содержимое проверено по Git; пользователь 12.11.2026 подтвердил `BUILD SUCCESSFUL` и `V7_BUILD_AND_TESTS=PASS` (41 задача up-to-date) | Проверить APK на физическом Android и отсутствие потери TXT/LRC |

| v8 — настройка меток слов Enhanced LRC | Редактор точной подстройки времени отдельных слов с защитой временных меток | **Опубликовано:** GitHub commit `02a5c28` от 12.11.2026; 11 автономных Kotlin-тестов PASS; пользователь сообщил «всё хорошо»; полный журнал Android Gradle v8 не предоставлен | В рамках полной регрессии проверить слова Enhanced LRC, сохранение TXT/LRC и отсутствие потери данных |
| v9 — музыкальная практика A–B | Повтор A–B в нижней панели и музыкальных инструментах; пауза цикла и шаг ±100 мс | **Опубликовано:** коммит `4ccdb04`; пользователь подтвердил «всё есть, всё работает» после поиска и установки функций | Полная регрессия Media3, LRC/MIDI и данных — в общей приёмке |
| v10 — список сохранённых исправлений аккордов | Подготовлены просмотр правок текущей песни по времени/источнику, переход к метке, изменение имени аккорда и подтверждаемое удаление одной правки | **Только пакет:** 11/11 локальных Kotlin-проверок, ZIP и SHA-256 PASS; Gradle Android, GitHub push и проверка на телефоне ещё не выполнены | Проверить сборку и тесты на Windows, затем интерфейс на устройстве; алгоритмы распознавания и MIDI не трогать |

**Последний независимо подтверждённый коммит приложения:** `4ccdb042fb8b81971a3e03bec265d61019101d5e` (v9). Новые изменения учитывать только после подтверждения GitHub, сборки и устройства по отдельности.

## Дизайн Modern Fusion — часть общей дорожной карты

Пять выбранных направлений **не равны пяти отдельным темам**. Они распределены по функциям и экранам:

| Направление | Где применяется | Текущий статус |
|---|---|---|
| **Material 3 Expressive** | Палитры, навигация, единый стиль кнопок, типографика, состояния, анимации | Частично реализовано v3–v5. Полноценная переработка навигации и доступности впереди |
| **Glassmorphism** | Отдельные стеклоподобные панели экрана музыки и видеоконтролов | Реализована полупрозрачная стилизация **без настоящего blur**; более сложные эффекты не обещаются без проверки производительности |
| **Neumorphism** | Сдержанные объёмные акценты у Play/Pause, не на всех кнопках | Тень центральной кнопки реализована; требуется оценка на устройствах |
| **Professional** | Каталог, папки, фильтры, сортировка, очереди, инструменты музыкальной практики | Улучшения медиатеки v5 реализованы в коде; продвинутый рабочий экран/инструменты ещё требуют работы |
| **Cinematic** | Видео, миниатюры, полное изображение и скрывающиеся панели управления | Градиентные видеоконтролы имеются; целостная библиотека кино, UX поворота и разных экранов не завершены |

### Отложенный порядок UI-работ (только после устранения P0 выше)

1. **Приёмка v5 на телефоне**: сортировка, фильтрация, поиск, меню файлов, избранное, плейлисты, мини-плеер; убедиться, что обновление поверх APK не удаляет данные.
2. **Экран музыки**: завершить визуальную иерархию «обложка / текст + аккорды / ноты», убрать дублирования (UX-01/02/06/14), обеспечить ключевые действия за 1–2 касания и читаемость.
3. **LRC / Enhanced LRC / аккорды**: правильная привязка к текущему треку, контраст текущей строки, точная разметка строк и слов, не исчезающее управление редактора (UX-03/04/08/11); сохранение ручных правок.
4. **Professional инструменты**: MIDI/Piano Roll, A–B, скорость, очередь и пять режимов воспроизведения, без подмены реального разделения звука на инструменты.
5. **Cinematic видео**: видеобиблиотека с миниатюрами, полноэкранный режим, касание для показа/скрытия панели, корректные жесты, ориентация и субтитры; подтвердить на устройстве.
6. **Завершение тем/адаптивность**: оценка контраста всех трёх палитр, доступность при увеличенном шрифте, маленькие и большие экраны, TalkBack, уменьшение анимаций, производительность.
7. **Итоговая регрессия**: Gradle сборка, модульные/UI-тесты, проверка на физическом телефоне и обновление без потерь. Нельзя объявлять задачу закрытой только по факту успешного Gradle.

### Строгие правила синхронизации и статусов

- **Один локальный проект:** `C:\Users\Oleg\Documents\AndroidProjects\FreeLocalPlayer`. Не создавать каталоги версий рядом.
- GitHub `OlegRakhimov/MyPlayer` — зеркальная версионная копия; локальный планировщик синхронизации выполняется при работающем компьютере, активной сессии и доступном интернете. При конфликте — остановка без force push, reset и удаления данных.
- Любой патч: SHA-256 исходников → резервная копия внутри `.patch-backups` → изменение → Gradle → проверка на устройстве → коммит и push при успешной автоматизированной проверке. Факт пуша не заменяет ручную проверку.
- Issue #1 фиксирует исполнение дизайн-этапов, но **не должен дублировать или заменять эту дорожную карту**. Для иных крупных работ создавать связанные задачи при необходимости.
- Статусы: **✅ на устройстве**, **🟡 в исходниках / сборке**, **🔧 дефект или нужна проверка**, **⬜ план**. Отсутствие подтверждения нельзя помечать ✅.

---

## Архивная база: дорожная карта, редакция 2 (сохранена для трассировки решений)

> Ниже полный текст редакции v2 от 11 ноября 2026 года, до интеграции Modern Fusion. Её исторические утверждения о недоступности исходников и предыдущих версиях не отражают текущее состояние GitHub; актуальная сводка выше имеет приоритет.

# FreeLocalPlayer («Мой плеер») — дорожная карта, редакция 2

**Дата редакции: 11 ноября 2026 года.** Пересмотрено после замечаний пользователя по интерфейсу версий 1.3.3–1.3.8, ошибок установки патча 1.3.7 и ошибок архивирования исходников PowerShell.

**Источник данных:** история разработки, замечания и скриншоты пользователя, результаты тестирования на телефоне и доступные патчи. **Ограничение:** полный актуальный проект Windows ещё не получен и не прошёл независимую ревизию; наличие кода не означает успешную сборку или подтверждение на устройстве.

**Главное решение редакции 2:** до добавления новых возможностей провести аудит фактического проекта и довести UX без потери функций. Нельзя считать исправление завершённым лишь потому, что собран ZIP с патчем.

## 1. Конечная цель

Сделать **полностью автономный Android-плеер без рекламы, подписки, аккаунтов и обязательного интернета**, одновременно пригодный для обычного прослушивания и для разбора музыки. Плеер не должен становиться сложнее с каждым добавлением функции: повседневное управление остаётся простым, профессиональные инструменты открываются по запросу.

**Неприкосновенное требование:** любое улучшение внешнего вида не должно удалять, скрывать без очевидного доступа или ломать ранее работавшие команды. Для каждого изменённого экрана ведётся матрица доступности действий и регрессионные проверки.

**Ожидаемый пользовательский сценарий:** приложение автоматически показывает доступную музыку и видео; пользователь выбирает песню или плейлист; видит обложку либо синхронизированный текст и аккорды; может переключать режимы очереди, скорость и повтор; при необходимости открывает редактирование текста, разметку караоке, нотный экран или инструменты анализа. Для видео — полноэкранный просмотр с управлением, которое появляется и скрывается по касанию.

### Целевая структура экранов

1. **Медиатека:** «Музыка», «Видео», «Плейлисты», поиск, фильтры, обложки и кадры видео. Ненавязчивый мини-плеер снизу.
2. **Сейчас играет (аудио):** название один раз; обложка / «Текст + аккорды» / «Ноты»; время и компактное управление, разворачиваемое по нажатию; доступные с одного действия очередь и режимы воспроизведения.
3. **Видео:** изображение во весь экран; однократное касание показывает/скрывает транспортное управление, жесты и системные кнопки не должны конфликтовать.
4. **Музыкальный практикум:** ноты по инструментам, фортепианная дорожка, табулатура, аккорды и ручные исправления, повтор A–B, сдвиг синхронизации. Всё это не занимает место при обычном прослушивании.
5. **Редактор:** отдельный полноэкранный TXT/LRC с управлением клавиатурой; отдельная точная разметка времени строк и слов.

## 2. Правило статусов

- **✅ Подтверждено пользователем** — наблюдалось в запущенном приложении или прямо подтверждено в чате.
- **🟡 Реализовано в исходниках / патче** — код или архив имеется, но полноценная проверка именно актуальной сборки на телефоне ещё не зафиксирована; возможны ошибки.
- **🔧 Доработать / проверить** — частично работает либо пользователь указал проблему качества и удобства.
- **⬜ План** — готовой проверенной функции пока нет.

Слова «реализовано» и «проверено на устройстве» не взаимозаменяемы.

## 3. Что уже сделано

### 3.1 Базовая медиатека и воспроизведение

| Функция | Статус | Примечание |
|---|---|---|
| Локальное воспроизведение аудио и видео без рекламы и аккаунтов | ✅ | Пользователь собирал и запускал приложение. Kotlin, Compose, AndroidX Media3. |
| Добавление отдельных файлов и папок | ✅/🟡 | Системный выбор файлов/папок; при отдельных форматах требуется проверка прав. |
| Автопоиск файлов через Android MediaStore | 🟡 | Реализован; полное покрытие каталогов зависит от разрешений и Android. |
| Поддержка WAV | 🟡 | Учтена в фильтрах и Media3; совместимость конкретных вариантов WAV зависит от кодека. |
| Фоновое воспроизведение и системное управление | 🟡 | Используется Media3 PlaybackService; нужно проверить уведомление и экран блокировки на устройстве. |
| Предыдущий/следующий трек, пауза, позиционирование по времени | ✅/🟡 | Интерфейс дорабатывался; доступность всех кнопок проверить после последнего обновления. |
| Изменение скорости 0,5–2× | ✅/🟡 | Реализовано; доступ к регулятору должен быть вторичным, но очевидным. |
| Пять режимов очереди: по порядку; повтор списка; повтор одной; случайно; случайно с повтором | 🟡 | Восстановлены в пользовательском интерфейсе патча 1.3.7. |
| Полноэкранное видео и скрытие управления по касанию | 🟡 | Реализовано в последующих патчах; требуется тест на телефоне/разных ориентациях. |
| Обложки MP3 и миниатюры видео | 🟡 | Локальное извлечение; LRU-кэш с лимитом 6 МБ, декодирование небольших изображений. |
| Иконка с неоновым басовым ключом | 🟡 | Подготовлен и применялся патч ресурсов; поведение лаунчера зависит от Android. |

### 3.2 Плейлисты, навигация, очередь

| Функция | Статус | Примечание |
|---|---|---|
| Создание и удаление плейлистов, добавление/удаление песен | ✅ | Базовая функциональность проверялась на телефоне. |
| Изменение порядка записей в плейлисте | 🟡 | Реализован сдвиг элементов; улучшить UX перестановки. |
| Полноэкранный выбор файлов в плейлист, поиск, фильтры, массовое добавление | ✅/🟡 | Исправлялось по скриншотам пользователя. |
| Избранное и недавние композиции | 🟡 | Добавлены в 1.2.2; проверить сохранение после перезапуска. |
| Системные ярлыки при долгом нажатии на значок приложения | 🟡 | Добавлены в 1.2.2; проверить на конкретном лаунчере. |
| Очередь воспроизведения с поиском и переходом к записи | 🟡 | **Патч 1.3.8 подготовлен, подтверждения установки/работы в чате нет.** |
| Компактный мини-плеер внизу библиотеки | 🟡/🔧 | Есть в 1.3.7; проверить удобство и читаемость на маленьком экране. |

### 3.3 Текст, караоке и аккорды

| Функция | Статус | Примечание |
|---|---|---|
| TXT-тексты, импорт и ручное редактирование | ✅/🔧 | В разных версиях редактор перекрывался клавиатурой; проверить итоговый экран. |
| LRC: подсветка и прокрутка строк по времени | 🟡/🔧 | Парсер и UI есть; нужна проверка точности на реальных треках. |
| Enhanced LRC: пословная подсветка, если в файле есть метки каждого слова | 🟡 | **Автоматической генерации этих меток из песни ещё нет.** |
| Ручная разметка начала строк, корректировка времени ±100 мс | 🟡/🔧 | Добавлена отдельная полноэкранная разметка и шкала перемотки; проверить удобство. |
| Привязка текста к конкретному треку | 🟡/🔧 | Исправлялась проблема «новый трек — старый текст»; требуется регрессионный тест. |
| Совместный просмотр «Текст + аккорды» | ✅/🔧 | Работает, но UX и точную привязку аккордов к словам нужно улучшить. |
| Автоматический анализ аккордов локально, без платного API | ✅ | Пользователь подтвердил, что после исправления классификатора 1.2.9 аккорды определяются. |
| Смена аккордов синхронно с песней | ✅ | Пользователь подтвердил. |
| Короткие интервалы анализа около 0,5 с | ✅/🔧 | Быстрые смены появились, но возможны ложные переключения. |
| Фильтрация скачков «Стабильно / Быстро 0,5 с» | 🟡/🔧 | Добавлена в 1.3.1; нужно измерять точность на реальной музыке. |
| Ручная правка аккордов с сохранением поверх анализа | 🟡 | Добавлена в 1.3.0; нужна проверка сохранности при новом анализе. |
| Повтор фрагмента A–B | 🟡 | Добавлен в 1.3.0; интеграцию с компактным UI проверить. |
| Диагностика декодирования, FFT и классификатора | ✅ | Журнал показал обработку почти всего MP3 и ошибку классификации; та была исправлена. |

### 3.4 Ноты и инструменты

| Функция | Статус | Примечание |
|---|---|---|
| Импорт MIDI и чтение дорожек | 🟡 | Реализовано в музыкальном режиме. |
| Отображение нотного стана / Piano roll | 🟡 | Ноты отображаются при наличии MIDI. |
| Предполагаемая табулатура гитары/баса | 🟡 | Отображение из MIDI — не гарантированное восстановление аппликатуры. |
| Выбор партии вокала, баса, гитары, клавишных, ударных и других | 🟡 | Основан на доступных дорожках MIDI; не означает, что эти партии выделены из MP3. |
| Ручной сдвиг MIDI относительно музыки | 🟡 | Реализован и сохраняется для композиции. |
| **Автоматическое выделение всех инструментов из MP3/WAV** | ⬜ | Ещё нет. Нужны source separation и транскрипция, устройства/лицензии требуют проверки. |
| **Автоматическое получение точных нот каждого инструмента из аудио** | ⬜ | Ещё нет. Наличие аккордов не равнозначно расшифровке нот. |

## 4. Последовательность версий (контекст изменений)

| Версия | Основное изменение |
|---|---|
| 1.0–1.1.1 | Базовый плеер, файлы, плейлисты, тексты, полноэкранное добавление, MediaStore. |
| 1.2.0–1.2.2 | MIDI/музыкальный режим, ярлыки лаунчера, избранное, недавние, видимая кнопка нот. |
| 1.2.3–1.2.6 | Обложки/миниатюры, WAV, локальные аккорды, интервалы по времени и объединение с текстами. |
| 1.2.7–1.2.9 | Viterbi, диагностика, исправление классификатора. Работу аккордов пользователь подтвердил. |
| 1.3.0–1.3.2 | Быстрая временная сетка, ручная правка, A–B, стабильность, полноэкранная разметка LRC. |
| 1.3.3–1.3.6 | Крупная переработка UX, работа над контрастом, текстами и видеоуправлением, точная перемотка разметки. |
| 1.3.7 | Возвращены видимые режимы воспроизведения; унификация мини-плеера и транспортного управления. |
| 1.3.8 | Очередь воспроизведения. **Архив патча создан; статус установки не подтверждён.** |

## 5. Замечания пользователя, которые нужно закрыть в первую очередь

> Это перечень дефектов UX/регрессий, выявленных по скриншотам и в ходе обновлений. Отдельные проблемы могли частично исправляться в патчах 1.3.4–1.3.8, но **не переводятся в статус «исправлено на устройстве» без повторного теста**.

| № | Замечание / воспроизводимый сценарий | Требование к конечному поведению | Приоритет |
|---|---|---|---|
| UX-01 | На экране одновременно значок карандаша и большая надпись «Редактировать текст» | **Одна** очевидная команда редактирования, без дублирования | P0 |
| UX-02 | Название песни повторяется в заголовке и внутри области текста | Одно название в панели трека; содержимое TXT/LRC не терять — если заголовок есть в самом пользовательском файле, отделить его визуально, но не удалять из данных автоматически | P1 |
| UX-03 | При запуске следующей песни остаётся текст предыдущей | Текст, LRC, ручные исправления и прокрутка строго привязаны к ID/URI активной композиции; чужой текст не показывается | **P0** |
| UX-04 | Текст расположен слишком низко и не виден при воспроизведении | Контент занимает главную часть экрана; транспортная панель компактна; видимая строка остаётся в области просмотра | **P0** |
| UX-05 | Надписи «Мой плеер», значок ноты и прочие элементы сливаются с фоном | Тема с читаемыми цветами текста/иконок во всех состояниях; проверка светлой и тёмной темы, маленького экрана, крупного шрифта | **P0** |
| UX-06 | Повторяется «Текст + аккорды TXT/LRC» и предложение добавить текст | Показать одно понятное пустое состояние только при отсутствии текста; лишний служебный заголовок убрать | P1 |
| UX-07 | Большой регулятор скорости перетягивает внимание | Скорость доступна в 1–2 касания, но не конкурирует с текстом и транспортными действиями | P1 |
| UX-08 | Кнопка «Не определён» выглядит как главный элемент; A–B мешает тексту | Неопределённый аккорд — неброская метка только в аккордовом контексте; A–B находится в инструментах практики/аккордов, при этом функция остаётся доступной | P1 |
| UX-09 | После упрощения исчезала кнопка перемотки вперёд; были труднодоступны предыдущая/следующая песни | Постоянный доступ к паузе, предыдущей/следующей; перемотка ±10 с и ползунок должны быть доступны через понятное раскрытие | **P0** |
| UX-10 | Режимы «по порядку / одна в цикле / смешано» потерялись из видимого интерфейса | Пять режимов: по порядку, повтор списка, повтор одной, смешано, смешано + повтор; активный режим ясно обозначен и меняется без поиска в глубоких настройках | **P0** |
| UX-11 | Полноэкранная разметка строк TXT/LRC имела ±3 с, но не имела шкалы всей песни | Непрерывная шкала 0:00–конец, кнопка в начало, ±3 с, пауза и фиксация времени строки; **в редакторе управление не должно автоматически исчезать** | **P0** |
| UX-12 | Поле «Новый плейлист» слишком большое и постоянно занимает место | Маленькая кнопка «+ Плейлист»; ввод имени — в отдельном диалоге только по запросу | P1 |
| UX-13 | При просмотре видео управление мешает картинке | Полный экран по значку; касание показывает/скрывает управление поверх видео; авто-скрытие только в режиме просмотра; устойчивый возврат и поворот | P1 |
| UX-14 | Много разных и дублирующихся панелей, неодинаковое поведение между экранами | Одна система навигации, единый вид transport controls, предсказуемое расположение кнопок; основное содержимое всегда в приоритете | **P0** |
| UX-15 | Очередь 1.3.8 подготовлена, но её работа ещё не подтверждена | Поиск по очереди, переход к треку без пересоздания плейлиста, правильная индикация текущего трека и поведение при shuffle | P1 |

### 5.1. Правила компоновки для аудио и видео

| Экран | Видимо постоянно | По раскрытию / в инструментах | Не должно происходить |
|---|---|---|---|
| Медиатека | Список/поиск, нижний мини-плеер с текущей песней и паузой | Очередь, повторы/перемешивание, расширенное управление | Большие формы создания плейлиста перекрывают список |
| «Сейчас играет»: обложка | Название один раз, обложка, пауза, время, явный доступ к трекам | Скорость, очередь, расширенный transport, повтор/случайный порядок | Функции спрятаны без очевидной точки входа |
| «Сейчас играет»: текст | Максимум места для текущего TXT/LRC; компактное управление и активная строка | Точные инструменты синхронизации, правка, анализ, A–B | Текст другой песни, дублирующиеся заголовки и огромные панели |
| Аккорды / практика | Аккорды и их таймлайн, связанность с текущей песней | Ручная коррекция, A–B, диагностические данные, подстройки | Ошибка распознавания выдаётся как доминирующая кнопка |
| Синхронизация строк | Весь текст, текущая строка, шкала 0:00–конец, пауза, ±3 с, «отметить» | Сдвиг меток ±100 мс, сохранение/отмена | Автоматическое скрытие управления, блокирующее разметку |
| Видео | Видеокадр на весь экран | По касанию: пауза, позиция, навигация, выход, скорость и очередь | Панель скрыта так, что выйти/перемотать нельзя |

### 5.2. Обязательная матрица регрессии после каждого UX-патча

- [ ] Композиция A с текстом A → композиция B с текстом B → композиция C без текста: на экране нет текста A/B, если он не относится к C.
- [ ] Пауза/продолжить; предыдущая/следующая композиция; перемотка вперёд/назад и позиционирование по всей длительности; работа на маленьком экране.
- [ ] Каждый из пяти режимов воспроизведения действительно меняет Media3 и правильно отображается в UI после перезапуска.
- [ ] Очередь открывается из медиатеки и экрана проигрывания, выбирает трек без потери плейлиста; shuffle и repeat сохраняются.
- [ ] Текст TXT/LRC видно при проигрывании; LRC прокручивается и синхронизируется; редактор не закрывается клавиатурой.
- [ ] Разметка строк: начало 0:00, произвольный seek, ±3 с, отметка, исправление ±100 мс, сохранение и повторное открытие.
- [ ] Автоматические аккорды по-прежнему определяются; ручные исправления остаются; A–B и скорость доступны без изменения алгоритма.
- [ ] Видео: полноэкранный вход/выход; появление/скрытие контролов; поворот; при паузе управление остаётся доступным.
- [ ] Плейлисты: создать, переименовать/удалить, добавить/убрать песню, изменить порядок; избранное и недавние не теряются.
- [ ] Крупный шрифт, экран малой высоты, тёмная тема и диалоги: нет обрезанных/невидимых кнопок и заголовков.
- [ ] Обновление поверх установленной версии не удаляет пользовательские данные и не нарушает сохранённые URI.

## 6. Что осталось сделать — в порядке приоритета

### Этап 0. Аудит текущего проекта и стабилизация (P0)

**Статус: обязателен до следующего функционального релиза.** Нельзя считать пользовательское «отлично, продолжаем» формальным логом сборки: для версии 1.3.8 нужно отдельно подтвердить установку и выполнение сценариев.

- Получить актуальный ZIP исходников с компьютера и установить, какая версия действительно в проекте и на телефоне (ориентир — 1.3.7/1.3.8).
- Проверить сборку `:app:assembleDebug :app:testDebugUnitTest`, устранить ошибки, проверить установку поверх предыдущей версии **без потери данных**.
- Провести регрессионные проверки: переключение песен/текстов, 5 режимов воспроизведения, очередь, плейлисты, WAV, видео, MIDI, аккорды, ручные правки, A–B.
- Зафиксировать базовую рабочую версию в одном каталоге и Git (при наличии репозитория), добавить короткий чек-лист приёмки.

**Критерий готовности:** приложение собирается; контрольные сценарии проходят на реальном телефоне; никакая ранее доступная функция не исчезла.

### Этап 1. Единый понятный интерфейс (P0–P1)

**Основа требований — таблица UX-01…UX-15 выше.** Не переписывать музыкальный движок ради переработки экранов. Прежде чем скрыть/перенести кнопку, проверить, как пользователь найдёт её за 1–2 касания.

- Единственная архитектура навигации: медиатека → сейчас играет → дополнительные инструменты.
- Убрать дублирующиеся кнопки/заголовки, выровнять контраст всех состояний (обычное/выбранное/недоступное), проверить маленький экран и крупный шрифт.
- Сохранить постоянную доступность основных действий: пауза, предыдущая/следующая, перемотка ±10 с, очередь, shuffle/repeat; скорость и инструменты в раскрывающемся управлении.
- Полноэкранное видео: появление по касанию и автоскрытие транспортной панели, правильная обработка поворота экрана.
- Сделать компактное создание плейлистов и удобное меню операций; исключить навязчивое отображение форм.

**Критерий готовности:** в обычном режиме в 1–2 касания доступно главное управление; контент (видео/обложка/текст) занимает основную область; нет потерянных функций.

### Этап 2. Тексты и музыкальная практика (P1)

- Гарантировать уникальную привязку текста к текущему медиафайлу (в том числе после удаления/повторного сканирования).
- Довести редактор TXT/LRC, экран разметки, автопрокрутку и видимость текста при открытой клавиатуре.
- Улучшить совместный показ аккордов над строками; точная привязка **к словам** только при достаточных временных метках или после отдельного выравнивания текста и аудио.
- Удобные ручные правки аккордов, история/отмена исправлений, экспорт и импорт разметки.
- Улучшить повтор A–B, смещение времени, масштабирование нот и безопасную работу на паузе.

**Критерий готовности:** пользователь может открыть песню, видеть нужный текст, исправить аккорд и точно разметить строку без борьбы с интерфейсом.

### Этап 3. Качество распознавания аккордов (P1–P2)

- Собрать тестовый набор из 20–30 разрешённых для использования композиций/фрагментов с размеченной правильной гармонией и временем смен.
- Измерять качество по метрикам (согласованность меток, точность выбора аккорда, ложные смены), а не «на глаз» по одной песне.
- Улучшить спектральные признаки: оценка настройки, CQT/chroma, по необходимости HPSS; проверить влияние нормализации, вокала и баса.
- Добавить привязку к музыкальным долям (beat tracking) и временное сглаживание, допускающее быстрые настоящие переходы.
- Отображать уверенность и «не определён» там, где данных недостаточно; не подменять результат вымышленными аккордами.
- Хранить результаты анализа и ручные правки по устойчивому ID/хэшу трека; рассмотреть Room при росте объёма данных.

**Критерий готовности:** количественные метрики на тестовом наборе лучше исходного анализатора; перезапуск и повторный анализ не теряют ручные коррекции.

### Этап 4. Ноты и партии из настоящего аудио (P2)

- Стабилизировать импорт MIDI/MusicXML и визуализацию реальных нот с временем, размером такта и сменами темпа.
- Прототипировать разделение источников (вокал, бас, ударные, прочее) и транскрипцию в MIDI **на ПК**; измерить ошибки.
- После тестов решить, какие модели реально запускать на Android офлайн с приемлемым расходом батареи/памяти; отдельно проверить права на веса и условия распространения.
- Для гитары и баса поддержать ручную коррекцию аппликатуры и табулатуры; MIDI сам по себе не задаёт струну и лад однозначно.
- Не обещать корректное извлечение каждой партии из любой смешанной записи.

**Критерий готовности:** хотя бы на оговорённом тестовом наборе партии и ноты воспроизводимо определяются с известным уровнем ошибок; ручная коррекция доступна.

### Этап 5. Надёжность и завершение продукта (P1–P3)

- Сделать резервное копирование/восстановление плейлистов, текстов, LRC, MIDI, ручных правок; протестировать миграции.
- Пересмотреть хранилище при росте медиатеки: сейчас базовые записи в `SharedPreferences` / JSON, **Room ещё не внедрён**.
- Обработать битые файлы, недоступные URI, удалённые треки, отсутствие разрешений, дубликаты и очень большие каталоги.
- Ограничить кэш/фоновые задачи; проверить память, батарею, холодный запуск, поворот, большие шрифты, TalkBack.
- Прогнать тесты на физическом устройстве, добавить Compose UI-тесты и испытание обновления поверх предыдущего APK.
- Если когда-либо понадобится публикация: отдельно проверить лицензии моделей/библиотек и права на тестовые медиа. **Публикация не является обязательной целью личного плеера.**

## 7. Главные технические принципы

### 7.1. Требования к безопасным патчам и верификации

**Вывод из ошибок патча 1.3.7:** дважды обнаружились несовпадения SHA-256 (`app/build.gradle.kts`, затем `NowPlayingScreen.kt`). Причиной были ошибочная эталонная сумма и неучтённый ранее вручную исправленный импорт `androidx.compose.foundation.layout.width`. В следующих обновлениях:

1. Брать исходную базу только из **актуального ZIP проекта**, а не из предположения, что все прошлые патчи точно установлены.
2. Для **каждого** изменяемого файла сравнивать ожидаемый и текущий SHA-256; при расхождении выводить diff/сводку изменений и останавливаться **без перезаписи**.
3. Не «чинить» несовпадение простым добавлением встреченного хэша в белый список без анализа содержимого; не терять локальные компиляционные исправления.
4. Сначала резервная копия в `.patch-backups` внутри единственного проекта, затем транзакционное применение с откатом при ошибке.
5. Проверять согласованность сигнатур вызовов между файлами, затем `assembleDebug`, unit-тесты и физический телефон. Не заявлять «готово», пока не известен результат тестов.
6. После каждой версии сохранять манифест изменённых файлов, исходные/целевые SHA-256 и результаты проверок.

### 7.2. Архив проекта: устранение проблемы блокировки ZIP

**Фактическое замечание:** архив `FreeLocalPlayer_Source_20261111-131422.zip` создавался с 86 файлами, но PowerShell не мог вычислить SHA-256 и файл нельзя было отправить, потому что ZIP-потоки оставались открыты. `finally` был вставлен отдельной командой, хотя должен быть частью общего блока `try/catch/finally`. Напечатанное `PROJECT_ARCHIVE=SUCCESS` **не являлось доказательством успеха**.

Для всех последующих экспортов исходников:

- Вызывать `Dispose()` для `ZipArchive`, затем файлового потока, **до** вычисления хэша, чтения ZIP и отправки.
- Использовать единый, неразрываемый блок `try/catch/finally` (лучше запускать `.ps1`, либо вставлять весь код единовременно).
- Проверять наличие, чтение записей, число файлов и SHA-256; только затем печатать `SNAPSHOT=SUCCESS`.
- Исключать `.git`, `.gradle`, `build`, `.patch-backups`, APK/AAB, кэши и типовые секреты. При этом ручная проверка исходников на токены обязательна: фильтры имён файлов не обнаруживают секреты внутри Kotlin/Gradle.
- Создавать один ZIP в `Downloads`, не клонируя сам проект. Это архив **исходников**, а не резервная копия пользовательской медиатеки с телефона.

### 7.3. Постоянные правила проекта

- **Одна папка проекта:** `C:\Users\Oleg\Documents\AndroidProjects\FreeLocalPlayer`.
- Патчи применяются только туда. Временная распаковка — в системный `TEMP` с автоматической очисткой; архивы и отчёты для передачи — в `Downloads`.
- Резервные копии проекта — внутри `FreeLocalPlayer\.patch-backups`; не плодить папки `FreeLocalPlayer-v...` рядом.
- Перед изменениями — SHA-256 известных исходников и резервная копия. Если состояние проекта неизвестно, сначала запросить полный ZIP, а не обходить защиту.
- Нельзя ради упрощения интерфейса удалять функцию: меняется место доступа и визуальная иерархия, а не функциональность.
- Локальная работа по умолчанию; никаких облачных API и обязательных подписок для воспроизведения/базового анализа.
- Прямо отделять «код подготовлен», «Gradle собрал», «тесты прошли», «на телефоне проверено».

## 8. Что считать готовым продуктом

**FreeLocalPlayer 2.0 (целевой результат)** — удобный офлайн Android-плеер, который:

1. Находит доступные аудио и видео, показывает миниатюры и мгновенно запускает воспроизведение.
2. Поддерживает очереди, плейлисты, поиск, пять режимов воспроизведения, скорость и фоновый режим без рекламы.
3. Показывает видео на полном экране, а музыку — с обложкой или связанным текстом и аккордами без лишних панелей.
4. Поддерживает синхронизацию LRC / Enhanced LRC, ручное исправление и точную разметку.
5. Определяет аккорды локально с понятной оценкой качества и позволяет их исправлять.
6. Показывает MIDI-ноты и партии, позволяет тренироваться с A–B и изменением скорости.
7. Надёжно сохраняет пользовательские данные и обновляется без потерь.

**Расширенная цель после 2.0:** качественное автоматическое выделение инструментов и распознавание партий непосредственно из MP3/WAV. Это отдельный исследовательский этап, а не уже выполненная функция и не обязательное условие базового стабильного плеера.

## 9. План релизов после версии 1.3.8

| Очередь работ | Цель | Условие перехода |
|---|---|---|
| **Релиз стабилизации (номер пока не присвоен)** | Ревизия загруженного ZIP, исправление UX-01…UX-15, восстановление/проверка навигации без новых экспериментов с функционалом | Сборка + unit/UI-тесты + регрессия на телефоне |
| Следующий релиз удобства | Полировка медиатеки, мини-плеера, текстов, видео и очереди | Нет дублирований, главные действия доступны в 1–2 касания |
| Музыкальный практикум | Точная разметка, корректировки, экспорт, улучшенная привязка аккордов | Данные каждой песни изолированы и сохраняются |
| Аналитический релиз | Измеренное улучшение классификатора и временных меток | Сравнимые количественные тесты, без ухудшения базовых песен |
| Стабильная **2.0** | Бэкап пользовательских данных, миграции, надёжность, доступность | Приёмка всех основных сценариев на устройстве |
| После 2.0 (исследование) | Выделение партий/нот из MP3/WAV | Прототип на ПК и измеренная точность/ресурсоёмкость |

Номера и сроки будущих версий намеренно не зафиксированы: их нельзя обоснованно определить до аудита текущего проекта.

## 10. Ближайший конкретный шаг

1. Закрыть открытые ZIP-потоки PowerShell и проверить, что архив исходников читается, SHA-256 рассчитывается и файл прикрепляется в чат. Если не удаётся — пересоздать архив одной полной командой с корректным `finally`.
2. Прочитать актуальный ZIP проекта, определить действительную версию и изменения относительно 1.3.8, не полагаясь на прежние эталонные SHA вслепую.
3. Зафиксировать полный перечень функций и точек входа до изменения UI, затем закрывать UX-01…UX-15 по убыванию приоритета.
4. Собрать/протестировать на устройстве; только после результатов считать этап завершённым. Новые крупные функции пока не добавлять.

---

**Основа редакции 2:** история диалога, скриншоты и замечания пользователя, доступные патчи до 1.3.8 и журнал двух ошибок проверки SHA-256 при обновлении 1.3.7, а также вывод PowerShell об ошибке блокировки ZIP. Актуальное содержимое папки Windows не читалось напрямую; тесты на физическом телефоне для 1.3.8 не подтверждены.
