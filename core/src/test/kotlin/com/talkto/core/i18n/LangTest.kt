package com.talkto.core.i18n

import com.google.common.truth.Truth.assertThat
import com.talkto.core.i18n.ScriptSegmenter.Segment
import org.junit.Test

class LangTest {
    @Test fun `codes, names and the other language`() {
        assertThat(Lang.of("en")).isEqualTo(Lang.EN)
        assertThat(Lang.of("xx")).isEqualTo(Lang.BG)
        assertThat(Lang.of(null)).isEqualTo(Lang.BG)
        assertThat(Lang.BG.other).isEqualTo(Lang.EN)
        assertThat(Lang.EN.nameIn(Lang.BG)).isEqualTo("английски")
        assertThat(Lang.BG.nameIn(Lang.EN)).isEqualTo("Bulgarian")
        assertThat(Lang.EN.pick("куче", "dog")).isEqualTo("dog")
    }
}

class ScriptSegmenterTest {
    @Test fun `mixed sentence is split by script`() {
        assertThat(ScriptSegmenter.segments("Куче на английски е dog."))
            .containsExactly(Segment("Куче на английски е ", Lang.BG), Segment("dog.", Lang.EN)).inOrder()
        assertThat(ScriptSegmenter.segments("\"Dog\" in Bulgarian is „куче“ 🐶."))
            .containsExactly(Segment("\"Dog\" in Bulgarian is „", Lang.EN), Segment("куче“ 🐶.", Lang.BG)).inOrder()
    }

    @Test fun `plain text is one segment and text without letters uses the fallback`() {
        assertThat(ScriptSegmenter.segments("Здравей, как си?")).containsExactly(Segment("Здравей, как си?", Lang.BG))
        assertThat(ScriptSegmenter.segments("Hello there!")).containsExactly(Segment("Hello there!", Lang.EN))
        assertThat(ScriptSegmenter.segments("42 %", Lang.EN)).containsExactly(Segment("42 %", Lang.EN))
        assertThat(ScriptSegmenter.segments("")).isEmpty()
    }

    @Test fun `a single stray latin letter does not switch the voice, a real word does`() {
        assertThat(ScriptSegmenter.segments("Таймер 5 h готов")).hasSize(1)
        assertThat(ScriptSegmenter.segments("Включи Wi-Fi сега")).containsExactly(
            Segment("Включи ", Lang.BG), Segment("Wi-Fi ", Lang.EN), Segment("сега", Lang.BG),
        ).inOrder()
    }

    @Test fun `dominant language`() {
        assertThat(ScriptSegmenter.dominant("Отвори Spotify моля")).isEqualTo(Lang.BG)
        assertThat(ScriptSegmenter.dominant("Open the camera")).isEqualTo(Lang.EN)
        assertThat(ScriptSegmenter.dominant("123", Lang.EN)).isEqualTo(Lang.EN)
    }
}
