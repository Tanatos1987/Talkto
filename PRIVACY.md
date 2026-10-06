# Политика за поверителност на „Знайко“ (ZnaiKo)

*Последна промяна: 6 октомври 2026 г.*

„Знайко“ е приложение за Android: говорещ любимец, който учи и играе с детето. Тази страница описва какви данни ползва приложението, къде отиват и как се изтриват. Тя се отнася до версията в Google Play (`znaiKo.app`).

## Кратко

- Приложението няма реклами, няма профили и не събира статистика за потребителите.
- Не продаваме и не предаваме данни на рекламодатели.
- Почти всичко остава на телефона. Данни излизат от телефона само в случаите, изброени в „Какво излиза от телефона“, и то само когато потребителят сам включи съответната функция.

## Какво се пази на телефона

Тези данни стоят само в паметта на приложението на телефона и не се изпращат никъде:

- състоянието на любимеца: ситост, настроение, монети, покупки, външен вид, къщичка;
- това, което детето е казало за себе си (например име, любим цвят, рожден ден), за да може Знайко да го помни;
- историята на разговорите, бележките и напомнянията;
- напредъкът в уроците, задачите и викторината;
- дневникът за родителите: минути в приложението, уроци, отговори, игри и приказки по дни, за последните 60 дни;
- отговорите, които детето е отбелязало с 🚩 (виж „Сигнал за отговор“);
- настройките, включително API ключовете, въведени от родител. Ключовете са криптирани с хардуерния ключодържател на Android (Android Keystore). Родителският PIN се пази само като хеш със сол, не като цифри.

Снимките, които потребителят избере за фон по настроение, се разпознават на самия телефон (Google ML Kit, без интернет).

## Какво излиза от телефона

1. **Режим Claude (по желание).** Ако родител въведе собствен API ключ от Anthropic, написаното или казаното към Знайко се изпраща на Anthropic, за да бъде отговорено. Заедно с него отиват нужните за отговора данни: част от разговора, запомнените факти за детето, възрастта му (ако родителят я е посочил) и състоянието на любимеца. Обработката е по правилата на Anthropic: https://www.anthropic.com/legal/privacy. Ако родителят е въвел адрес на семеен сървър вместо ключ, същите данни минават през този сървър, който родителят сам е избрал, и оттам към Anthropic. Без ключ и без сървър, или когато родителят изключи Claude, приложението работи изцяло на телефона.
2. **Аватар от снимка (по желание).** Ако родител въведе собствен ключ от Stability AI и детето избере снимка или направи такава с камерата, снимката се изпраща на Stability AI, за да бъде нарисувана като аватар. Правила: https://stability.ai/privacy-policy.
3. **Разпознаване и синтез на реч.** Когато детето говори на Знайко, гласът се разпознава от услугата за разпознаване на реч на телефона (обикновено Google). Когато Знайко говори, използва гласа за синтез на реч на телефона. Тези услуги могат да обработват звука или текста според собствените си правила.
4. **Обратна връзка.** Бутонът „Обратна връзка“ отваря имейл приложение с готово писмо. Нищо не се изпраща, докато потребителят сам не натисне „Изпрати“. Същото важи за бутона „Изпрати по имейл“ в родителския кът, с който родител препраща сигналите за отговори.
5. **Сигнал за отговор.** До всеки отговор на Claude има бутон 🚩. Когато детето го натисне и избере причина (страшно, грубо, невярно, не е за деца, друго), до нас стигат: текстът на отговора, причината, времето, езикът, моделът и версията на приложението. Не изпращаме въпроса на детето, името му, запомнените факти, нито идентификатор на телефона. Ползваме сигналите само за да правим отговорите по-безопасни и ги пазим до една година. Ако някой сигнал не може да тръгне (няма интернет), приложението опитва пак по-късно.

## Разрешения

- **Микрофон:** само докато Знайко слуша, след натискане на бутона за говорене.
- **Камера:** само ако потребителят сам поиска снимка за аватар.
- **Снимки:** само избраните от потребителя.
- **Известия и точни аларми:** за напомнянията, които потребителят е задал.
- **Работа на заден план:** за да довърши отговор, започнат от потребителя.

## Деца

Приложението е направено за деца, заедно с родител. Режимите, които изпращат данни навън (Claude и аватар от снимка), изискват ключ, който въвежда възрастен. Ключовете, семейният сървър, изключването на Claude и дневният лимит за време стоят в родителски кът, заключен с PIN. Без ключ няма обмен с външни услуги, освен разпознаването и синтеза на реч на самия телефон и сигналите за отговори, които детето само изпраща с 🚩.

## Изтриване

- Историята на разговорите се изтрива от приложението, а отделни запомнени факти се махат от Настройки.
- Сигналите за отговори се изтриват от родителския кът. За да изтрием и копието, което е стигнало до нас, пишете ни чрез „Обратна връзка“ с датата и часа на сигнала.
- Деинсталирането изтрива всички данни на приложението от телефона.
- Данните, изпратени към Anthropic или Stability AI, се пазят по техните правила. Ние нямаме достъп до тях.

## Контакт

Въпроси за тази политика: чрез бутона „Обратна връзка“ в приложението или на страницата на приложението в Google Play.

---

# Privacy policy for ZnaiKo

*Last updated: 6 October 2026*

ZnaiKo is an Android app: a talking pet that learns and plays with a child. This page describes what data the app uses, where it goes and how it is deleted. It covers the Google Play version (`znaiKo.app`).

## In short

- No ads, no accounts, no analytics.
- We do not sell or share data with advertisers.
- Almost everything stays on the phone. Data leaves the phone only in the cases listed under "What leaves the phone", and only when the user turns that feature on.

## What stays on the phone

These are stored only in the app's storage on the phone and are not sent anywhere:

- the pet's state: food, mood, coins, purchases, looks, house;
- what the child has told ZnaiKo about themselves (for example a name, a favourite colour, a birthday), so ZnaiKo can remember it;
- conversation history, notes and reminders;
- progress in lessons, maths tasks and trivia;
- the parents' log: minutes in the app, lessons, answers, games and stories per day, for the last 60 days;
- answers the child marked with 🚩 (see "Flagging an answer");
- settings, including API keys entered by a parent. Keys are encrypted with the Android Keystore. The parents' PIN is kept only as a salted hash, never as digits.

Photos chosen for mood backgrounds are analysed on the phone (Google ML Kit, offline).

## What leaves the phone

1. **Claude mode (optional).** If a parent enters their own Anthropic API key, what is typed or said to ZnaiKo is sent to Anthropic to be answered, together with what the answer needs: part of the conversation, the remembered facts about the child, the child's age (if the parent set it) and the pet's state. Anthropic's policy applies: https://www.anthropic.com/legal/privacy. If the parent entered a family server address instead of a key, the same data goes through that server, chosen by the parent, and on to Anthropic. Without a key or a server, or when the parent switches Claude off, the app works entirely on the phone.
2. **Avatar from a photo (optional).** If a parent enters their own Stability AI key and a photo is chosen or taken, it is sent to Stability AI to be drawn as an avatar. Policy: https://stability.ai/privacy-policy.
3. **Speech.** When the child speaks, the phone's speech recognition service (usually Google) turns the voice into text. When ZnaiKo speaks, it uses the phone's text-to-speech voice. These services may process audio or text under their own policies.
4. **Feedback.** The feedback button opens an email app with a prepared message. Nothing is sent until the user presses send. The same goes for "Send by e-mail" in the parents' corner, which passes flagged answers on.
5. **Flagging an answer.** Every answer from Claude has a 🚩 button. When the child taps it and picks a reason (scary, rude, not true, not for kids, something else), we receive: the text of the answer, the reason, the time, the language, the model and the app version. We do not send the child's question, name or remembered facts, nor any phone identifier. We use flags only to make answers safer and keep them for up to one year. If a flag cannot go out (no internet), the app tries again later.

## Permissions

- **Microphone:** only while ZnaiKo is listening, after the talk button is pressed.
- **Camera:** only when the user asks for an avatar photo.
- **Photos:** only the ones the user picks.
- **Notifications and exact alarms:** for reminders the user has set.
- **Background work:** to finish a reply the user started.

## Children

The app is made for children, together with a parent. The modes that send data out (Claude and avatar from a photo) need a key entered by an adult. The keys, the family server, switching Claude off and the daily time limit sit in a parents' corner locked with a PIN. Without a key there is no exchange with outside services, apart from the phone's own speech recognition and text-to-speech and the answer flags the child sends with 🚩.

## Deletion

- Conversation history can be deleted in the app, and single remembered facts can be removed in Settings.
- Flagged answers are deleted in the parents' corner. To have the copy we received deleted too, write to us through the feedback button with the date and time of the flag.
- Uninstalling the app deletes all of its data from the phone.
- Data sent to Anthropic or Stability AI is kept under their policies; we have no access to it.

## Contact

Questions about this policy: through the feedback button in the app, or on the app's Google Play page.
