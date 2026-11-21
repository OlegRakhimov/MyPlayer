# FreeLocalPlayer — 0-B.3.3: предварительная проверка APK без установки

## Исходные факты, 21 ноября 2026

- Windows Gradle 0-B.3.1: 41/41 целевых JVM-тестов PASS; debug APK успешно собран; на Motorola не установлен.
- На физической Motorola Edge 60 Pro установлен `com.example.freelocalplayer` версии `1.3.12 (20)`.
- 0-B.3.2: **проверенный актуальный SAF ZIP** `FreeLocalPlayer-backup-1795256299520.zip`, SHA-256 `FCE3747B369A68052572B4F43179E17D8B8BF2F34964525C416B5D25A3CC0858`, `SAF_ZIP_MANIFEST_SHA256=PASS entries=10`, `BACKUP_CANONICAL_JSON_MATCH=PASS`, совпали все пять архивируемых SharedPreferences и 9 sidecar-файлов MIDI/аккордов, `RESULT=SUCCESS EXISTING_SAF_ZIP_MATCHES_CURRENT_PHONE_DATA`.
- В медиатеке 633 трека, 3 плейлиста, `retiredMedia=0`. Нет настоящих архивных записей для проверки возврата старого UUID без подготовки отдельной безопасной тестовой среды.
- Исходный baseline XML при 11:11 отличался от текущего. Причина не установлена. Текущий актуальный SHA-256 `local_library_v1.xml`: `49284D7B76742D2B2AC6E23F187FEAD3CAB55057F80E3173DBE7101871D9721A`. Этот SHA подтверждён совпадением содержимого с ZIP.
- В `app/build.gradle.kts` указаны `applicationId = com.example.freelocalplayer`, `versionCode=20`, `versionName=1.3.12`; отдельные release-signingConfigs не определены. Следовательно, **неизвестно**, совпадает ли подпись локального `app-debug.apk` с установленным APK. Совпадение версий недостаточно.

## Подготовлен read-only PowerShell 5.1 preflight

`Verify-FreeLocalPlayer-0B33-APK-Signature-ReadOnly-PS51.ps1` проверяет:

1. Подключено ровно одно авторизованное устройство `ZY22LHSVD7`; модель Motorola Edge 60 Pro, не эмулятор; приложение остановлено; версия `1.3.12 (20)`.
2. Текущий SHA-256 `shared_prefs/local_library_v1.xml` равен подтверждённому на этапе SAF ZIP. Любое изменение прерывает проверку: нельзя опираться на устаревший ZIP.
3. Через `adb shell pm path` определяется путь системно установленного `base.apk`; `adb pull` делает **только копию** в `.patch-backups\apk-signature-preflight-0b33-*`. На устройстве нет операций записи или удаления.
4. Из установленных Android SDK build-tools выбираются `aapt.exe` и `apksigner.bat`. Через `aapt dump badging` проверяются package, versionCode, versionName у обоих APK; через `apksigner verify --verbose --print-certs` сравниваются наборы SHA-256 подписывающих сертификатов. При любом несовпадении — `INSTALL_BLOCKED_SIGNATURE_CERTIFICATE_MISMATCH` без установки.
5. Повторная проверка SHA-256 canonical XML и пути установленного APK. В результаты пишутся контрольные суммы APK, сертификата и статусы; media/private JSON в журнал не копируются.

**SHA-256 самого скрипта:** `D7C1777C55E4E5E05B83E2C4C3D1E4CDC0174B9BB67B17B1B615014648970B7E`.

### Фактическая Windows/Motorola проверка — 21 ноября 2026, 12:00

Скрипт `Verify-FreeLocalPlayer-0B33-APK-Signature-ReadOnly-PS51.ps1` успешно выполнен пользователем:

- `SCRIPT_SHA256=PASS`, `POWERSHELL_SYNTAX=PASS`, `VERIFIED_PHYSICAL_MOTOROLA=PASS model=motorola edge 60 pro`, `EMULATOR_USED=NO`, `PLAYER_PROCESS_RUNNING=NO`;
- `MAIN_APP_VERSION=PASS versionName=1.3.12 versionCode=20`, `VERIFIED_ZIP_CANONICAL_BASELINE_STILL_CURRENT=PASS`, текущий canonical SHA-256 `49284D7B76742D2B2AC6E23F187FEAD3CAB55057F80E3173DBE7101871D9721A`;
- `INSTALLED_BASE_APK_PULL=PASS read_only`, установленный SHA-256 `6927E2854BA8CA2D3CAA6817E5D04B6AB6196D81D27940CFAA51635465BF533C`;
- Локальный `app-debug.apk` SHA-256 `8F50B8CFBBF8F149E36E79270053B1451CCF9A73FAF1459D48EE6FA0B47EEEB3`;
- `APK_PACKAGE_AND_VERSION_MATCH=PASS package=com.example.freelocalplayer versionName=1.3.12 versionCode=20`;
- `SIGNING_CERT_SHA256=ADAF14FBA3C90D25842049F39CE56F773F1C14CC90FEE83D56F109E0353C2A4B`, `SIGNING_CERTIFICATES_MATCH=PASS`;
- `PHONE_CANONICAL_XML_UNCHANGED=PASS`, `MAIN_APP_APK_PATH_UNCHANGED=PASS`, `PHONE_APP_DATA_MODIFIED=NO`, `APK_INSTALLED=NO`;
- `RESULT=SUCCESS FREELOCALPLAYER_0B33_APK_SIGNING_PREFLIGHT`, `RESULT=SUCCESS FREELOCALPLAYER_0B33_VERIFIED`.

**Итог:** подписанное обновление теоретически возможно без удаления данных, но этот этап **не выполняет установку** и сам по себе не подтверждает поведение UI. В исходном каталоге пользователя `retiredMedia=0`; позитивную перепривязку нельзя проверять удалением настоящих песен. Следующая безопасная мера — отдельная QA-сборка с собственным package id и синтетической медиатекой, затем только при необходимости — плановое обновление основного APK после копии данных.

### Статус

Скрипт подготовлен и статически проверен на отсутствие команд `install`/`uninstall`/`pm clear`/`force-stop`, но **на Windows/Motorola ещё не выполнялся**. Совпадение сертификатов **не равно разрешению на установку**: потребуется отдельно проверить свежесть APK относительно исходников, пользовательскую конфигурацию и разработать отдельный изолированный QA-путь. Пока `retired=0`, для проверки позитивного сценария нельзя удалять реальные пользовательские медиа.
