# Talkto

Android приложение: виртуален любимец в стил Тамагочи, който е и AI асистент с достъп до файловете и приложенията на телефона. Мозъкът е Claude (Anthropic Java SDK, tool use), лицето е аватар, генериран от ваша снимка и анимиран на живо.

- Kotlin 2.3, Jetpack Compose (Material 3), minSdk 30 (Android 11), targetSdk 36
- Модул `:core` - чист JVM Kotlin, цялата бизнес логика, тества се за секунди без емулатор
- Модул `:app` - Android: UI, услуги, Room, DataStore, ML Kit, Shizuku

## Структура на проекта

```
Talkto/
├── settings.gradle.kts            # :core + :app
├── build.gradle.kts               # plugin aliases
├── gradle/libs.versions.toml      # всички версии на едно място
├── gradle.properties
├── keystore.properties.example    # шаблон за подписване на release
├── .github/workflows/android.yml  # CI: тестове, lint, APK като артефакт
│
├── core/                          # чист Kotlin/JVM, без Android
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/talkto/core/
│       │   ├── agent/
│       │   │   ├── ClaudeAgent.kt         # tool-use цикъл, prompt caching, fallback, rollback
│       │   │   ├── ToolProtocol.kt        # 5-те инструмента със strict JSON schema
│       │   │   └── ToolDispatcher.kt      # изпълнение, валидиране, confirmation gate, памет
│       │   ├── files/
│       │   │   ├── PathGuard.kt           # root jail, симлинкове, защитени папки
│       │   │   ├── FileSystemManager.kt   # търсене/копиране/местене/триене/подреждане
│       │   │   └── FileModels.kt
│       │   ├── memory/
│       │   │   ├── MemoryRepository.kt    # откриване на навици + системен промпт
│       │   │   └── MemoryModels.kt        # ActionRecord, Habit, ActionLogStore
│       │   ├── avatar/
│       │   │   ├── AvatarGenerator.kt     # снимка -> аватар: валидиране, кеш, retry
│       │   │   ├── ImageTransformApi.kt   # интерфейс + Stability AI клиент (OkHttp)
│       │   │   ├── LipSync.kt             # текст -> виземи (кирилица и латиница)
│       │   │   └── AvatarModels.kt        # стилове, FaceAnchors, изражения, жестове
│       │   ├── apps/AppController.kt      # интерфейс + размито търсене на приложения
│       │   └── error/                     # TalktoError, ErrorMapper
│       └── test/kotlin/...                # 74 unit теста
│
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/talkto/app/
        │   │   ├── TalktoApp.kt               # Application + AppContainer (DI)
        │   │   ├── MainActivity.kt
        │   │   ├── agent/
        │   │   │   ├── AgentService.kt        # foreground service за задачите на агента
        │   │   │   ├── AgentSession.kt        # разговор, Anthropic клиент, реакции на аватара
        │   │   │   └── ConfirmationBroker.kt  # агент <-> диалог за потвърждение
        │   │   ├── avatar/
        │   │   │   ├── AvatarEngine.kt        # качване, генерация, анимация (+ Live2D mapping)
        │   │   │   ├── FaceAnchorDetector.kt  # ML Kit: очи, уста, лице
        │   │   │   ├── SpeechEngine.kt        # TTS + lip-sync по думи
        │   │   │   └── Outfit.kt              # шапки, очила, дрехи
        │   │   ├── apps/
        │   │   │   ├── AndroidAppController.kt
        │   │   │   ├── TalktoAccessibilityService.kt  # swipe-out от Recents
        │   │   │   └── PrivilegedShell.kt     # Shizuku + root force-stop
        │   │   ├── data/db/TalktoDatabase.kt  # Room: action_log, dismissed_habit
        │   │   ├── data/prefs/Stores.kt       # DataStore: настройки, аватар, любимец
        │   │   ├── error/GlobalErrorHandler.kt
        │   │   ├── files/StorageAccess.kt     # All Files Access
        │   │   ├── pet/PetEngine.kt           # глад, енергия, щастие, връзка
        │   │   ├── security/KeyCipher.kt      # AES-GCM в Android Keystore
        │   │   └── ui/
        │   │       ├── TamagotchiUI.kt        # главен екран, гардероб, аватар, настройки
        │   │       ├── MainViewModel.kt
        │   │       ├── components/AvatarStage.kt  # live portrait на Canvas
        │   │       └── theme/Theme.kt
        │   └── res/ (strings на български, иконa, accessibility config, file paths)
        ├── test/        # Robolectric: Room + MemoryRepository, GlobalErrorHandler
        └── androidTest/ # Compose UI smoke тестове на устройство
```

## Как работи

### С API ключ и без

| | Без ключ (офлайн режим) | С Claude ключ | + Stability ключ |
|---|---|---|---|
| Любимецът: хранене, игра, сън, статистики, гардероб | да | да | да |
| Прости команди: „отвори камера“, „затвори spotify“, „намери снимки в изтегляния“, „премести a.pdf в документи“, „изтрий …“, „подреди изтегляния по месец“ | да | да | да |
| Защита на файловете, dry-run, диалог за потвърждение, кошче | да | да | да |
| Запис и показване на навиците („какво си научил“) | да | да | да |
| Свободен разговор, задачи от няколко стъпки, предложения за автоматизация | | да | да |
| Аватар от снимка | | | да |

Офлайн режимът е `OfflineAgent` в `:core`: детерминиран разпознавач на команди на български и английски, който вика същия `ToolDispatcher` като Claude. Затова правилата за безопасност и паметта работят еднакво и в двата режима. Кажете „помощ“ за пълния списък команди. Когато има ключ, но няма интернет, команда, която офлайн разпознавачът разбира, пак се изпълнява локално. В заглавната лента има значка „Офлайн режим“ / „Claude“, а при докосване се отварят Настройки.

### Агентът (`ClaudeAgent`)

Ръчен tool-use цикъл върху `com.anthropic:anthropic-java`. Модел по подразбиране `claude-opus-5`, effort `medium` (кратки говорими отговори, ниска латентност на телефон; сменя се в `AgentConfig`). Всяка заявка е подредена така:

```
tools (5 инструмента, непроменими)
system[0]  статичен промпт: личност + правила        <- cache breakpoint
system[1]  live context: час, настроение, права, научени навици
messages   история (append-only)
```

Инструментите и статичният промпт идват от prompt cache при всяко повикване. Сменя се само малкият блок с контекста.

Включен е server-side refusal fallback (beta `server-side-fallback-2026-07-01`, `fallbacks: "default"`). Ако не го искате, `AgentConfig(serverSideFallbacks = false)`.

Ход, който гръмне по средата (мрежа, липсващ ключ), се връща назад, така историята никога не остава с `tool_use` без `tool_result`.

### Инструментите (`ToolProtocol`)

| Инструмент | Какво прави |
|---|---|
| `manage_file` | `list`, `search`, `copy`, `move`, `rename`, `mkdir`, `organize`, `delete`, `empty_trash` |
| `launch_app` | отваря по име на всеки език или по package; `list_only` връща списък |
| `terminate_app` | `auto` / `root` / `shizuku` / `accessibility` / `background_kill` |
| `generate_avatar_from_image` | от избраната снимка или от файл на паметта, 5 стила |
| `animate_avatar` | изражение + жест + реплика с lip-sync |

Всички схеми са `strict: true` с `additionalProperties: false`. Диспечерът никога не хвърля: грешката става `tool_result` с `is_error: true` и подсказка какво да каже Claude на потребителя.

### Безопасност на файловете

1. `PathGuard` канонизира всеки път (резолва симлинкове), така `Download/../../../system` или симлинк към `/data` не минават.
2. Разрешени са само корените на споделената памет (вътрешна + SD/USB). `/system`, `/data`, `/proc` и подобни се отказват винаги.
3. `Android/data`, `Android/obb`, `Android/media` са забранени. Стандартните папки (`DCIM`, `Download`, `Documents`...) не могат да се трият или местят, съдържанието им може.
4. Триенето е на две стъпки. `planDeletion` е dry-run: брой файлове, размер, примерни имена и еднократен токен, валиден 5 минути. `executeDeletion` приема само токена и отказва, ако файловете са се променили след dry-run-а. Между двете стъпки приложението показва собствен диалог, независимо какво казва моделът.
5. По подразбиране изтритото отива в `.talkto_trash`, не изчезва.
6. `organize` и `overwrite` също минават през диалог.

### Адаптивна памет (`MemoryRepository` + Room)

Всяко успешно действие се записва в `action_log`. Детекторите търсят:

- едни и същи премествания между две папки за един вид файлове (напр. WhatsApp снимки към `Pictures/Family` всяка неделя);
- повторно подреждане на една папка;
- отваряне на приложение по едно и също време (делнични дни и уикенд отделно);
- двойки приложения (A, после B до 5 минути);
- често затваряни приложения.

Увереността намалява с полуживот 14 дни, така стар навик изчезва сам. Най-силните 8 навика влизат в системния промпт като `<learned_habits>`. Claude предлага автоматизация веднъж и никога не я пуска без изрично „да". От Настройки всеки навик може да се забрави завинаги.

### Аватарът

**Качване.** Photo Picker или камера (FileProvider). Снимката се декодира с `ImageDecoder` (с EXIF ротация), смалява се до 1024 px и се кодира отново като JPEG, което маха GPS и другите метаданни преди нещо да напусне телефона.

**Генерация.** `AvatarGenerator` проверява magic bytes, пази кеш по SHA-256 (снимка + стил), повтаря при мрежови грешки и 429 с експоненциално изчакване и проверява, че отговорът наистина е картинка. Бекендът по подразбиране е Stability AI `v2beta/stable-image/control/structure`: пази геометрията на лицето и сменя стила. Друг доставчик се добавя като имплементация на `ImageTransformApi`.

**Анимация (live portrait).** ML Kit намира очите, устата и рамката на лицето. `AvatarStage` рисува върху статичната картинка:

- дишане: бавно вертикално мащабиране от долния ръб;
- мигане: клепачи в цвета на кожата (семплиран от картинката), на произволни интервали 2-6 s, понякога двойно;
- lip-sync: `SpeechEngine` получава границите на думите от Android TTS (`onRangeStart`), `VisemePlanner` ги превръща във виземи, устата се рисува според отвореност/ширина/закръгленост;
- жестове: кимане, клатене, махане, подскок, завъртане;
- изражения: руменина, сълза, сърчица, „Zz", „?", „!".

Без генериран аватар се показва вграденото зелено създание със същата анимация.

**Live2D / Spine.** `Live2DParameters.from(pose, ...)` превръща същата поза в стандартните Cubism параметри (`ParamEyeLOpen`, `ParamMouthOpenY`, `ParamMouthForm`, `ParamAngleX`...). За rigged модел: сложете Cubism SDK for Native/Java (не е в Maven, изтегля се от live2d.com след приемане на лиценза), заредете `.moc3` и подавайте картата на `CubismModel.setParameterValue` във всеки кадър вместо `AvatarStage`.

**Гардероб.** 5 шапки, 4 вида очила, 4 дрехи, 8 цвята. Рисуват се като вектори спрямо лицевите ориентири и се пазят в DataStore.

### Контрол на приложения

`launch_app` използва `getLaunchIntentForPackage`. `terminate_app` с `auto` пробва от най-силния към най-слабия метод:

| Метод | Ефект | Изисква |
|---|---|---|
| root | `am force-stop` | root + одобрение в su мениджъра |
| Shizuku | `IActivityManager.forceStopPackage` като shell (uid 2000) | приложението Shizuku, пуснато и одобрило Talkto |
| accessibility | отваря Recents, намира картата, плъзга я нагоре | включена услуга за достъпност на Talkto |
| background_kill | `killBackgroundProcesses` | нищо; работи само ако приложението вече е във фон |

### Грешки

`GlobalErrorHandler` хваща грешките от корутините, от хода на агента, от UI действията и неуловените изключения от фонови нишки. Всяка грешка става `TalktoError.Kind`, а аватарът казва приятелска реплика (напр. „Нямам права за тази папка...") с подходящо изражение. При срив на главната нишка се оставя маркер и при следващото пускане Talkto се извинява.

## Build и APK

### Изисквания

- Android Studio Narwhal (2025.1) или по-нов, с вграден JDK 21
- Android SDK Platform 36 (Android Studio го предлага сам при първо отваряне)
- Устройство или емулатор с Android 11+

### 1. Отваряне на проекта

1. `git clone` на хранилището, после **File > Open** и изберете папката `Talkto` (тази с `settings.gradle.kts`).
2. Изчакайте **Gradle Sync**. Първият път се теглят Gradle 8.14.3 и зависимостите, отнема няколко минути.
3. Ако Studio предложи по-нови версии на библиотеки или AGP, спокойно ги приемете: всички са в `gradle/libs.versions.toml`.

### 2. Пускане на тестовете

От терминала на Android Studio (или обикновен терминал в папката на проекта):

```bash
# Ядро: файлова система, генератор на аватари, памет, диспечер (JVM, без емулатор)
./gradlew :core:test

# Android unit тестове под Robolectric: Room + MemoryRepository, GlobalErrorHandler
./gradlew :app:testDebugUnitTest

# UI тестове на свързано устройство/емулатор
./gradlew :app:connectedDebugAndroidTest

# Статичен анализ
./gradlew :app:lintDebug
```

От интерфейса: десен бутон върху `core/src/test` > **Run 'Tests in core'**. Отчетите са в `core/build/reports/tests/test/index.html` и `app/build/reports/tests/`.

### 3. Debug APK

```bash
./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. Signed Release APK

**Вариант А: през Android Studio.**

1. **Build > Generate Signed App Bundle / APK...** > **APK** > Next.
2. **Create new...** под *Key store path*. Изберете път извън проекта (напр. `~/keys/talkto-release.jks`), парола, alias `talkto`, валидност 25+ години, попълнете поне едно поле в *Certificate*.
3. Next > build variant **release** > **Create**.
4. APK-то е в `app/release/app-release.apk`.

**Вариант Б: от командния ред (повторяемо, подходящо и за CI).**

```bash
# еднократно: генериране на ключ
mkdir -p keys
keytool -genkeypair -v -keystore keys/talkto-release.jks -alias talkto \
  -keyalg RSA -keysize 4096 -validity 10000

cp keystore.properties.example keystore.properties
# редактирайте keystore.properties: storeFile, storePassword, keyAlias, keyPassword

./gradlew :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

`keystore.properties` и `*.jks` са в `.gitignore`. Пазете копие на ключа: без него не можете да пускате обновления върху вече инсталирано приложение.

Release build-ът е с R8 (`isMinifyEnabled`, `isShrinkResources`). Правилата за Anthropic SDK (Jackson), OkHttp, kotlinx.serialization и Shizuku са в `app/proguard-rules.pro`. След първия release build минете през основните сценарии на реално устройство, защото R8 проблемите излизат само по време на изпълнение.

**CI.** `.github/workflows/android.yml` пуска тестовете и lint, строи debug APK и го качва като артефакт `talkto-apk`. За подписан release добавете в GitHub Secrets: `TALKTO_KEYSTORE_B64` (`base64 -w0 keys/talkto-release.jks`), `TALKTO_KEYSTORE_PASSWORD`, `TALKTO_KEY_ALIAS`, `TALKTO_KEY_PASSWORD`.

### 5. Първо пускане на телефона

1. Инсталирайте APK-то (при sideload Android ще поиска разрешение „Инсталиране на неизвестни приложения").
2. По желание: **Настройки** (зъбното колело) > Claude API ключ (console.anthropic.com) за свободен разговор и Stability AI ключ (platform.stability.ai) за аватар от снимка. Без тях приложението работи в офлайн режим.
3. Банерът „Нужен ми е достъп до файловете" > **Разреши** > включете *Allow access to manage all files*.
4. За затваряне на приложения: Настройки > **Достъпност** > Talkto > включете. По желание инсталирайте Shizuku и одобрете Talkto от неговото приложение.
5. Разрешете известията (нужни са за foreground услугата и за потвържденията, когато Talkto не е на екрана).

## Какво да знаете

- **Проверено дотук:** `:core` (74 теста), Robolectric тестовете на `:app`, lint и `assembleDebug` минават в CI. Instrumented тестовете и поведението на реално устройство не са проверени.
- **Google Play:** `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES` и услуга за достъпност, която затваря други приложения, минават Play review само с добра обосновка. Проектът е направен за sideload и вътрешна дистрибуция.
- **API ключове:** пазят се криптирани с Android Keystore и отиват само до съответния API. Ако ще разпространявате приложението на други хора, сложете собствен backend proxy пред Claude и Stability, вместо всеки потребител да въвежда свой ключ.
- **Accessibility swipe** зависи от launcher-а: работи на Pixel Launcher, One UI и MIUI, където картите в Recents носят името на приложението. На launcher, който не ги показва на услугите за достъпност, `auto` минава към следващия метод.
- **Стартиране на приложения** работи, докато Talkto е на екрана (така е по замисъл: вие пишете команда). Android 10+ ограничава отварянето на activity от фона.
- **TTS на български** изисква гласови данни за bg-BG (Google Speech Services ги има). Без тях Talkto говори с гласа по подразбиране, а устата пак се движи.
