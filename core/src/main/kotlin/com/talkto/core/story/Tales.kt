package com.talkto.core.story

import com.talkto.core.i18n.Lang
import kotlin.random.Random

enum class TaleKind(val bg: String, val en: String, val emoji: String) {
    FABLE("Басни", "Fables", "🦊"),
    FAIRY_TALE("Приказки", "Fairy tales", "🏰"),
    ;

    fun label(lang: Lang) = lang.pick(bg, en)
}

/**
 * A short tale ZnaiKo can read without the internet. Classic fables and folk tales are retold here in our own words;
 * [bedtime] marks the calm ones for the night. A fable's [moralBg] / [moralEn] is its lesson in one sentence.
 */
data class Tale(
    val id: String,
    val kind: TaleKind,
    val emoji: String,
    val titleBg: String,
    val titleEn: String,
    val textBg: String,
    val textEn: String,
    val moralBg: String = "",
    val moralEn: String = "",
    val bedtime: Boolean = false,
) {
    fun title(lang: Lang) = lang.pick(titleBg, titleEn)
    fun text(lang: Lang) = lang.pick(textBg, textEn)
    fun moral(lang: Lang) = lang.pick(moralBg, moralEn)

    /** What ZnaiKo reads aloud: the title, the tale and, for a fable, its lesson. */
    fun spoken(lang: Lang): String = buildString {
        append(title(lang)).append(". ").append(text(lang))
        if (moral(lang).isNotBlank()) append(" ").append(moral(lang))
    }
}

/** A folk riddle with its answer (and other accepted answers). */
data class Riddle(
    val id: String,
    val emoji: String,
    val questionBg: String,
    val questionEn: String,
    val answerBg: String,
    val answerEn: String,
    val altBg: List<String> = emptyList(),
    val altEn: List<String> = emptyList(),
) {
    fun question(lang: Lang) = lang.pick(questionBg, questionEn)
    fun answer(lang: Lang) = lang.pick(answerBg, answerEn)
    fun alternatives(lang: Lang) = if (lang == Lang.BG) altBg else altEn
}

object Tales {

    val all: List<Tale> = listOf(
        // ------------------------------------------------------------------ fables
        Tale(
            "fox_grapes", TaleKind.FABLE, "🍇",
            "Лисицата и гроздето", "The Fox and the Grapes",
            "Един горещ летен ден гладна лисица минавала покрай лозе. Високо над главата ѝ висели узрели, сочни гроздове. " +
                "Лисицата скочила веднъж, но не стигнала. Скочила втори път, после трети, но гроздето все така висяло високо. " +
                "Накрая тя се уморила, вирнала нос и си тръгнала. „Хм, то и без това е кисело и зелено. Не ми се яде такова грозде!“ казала тя.",
            "One hot summer day a hungry fox walked past a vineyard. High above her head hung ripe, juicy bunches of grapes. " +
                "The fox jumped once, but could not reach them. She jumped a second time, then a third, but the grapes still hung high. " +
                "At last she grew tired, put her nose in the air and walked away. \"Hmph, they are sour and green anyway. I don't want grapes like that!\" she said.",
            "Поука: лесно е да кажем, че не искаме онова, което не можем да стигнем.",
            "The lesson: it is easy to say we don't want what we cannot reach.",
        ),
        Tale(
            "hare_tortoise", TaleKind.FABLE, "🐢",
            "Заекът и костенурката", "The Hare and the Tortoise",
            "Заекът обичал да се хвали колко бързо тича и често се присмивал на бавната костенурка. " +
                "Един ден костенурката му казала: „Хайде да се надбягваме!“ Всички животни се събрали да гледат. " +
                "Заекът хукнал напред и скоро костенурката изчезнала от погледа му. „Имам толкова време, ще подремна“, рекъл той и легнал под едно дърво. " +
                "А костенурката вървяла ли, вървяла, бавно, но без да спира. Когато заекът се събудил, видял, че тя вече пресича финала. Всички животни ѝ ръкопляскали.",
            "The hare loved to boast about how fast he could run, and he often laughed at the slow tortoise. " +
                "One day the tortoise said, \"Let's have a race!\" All the animals gathered to watch. " +
                "The hare dashed ahead and soon could not even see the tortoise. \"I have plenty of time, I'll have a nap,\" he said, and lay down under a tree. " +
                "But the tortoise kept walking, slowly but without stopping. When the hare woke up, he saw her crossing the finish line. All the animals clapped for her.",
            "Поука: който върви бавно, но упорито, стига далеч.",
            "The lesson: slow and steady wins the race.",
        ),
        Tale(
            "ant_grasshopper", TaleKind.FABLE, "🐜",
            "Мравката и щурецът", "The Ant and the Grasshopper",
            "Цяло лято щурецът свирел на цигулката си и пеел на слънце. А мравката мъкнела зрънце след зрънце към мравуняка си. " +
                "„Ела да пееш с мен!“ викал щурецът. „Не мога, събирам храна за зимата“, отговаряла мравката. " +
                "Дошла зимата, навалял сняг и щурецът нямал какво да яде. Почукал на вратата на мравката, треперещ от студ. " +
                "Добрата мравка го пуснала вътре и му дала от своите зрънца. А щурецът ѝ свирил цяла зима и обещал догодина да помага.",
            "All summer long the grasshopper played his fiddle and sang in the sun. Meanwhile the ant carried grain after grain to her anthill. " +
                "\"Come and sing with me!\" called the grasshopper. \"I can't, I'm gathering food for the winter,\" answered the ant. " +
                "Winter came, snow fell, and the grasshopper had nothing to eat. He knocked on the ant's door, shivering with cold. " +
                "The kind ant let him in and shared her grain. The grasshopper played music for her all winter and promised to help next year.",
            "Поука: има време за игра и време за работа, а добрият приятел помага в беда.",
            "The lesson: there is a time to play and a time to work, and a good friend helps in hard times.",
        ),
        Tale(
            "lion_mouse", TaleKind.FABLE, "🦁",
            "Лъвът и мишката", "The Lion and the Mouse",
            "Един лъв спял в сянката, когато малка мишка се покатерила по гривата му. Лъвът се събудил и я хванал с голямата си лапа. " +
                "„Моля те, пусни ме!“ изписукала мишката. „Някой ден и аз ще ти помогна.“ Лъвът се засмял: „Ти? Да помогнеш на мен?“ Но я пуснал. " +
                "Няколко дни по-късно лъвът се оплел в ловджийска мрежа. Колкото повече се дърпал, толкова повече се заплитал. " +
                "Мишката чула рева му, дотичала и гризала въжетата, докато мрежата се разкъсала. Лъвът бил свободен. „Благодаря ти, малка приятелко“, казал той.",
            "A lion was sleeping in the shade when a little mouse climbed up his mane. The lion woke up and caught her with his big paw. " +
                "\"Please let me go!\" squeaked the mouse. \"One day I will help you too.\" The lion laughed, \"You? Help me?\" But he let her go. " +
                "A few days later the lion got caught in a hunter's net. The more he pulled, the more tangled he became. " +
                "The mouse heard him roar, ran over and gnawed the ropes until the net broke. The lion was free. \"Thank you, little friend,\" he said.",
            "Поука: никой не е толкова малък, че да не може да помогне.",
            "The lesson: no one is too small to help.",
        ),
        Tale(
            "crow_fox", TaleKind.FABLE, "🧀",
            "Гарванът и лисицата", "The Crow and the Fox",
            "Гарван намерил парче сирене, кацнал на един клон и се канел да го изяде. Отдолу минала хитра лисица и много ѝ се приискало сиренето. " +
                "„Ах, какъв красив гарван!“ започнала тя. „Какви лъскави пера, какви очи! Сигурно и гласът ти е най-хубавият в гората. Ще ми попееш ли?“ " +
                "Гарванът толкова се зарадвал на хвалбите, че отворил човка и изграчил: „Гра-а-а!“ Сиренето паднало право в устата на лисицата. " +
                "„Благодаря за сиренето!“ подвикнала тя и избягала.",
            "A crow found a piece of cheese, sat on a branch and was about to eat it. A clever fox passed below and wanted the cheese very much. " +
                "\"Oh, what a beautiful crow!\" she began. \"What shiny feathers, what eyes! Your voice must be the loveliest in the forest. Will you sing for me?\" " +
                "The crow was so pleased by the praise that he opened his beak and cawed, \"Caw-w-w!\" The cheese fell straight into the fox's mouth. " +
                "\"Thanks for the cheese!\" she called, and ran away.",
            "Поука: не вярвай на всеки, който те хвали, за да получи нещо.",
            "The lesson: don't trust everyone who praises you to get something.",
        ),
        Tale(
            "boy_wolf", TaleKind.FABLE, "🐑",
            "Момчето, което викаше „Вълк!“", "The Boy Who Cried Wolf",
            "Едно момче пасяло овцете на селото. Скучно му било и веднъж се развикало: „Вълк! Вълк!“ Хората дотичали, но вълк нямало, а момчето се смеело. " +
                "На другия ден пак извикало: „Вълк!“ Хората пак дотичали и пак нямало вълк. " +
                "Но на третия ден наистина дошъл вълк. „Вълк! Помощ!“ викало момчето с всичка сила, ала никой не дошъл, защото вече не му вярвали. " +
                "Овцете се разбягали из полето и цяла вечер всички ги търсили. Оттогава момчето винаги казвало истината.",
            "A boy looked after the village sheep. He was bored, so one day he shouted, \"Wolf! Wolf!\" The people came running, but there was no wolf, and the boy laughed. " +
                "The next day he shouted \"Wolf!\" again. The people came running again, and again there was no wolf. " +
                "But on the third day a wolf really came. \"Wolf! Help!\" the boy shouted as loud as he could, but nobody came, because nobody believed him any more. " +
                "The sheep ran all over the fields, and everyone spent the whole evening looking for them. From then on the boy always told the truth.",
            "Поука: на онзи, който лъже, не вярват дори когато казва истината.",
            "The lesson: nobody believes a liar, even when he tells the truth.",
        ),
        Tale(
            "wind_sun", TaleKind.FABLE, "☀️",
            "Вятърът и слънцето", "The North Wind and the Sun",
            "Веднъж Северният вятър и Слънцето спорели кой е по-силен. Видели човек с топло палто. „Който го накара да си свали палтото, той е по-силният“, казало Слънцето. " +
                "Вятърът задухал силно, после още по-силно. Но колкото повече духал, толкова по-здраво човекът се загръщал в палтото. " +
                "Тогава Слънцето се показало и започнало да грее нежно и топло. Стоплило се, стоплило, и човекът сам си свалил палтото. ",
            "Once the North Wind and the Sun argued about who was stronger. They saw a man in a warm coat. \"Whoever makes him take off his coat is the stronger one,\" said the Sun. " +
                "The Wind blew hard, then harder still. But the more he blew, the tighter the man wrapped himself in his coat. " +
                "Then the Sun came out and began to shine gently and warmly. It grew warmer and warmer, and the man took off his coat by himself.",
            "Поука: с доброта се постига повече, отколкото със сила.",
            "The lesson: kindness gets more done than force.",
            bedtime = true,
        ),
        Tale(
            "crow_pitcher", TaleKind.FABLE, "🏺",
            "Гаргата и стомната", "The Crow and the Pitcher",
            "Беше много горещо и една жадна гарга търсеше вода. Най-сетне намери стомна, но водата беше на самото дъно и човката ѝ не стигаше. " +
                "Гаргата помисли, помисли и измисли нещо. Взе едно камъче и го пусна в стомната. После още едно и още едно. " +
                "С всяко камъче водата се качваше все по-нагоре, докато стигна чак до гърлото на стомната. И гаргата пи, колкото си поиска.",
            "It was very hot, and a thirsty crow was looking for water. At last she found a pitcher, but the water was right at the bottom and her beak could not reach it. " +
                "The crow thought and thought, and had an idea. She picked up a pebble and dropped it into the pitcher. Then another, and another. " +
                "With every pebble the water rose higher, until it reached the top of the pitcher. And the crow drank as much as she wanted.",
            "Поука: когато мислиш, ще намериш изход.",
            "The lesson: if you think hard, you will find a way.",
        ),
        Tale(
            "town_country_mouse", TaleKind.FABLE, "🐭",
            "Градската и полската мишка", "The Town Mouse and the Country Mouse",
            "Полската мишка покани на гости братовчедка си от града и я нагости с жито и корени. „Колко просто ядеш!“ каза градската мишка. „Ела у дома, да видиш истинско угощение.“ " +
                "В града имаше сирене, торти и сладкиши. Но едва започнаха да ядат, и вратата се отвори. Мишките побягнаха и се скриха. После залая куче, после измяука котка. " +
                "„Благодаря ти“, каза полската мишка, „но аз се прибирам. По-добре житце на спокойствие, отколкото торта със страх.“",
            "The country mouse invited her cousin from the town and fed her wheat and roots. \"How plain your food is!\" said the town mouse. \"Come to my home and see a real feast.\" " +
                "In the town there were cheese, cakes and sweets. But they had just started eating when the door opened. The mice ran and hid. Then a dog barked, then a cat miaowed. " +
                "\"Thank you,\" said the country mouse, \"but I'm going home. Better a little wheat in peace than cake with fear.\"",
            "Поука: спокойният живот струва повече от богатството.",
            "The lesson: a quiet, happy life is worth more than riches.",
            bedtime = true,
        ),
        Tale(
            "dog_reflection", TaleKind.FABLE, "🦴",
            "Кучето и сянката му", "The Dog and His Reflection",
            "Едно куче носело в устата си голям кокал и минавало по мостче над река. Погледнало във водата и видяло друго куче, и то с кокал, още по-голям. " +
                "„Ще взема и неговия кокал!“ помислило кучето и излаяло: „Бау!“ Щом отворило уста, собственият му кокал цопнал във водата и потънал. " +
                "А другото куче изчезнало, защото било само отражението му. Така алчното куче останало без нищо.",
            "A dog was carrying a big bone in his mouth across a little bridge over a river. He looked into the water and saw another dog with a bone, an even bigger one. " +
                "\"I'll take his bone too!\" thought the dog, and barked, \"Woof!\" As soon as he opened his mouth, his own bone splashed into the water and sank. " +
                "And the other dog vanished, because it was only his reflection. So the greedy dog was left with nothing.",
            "Поука: който иска всичко, може да остане без нищо.",
            "The lesson: if you want everything, you may end up with nothing.",
        ),
        Tale(
            "dove_ant", TaleKind.FABLE, "🕊️",
            "Гълъбицата и мравката", "The Dove and the Ant",
            "Една мравка слязла до потока да пие вода, но водата я отнесла. „Помощ!“ викала тя. Гълъбица, кацнала на близкото дърво, откъснала листо и го пуснала до нея. " +
                "Мравката се качила на листото и стигнала невредима до брега. " +
                "Малко по-късно един ловец се прицелил в гълъбицата. Мравката видяла това и го ухапала по крака. Ловецът подскочил, гълъбицата чула шума и отлетяла на сигурно място.",
            "An ant went down to a stream to drink, but the water swept her away. \"Help!\" she cried. A dove sitting in a nearby tree picked a leaf and dropped it next to her. " +
                "The ant climbed onto the leaf and floated safely to the bank. " +
                "A little later a hunter aimed at the dove. The ant saw this and bit his foot. The hunter jumped, the dove heard the noise and flew away to safety.",
            "Поука: доброто се връща с добро.",
            "The lesson: one good turn deserves another.",
            bedtime = true,
        ),
        Tale(
            "bundle_sticks", TaleKind.FABLE, "🪵",
            "Снопът пръчки", "The Bundle of Sticks",
            "Един баща имал трима сина, които все се карали. Веднъж той им донесъл сноп пръчки, вързани заедно. „Счупете го“, казал. " +
                "Всеки от синовете опитал с всичка сила, но снопът не се счупил. Тогава бащата развързал пръчките и дал на всеки по една. Те ги счупили лесно. " +
                "„Виждате ли?“ усмихнал се бащата. „Заедно сте силни като този сноп. Сами всеки може да ви пречупи.“",
            "A father had three sons who were always quarrelling. One day he brought them a bundle of sticks tied together. \"Break it,\" he said. " +
                "Each son tried with all his might, but the bundle would not break. Then the father untied the sticks and gave each son one. They broke them easily. " +
                "\"You see?\" smiled the father. \"Together you are strong like this bundle. Alone, anyone can break you.\"",
            "Поука: в единството е силата.",
            "The lesson: united we stand.",
        ),

        // ------------------------------------------------------------- fairy tales
        Tale(
            "grandpas_mitten", TaleKind.FAIRY_TALE, "🧤",
            "Дядовата ръкавичка", "Grandpa's Mitten",
            "Дядо вървял през зимната гора и изпуснал ръкавичката си в снега. Притичала мишка: „Ох, каква топла къщичка!“ и се сгушила вътре. " +
                "После дошла жаба: „Кой живее в ръкавичката?“ „Мишката Гризанка. А ти коя си?“ „Жабата Скачанка. Пуснете ме!“ И станали две. " +
                "Дошло зайче Бегачо, после лисичка Хубавица, после вълчо Сивчо, и всички се сгушили вътре. Накрая дошла и мечката Тромава. " +
                "Ръкавичката се надула, шевовете запукали... Тогава дядо се върнал да я търси и кучето му излаяло. Животните изскочили и се пръснали по гората, " +
                "а дядо си прибрал ръкавичката, малко поразтеглена, но още топла.",
            "Grandpa was walking through the winter forest and dropped his mitten in the snow. A mouse ran up: \"Oh, what a warm little house!\" and snuggled inside. " +
                "Then a frog came: \"Who lives in the mitten?\" \"Nibbles the mouse. And who are you?\" \"Hopper the frog. Let me in!\" And now there were two. " +
                "Then came Quickfoot the hare, then Pretty the fox, then Greycoat the wolf, and they all squeezed inside. Last of all came Clumsy the bear. " +
                "The mitten puffed up, the seams began to creak... Just then Grandpa came back to look for it, and his dog barked. The animals jumped out and scattered through the forest, " +
                "and Grandpa picked up his mitten, a little stretched, but still warm.",
            bedtime = true,
        ),
        Tale(
            "turnip", TaleKind.FAIRY_TALE, "🌱",
            "Дядо и ряпа", "The Giant Turnip",
            "Посадил дядо ряпа и тя пораснала голяма-преголяма. Хванал дядо ряпата: дърпа, дърпа, не може да я извади. " +
                "Повикал баба. Баба за дядо, дядо за ряпата: дърпат, дърпат, не могат. Повикала баба внучката. Внучката за баба, баба за дядо: дърпат, дърпат, не могат. " +
                "Повикала внучката кучето, кучето повикало котката, котката повикала мишката. Мишката за котката, котката за кучето, кучето за внучката, внучката за баба, баба за дядо, дядо за ряпата. " +
                "Дръпнали всички заедно и... хоп! Ряпата излязла. И всички я яли за вечеря, и за мъничката мишка останало.",
            "Grandpa planted a turnip, and it grew big, very, very big. Grandpa took hold of the turnip: he pulled and pulled, but he could not pull it out. " +
                "He called Grandma. Grandma held Grandpa, Grandpa held the turnip: they pulled and pulled, but they could not. Grandma called their granddaughter. " +
                "The granddaughter called the dog, the dog called the cat, the cat called the mouse. The mouse held the cat, the cat held the dog, the dog held the granddaughter, the granddaughter held Grandma, Grandma held Grandpa, and Grandpa held the turnip. " +
                "They all pulled together and... pop! Out came the turnip. Everyone had it for supper, and there was some left for the little mouse too.",
            moralBg = "Заедно можем повече.",
            moralEn = "Together we can do more.",
            bedtime = true,
        ),
        Tale(
            "three_pigs", TaleKind.FAIRY_TALE, "🐷",
            "Трите прасенца", "The Three Little Pigs",
            "Три прасенца решили да си построят къщички. Първото набързо си направило къща от слама и отишло да играе. Второто сковало къща от пръчки и също отишло да играе. " +
                "А третото цял ден редило тухла по тухла. Дошъл гладният вълк. Духнал, пухнал и сламената къщичка отлетяла. Прасенцето избягало при брат си. " +
                "Вълкът духнал, пухнал и къщата от пръчки се разпаднала. Двете прасенца избягали при третото. " +
                "Вълкът духал, пухал пред тухлената къща, докато се изпоти, но тя дори не помръднала. Накрая той се отказал и си отишъл в гората. " +
                "А трите прасенца отпразнували с песни и после двете по-малки си построили тухлени къщи.",
            "Three little pigs decided to build houses. The first quickly made a house of straw and went off to play. The second made a house of sticks and went to play too. " +
                "But the third spent the whole day laying brick after brick. Along came the hungry wolf. He huffed and he puffed, and the straw house flew away. The pig ran to his brother. " +
                "The wolf huffed and puffed, and the house of sticks fell apart. The two pigs ran to the third. " +
                "The wolf huffed and puffed at the brick house until he was all sweaty, but it did not even wobble. At last he gave up and went back to the forest. " +
                "The three little pigs celebrated with songs, and later the two younger ones built brick houses too.",
            moralBg = "Работата, свършена добре, ни пази.",
            moralEn = "Work done well keeps us safe.",
        ),
        Tale(
            "goldilocks", TaleKind.FAIRY_TALE, "🐻",
            "Златокоска и трите мечета", "Goldilocks and the Three Bears",
            "В гората живеели три мечета: Татко Мечо, Мама Меца и Малкото Мече. Една сутрин сварили каша и излезли на разходка, докато изстине. " +
                "Покрай къщичката минало момиченце със златни коси. Влязло вътре и опитало кашата: голямата купа била твърде гореща, средната твърде студена, а малката точно както трябва, и я изяло. " +
                "Седнало на столчетата: малкото столче се счупило. Качило се на креватите: малкото креватче било точно по мярка и Златокоска заспала. " +
                "Мечетата се върнали. „Някой е ял от кашата ми!“ изписукало Малкото Мече. „И някой спи в креватчето ми!“ Златокоска се събудила, извинила се и обещала да поправи столчето. " +
                "Оттогава тя винаги чука, преди да влезе.",
            "Three bears lived in the forest: Daddy Bear, Mummy Bear and Baby Bear. One morning they made porridge and went for a walk while it cooled. " +
                "A little girl with golden hair passed their cottage. She went in and tasted the porridge: the big bowl was too hot, the middle one too cold, and the little one just right, so she ate it all. " +
                "She sat on the chairs, and the little chair broke. She climbed onto the beds, the little bed was just right, and Goldilocks fell asleep. " +
                "The bears came home. \"Someone's been eating my porridge!\" squeaked Baby Bear. \"And someone's sleeping in my bed!\" Goldilocks woke up, said sorry, and promised to mend the little chair. " +
                "Ever since then, she always knocks before she goes in.",
            moralBg = "Чуждото се пипа само с позволение.",
            moralEn = "Ask before you touch what belongs to others.",
        ),
        Tale(
            "ugly_duckling", TaleKind.FAIRY_TALE, "🦢",
            "Грозното патенце", "The Ugly Duckling",
            "Мама патица мътела яйцата си и от тях излезли пухкави жълти патенца. Последното яйце било голямо и от него излязло сиво, тромаво пате. " +
                "„Колко е грозно!“ смеели се другите и никой не искал да играе с него. Тъжното пате тръгнало по света сам-самичко. " +
                "Прекарало студената зима в тръстиките. Когато дошла пролетта, то видяло на езерото красиви бели птици. „Ще ме прогонят“, помислило си, но се приближило. " +
                "Погледнало във водата и не повярвало: от там го гледал прекрасен бял лебед. Патенцето било лебедче! Другите лебеди го поздравили като брат, а то се почувствало щастливо както никога.",
            "Mother Duck sat on her eggs, and out came fluffy yellow ducklings. The last egg was big, and out of it came a grey, clumsy duckling. " +
                "\"How ugly he is!\" laughed the others, and nobody wanted to play with him. The sad duckling set off into the world all alone. " +
                "He spent the cold winter among the reeds. When spring came, he saw beautiful white birds on the lake. \"They will chase me away,\" he thought, but he swam closer. " +
                "He looked into the water and could not believe it: a beautiful white swan was looking back at him. The duckling was a swan! The other swans greeted him like a brother, and he was happier than ever before.",
            moralBg = "Всеки е хубав по свой начин и всеки има нужда от доброта.",
            moralEn = "Everyone is beautiful in their own way, and everyone needs kindness.",
            bedtime = true,
        ),
        Tale(
            "little_red_hen", TaleKind.FAIRY_TALE, "🐔",
            "Кокошката и житното зрънце", "The Little Red Hen",
            "Кокошката намерила житно зрънце. „Кой ще го посади?“ попитала тя. „Не и аз“, казала котката. „Не и аз“, казало кучето. „Не и аз“, казала патицата. „Тогава ще го посадя сама.“ " +
                "Житото пораснало. „Кой ще го ожъне?“ „Не и аз“, казали всички. Кокошката го ожънала сама, занесла го на мелницата и сама опекла хляб. " +
                "Мирисът на топъл хляб се разнесъл по двора. „Кой ще яде хляба?“ попитала кокошката. „Аз! Аз! Аз!“ извикали всички. " +
                "„Този път ще го ядем с пиленцата, защото ние работихме“, казала кокошката. „Но догодина, ако помагате, ще има за всички.“ И догодина всички помагали.",
            "The little red hen found a grain of wheat. \"Who will plant it?\" she asked. \"Not I,\" said the cat. \"Not I,\" said the dog. \"Not I,\" said the duck. \"Then I will plant it myself.\" " +
                "The wheat grew. \"Who will cut it?\" \"Not I,\" said everyone. The hen cut it herself, took it to the mill, and baked the bread herself. " +
                "The smell of warm bread spread across the yard. \"Who will eat the bread?\" asked the hen. \"Me! Me! Me!\" they all shouted. " +
                "\"This time my chicks and I will eat it, because we did the work,\" said the hen. \"But next year, if you help, there will be enough for everyone.\" And next year, everyone helped.",
        ),
        Tale(
            "star_money", TaleKind.FAIRY_TALE, "🌟",
            "Звездните пари", "The Star Money",
            "Имало едно бедно, но добро момиченце. Нямало нищо освен дрехите на гърба си и едно парче хляб. Тръгнало то по полето. " +
                "Срещнало гладен старец и му дало хляба си. Срещнало дете, на което му било студено на главата, и му дало шапчицата си. После дало и жилетката си на друго дете, което зъзнело. " +
                "Стъмнило се и момиченцето застанало само под звездите. Изведнъж звездите започнали да падат от небето и на земята се превръщали в блестящи златни монети. " +
                "Момиченцето ги събрало, купило си топли дрехи и хляб и до края на живота си помагало на всеки, който има нужда.",
            "There was once a poor but kind little girl. She had nothing but the clothes she wore and a piece of bread. She set off across the fields. " +
                "She met a hungry old man and gave him her bread. She met a child whose head was cold and gave him her little cap. Then she gave her jacket to another child who was shivering. " +
                "Night fell, and the girl stood alone under the stars. Suddenly the stars began to fall from the sky, and on the ground they turned into shining gold coins. " +
                "The girl gathered them, bought warm clothes and bread, and for the rest of her life she helped everyone in need.",
            moralBg = "Доброто сърце е най-голямото богатство.",
            moralEn = "A kind heart is the greatest treasure.",
            bedtime = true,
        ),
        Tale(
            "znaiko_star", TaleKind.FAIRY_TALE, "⭐",
            "Знайко и малката звезда", "ZnaiKo and the Little Star",
            "Една вечер Знайко гледал небето и видял, че една малка звезда премигва тъжно. „Какво ти е?“ попитал той. „Изгубих си светлинката“, прошепнала звездата. " +
                "Знайко се замислил. Събрал светулките от поляната, помолил луната за малко сребро и духнал нежно към небето. " +
                "Светулките литнали нагоре и закръжили около звездата, а луната я погалила с лъч. Звездата пламнала и засияла по-ярко от всякога. " +
                "„Благодаря ти, Знайко!“ звъннала тя. „Всяка вечер ще светя над твоята къщичка.“ И оттогава, когато Знайко заспи, една звездичка пази съня му.",
            "One evening ZnaiKo was looking at the sky and saw a little star twinkling sadly. \"What's wrong?\" he asked. \"I've lost my light,\" whispered the star. " +
                "ZnaiKo thought hard. He gathered the fireflies from the meadow, asked the moon for a little silver, and blew gently towards the sky. " +
                "The fireflies flew up and circled the star, and the moon stroked it with a beam. The star sparkled and shone brighter than ever. " +
                "\"Thank you, ZnaiKo!\" it chimed. \"I'll shine over your little house every night.\" And ever since, when ZnaiKo falls asleep, a little star watches over his dreams.",
            bedtime = true,
        ),
        Tale(
            "znaiko_rainbow", TaleKind.FAIRY_TALE, "🌈",
            "Знайко и дъгата", "ZnaiKo and the Rainbow",
            "След лятната буря над поляната изгряла дъга, но в нея липсвал един цвят. „Синият ми избяга!“ проплака дъгата. " +
                "Знайко тръгнал да го търси. Попитал незабравката: „Виждала ли си синия цвят?“ „Малко от него е в мен“, казала тя и му дала капчица синьо. " +
                "Попитал морето и то му дало вълничка синьо. Попитал небето и то му дало парченце от себе си. " +
                "Знайко събрал синьото в шепи и го хвърлил към дъгата. Тя засияла с всичките си седем цвята: червено, оранжево, жълто, зелено, синьо, тъмносиньо и лилаво. " +
                "А всички деца на поляната запляскали с ръце.",
            "After a summer storm a rainbow rose over the meadow, but one of its colours was missing. \"My blue has run away!\" cried the rainbow. " +
                "ZnaiKo set off to find it. He asked the forget-me-not, \"Have you seen the colour blue?\" \"A little of it is in me,\" she said, and gave him a drop of blue. " +
                "He asked the sea, and it gave him a little wave of blue. He asked the sky, and it gave him a piece of itself. " +
                "ZnaiKo gathered the blue in his hands and tossed it to the rainbow. It shone with all seven colours: red, orange, yellow, green, blue, indigo and violet. " +
                "And all the children in the meadow clapped their hands.",
            bedtime = true,
        ),
    )

    val riddles: List<Riddle> = listOf(
        Riddle("watermelon", "🍉", "Зелена къщичка, червени стаички, а в тях черни човечета. Какво е?", "A green little house with red rooms inside, and little black people in them. What is it?", "диня", "watermelon", listOf("динята")),
        Riddle("clock", "⏰", "Няма крака, а върви; няма ръце, а сочи. Какво е?", "It has no legs, but it runs; it has no fingers, but it points. What is it?", "часовник", "clock", listOf("часовникът"), listOf("watch")),
        Riddle("snow", "❄️", "Бяла покривка цялото поле покри. Какво е?", "A white blanket covered the whole field. What is it?", "сняг", "snow", listOf("снегът", "снега")),
        Riddle("cabbage", "🥬", "Сто ризи има, а нито едно копче. Какво е?", "It wears a hundred shirts, but not a single button. What is it?", "зелка", "cabbage", listOf("зелето", "зеле")),
        Riddle("cloud", "☁️", "Без очи плаче, без крила лети. Какво е?", "It cries without eyes and flies without wings. What is it?", "облак", "cloud", listOf("облакът", "облака")),
        Riddle("hole", "🕳️", "Колкото повече вземаш от нея, толкова по-голяма става. Какво е?", "The more you take from it, the bigger it gets. What is it?", "дупка", "hole", listOf("дупката")),
        Riddle("piano", "🎹", "Има много клавиши, а не отключва нито една врата. Какво е?", "It has lots of keys, but it can't open a single door. What is it?", "пиано", "piano", listOf("пианото")),
        Riddle("towel", "🛁", "Колкото повече суши, толкова по-мокра става. Какво е?", "The more it dries, the wetter it gets. What is it?", "кърпа", "towel", listOf("кърпата", "хавлия")),
        Riddle("bottle", "🍾", "Има гърло, а няма глава. Какво е?", "It has a neck but no head. What is it?", "бутилка", "bottle", listOf("шише", "шишето", "бутилката")),
        Riddle("sponge", "🧽", "Цялата е на дупки, а пак държи вода. Какво е?", "It is full of holes, but it still holds water. What is it?", "гъба", "sponge", listOf("гъбата")),
        Riddle("fir", "🌲", "Зиме и лете все в едно и също зелено. Какво е?", "Winter and summer, always in the same green. What is it?", "елха", "fir tree", listOf("бор", "елхата", "борът"), listOf("pine", "pine tree", "christmas tree", "fir")),
        Riddle("snail", "🐌", "Ходи бавно, а къщата си носи на гърба. Кой е?", "It walks slowly and carries its house on its back. Who is it?", "охлюв", "snail", listOf("охлювът", "охлюва")),
        Riddle("butterfly", "🦋", "Има крила, но не е птица; лети от цвят на цвят. Коя е?", "It has wings but it isn't a bird; it flies from flower to flower. What is it?", "пеперуда", "butterfly", listOf("пеперудата")),
        Riddle("chair", "🪑", "Има четири крака, а никъде не ходи. Какво е?", "It has four legs, but it never walks anywhere. What is it?", "стол", "chair", listOf("столът", "стола", "маса"), listOf("table")),
        Riddle("moon", "🌙", "Нощем свети, денем се крие, понякога е кръгла, понякога е рогче. Каква е?", "It shines at night and hides by day; sometimes it's round, sometimes a thin smile. What is it?", "луна", "moon", listOf("луната")),
        Riddle("sun", "☀️", "Жълта топка в небето, всички стопля, никой не я хваща. Какво е?", "A yellow ball in the sky that warms everyone, but nobody can catch it. What is it?", "слънце", "sun", listOf("слънцето")),
        Riddle("potato", "🥔", "Има очи, а не вижда. Какво е?", "It has eyes but cannot see. What is it?", "картоф", "potato", listOf("картофът", "картофа")),
        Riddle("rain", "🌧️", "Пада от небето, а никога не се удря. Какво е?", "It falls from the sky but never gets hurt. What is it?", "дъжд", "rain", listOf("дъждът", "дъжда")),
        Riddle("coin", "🪙", "Има глава и опашка, а няма тяло. Какво е?", "It has a head and a tail, but no body. What is it?", "монета", "coin", listOf("монетата", "стотинка", "пара")),
        Riddle("egg", "🥚", "Бяла къщичка без врати и прозорци, а вътре жълто слънчице. Какво е?", "A little white house with no doors or windows, and a yellow sun inside. What is it?", "яйце", "egg", listOf("яйцето")),
    )

    fun of(kind: TaleKind): List<Tale> = all.filter { it.kind == kind }

    fun byId(id: String): Tale? = all.firstOrNull { it.id == id }

    /** A tale not heard lately: [recent] are the ids read most recently, newest last. */
    fun pick(kind: TaleKind?, recent: List<String>, random: Random = Random.Default, bedtimeOnly: Boolean = false): Tale {
        val pool = all.filter { (kind == null || it.kind == kind) && (!bedtimeOnly || it.bedtime) }.ifEmpty { all }
        val fresh = pool.filter { it.id !in recent }.ifEmpty { pool }
        return fresh[random.nextInt(fresh.size)]
    }

    fun riddle(recent: List<String>, random: Random = Random.Default): Riddle {
        val fresh = riddles.filter { it.id !in recent }.ifEmpty { riddles }
        return fresh[random.nextInt(fresh.size)]
    }
}
