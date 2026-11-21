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

### Статус

Скрипт подготовлен и статически проверен на отсутствие команд `install`/`uninstall`/`pm clear`/`force-stop`, но **на Windows/Motorola ещё не выполнялся**. Совпадение сертификатов **не равно разрешению на установку**: потребуется отдельно проверить свежесть APK относительно исходников, пользовательскую конфигурацию и разработать отдельный изолированный QA-путь. Пока `retired=0`, для проверки позитивного сценария нельзя удалять реальные пользовательские медиа.
