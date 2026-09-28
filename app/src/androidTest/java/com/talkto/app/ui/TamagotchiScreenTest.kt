package com.talkto.app.ui

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.talkto.app.MainActivity
import com.talkto.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test on a real device/emulator: the full app graph starts, the pet renders and reacts. */
@RunWith(AndroidJUnit4::class)
class TamagotchiScreenTest {

    // Pre-grant, so the API 33+ notification dialog never covers the screen under test.
    @get:Rule(order = 0) val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Test fun screenShowsPetAndControls() {
        rule.onNodeWithText("TALKTO").assertIsDisplayed()
        rule.onNodeWithContentDescription("Talkto").assertIsDisplayed()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.action_feed)).assertIsDisplayed()
    }

    @Test fun wardrobeOpens() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.action_wardrobe)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.hat_crown)).assertIsDisplayed()
    }

    @Test fun feedingDoesNotCrash() {
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.action_feed)).performClick()
        rule.onNodeWithText("TALKTO").assertIsDisplayed()
    }
}
