# FreeLocalPlayer — 0-B.3.2: подготовка безопасной Android UI QA

**Статус:** подготовлен read-only preflight + проверка SAF ZIP на ПК; реальная Android UI QA обновлённого APK пока НЕ проходила.

## Доказанная исходная база

20 ноября 2026 года пользователь выполнил Windows Gradle 0-B.3.1: `BUILD SUCCESSFUL in 43s`, `GRADLE_UNIT_TEST_AND_DEBUG_COMPILE=PASS`, `ConfirmedRetiredMediaRebindTest=18/18`, `RetiredMediaRelinkTest=15/15`, `RelinkBackupReceiptTest=8/8`. Всего 41 целевой JVM-тест. `MAIN_APP_APK_INSTALL=NO`, `PHONE_DATA_MODIFIED=NO`, `ROOM_PRIMARY_STORAGE=UNCHANGED`.

## 21 ноября 2026: устранена ошибка обнаружения adb.exe в PowerShell

Первый запуск `Verify-FreeLocalPlayer-0B32-PhoneBaseline-Backup-PS51.ps1 -Mode Baseline` на Windows остановился после создания локальной папки журнала в `.patch-backups`:

```text
Get-Command : Имя "adb.exe" не распознано
RESULT=FAILED
```

Причина — скрипт требовал `adb.exe` в `PATH` и запускал `& adb.exe` без поиска установленного Android SDK. По этому журналу **не было обращений ADB к телефону**, не было установки APK или изменения пользовательских данных.

Подготовлен `Verify-FreeLocalPlayer-0B32-PhoneBaseline-Backup-PS51-v2.ps1`, SHA-256 `15C9A8EA61D474AF956897339242AD9138F183EE93AB2D7F6621183C2BD2CE82`. Новый `Resolve-AdbExecutable` последовательно ищет путь SDK из `local.properties`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, стандартные `LOCALAPPDATA\\Android\\Sdk\\platform-tools` и `PATH`. При нестандартной установке поддерживается `-AdbPath <полный путь к adb.exe>`. `Exec-Adb` использует только разрешённый абсолютный путь. Остальные проверки сохранены; запрет `adb install`/`uninstall`/`pm clear` остаётся.

**Статус v2:** подготовлен, побайтово проверен SHA-256 и отличия от v1; полноценный запуск Windows/Motorola ещё не подтверждён. Не считать Baseline/VerifyBackup PASS до пользовательского журнала.

## Этап A: только чтение данных Motorola

`Verify-FreeLocalPlayer-0B32-PhoneBaseline-Backup-PS51.ps1` работает на Windows PowerShell 5.1. В режиме `Baseline`:

1. Требует **единственное подключённое авторизованное физическое устройство** `ZY22LHSVD7`, `motorola edge 60 pro`, без эмулятора.
2. Отказывает, если работает процесс `com.example.freelocalplayer`, и сверяет неизменённое установленное приложение `versionName=1.3.12`, `versionCode=20`.
3. Через `adb shell run-as` **читает SHA-256** канонического `shared_prefs/local_library_v1.xml` и дополнительных файлов preferences (история/избранное, MIDI-offsets, оформление, права и checkpoint).
4. Сверяет хеши до/после операции, проверяет сохранение исходного пути основного APK; записывает лог и локальную контрольную запись **только внутри** `.patch-backups`.
5. НЕ выполняет `adb install`, `adb uninstall`, `pm clear`, `force-stop`, изменяющие Git-команды, изменения в файлах устройства, переход на Room-primary и удаление пользовательских медиа.

## Фактический результат Baseline v2 — 21 ноября 2026 года, 11:11 (локальный журнал пользователя)

`Verify-FreeLocalPlayer-0B32-PhoneBaseline-Backup-PS51-v2.ps1 -Mode Baseline` выполнился полностью на физической Motorola Edge 60 Pro. Подтверждённые строки:

- `SCRIPT_SHA256=PASS`, `POWERSHELL_SYNTAX=PASS`, `ADB_RESOLVED=PASS path=C:\\Users\\Oleg\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe`;
- `VERIFIED_PHYSICAL_MOTOROLA=PASS model=motorola edge 60 pro`, `EMULATOR_USED=NO`, `PLAYER_PROCESS_RUNNING=NO`;
- `MAIN_APP_VERSION=PASS versionName=1.3.12 versionCode=20`, `MAIN_APP_PACKAGE_PATH=PASS`;
- `CANONICAL_XML_SHA256_BEFORE=CCC8BB8D339DAFD8369CCCD3EABE0B96CD565AC80727C7BAE50F80E5F360E0C5`;
- `CANONICAL_XML_SHA256_AFTER=CCC8BB8D339DAFD8369CCCD3EABE0B96CD565AC80727C7BAE50F80E5F360E0C5`;
- `CANONICAL_XML_UNCHANGED=PASS`, `AUXILIARY_PREFERENCES_UNCHANGED=PASS`, `MAIN_APP_APK_PATH_UNCHANGED=PASS`;
- `MAIN_APP_APK_INSTALL=NO`, `PHONE_DATA_MODIFIED_BY_SCRIPT=NO`, `ROOM_PRIMARY_STORAGE=NOT_MODIFIED`, `BASELINE_MARKER_SAVED=PASS`;
- `RESULT=SUCCESS FREELOCALPLAYER_0B32_READ_ONLY_DEVICE_BASELINE`, `RESULT=SUCCESS FREELOCALPLAYER_0B32_BASELINE`.

Папка результатов: `C:\\Users\\Oleg\\Documents\\AndroidProjects\\FreeLocalPlayer\\.patch-backups\\phone-baseline-0b32-20261121-111156288`. Baseline успешно создан; **свежий SAF ZIP ещё не экспортирован и этап `VerifyBackup` пока не выполнялся**. Следующий запуск: создать новый `FreeLocalPlayer-0B32-Verified.zip` через приложение и затем проверить командой `-Mode VerifyBackup`. При несовпадении хешей **не** форсировать прохождение — сначала установить причину изменения.

## Этап B: проверенный SAF ZIP

**После** Baseline пользователь вручную запускает существующую в приложении функцию «Резервная копия → Экспорт ZIP» и сохраняет новый архив с именем `FreeLocalPlayer-0B32-Verified.zip` в стандартную папку `Download` Motorola.

Затем плеер закрывается, на Windows запускается скрипт с `-Mode VerifyBackup`:
- Использует `adb pull` (только чтение с телефона) для `/sdcard/Download/FreeLocalPlayer-0B32-Verified.zip` или явный параметр `-BackupPath` для копии с ПК.
- Проверяет актуальный исходный SHA-256 из предыдущего `Baseline`; при изменениях блокирует переход к Android UI QA.
- Без распаковки в приватный каталог телефона проверяет ZIP: точную версию `FreeLocalPlayer-offline-backup`/1, `sourcePackage`, имена и уникальность записей, пределы размеров, полноту `manifest.json`, SHA-256 и размеры **каждой** записи, наличие и структуру `prefs.json`, media/playlists/retired counts.
- Требует дату создания ZIP не раньше baseline с небольшим допуском часов устройств. Сохраняет локальный ZIP и лог только внутри `.patch-backups`.
- Дважды сверяет хеши preferences на устройстве и путь установленного APK. **Не импортирует** ZIP и ничего не меняет в телефоне.

Эта проверка подтверждает целостность архива и отсутствие выявленных изменений настроек, но **не доказывает**, что пользователь верно выбрал сопоставление двух физических музыкальных файлов.

## Следующие ворота — только после Baseline/ZIP PASS

- Отдельно подтвердить signing certificate подготовленного нового APK совпадает с установленным.
- Подготовить согласованный безопасный тестовый сценарий UI, не удаляя настоящие пользовательские треки. Не применять `pm clear`/uninstall для «чистой среды» на Motorola.
- После отдельной процедуры установки существующим сертификатом на физическую Motorola проверить видимость пункта «Восстановить связь удалённого трека», отмену, отсутствие разрешения подтверждения без ZIP, отказ для конфликтов и при отсутствии совпадающих метаданных, затем отсутствие изменений канонического JSON в сценариях отказа.
- Подтвердить неизменность медиа/текстов/MIDI, прохождение экспорта и сохранение после полного перезапуска приложения только на подготовленных QA-записях.
- Не объявлять UI QA успешной без реального лога и пользовательского подтверждения.

**Важно:** артефакт PowerShell подготовлен в чате и статически проверен на запрещённые команды и SHA-256, но на конкретном Windows PowerShell/телефоне ещё **не запускался**.
