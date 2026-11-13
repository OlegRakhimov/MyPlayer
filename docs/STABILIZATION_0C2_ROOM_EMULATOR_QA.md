# FreeLocalPlayer — 0-C.2-QA: реальная проверка теневой базы Room на эмуляторе

**Статус:** исходники и тесты подготовлены; полноценный Android Gradle и инструментальные тесты на Windows/Android должны быть выполнены отдельно. Основное хранение **по-прежнему JSON**, Room — только проверяемая копия. Версия приложения остаётся `1.3.12 (20)`, пакет `com.example.freelocalplayer` без изменений.

## Зачем отдельный QA-шаг

В исходном тесте `ShadowRoomMigrationDeviceTest` Room-таблицы заполнялись напрямую, через DAO. Это проверяло SQLite, но **не проверяло фактический алгоритм** `ShadowRoomMirror.mirror()` (вычисление diff, удаление устаревших строк, проверка данных и транзакционный stamp).

Теперь алгоритм вынесен в `internal fun mirrorInto(room: ShadowRoomDatabase, source: LibraryState)`. Работающее приложение по-прежнему вызывает `mirror(context, source)` и этот же метод. Не изменились `LibraryStore`, код экспорта ZIP, Media3, UUID, схема Room или версия Android.

## Проверки в Android instrumentation

К существующему `inMemorySqliteRoundTripAndTransactionalRollback` добавлены:

1. `productionMirrorUpdatesDeletesAndKeepsUserUuid`: начальная копия, изменение LRC, вставка нового трека, реорганизация плейлистов, удаление архивной записи, повторная синхронизация, очистка и проверка отсутствия устаревших строк.
2. `productionMirrorRejectsDuplicateUuidAndPreservesPreviousRows`: недопустимые повторяющиеся UUID не изменяют прежние данные и контрольную подпись.
3. `productionMirrorBulkCatalogRoundTrip`: 1200 треков, пять упорядоченных плейлистов, повторное чтение и повторная синхронизация.

Все тесты используют **только in-memory Room на эмуляторе**; реальный файл `flp-room-shadow-v1.db` телефона они не открывают. PowerShell `tools/Verify-FreeLocalPlayer-RoomShadow.ps1` сначала проверяет serial вида `emulator-*` **и** `ro.kernel.qemu=1`, затем собирает AndroidTest, устанавливает APK на **эмулятор**, запускает четыре теста и проверяет, что приложение на эмуляторе выводит `ROOM_MIRROR_VERIFIED` при открытии своей базы.

Если эмулятор не запущен, скрипт попробует запустить существующий AVD `sdk_gphone16k_x86_64`; при отсутствии AVD он остановится без установки на телефон. Логи хранятся только внутри `C:\Users\Oleg\Documents\AndroidProjects\FreeLocalPlayer\.patch-backups\room-qa-0c2-*`.

## Запуск после успешного установщика

```powershell
$Project = 'C:\Users\Oleg\Documents\AndroidProjects\FreeLocalPlayer'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $Project 'tools\Verify-FreeLocalPlayer-RoomShadow.ps1')
```

Ожидаемые финальные сообщения при фактическом успехе:

```text
ROOM_DEVICE_IS_EMULATOR=PASS
GRADLE_QA_BUILD=PASS
EMULATOR_APKS_INSTALLED=PASS
ROOM_ANDROID_INSTRUMENTATION=PASS tests=4
ROOM_APP_SHADOW_OPEN=PASS
PHYSICAL_PHONE_CHANGED=NO
RESULT=SUCCESS ROOM_EMULATOR_QA_PASS
```

**НЕ считать тест пройденным только по `assembleDebugAndroidTest`:** это компиляция, а не исполнение SQLite на устройстве.

## Ограничения и последующие проверки

- Android SDK и эмулятора в среде подготовки нет. Поэтому пока проверено только Kotlin-совместимость на тестовых API-заглушках и 6 операций алгоритма через in-memory mock DAO (без SQLite); это **не** заменяет Android Gradle/Room instrumentation.
- `adb install -r` выполняется только для подтверждённого эмулятора; никаких `adb uninstall`, `pm clear`, изменения Android package ID или удаления старой медиатеки на телефоне.
- Экспорт ZIP 0-A и **восстановление на отдельной тестовой установке**, проверка ZIP-отката после ошибки, поворот/смерть процесса и реальный профиль производительности 5000–20000 треков остаются отдельными блокирующими проверками перед переключением основного хранения на Room (этап 0-C.3).
- При любых сообщениях `ROOM_MIRROR_FAILED`, падениях instrumentation, несоответствии количества тестов или отсутствующем `ROOM_MIRROR_VERIFIED` итогом считается **FAIL**, а не частичный PASS.
