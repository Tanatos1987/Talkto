# ZnaiKo - бележки по проекта

Документът е за Claude Project или за всеки, който поема работата. Описва какво има в приложението, как се прави версия за Google Play и докъде е стигнала публикацията.

Състояние към 2 октомври 2026: последната версия е **1.1.57** (versionCode 57). Затворено тестване (Alpha) още не е публикувано.

## Какво представлява приложението

ZnaiKo (Знайко) е виртуален любимец в стил Тамагочи за деца, който е и AI асистент. Мозъкът е Claude през Anthropic Java SDK. Лицето е анимиран аватар.

Направено досега:

- по-добро българско произношение на гласа;
- история при първо пускане;
- забавен шрифт за логото;
- емоджи на лентите със състоянието и на бутоните;
- детски дизайн;
- задачи по алгебра и геометрия;
- игри: 3D тетрис и match-3;
- хранене със здравословна или вредна храна, която променя тялото на героя;
- бутон за обратна връзка, който отваря имейл приложение с готово писмо;
- превключване на езика: едно докосване на флагчето сменя интерфейса, речта, микрофона и известията между български и английски;
- политика за поверителност на двата езика в `PRIVACY.md`;
- графики за Play в `store/` (иконка 512x512, feature graphic 1024x500).

## Две версии (product flavors)

| | `full` | `play` |
|---|---|---|
| Пакет | `com.talkto.app` | `znaiKo.app` |
| `BuildConfig.PLAY_STORE` | false | true |
| Достъп до всички файлове, списък с приложения, accessibility | да | премахнати |
| Достъп до галерията | да | не, снимки само през системния picker |
| hiddenapibypass | да | не се включва |

`app/src/play/AndroidManifest.xml` маха със `tools:node="remove"`:

- MANAGE_EXTERNAL_STORAGE;
- QUERY_ALL_PACKAGES;
- READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED, READ_EXTERNAL_STORAGE;
- accessibility услугата.

Пак там AgentService е обявен с тип `specialUse`.

Свързани промени в кода:

- `TamagotchiUI.kt`, BackgroundsSheet: бутонът за автоматично сканиране на галерията се показва само когато `!BuildConfig.PLAY_STORE`;
- `PrivilegedShell.kt`: HiddenApiBypass се вика през reflection (`exemptHiddenApi`), затова play версията се компилира и без библиотеката;
- `app/build.gradle.kts`: `"fullImplementation"(libs.hiddenapibypass)`;
- `proguard-rules.pro`: keep и dontwarn правила за `org.lsposed.hiddenapibypass`.

## Билд и подписване

CI е в `.github/workflows/android.yml`. Всеки push на клона `claude/talkto-android-app-k5s2jz` пуска тестове и подписан `bundlePlayRelease`.

- versionCode = номерът на CI run-а, versionName = `1.1.<run>`.
- AAB файлът се сваля от страницата на run-а в GitHub Actions, секция Artifacts.
- GitHub secrets: `TALKTO_KEYSTORE_B64`, `TALKTO_KEYSTORE_PASSWORD`, `TALKTO_KEY_ALIAS` (= `znaiko`), `TALKTO_KEY_PASSWORD`.
- Keystore файлът и паролите **никога** не влизат в репото. Шаблонът за локален билд е `keystore.properties.example`.

Версии, качени в Play библиотеката:

| Версия | Какво има | CI run |
|---|---|---|
| 1.1.53 | първа play версия, още иска достъп до галерията | - |
| 1.1.55 | без разрешения за снимки | 36752832097 |
| 1.1.57 | и без hiddenapibypass | 36824358734 |

## Google Play Console: какво е минато

1. **Вътрешно тестване**: качени са 53 и после 55.
2. **Грешка за снимки и видео (READ_MEDIA_IMAGES)**: изчезна, след като 55 замени 53 и във вътрешното тестване. Play гледа всички активни версии, не само тази в черновата.
3. **Грешка 66DEF1F0 при качване**: файлът вече е в библиотеката. Решение: "Добавяне от библиотеката", а не ново качване.
4. **Декларация за foreground service**: отметка "Други" и текст:

   > ZnaiKo runs a short foreground service only while it finishes a reply the child explicitly asked for, such as reading an answer aloud, after the app goes to the background. A notification is visible the whole time and the service stops when the reply is done.

   Ако пита какво става при прекъсване:

   > The child would lose the answer they just asked for; the reply stops mid-sentence.

   Ако иска видео: запис на екрана, качен в YouTube като Unlisted.

5. **Предупреждение за неподдържан API (hiddenapibypass)**: може да се продължи въпреки него. 1.1.57 го маха изцяло.

## Какво остава

- [ ] Декларация за рекламен идентификатор (Advertising ID): отговор **Не**. Приложението няма реклами и не ползва AD_ID.
- [ ] По желание: 1.1.57 в затвореното и във вътрешното тестване, през "Добавяне от библиотеката" или качване на AAB файла.
- [ ] Преглед и публикуване на затвореното тестване (Alpha).
- [ ] Формуляр Data safety.
- [ ] По желание: debug symbols, за да няма предупреждение за native код.
- [ ] Имейл за контакт в `PRIVACY.md` (раздел Feedback засега няма адрес).

## Правила при работа по репото

- Работата върви само в клона `claude/talkto-android-app-k5s2jz` (PR #2).
- Без тайни в git: keystore, пароли и API ключове стоят само в GitHub secrets или локално.
- Всяка промяна по play версията се проверява с `./gradlew :app:bundlePlayRelease` или в CI преди да се качи в Play.
