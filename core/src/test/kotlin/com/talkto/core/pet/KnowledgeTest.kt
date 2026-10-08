package com.talkto.core.pet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KnowledgeTest {
    @Test fun `updates arrive at growing thresholds`() {
        assertThat(Knowledge.updatesFor(0)).isEqualTo(0)
        assertThat(Knowledge.updatesFor(29)).isEqualTo(0)
        assertThat(Knowledge.updatesFor(30)).isEqualTo(1)
        assertThat(Knowledge.updatesFor(90)).isEqualTo(2)
        assertThat(Knowledge.updatesFor(1_000_000)).isEqualTo(Knowledge.UPDATES.size)
        assertThat(Knowledge.progress(60)).isWithin(1e-4f).of(0.5f)
    }

    @Test fun `versions and the list of news between two versions`() {
        assertThat(Knowledge.versionName(0)).isEqualTo("1.0")
        assertThat(Knowledge.versionName(3)).isEqualTo("1.3")
        assertThat(Knowledge.versionName(10)).isEqualTo("2.0")
        assertThat(Knowledge.between(1, 3).map { it.number }).containsExactly(2, 3).inOrder()
        assertThat(Knowledge.UPDATES.map { it.number }).isEqualTo((1..Knowledge.UPDATES.size).toList())
    }

    @Test fun `the sprout grows, flowers and gets a star`() {
        assertThat(Knowledge.look(0).flower).isFalse()
        assertThat(Knowledge.look(4).flower).isTrue()
        assertThat(Knowledge.look(8).star).isTrue()
        assertThat(Knowledge.look(10).sproutScale).isGreaterThan(Knowledge.look(1).sproutScale)
    }
}
