# FreeLocalPlayer — 0-B.3.4: изолированная QA-сборка для диалога восстановления UUID

## Почему не тестируем на рабочей медиатеке

21 ноября 2026 на физической Motorola Edge 60 Pro подтверждено: `FreeLocalPlayer 1.3.12 (20)`, `633` медиа, `3` плейлиста, `retiredMedia=0`, SAF ZIP полностью совпадает с сохранённым состоянием (SHA-256 `FCE3747B369A68052572B4F43179E17D8B8BF2F34964525C416B5D25A3CC0858`), сертификат установленного APK совпадает с локальным debug APK (SHA-256 сертификата `ADAF14FBA3C90D25842049F39CE56F773F1C14CC90FEE83D56F109E0353C2A4B`). Исходный debug APK **не установлен**. При `retiredMedia=0` реальная проверка восстановления UUID потребовала бы вмешательства в пользовательские данные, чего делать нельзя.

## Что добавлено

1. `app/build.gradle.kts`: новый build type `qa`, наследует параметры `debug`, но имеет **другой applicationId** через `applicationIdSuffix = ".qa"`. Обычный `debug` остаётся `com.example.freelocalplayer`; QA APK использует `com.example.freelocalplayer.qa`, независимое внутреннее хранилище Android.
2. `app/src/qa/AndroidManifest.xml`: добавлена **только для QA** отдельная экспортированная `com.example.freelocalplayer.qa.RelinkQaActivity`, запускаемая явной командой `am start -n com.example.freelocalplayer.qa/com.example.freelocalplayer.qa.RelinkQaActivity`. Пусковая иконка стандартного `MainActivity` остаётся; тест запускается по явному имени QA Activity.
3. `app/src/qa/java/com/example/freelocalplayer/qa/RelinkQaFixture.kt`: независимая искусственная медиатека **только в оперативной памяти**: 1 архивный UUID, 1 подходящий новый UUID, 1 кандидат с несовпадающим размером, 1 существующий объект, 1 плейлист, 1 синтетический текст.
4. `app/src/qa/java/com/example/freelocalplayer/qa/RelinkQaActivity.kt`: использует **настоящий production `RetiredMediaRelinkDialog`**, передавая искусственные данные. Кнопка «Экспортировать резервный ZIP» **только моделирует разрешение**, не создаёт архив и не читает настройки. Подтверждение применяет `ConfirmedRetiredMediaRebind.confirmed` **только к локальному Compose state**, проверяет старый UUID, новый UUID, текст и позицию плейлиста. В интерфейсе явно указано: это симуляция, а не настоящая запись и не реальное доказательство MediaStore/SAF.
5. `app/src/testQa/java/com/example/freelocalplayer/qa/RelinkQaFixtureTest.kt`: **6 отдельных QA-only JVM-тестов**: структура данных, правильный кандидат, неверный размер, восстановление пользовательских данных в памяти, неизменность исходной модели/отдельность экземпляров, требование явного подтверждения.

## Windows Gradle QA: фактически PASS 21.11.2026

Журнал пользователя подтвердил `BUILD SUCCESSFUL in 2m 10s` (86 задач: 43 выполнено и 43 актуальны), `GRADLE_QA_BUILD_AND_JVM_TESTS=PASS` и все четыре набора:

- `ConfirmedRetiredMediaRebindTest`: **18/18 PASS**;
- `RetiredMediaRelinkTest`: **15/15 PASS**;
- `RelinkBackupReceiptTest`: **8/8 PASS**;
- `RelinkQaFixtureTest` (testQaUnitTest): **6/6 PASS**.

Итого **47 целевых JVM-тестов**. `QA_APP_ISOLATION=PASS production=com.example.freelocalplayer qa=com.example.freelocalplayer.qa`, версии `1.3.12 (20)` и `1.3.12-qa (20)`. SHA-256 основного debug APK `8F50B8CFBBF8F149E36E79270053B1451CCF9A73FAF1459D48EE6FA0B47EEEB3`; SHA-256 QA APK `29C63BA4E22C14B482A48F6AFFCDD0DB005BEE2F9CF7BEDC393F7D5B8C414233`. Путь: `app\\build\\outputs\\apk\\qa\\app-qa.apk`. `MAIN_APP_APK_INSTALL=NO`, `QA_APP_APK_INSTALL=NO`, `PHONE_DATA_MODIFIED=NO`, `ROOM_PRIMARY_STORAGE=UNCHANGED`, `RESULT=SUCCESS FREELOCALPLAYER_0B34_QA_APK_AND_JVM_VERIFIED`.

**Интерфейс на телефоне всё ещё не проверен.** Следующий шаг — отдельная контролируемая установка **только QA-пакета** после read-only preflight и повторной проверки SHA-256 основной медиатеки, затем ручная проверка диалога.

## Сборка и границы верификации

Первый этап — `gradlew.bat --no-daemon :app:testDebugUnitTest :app:testQaUnitTest :app:assembleDebug :app:assembleQa` на Windows, без подключения телефона. Скрипт `Verify-FreeLocalPlayer-0B34-QA-Isolated-PS51.ps1` проверяет `main` и закреплённый commit, Git blob-хеши новых файлов, неизменность production `applicationId`, JVM-тесты и пакеты собранных APK через `aapt dump badging`. **Реальная Gradle QA этого этапа ещё не выполнена.**

После Windows PASS, отдельным этапом возможна установка `app-qa.apk`, которая не обновляет и не удаляет рабочий `com.example.freelocalplayer`. Скрипт сборки **не содержит ADB install и не подключается к телефону**. Результаты UI должны подтверждаться скриншотами/логом с физического устройства. Production APK можно обновлять только по отдельной безопасной процедуре; совпадение сертификата уже подтверждено, но новая QA-сборка ещё не проверена.

### Явные ограничения

- Синтетическая QA Activity **не запрашивает доступ к MediaStore, не сохраняет ZIP, JSON, MIDI или Room**. В отличие от production, здесь **нет** проверки существующего файла или настоящей сохранённой резервной копии; интерфейс исключительно имитирует эти условия. Поэтому PASS этой симуляции **не доказывает** безопасность реальной операции восстановления.
- **Нельзя** создавать tombstone удалением настоящего медиа на Motorola ради UI QA.
- Нет необходимости создавать вторую папку проекта, отключать автосинхронизацию, делать `git reset --hard`, `adb uninstall`, `pm clear`, переключать Room-primary или переименовывать production package.
