package com.talkto.core.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SystemPromptTest {

    @Test fun `every build keeps the child safety rules`() {
        listOf(SystemPrompt.FULL, SystemPrompt.PLAY).forEach { b ->
            val p = SystemPrompt.build(b)
            assertThat(p).contains("children")
            assertThat(p).contains("116 111")
            assertThat(p).contains("Never ask for or repeat private details")
        }
    }

    @Test fun `the play build does not offer a file manager`() {
        val play = SystemPrompt.build(SystemPrompt.PLAY)
        assertThat(play).contains("no file manager")
        assertThat(play).doesNotContain("confirmation_token")
        assertThat(play).doesNotContain("manage files")
    }

    @Test fun `the full build explains two step deletion`() {
        val full = SystemPrompt.build(SystemPrompt.FULL)
        assertThat(full).contains("manage files")
        assertThat(full).contains("confirmation_token")
        assertThat(full).doesNotContain("no file manager")
    }

    @Test fun `the prompt is stable so it stays cached`() {
        assertThat(SystemPrompt.build(SystemPrompt.PLAY)).isEqualTo(SystemPrompt.build(SystemPrompt.PLAY))
        assertThat(AgentConfig().systemPrompt).isEqualTo(SystemPrompt.build(SystemPrompt.FULL))
    }
}
