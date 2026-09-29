package com.talkto.core.pet

/** What taught ZnaiKo something, and how much it counts. */
enum class KnowledgeSource(val points: Int) {
    /** Each message the user writes or says. */
    CHAT(1),
    /** A new fact about the user was learned (name, birthday, favourite things). */
    FACT(6),
    /** A finished game, whoever won. */
    GAME(3),
    /** ZnaiKo lost: it learns most from its mistakes. */
    GAME_LOST(5),
    /** A new day lived. */
    DAY(4),
}

/** One update ZnaiKo installs when it has learned enough. */
data class ZnaiKoUpdate(
    /** 1, 2, 3 ... ; shown as "1.1", "1.2" ... */
    val number: Int,
    val title: String,
    val news: List<String>,
) {
    val version: String get() = Knowledge.versionName(number)
}

/**
 * Knowledge is separate from XP: XP comes from care and makes ZnaiKo grow up (life stages), knowledge comes from
 * talking and playing and makes it smarter. Every threshold installs an update: it plays games better
 * ([com.talkto.core.games.Skill] = number of updates), and its look changes (the sprout grows, then flowers).
 * Update n needs 15·n·(n+1) knowledge: 30, 90, 180, 300 ... so the first one comes on day one, later ones take longer.
 */
object Knowledge {
    fun needed(update: Int): Int = 15 * update * (update + 1)

    fun updatesFor(knowledge: Int): Int {
        var n = 0
        while (n < UPDATES.size && knowledge >= needed(n + 1)) n++
        return n
    }

    /** 0..1 towards the next update (1 when all are installed). */
    fun progress(knowledge: Int): Float {
        val n = updatesFor(knowledge)
        if (n >= UPDATES.size) return 1f
        val from = needed(n)
        val to = needed(n + 1)
        return ((knowledge - from).toFloat() / (to - from)).coerceIn(0f, 1f)
    }

    fun versionName(updates: Int): String = if (updates < 10) "1.$updates" else "${1 + updates / 10}.${updates % 10}"

    fun update(number: Int): ZnaiKoUpdate? = UPDATES.getOrNull(number - 1)

    /** Updates installed between [from] (exclusive) and [to] (inclusive), oldest first. */
    fun between(from: Int, to: Int): List<ZnaiKoUpdate> = ((from + 1)..to).mapNotNull(::update)

    /** How the sprout on ZnaiKo's head looks after [updates]: leaves, then a flower, then a little star. */
    data class Look(val sproutScale: Float, val leaves: Int, val flower: Boolean, val star: Boolean)

    fun look(updates: Int): Look = Look(
        sproutScale = 1f + updates.coerceAtMost(12) * 0.07f,
        leaves = (2 + updates / 2).coerceAtMost(4),
        flower = updates >= 4,
        star = updates >= 8,
    )

    val UPDATES = listOf(
        ZnaiKoUpdate(1, "Първото обновление", listOf("Играя по-внимателно: по-рядко правя глупави ходове.", "Листенцето ми порасна.")),
        ZnaiKoUpdate(2, "Малкият стратег", listOf("Мисля с един ход напред в шаха.", "На „Четири в редица“ вече пазя средата.")),
        ZnaiKoUpdate(3, "Паметливко", listOf("В Мемори помня повече карти.", "Поникна ми трето листенце.")),
        ZnaiKoUpdate(4, "Цветенце", listOf("На главата ми цъфна цвете!", "В „Не се сърди, човече“ бягам от опасност.")),
        ZnaiKoUpdate(5, "Хитрецът", listOf("На морски шах почти не греша.", "Търся по-дълбоко в шаха.")),
        ZnaiKoUpdate(6, "Четири листа", listOf("Имам четири листенца, като детелина за късмет.", "По-рядко забравям какво съм видял.")),
        ZnaiKoUpdate(7, "Шахматистът", listOf("Виждам три хода напред в шаха.", "Пазя фигурите си по-добре.")),
        ZnaiKoUpdate(8, "Звездичка", listOf("Над цветето ми светна звездичка.", "В Мемори помня почти всичко.")),
        ZnaiKoUpdate(9, "Мъдрецът", listOf("Играя почти без грешки.", "Още по-силен съм на „Четири в редица“.")),
        ZnaiKoUpdate(10, "Гросмайсторът", listOf("Това е най-силната ми версия: играя с пълна сила.", "Благодаря, че ме научи на толкова неща!")),
    )
}
