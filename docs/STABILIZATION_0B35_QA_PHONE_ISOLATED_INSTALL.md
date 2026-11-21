# FreeLocalPlayer — этап 0-B.3.5: защищённая установка отдельного QA-пакета

## Подтверждённое состояние до установки (21.11.2026)

Пользователь успешно выполнил Windows Gradle этапа 0-B.3.4: `BUILD SUCCESSFUL in 2m 10s`, 86 Gradle задач, 47/47 целевых JVM-тестов PASS. `QA_APP_ISOLATION=PASS production=com.example.freelocalplayer qa=com.example.freelocalplayer.qa`. QA APK `app/build/outputs/apk/qa/app-qa.apk`, SHA-256 `29C63BA4E22C14B482A48F6AFFCDD0DB005BEE2F9CF7BEDC393F7D5B8C414233`, версия `1.3.12-qa (20)`; основной debug APK SHA-256 `8F50B8CFBBF8F149E36E79270053B1451CCF9A73FAF1459D48EE6FA0B47EEEB3`, `1.3.12 (20)`.

На физической Motorola Edge 60 Pro ранее проверены актуальный SAF ZIP (`FCE3747B369A68052572B4F43179E17D8B8BF2F34964525C416B5D25A3CC0858`), медиатека (633 медиа, 3 плейлиста, 0 retired), canonical XML SHA-256 `49284D7B76742D2B2AC6E23F187FEAD3CAB55057F80E3173DBE7101871D9721A`. Подпись установленного production APK совпадает с ранее собранным debug APK, сертификат SHA-256 `ADAF14FBA3C90D25842049F39CE56F773F1C14CC90FEE83D56F109E0353C2A4B`.

## Установка: только по явному выбору режима

В чате подготовлен PowerShell 5.1 `Verify-FreeLocalPlayer-0B35-QA-Phone-PS51.ps1`, SHA-256 `E605FB7BA2289EB05D8D6BD455F568354B144E0DE72560A76E204381057C6C69`. Работает только в единственном каталоге проекта, пишет результаты в `.patch-backups/qa-phone-0b35-*`.

### Режим Preflight (по умолчанию, без установки)

1. Находит `adb.exe`, `aapt.exe`, `apksigner.bat` в установленном Android SDK.
2. Сверяет точный SHA-256 `app-qa.apk`, пакет `com.example.freelocalplayer.qa`, версию `1.3.12-qa (20)`, сертификат SHA-256 `ADAF14...`.
3. Подтверждает единственную авторизованную физическую Motorola Edge 60 Pro, отсутствие работающего production процесса, установленную production версию `1.3.12 (20)`.
4. Проверяет baseline SHA-256 `local_library_v1.xml` и неизменный путь установленного production APK.
5. **Прерывает выполнение**, если QA-пакет уже установлен, если отсутствуют ключевые проверки или если базовая медиатека изменилась.

### Режим InstallAndLaunch (изменяет только QA-пакет)

Повторяет все проверки Preflight. Затем выполняет **ровно одну установку** отдельного `com.example.freelocalplayer.qa` через `adb install --no-incremental app-qa.apk` **без `-r`**, без удаления существующих пакетов. Проверяет, что установлен отдельный QA-пакет и что canonical XML / production APK не изменились; запускает только QA Activity явным компонентом `com.example.freelocalplayer.qa/com.example.freelocalplayer.qa.RelinkQaActivity`. Повторно сверяет SHA-256 XML и путь production APK.

В скрипте **нет** `adb uninstall`, `pm clear`, `force-stop`, `git reset`, установки основного APK или обращения к пользовательским медиафайлам. Установленная QA Activity показывает настоящий production Compose-диалог на полностью синтетической `RelinkQaFixture`, а «экспорт ZIP» имитирует и не пишет на диск.

## Ручные UI-сценарии

- Открыть QA Activity и настоящий диалог; убедиться, что кнопка подтверждения недоступна без выбранной архивной записи/нового кандидата и отметки подтверждения.
- Выбрать «QA: неверный размер»: `SIZE_MISMATCH` блокирует восстановление.
- Выбрать корректный кандидат, включить отметку, затем выполнить **симуляцию ZIP**; подтверждение должно изменить только локальный Compose state. Проверить на экране `SIMULATED_RELINK=PASS`.
- Нажать «Сбросить синтетический сценарий», убедиться, что данные возвращаются.
- Фактический `LibraryStore.saveConfirmed`, `MediaStore` файл, доступность SAF ZIP, Room и пользовательская медиатека в этих сценариях **не затрагиваются**. UI PASS не равен проверке production сценария сохранения UUID.

## Границы доказательства

**На момент создания документа** реальный Android Preflight, установка QA APK и UI-тесты на Motorola ещё **не выполнены**. Для успеха нужны действительные логи устройства и пользовательское подтверждение работы кнопок. Подписи и изоляция отдельно подтверждены на ПК, но фактическое поведение QA Activity на телефоне пока неизвестно. В случае отказа скрипт останавливается; **не** запускать `adb uninstall`, `pm clear` или обновление основного приложения для обхода проверки.
