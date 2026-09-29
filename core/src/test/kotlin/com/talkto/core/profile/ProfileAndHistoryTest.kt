package com.talkto.core.profile

import com.google.common.truth.Truth.assertThat
import com.talkto.core.agent.ClaudeAgent
import com.talkto.core.history.HistoryRepository
import com.talkto.core.history.HistoryStore
import com.talkto.core.history.Speaker
import com.talkto.core.history.Utterance
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.ZoneOffset

class InMemoryProfileStore : ProfileStore {
    private val facts = LinkedHashMap<String, Fact>()
    override suspend fun upsert(fact: Fact) { facts[fact.key] = fact }
    override suspend fun all() = facts.values.toList()
    override suspend fun delete(key: String) = facts.remove(key) != null
}

class InMemoryHistoryStore : HistoryStore {
    val items = mutableListOf<Utterance>()
    override suspend fun insert(u: Utterance): Long { items += u.copy(id = items.size + 1L); return items.size.toLong() }
    override suspend fun recent(limit: Int) = items.takeLast(limit)
    override suspend fun search(query: String, limit: Int) = items.filter { query.lowercase() in it.text.lowercase() }.reversed().take(limit)
    override suspend fun count() = items.size
    override suspend fun clear() = items.clear()
    override suspend fun deleteOlderThan(cutoffMs: Long): Int {
        val before = items.size; items.removeAll { it.atMs < cutoffMs }; return before - items.size
    }
}

class FactExtractorTest {
    private fun ex(s: String) = FactExtractor.extract(s).toMap()

    @Test fun `names need a capital letter`() {
        assertThat(ex("Казвам се Мария")).containsEntry("name", "Мария")
        assertThat(ex("аз съм Петър и обичам котки")).containsExactly("name", "Петър", "likes:котки", "котки")
        assertThat(ex("аз съм гладен")).isEmpty()
        assertThat(ex("My name is Alex")).containsEntry("name", "Alex")
    }

    @Test fun `likes, dislikes, city, job, birthday, favourites`() {
        assertThat(ex("обичам кафе без захар")).containsEntry("likes:кафе без захар", "кафе без захар")
        assertThat(ex("не обичам понеделниците")).containsExactly("dislikes:понеделниците", "понеделниците")
        assertThat(ex("живея в Пловдив")).containsEntry("city", "Пловдив")
        assertThat(ex("работя като медицинска сестра")).containsEntry("job", "медицинска сестра")
        assertThat(ex("рожденият ми ден е на 15.03")).containsEntry("birthday", "15.03")
        assertThat(ex("любимият ми цвят е зеленото")).containsEntry("favourite:цвят", "зеленото")
    }

    @Test fun `not every sentence is a fact`() {
        assertThat(ex("обичам да плувам")).isEmpty()
        assertThat(ex("обичам те")).isEmpty()
        assertThat(ex("отвори камера")).isEmpty()
    }

    @Test fun `remember and aliases`() {
        assertThat(ex("запомни, че ключът е под саксията").values).containsExactly("ключът е под саксията")
        assertThat(ex("когато кажа кино, направи тихо")).containsExactly("alias:кино", "тихо")
        assertThat(ex("Когато кажа „лека нощ“ направи „угаси фенерчето“")).containsExactly("alias:лека нощ", "угаси фенерчето")
    }
}

class ProfileRepositoryTest {
    private var now = 1L
    private val repo = ProfileRepository(InMemoryProfileStore(), clock = { now++ })

    @Test fun `learning updates instead of duplicating`() = runTest {
        assertThat(repo.learnFrom("Казвам се Мария").single().value).isEqualTo("Мария")
        assertThat(repo.learnFrom("Казвам се Мария")).isEmpty() // nothing new
        repo.learnFrom("Казвам се Мими")
        assertThat(repo.get("name")).isEqualTo("Мими")
        assertThat(repo.all().count { it.key == "name" }).isEqualTo(1)
    }

    @Test fun `aliases expand and are hidden from the prompt`() = runTest {
        repo.learnFrom("когато кажа кино, направи тихо")
        repo.learnFrom("обичам джаз")
        assertThat(repo.expandAlias("Кино!")).isEqualTo("тихо")
        val block = repo.promptBlock()
        assertThat(block).contains("likes:джаз")
        assertThat(block).doesNotContain("alias")
    }

    @Test fun `forget by word and forget all`() = runTest {
        repo.learnFrom("обичам джаз")
        repo.learnFrom("живея в Варна")
        assertThat(repo.forget("джаз")).isEqualTo(1)
        assertThat(repo.all().map { it.key }).containsExactly("city")
        assertThat(repo.forgetAll()).isEqualTo(1)
        assertThat(repo.promptBlock()).isEmpty()
    }

    @Test fun `birthday in several formats`() = runTest {
        repo.remember("birthday", "15.03")
        assertThat(repo.isBirthday(15, 3)).isTrue()
        repo.remember("birthday", "7 май")
        assertThat(repo.isBirthday(7, 5)).isTrue()
        assertThat(repo.isBirthday(8, 5)).isFalse()
    }
}

class HistoryRepositoryTest {
    private var now = 1_790_604_600_000L
    private val store = InMemoryHistoryStore()
    private val repo = HistoryRepository(store, clock = { now++ })

    @Test fun `records, searches and exports`() = runTest {
        repo.record(Speaker.USER, "Как се прави баница?", "offline")
        repo.record(Speaker.TALKTO, "С точени кори и сирене.", "claude")
        repo.record(Speaker.USER, "   ", "offline") // blank lines are skipped
        assertThat(repo.count()).isEqualTo(2)
        assertThat(repo.search("баница").single().speaker).isEqualTo(Speaker.USER)
        assertThat(repo.export(ZoneOffset.UTC)).isEqualTo(
            "[2026-09-28 14:10] Аз: Как се прави баница?\n[2026-09-28 14:10] ZnaiKo: С точени кори и сирене.",
        )
    }

    @Test fun `recording can be switched off`() = runTest {
        repo.enabled = false
        repo.record(Speaker.USER, "таен разговор", "offline")
        assertThat(repo.count()).isEqualTo(0)
    }
}

class ClaudeSeedTest {
    @Test fun `seed merges same-speaker lines, starts with the user and ends with ZnaiKo`() = runTest {
        val agent = ClaudeAgent(
            client = { error("no network in this test") },
            dispatcher = com.talkto.core.agent.ToolDispatcherFixtures.dispatcher(),
            memory = com.talkto.core.memory.MemoryRepository(com.talkto.core.memory.InMemoryActionLogStore()),
            liveContext = { "" },
        )
        agent.seed(listOf(false to "здрасти", true to "а", true to "б", false to "в", true to "г"))
        // talkto-first dropped, "а\nб" merged, trailing user "г" dropped -> [user, assistant]
        assertThat(agent.historySize).isEqualTo(2)
        agent.seed(listOf(true to "x", false to "y"))
        assertThat(agent.historySize).isEqualTo(2) // only applies to an empty history
    }
}
