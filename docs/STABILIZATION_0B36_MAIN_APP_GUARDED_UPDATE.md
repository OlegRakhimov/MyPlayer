# FreeLocalPlayer — 0-B.3.6: обновление основного APK с защитой пользовательской медиатеки

## Основание

21 ноября 2026 пользователь подтвердил удаление отдельного QA-приложения на Motorola: `QA_UNINSTALL=PASS`, `QA_REMOVED=PASS`, `MAIN_PLAYER_PRESERVED=PASS`, `MAIN_PLAYER_LAUNCH=PASS`, `MAIN_PLAYER_DATA_CLEARED=NO`. Отдельный QA build type и QA-only исходники удалены из GitHub. Продолжаем тестирование **только** `com.example.freelocalplayer` в единственном каталоге `C:\Users\Oleg\Documents\AndroidProjects\FreeLocalPlayer`.

Предыдущие подтверждённые проверки: 41 целевой JVM-тест основных стадий, синтетический UI сценарий в удалённой QA-сборке, подпись установленного и нового основного APK совпадает (сертификат SHA-256 `ADAF14FBA3C90D25842049F39CE56F773F1C14CC90FEE83D56F109E0353C2A4B`). Проверенный ZIP `FreeLocalPlayer-backup-1795256299520.zip`, SHA-256 `FCE3747B369A68052572B4F43179E17D8B8BF2F34964525C416B5D25A3CC0858`, совпадал с текущим на момент проверки каталогом (633 медиа, 3 плейлиста, 0 retired, 9 music-mode файлов). **После повторного открытия основного приложения это совпадение уже не гарантировано; перед установкой проверить заново.**

Новый основной APK из Windows сборки: `app\build\outputs\apk\debug\app-debug.apk`, SHA-256 `8F50B8CFBBF8F149E36E79270053B1451CCF9A73FAF1459D48EE6FA0B47EEEB3`, `com.example.freelocalplayer`, `1.3.12 (20)`. В данном гейте не менять applicationId, versionCode, Room-primary, не создавать QA-пакет.

## Подготовлен Windows PowerShell 5.1 скрипт

Файл `Upgrade-FreeLocalPlayer-0B36-Main-PS51.ps1`, SHA-256 `BE3FF29EEE44B9F6565028FF4C2A4658B35005E72A5403B3CA6EC6C46671D283`. Результаты только в `.patch-backups\main-upgrade-0b36-*`. **На Windows и Motorola ещё не запускался.**

### Режим Preflight (без установки)

1. Разрешает только одну авторизованную физическую Motorola Edge 60 Pro `ZY22LHSVD7`. Требует остановить основной процесс после завершения обычного сохранения; QA-пакета на устройстве быть не должно.
2. Проверяет фактический SHA-256 нового APK, версию/пакет AAPT и подпись `apksigner verify --print-certs`; проверяет сертификат установленного APK путем **чтения** `pm path` + `adb pull`. При расхождении прекращает выполнение.
3. Находит ранее использованный `Diagnose-FreeLocalPlayer-0B32-Backup-Drift-PS51.ps1` в Downloads. Запускает полный read-only анализ `FreeLocalPlayer-backup-1795256299520.zip` из `/sdcard/Download` с проверкой manifest, SHA-256 всех ZIP файлов, значений пяти prefs, девяти music-mode sidecars (фактическое число может отличаться), сверкой с текущим состоянием телефона. При любом несовпадении ZIP с телефоном **не допускать установку**, новый ZIP экспортировать и проверить отдельно, не пытаться обходить гейт.
4. До и после анализа ZIP проверяет SHA-256 всех имеющихся XML preferences (включая optional playback checkpoint) и sidecar файлов, а также путь установленного APK.

### Режим InstallAndLaunch (явное обновление основного пакета)

Полностью повторяет Preflight. Затем только при полном PASS запускает **ровно одну** команду `adb install --no-incremental -r app-debug.apk` с сохранением данных. **Никаких `adb uninstall`, `pm clear`, downgrade, либо установки QA.**

Сразу после успешной установки, **до первого запуска MainActivity**, повторно проверяет SHA-256 приватных настроек и music sidecar, установленную версию и сертификат установленного обновления. Если проверка сохранности не проходит — останавливается и требует расследования, **автоматического отката нет**. При PASS открывает `com.example.freelocalplayer/.MainActivity`.

## 21.11.2026 13:52 — ошибка отсутствующей диагностики, самодостаточный скрипт v2

Пользователь выполнил `InstallAndLaunch`: проверки APK SHA-256, сертификата, версии, Motorola, отсутствия QA и текущего XML прошли, после чего получено `RESULT=FAILED BACKUP_DIAGNOSTIC_SCRIPT_MISSING_IN_DOWNLOADS`. В исходном скрипте эта проверка расположена **перед** `adb install`; по представленному журналу обновление APK не началось. Это ошибка зависимости PowerShell, не доказательство повреждения медиатеки.

Подготовлен `Upgrade-FreeLocalPlayer-0B36-Main-SelfContained-v2.ps1`, SHA-256 `E441ED498BCFCF6FA194BD3A42EDF3B1F04EF5CEBEBB39E383EE998FE065B747` (47 701 байт). Диагностика ZIP включена внутрь скрипта байт-в-байт, её SHA-256 `34D9D93BAB5C2222BFF3AC534115731CA124D142E0241EBDA7061A0BFE834EB2` проверяется перед запуском. Временный проверочный файл создаётся только в `.patch-backups/main-upgrade-0b36-*` и удаляется после завершения диагностики. Отдельный файл в `Downloads` **не нужен**. Если явно передан `-DiagnosticPath`, он тоже проверяется по SHA-256.

Проверки ZIP/сертификата/пакета/версии/SHA APK, сверки private XML и MIDI/chord sidecar до/после сохранены. В режиме `Preflight` нет установки; режим `InstallAndLaunch` использует **только одну** команду `adb install --no-incremental -r` для основного пакета, без uninstall, pm clear и обхода проверки ZIP.

**Статус v2:** побайтовое соответствие встроенной диагностики эталону и статические проверки подтверждены. **Windows PowerShell/Android испытание v2 ещё не выполнялось**; APK не считается обновлённым до нового журнала.

## 21.11.2026 14:00 — фактический PASS основного обновления на Motorola

Получен реальный журнал пользователя `Upgrade-FreeLocalPlayer-0B36-Main-SelfContained-v2.ps1`. **Режим Preflight: PASS**: `BACKUP_DIAGNOSTIC=PASS source=bundled_sha256_verified`, `MAIN_APK_SHA256=PASS 8F50B8CFBBF8F149E36E79270053B1451CCF9A73FAF1459D48EE6FA0B47EEEB3`, `MAIN_APK_PACKAGE_AND_VERSION=PASS`, `MAIN_APK_CERTIFICATE=PASS ADAF14FBA3C90D25842049F39CE56F773F1C14CC90FEE83D56F109E0353C2A4B`, физическая Motorola PASS, процесс плеера остановлен, QA-пакет отсутствует, `CANONICAL_XML_SHA256_BEFORE=49284D7B76742D2B2AC6E23F187FEAD3CAB55057F80E3173DBE7101871D9721A`, `BACKUP_CANONICAL_JSON_MATCH=PASS`, `PHONE_AND_ZIP_CATALOG_COUNTS tracks=633 playlists=3 retired=0`, `BACKUP_PHONE_MUSIC_FILES_MATCH=PASS count=9`, `BEFORE_INSTALL_PRIVATE_DATA_UNCHANGED=PASS files=15`, сертификаты совпали, `MAIN_APP_APK_INSTALL=NO`, `RESULT=SUCCESS FREELOCALPLAYER_0B36_MAIN_PREFLIGHT`.

**Режим InstallAndLaunch: PASS**: все проверки выполнены повторно и подтвердили соответствие существующему ZIP SHA-256 `FCE3747B369A68052572B4F43179E17D8B8BF2F34964525C416B5D25A3CC0858`. Далее `MAIN_APP_UPDATE=START same_package_preserve_data`, `MAIN_APP_UPDATE=PASS`, версия `1.3.12 (20)`, `AFTER_INSTALL_PRIVATE_DATA_UNCHANGED=PASS files=15`, `PHONE_APP_DATA_PRESERVED=PASS`, `POST_INSTALL_APK_SIGNATURE_AND_PACKAGE=PASS`, `MAIN_APP_LAUNCH=PASS`, `QA_APP_INSTALL=NO`, `PHONE_DATA_CLEARED=NO`, `RESULT=SUCCESS FREELOCALPLAYER_0B36_MAIN_UPDATED_AND_LAUNCHED`, `RESULT=SUCCESS FREELOCALPLAYER_MAIN_UPDATED`.

**Итог:** безопасное обновление основного приложения **подтверждено на телефоне**. Пользовательские файлы на момент проверки непосредственно после установки сохранились; приложение запущено. **Не утверждать**, что 633 трека, тексты и аккорды повторно проверены уже после запуска, что пробное восстановление старого UUID прошло на живом каталоге или что все UI-функции работают: этого в журнале нет.

**Проверка исходников `PlayerApp.kt`:** в заголовке показывается `library.media.size`, рядом иконка сохранения «Резервная копия и восстановление»; в диалоге пункт `Восстановить связь удалённого трека (N)` имеет `enabled = library.retiredMedia.isNotEmpty()`. Для ранее проверенного `retired=0` ожидается неактивный пункт и подсказка `В архиве медиатеки нет удалённых записей.`. Это **не ошибка UI**. Не создавать записи путём удаления настоящих медиа ради теста.

Следующий этап — ручная production post-launch проверка количества медиа/списков, реальных текстов, MIDI/аккордов, перехода по вкладкам, работы воспроизведения и восстановление после штатного перезапуска. Если после использования изменится canonical SHA-256, **не сравнивать старую копию как актуальную** без нового экспорта. Не запускать импорт резервного ZIP: он заменяет нынешние данные.

## Приёмочные условия

- `RESULT=SUCCESS FREELOCALPLAYER_0B36_MAIN_PREFLIGHT` — актуальный ZIP и неизменные приватные файлы, установка ещё не выполнялась.
- `RESULT=SUCCESS FREELOCALPLAYER_0B36_MAIN_UPDATED_AND_LAUNCHED` — основное приложение установлено поверх старого и запущено, приватные prefs/sidecar не изменились **до первого запуска**; это **не** функциональная проверка всех 633 медиа.
- После запуска вручную проверить медиатеку, плейлисты, тексты, MIDI, аккорды, скорость, режимы, сохранение после перезапуска и отсутствие потери связей.
- `retiredMedia=0` на предыдущем ZIP: кнопка реального восстановления будет неактивна без старых удалённых записей. **Не удалять реальные пользовательские песни**, чтобы заставить появиться восстановление. Для положительного сценария потребуется отдельный безопасный подход, согласованный с пожеланием пользователя работать в основном приложении.

## Ограничения

Подпись APK, целостность ZIP и сохранение файлов при обновлении *не доказывают* правильность данных после дальнейшего сканирования, выбора пользователем media URI или отказа Android в разрешении. Не объявлять этап PASS до реального журнала Motorola. Скрипт проверен **статически** (одна точка установки после fail-closed ворот, ноль команд uninstall/clear), но запуск в PowerShell 5.1 ещё не подтверждён.
