package com.talkto.app.screens

import android.Manifest
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.talkto.app.MainActivity
import com.talkto.app.R
import com.talkto.app.i18n.LanguageRepository
import com.talkto.app.ui.MainViewModel
import com.talkto.core.games.GameKind
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.time.LocalDate

/**
 * Not a check of right and wrong: a walk through ZnaiKo's screens that leaves a screenshot of each one and short screen
 * recordings of its motion, so the look can be judged without a phone. CI's `screens` job runs it on an emulator
 * (.github/scripts/screen-tour.sh) and uploads the pictures as the talkto-screens artifact. Every step starts ZnaiKo
 * afresh, so a step that goes wrong still leaves the other steps' pictures.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ScreenTour {

    @get:Rule val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext

    /** Inside the app, so the script takes the pictures out with run-as. */
    private val out = File(context.filesDir, "screens").apply { mkdirs() }

    // ----------------------------------------------------------------------- the first start

    @Test fun a01_firstStart() = open(firstStart = true) { vm ->
        shot("01-age")
        ui { vm.setBirth(LocalDate.now().year - 8, 3) }
        shot("02-story", wait = 2_500)
        ui { vm.closeStory() }
        shot("03-main", wait = 2_500)
    }

    // ----------------------------------------------------------------------- the sheets, as a child opens them

    @Test fun b01_food() = open { tap(By.desc(text(R.string.action_feed))); shots("10-food") }

    @Test fun b02_games() = open { tap(By.desc(text(R.string.action_play))); shots("11-games") }

    @Test fun b03_wardrobe() = open { tap(By.desc(text(R.string.action_wardrobe))); shots("12-wardrobe") }

    @Test fun b04_learn() = open { tap(By.desc(text(R.string.action_learn))); shots("13-learn") }

    @Test fun b05_stories() = open { tap(By.desc("Приказки")); shots("14-stories") }

    @Test fun b06_house() = open { tap(By.desc(text(R.string.action_house))); shots("15-house") }

    @Test fun b07_settings() = open { tap(By.desc(text(R.string.action_settings))); shots("16-settings") }

    @Test fun b08_shop() = open { tap(By.textStartsWith("🪙")); shots("17-shop") }

    // ----------------------------------------------------------------------- games and lessons

    @Test fun c01_tetris() = open { vm -> ui { vm.openArcade(MainViewModel.Arcade.TETRIS) }; shot("20-tetris", wait = 3_000) }

    @Test fun c02_sweets() = open { vm -> ui { vm.openArcade(MainViewModel.Arcade.SWEETS) }; shot("21-sweets", wait = 3_000) }

    @Test fun c03_catchFood() = open { vm -> ui { vm.openArcade(MainViewModel.Arcade.FEED) }; shot("22-catch-food", wait = 3_000) }

    @Test fun c04_letterRain() = open { vm -> ui { vm.openArcade(MainViewModel.Arcade.LETTERS) }; shot("23-letter-rain", wait = 3_000) }

    @Test fun c05_maths() = open { vm -> ui { vm.startMath() }; shot("24-maths", wait = 2_500) }

    @Test fun c06_trivia() = open { vm -> ui { vm.startTrivia() }; shot("25-trivia", wait = 2_500) }

    @Test fun c07_drawing() = open { vm -> ui { vm.openDrawing() }; shot("26-drawing", wait = 2_500) }

    @Test fun c08_lesson() = open { vm -> ui { vm.startLesson(null, false) }; shot("27-lesson", wait = 2_500) }

    @Test fun c09_ticTacToe() = open { vm -> ui { vm.startGame(GameKind.TIC_TAC_TOE) }; shot("28-tic-tac-toe", wait = 2_500) }

    @Test fun c10_connectFour() = open { vm -> ui { vm.startGame(GameKind.CONNECT_FOUR) }; shot("29-connect-four", wait = 2_500) }

    @Test fun c11_ludo() = open { vm -> ui { vm.startGame(GameKind.LUDO) }; shot("30-ludo", wait = 2_500) }

    @Test fun c12_chess() = open { vm -> ui { vm.startGame(GameKind.CHESS) }; shot("31-chess", wait = 2_500) }

    @Test fun c13_memory() = open { vm -> ui { vm.startGame(GameKind.MEMORY) }; shot("32-memory", wait = 2_500) }

    // ----------------------------------------------------------------------- motion, filmed

    @Test fun d01_spin() = open { vm -> record("40-spin", seconds = 6) { pause(800); ui { vm.play() } } }

    @Test fun d02_feeding() = open {
        record("41-feeding", seconds = 8) {
            tap(By.desc(text(R.string.action_feed)))
            pause(1_500)
            tap(By.text("🥕"))
        }
    }

    @Test fun d03_sheet() = open {
        record("42-sheet", seconds = 6) {
            tap(By.desc(text(R.string.action_play)))
            pause(2_500)
            device.pressBack()
        }
    }

    // ----------------------------------------------------------------------- helpers

    /** Starts ZnaiKo, past the age question and the story unless [firstStart], runs [step] and closes it again. */
    private fun open(firstStart: Boolean = false, step: (MainViewModel) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: MainViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[MainViewModel::class.java] }
            // The first frame, the 3D creature and what the app loads in the background.
            pause(5_000)
            if (!firstStart) {
                ui { if (vm.settings.value.needsAge) vm.setBirth(LocalDate.now().year - 8, 3) }
                pause(500)
                ui { if (!vm.settings.value.storySeen) vm.closeStory() }
                pause(1_500)
            }
            step(vm)
        }
    }

    /** The screen's words are in ZnaiKo's language, not the emulator's. */
    private fun text(res: Int): String = LanguageRepository.localized(context, LanguageRepository.read(context)).getString(res)

    private fun ui(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun pause(ms: Long) = SystemClock.sleep(ms)

    private fun tap(selector: BySelector) {
        val found = device.wait(Until.findObject(selector), 10_000) ?: error("Not on the screen: $selector")
        found.click()
    }

    private fun shot(name: String, wait: Long = 1_500) {
        pause(wait)
        device.takeScreenshot(File(out, "$name.png"))
    }

    /** A sheet as it opens, then pulled up to show the rest of it. */
    private fun shots(name: String) {
        shot(name)
        val x = device.displayWidth / 2
        device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, 40)
        shot("$name-2")
    }

    /** Films the screen while [action] runs; the script then makes one picture of the film's frames. */
    private fun record(name: String, seconds: Int, action: () -> Unit) {
        val film = instrumentation.uiAutomation.executeShellCommand(
            "screenrecord --size 720x1600 --bit-rate 6000000 --time-limit $seconds /data/local/tmp/znaiko-$name.mp4",
        )
        pause(500)
        try {
            action()
        } finally {
            // Reading to the end waits until screenrecord stops at its time limit.
            ParcelFileDescriptor.AutoCloseInputStream(film).use { it.readBytes() }
        }
    }
}
