package com.talkto.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.speech.RecognizerIntent
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkto.app.R
import com.talkto.app.agent.PendingConfirmation
import com.talkto.app.files.StorageAccess
import com.talkto.app.games.Board
import com.talkto.app.pet.PetState
import com.talkto.app.avatar3d.Avatar3DView
import com.talkto.app.background.BackgroundLibrary
import com.talkto.app.background.MoodBackdrop
import com.talkto.app.ui.components.AvatarStage
import com.talkto.app.ui.components.petTouches
import com.talkto.app.ui.games.GameDialog
import com.talkto.app.ui.games.GamesSheet
import com.talkto.app.ui.games.UpdateDialog
import com.talkto.app.ui.learn.LanguagePicker
import com.talkto.app.ui.learn.LearnSheet
import com.talkto.app.ui.learn.LessonDialog
import com.talkto.app.ui.learn.PracticeChip
import com.talkto.app.ui.house.HouseSheet
import com.talkto.app.ui.look.CoinsPill
import com.talkto.app.ui.look.CreatorSheet
import com.talkto.app.ui.quiz.QuizDialog
import com.talkto.app.ui.shop.ShopSheet
import com.talkto.core.commands.AppCommand
import androidx.compose.material.icons.rounded.Home
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.sp
import com.talkto.app.i18n.screenLang
import com.talkto.app.ui.theme.TalktoColors
import com.talkto.app.voice.VoiceLanguage
import com.talkto.core.voice.VoicePreset
import com.talkto.app.voice.VoiceState
import com.talkto.core.agent.ConfirmationRequest
import com.talkto.core.avatar.AvatarStyle
import com.talkto.core.agent.ToolProtocol
import com.talkto.core.history.Speaker
import com.talkto.core.memory.Habit
import com.talkto.core.profile.ProfileRepository
import com.talkto.core.scene.MoodScene
import com.talkto.core.pet.LifeStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class Sheet { NONE, CREATOR, AVATAR, SETTINGS, HISTORY, BACKGROUNDS, GAMES, LEARN, SHOP, HOUSE, FOOD }

@Composable
fun TamagotchiScreen(vm: MainViewModel) {
    val pet by vm.pet.collectAsStateWithLifecycle()
    val pose by vm.pose.collectAsStateWithLifecycle()
    val visual by vm.visual.collectAsStateWithLifecycle()
    val outfit by vm.outfit.collectAsStateWithLifecycle()
    val agent by vm.agent.collectAsStateWithLifecycle()
    val systemLine by vm.systemLine.collectAsStateWithLifecycle()
    val permissions by vm.permissions.collectAsStateWithLifecycle()
    val confirmation by vm.confirmation.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lookAt by vm.lookAt.collectAsStateWithLifecycle()
    val look by vm.look.collectAsStateWithLifecycle()
    val house by vm.house.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    var aboutMe by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.screens.collect { cmd ->
            when (cmd) {
                AppCommand.OpenHouse -> sheet = Sheet.HOUSE
                AppCommand.OpenShop -> sheet = Sheet.SHOP
                AppCommand.OpenCreator -> sheet = Sheet.CREATOR
                AppCommand.AboutMe -> { aboutMe = true; sheet = Sheet.HOUSE }
                else -> Unit
            }
        }
    }

    val lastReply = agent.messages.lastOrNull { !it.fromUser }
    val bubbleText = when {
        systemLine != null && (lastReply == null || systemLine!!.atMs > lastReply.atMs) ->
            systemLine!!.text ?: stringResource(systemLine!!.res, *systemLine!!.args.toTypedArray())
        else -> lastReply?.text
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Header(
            online = settings.hasClaudeKey || !settings.loaded,
            coins = pet.coins,
            gains = vm.coinGains,
            atHome = pet.atHome,
            onCoins = { sheet = Sheet.SHOP },
            onHouse = { sheet = Sheet.HOUSE },
            onSettings = { vm.loadHabits(); vm.loadProfile(); sheet = Sheet.SETTINGS },
            onLanguage = vm::toggleLanguage,
        )
        StatsRow(pet)
        if (!permissions.allFiles) PermissionBanner()
        Spacer(Modifier.height(8.dp))

        DeviceScreen(Modifier.weight(1f)) {
            val background by vm.backgroundConfig.collectAsStateWithLifecycle()
            if (background.enabled) {
                val scene = MoodScene.forMood(pose.expression, pet.sleeping)
                MoodBackdrop(scene, background.photos[scene].orEmpty(), Modifier.fillMaxSize())
            }
            val stageModifier = Modifier.fillMaxSize().padding(top = 72.dp, bottom = 12.dp, start = 24.dp, end = 24.dp)
            // What ZnaiKo eats shows on its body: round and pale from junk food, slim and shiny from healthy food.
            val fedLook = remember(look, pet.fat.toInt(), pet.vitality.toInt()) { com.talkto.core.pet.Nutrition.look(look, pet.fat, pet.vitality) }
            // A photo avatar is a 2D portrait; the built-in creature is 3D unless switched off in Settings.
            if (visual.bitmap == null && settings.avatar3d) {
                Avatar3DView(
                    pose = pose,
                    outfit = outfit,
                    stage = pet.stage,
                    sleeping = pet.sleeping,
                    reactions = vm.reactions,
                    lookAt = lookAt,
                    modifier = stageModifier,
                    body = vm.body,
                    twirl = vm.twirl,
                    growth = pet.stageGrowth,
                    updates = pet.updates,
                    look = fedLook,
                    house = house,
                    atHome = pet.atHome,
                    roundness = com.talkto.core.pet.Nutrition.roundness(pet.fat),
                )
            } else {
                AvatarStage(
                    visual = visual, pose = pose, outfit = outfit, sleeping = pet.sleeping, stage = pet.stage, modifier = stageModifier,
                    look = fedLook, house = house, atHome = pet.atHome,
                )
            }
            // Transparent layer above either renderer: slaps, hits, pats and caresses. At home a tap opens the house.
            Box(
                stageModifier.petTouches(
                    body = vm.body,
                    onDown = vm::onTouchDown,
                    onTouch = { t ->
                        if (pet.atHome) {
                            sheet = Sheet.HOUSE
                        } else {
                            vm.onTouch(t)
                        }
                    },
                    onTwirl = { if (!pet.atHome) vm.twirl.drag(it) },
                    onTwirlEnd = vm.twirl::release,
                ),
            )
            com.talkto.app.ui.food.FeedingOverlay(vm.meals, stageModifier)
            SpeechBubble(
                text = when {
                    agent.busy && agent.activeTool != null -> stringResource(R.string.tool_running, toolLabel(agent.activeTool!!))
                    agent.busy -> stringResource(R.string.thinking)
                    else -> bubbleText
                },
                busy = agent.busy,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
            )
            if (visual.generating) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TalktoColors.Sunflower)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.avatar_generating), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        ActionRow(
            sleeping = pet.sleeping,
            onFeed = { sheet = Sheet.FOOD },
            onPlay = { sheet = Sheet.GAMES },
            onSleep = vm::toggleSleep,
            onWardrobe = { sheet = Sheet.CREATOR },
            onLearn = { sheet = Sheet.LEARN },
        )
        Spacer(Modifier.height(12.dp))
        val voice by vm.voice.collectAsStateWithLifecycle()
        // Without the microphone permission the system dialog still works, so a "no" is not a dead end.
        val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.startListening(dialogOnly = !granted) }
        val speechDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            vm.onVoiceDialogResult(result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))
        }
        LaunchedEffect(Unit) {
            vm.voiceDialogRequests.collect { lang ->
                try {
                    speechDialog.launch(vm.voiceDialogIntent(lang))
                } catch (_: ActivityNotFoundException) {
                    vm.onVoiceDialogUnavailable()
                }
            }
        }
        val ctx = LocalContext.current
        val practice by vm.practice.collectAsStateWithLifecycle()
        practice?.let { target -> PracticeChip(target, onStop = vm::stopPractice) }
        LaunchedEffect(Unit) { vm.lessonRequests.collect { sheet = Sheet.LEARN } }
        ChatInput(
            busy = agent.busy,
            voice = voice,
            language = runCatching { VoiceLanguage.valueOf(settings.voiceLanguage) }.getOrDefault(VoiceLanguage.AUTO),
            onSend = vm::send,
            onMic = {
                when {
                    voice.listening -> vm.stopListening()
                    ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> vm.startListening()
                    else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onLanguage = vm::setVoiceLanguage,
        )
        Spacer(Modifier.height(12.dp))
    }

    when (sheet) {
        Sheet.CREATOR -> CreatorSheet(vm, onPhoto = { sheet = Sheet.AVATAR }, onShop = { sheet = Sheet.SHOP }, onDismiss = { sheet = Sheet.NONE })
        Sheet.SHOP -> ShopSheet(vm, onDismiss = { sheet = Sheet.NONE })
        Sheet.HOUSE -> HouseSheet(vm, askAboutMe = aboutMe, onShop = { aboutMe = false; sheet = Sheet.SHOP }, onDismiss = { aboutMe = false; sheet = Sheet.NONE })
        Sheet.LEARN -> LearnSheet(
            vm,
            onLesson = { topic, speaking -> sheet = Sheet.NONE; vm.startLesson(topic, speaking) },
            onPractice = { sheet = Sheet.NONE; vm.startPractice() },
            onDismiss = { sheet = Sheet.NONE },
        )
        Sheet.AVATAR -> AvatarCreatorSheet(vm, onDismiss = { sheet = Sheet.NONE })
        Sheet.SETTINGS -> SettingsSheet(
            vm, permissions, onDismiss = { sheet = Sheet.NONE },
            onHistory = { sheet = Sheet.HISTORY }, onBackgrounds = { sheet = Sheet.BACKGROUNDS },
        )
        Sheet.BACKGROUNDS -> BackgroundsSheet(vm, onDismiss = { sheet = Sheet.NONE })
        Sheet.HISTORY -> HistorySheet(vm, onDismiss = { sheet = Sheet.NONE })
        Sheet.GAMES -> GamesSheet(
            pet,
            onPick = { kind, players -> sheet = Sheet.NONE; vm.startGame(kind, players) },
            onQuickPlay = { sheet = Sheet.NONE; vm.play() },
            onDismiss = { sheet = Sheet.NONE },
            onMath = { sheet = Sheet.NONE; vm.startMath() },
            onTrivia = { sheet = Sheet.NONE; vm.startTrivia() },
            onTetris = { sheet = Sheet.NONE; vm.openArcade(MainViewModel.Arcade.TETRIS) },
            onSweets = { sheet = Sheet.NONE; vm.openArcade(MainViewModel.Arcade.SWEETS) },
        )
        Sheet.FOOD -> com.talkto.app.ui.food.FoodSheet(pet, onEat = { f -> sheet = Sheet.NONE; vm.feed(f) }, onDismiss = { sheet = Sheet.NONE })
        Sheet.NONE -> Unit
    }

    val game by vm.games.state.collectAsStateWithLifecycle()
    game?.let { g ->
        GameDialog(g, vm.games, onPlayAgain = { vm.startGame(g.kind, (g.board as? Board.LudoBoard)?.players ?: 2) })
    }
    val lesson by vm.lessons.state.collectAsStateWithLifecycle()
    lesson?.let { LessonDialog(it, vm) }
    val quiz by vm.quiz.state.collectAsStateWithLifecycle()
    quiz?.let { QuizDialog(it, vm) }
    val updates by vm.pendingUpdates.collectAsStateWithLifecycle()
    updates.firstOrNull()?.let { UpdateDialog(it, pet, onDismiss = vm::dismissUpdate) }

    confirmation?.let { ConfirmationDialog(it, onAnswer = vm::answerConfirmation) }
    val arcade by vm.arcade.collectAsStateWithLifecycle()
    when (arcade) {
        MainViewModel.Arcade.TETRIS -> com.talkto.app.ui.games.TetrisDialog(onClose = vm::closeArcade, onFinish = { p, l -> vm.arcadeFinished(MainViewModel.Arcade.TETRIS, p, lines = l) })
        MainViewModel.Arcade.SWEETS -> com.talkto.app.ui.games.SweetsDialog(onClose = vm::closeArcade, onFinish = { p, w -> vm.arcadeFinished(MainViewModel.Arcade.SWEETS, p, won = w) })
        null -> Unit
    }
    // The story of ZnaiKo and the friends: once at the first start, and again from Settings.
    val storyReplay by vm.storyReplay.collectAsStateWithLifecycle()
    if ((settings.loaded && !settings.storySeen) || storyReplay) com.talkto.app.ui.story.StoryDialog(vm)
}

// ----------------------------------------------------------------------- header & stats

@Composable
private fun Header(
    online: Boolean,
    coins: Int,
    gains: kotlinx.coroutines.flow.Flow<Int>,
    atHome: Boolean,
    onCoins: () -> Unit,
    onHouse: () -> Unit,
    onSettings: () -> Unit,
    onLanguage: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        com.talkto.app.ui.components.ZnaiKoLogo(size = 24.sp)
        Spacer(Modifier.width(6.dp))
        // Mode badge, an emoji so it fits narrow phones next to the flag: tapping it opens Settings, where the key can be added.
        val mode = stringResource(if (online) R.string.mode_online else R.string.mode_offline)
        Box(
            Modifier.size(30.dp).clip(CircleShape)
                .background(if (online) TalktoColors.Mint.copy(alpha = 0.35f) else TalktoColors.Sunflower.copy(alpha = 0.45f))
                .clickable(onClickLabel = mode, onClick = onSettings)
                .semantics { contentDescription = mode },
            contentAlignment = Alignment.Center,
        ) { Text(if (online) "🌐" else "📴", fontSize = 16.sp) }
        Spacer(Modifier.weight(1f))
        // The language flag: one tap switches the screens, the voice and the replies between Bulgarian and English.
        val bulgarian = com.talkto.app.i18n.screenLang() == com.talkto.core.i18n.Lang.BG
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(
                onClickLabel = if (bulgarian) "Switch to English" else "Превключи на български", onClick = onLanguage,
            ).semantics { contentDescription = if (bulgarian) "Език: български. Докосни за английски." else "Language: English. Tap for Bulgarian." },
            contentAlignment = Alignment.Center,
        ) { Text(if (bulgarian) "🇧🇬" else "🇬🇧", fontSize = 22.sp) }
        Spacer(Modifier.width(6.dp))
        // Coins, with each new gain floating up from them.
        Box(contentAlignment = Alignment.Center) {
            CoinsPill(coins, onCoins)
            CoinGain(gains, Modifier.align(Alignment.TopCenter))
        }
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = onHouse,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(if (atHome) TalktoColors.Sunflower else TalktoColors.Mint),
        ) {
            Icon(Icons.Rounded.Home, contentDescription = stringResource(R.string.action_house), tint = TalktoColors.Ink, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(6.dp))
        // A filled, high-contrast button: the gear must be easy to find on every mood background and theme.
        IconButton(
            onClick = onSettings,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(TalktoColors.Sunflower),
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.action_settings), tint = TalktoColors.Ink, modifier = Modifier.size(26.dp))
        }
    }
}

/** "+5 🪙" rising and fading each time coins come in. */
@Composable
private fun CoinGain(gains: kotlinx.coroutines.flow.Flow<Int>, modifier: Modifier) {
    var amount by remember { mutableStateOf(0) }
    val rise = remember { Animatable(1f) }
    LaunchedEffect(gains) {
        gains.collect { n ->
            amount = n
            rise.snapTo(0f)
            rise.animateTo(1f, tween(1_100))
        }
    }
    if (rise.value < 1f && amount > 0) {
        Text(
            "+$amount",
            color = TalktoColors.Sunflower,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            modifier = modifier.graphicsLayer {
                translationY = -rise.value * 70f
                alpha = 1f - rise.value
            },
        )
    }
}

@Composable
private fun StatsRow(pet: PetState) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("🍗", stringResource(R.string.stat_satiety), pet.satiety, TalktoColors.Tomato, Modifier.weight(1f))
        Stat("⚡", stringResource(R.string.stat_energy), pet.energy, TalktoColors.Sunflower, Modifier.weight(1f))
        Stat("😊", stringResource(R.string.stat_happiness), pet.happiness, TalktoColors.Mint, Modifier.weight(1f))
        Stat("💞", stringResource(R.string.stat_bond), pet.bond, TalktoColors.Denim, Modifier.weight(1f))
        Stat("💪", com.talkto.app.i18n.tr("Здраве", "Health"), pet.vitality, Color(0xFF9B5DE5), Modifier.weight(1f))
    }
    LevelRow(pet)
}

/** Level, life stage and daily streak, with progress to the next level. */
@Composable
private fun LevelRow(pet: PetState) {
    val progress by animateFloatAsState(pet.levelProgress, label = "level")
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.level_short, pet.level, stageLabel(pet.stage)),
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF9B5DE5),
            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
            drawStopIndicator = {},
        )
        if (pet.streakDays > 1) {
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.streak_short, pet.streakDays), style = MaterialTheme.typography.labelSmall)
        }
    }
    KnowledgeRow(pet)
}

/** Version, knowledge towards the next update, and age. */
@Composable
private fun KnowledgeRow(pet: PetState) {
    val progress by animateFloatAsState(pet.knowledgeProgress, label = "knowledge")
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.knowledge_short, pet.version), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = TalktoColors.Sunflower,
            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
            drawStopIndicator = {},
        )
        val age = pet.ageDays(System.currentTimeMillis())
        if (age > 0) {
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.age_days, age), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun stageLabel(stage: LifeStage) = stringResource(
    when (stage) {
        LifeStage.EGG -> R.string.stage_egg
        LifeStage.BABY -> R.string.stage_baby
        LifeStage.CHILD -> R.string.stage_child
        LifeStage.TEEN -> R.string.stage_teen
        LifeStage.ADULT -> R.string.stage_adult
    },
)

@Composable
private fun Stat(emoji: String, label: String, value: Float, color: Color, modifier: Modifier) {
    val animated by animateFloatAsState(value / 100f, label = "stat")
    // An emoji instead of a word, so children who cannot read yet understand it; the word stays for TalkBack.
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = "$label ${value.toInt()}%" }, verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(4.dp))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)),
            color = if (value < 25f) TalktoColors.Tomato else color,
            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun PermissionBanner() {
    val ctx = LocalContext.current
    Surface(
        color = TalktoColors.Sunflower.copy(alpha = 0.25f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.perm_storage_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.perm_storage_body), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { StorageAccess.openAllFilesAccess(ctx) }) { Text(stringResource(R.string.perm_grant)) }
        }
    }
}

// ----------------------------------------------------------------------- the "device"

/** The LCD window of the toy: soft green screen with a faint pixel grid. */
@Composable
private fun DeviceScreen(modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    val screen = MaterialTheme.colorScheme.surfaceVariant
    val dot = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(screen)
            .border(BorderStroke(6.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)), RoundedCornerShape(36.dp)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 14.dp.toPx()
            var y = step / 2
            while (y < size.height) {
                var x = step / 2
                while (x < size.width) {
                    drawCircle(dot, 1.4.dp.toPx(), Offset(x, y)); x += step
                }
                y += step
            }
        }
        content()
    }
}

@Composable
private fun SpeechBubble(text: String?, busy: Boolean, modifier: Modifier) {
    AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground),
            modifier = Modifier.animateContentSize(),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

// ----------------------------------------------------------------------- controls

@Composable
private fun ActionRow(
    sleeping: Boolean,
    onFeed: () -> Unit,
    onPlay: () -> Unit,
    onSleep: () -> Unit,
    onWardrobe: () -> Unit,
    onLearn: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        ToyButton("🍎", stringResource(R.string.action_feed), TalktoColors.Tomato, onFeed)
        ToyButton("🎮", stringResource(R.string.action_play), TalktoColors.Mint, onPlay)
        ToyButton(
            if (sleeping) "☀️" else "🌙",
            stringResource(if (sleeping) R.string.action_wake else R.string.action_sleep),
            TalktoColors.Denim, onSleep,
        )
        ToyButton("👕", stringResource(R.string.action_wardrobe), TalktoColors.Sunflower, onWardrobe)
        ToyButton("📚", stringResource(R.string.action_learn), Color(0xFF9B5DE5), onLearn)
    }
}

/** Big round button like the ones on the plastic egg, with an emoji instead of a word; the word is for TalkBack. */
@Composable
private fun ToyButton(emoji: String, label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(color)
            .border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 28.sp)
    }
}

@Composable
private fun ChatInput(
    busy: Boolean,
    voice: VoiceState,
    language: VoiceLanguage,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    onLanguage: (VoiceLanguage) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank() && !busy) {
            onSend(text); text = ""
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Language chip: tap cycles AUTO -> BG -> EN for speech recognition.
        TextButton(onClick = { onLanguage(VoiceLanguage.entries[(language.ordinal + 1) % VoiceLanguage.entries.size]) }, modifier = Modifier.width(52.dp)) {
            Text(
                when (language) { VoiceLanguage.AUTO -> "BG/EN"; VoiceLanguage.BG -> "BG"; VoiceLanguage.EN -> "EN" },
                style = MaterialTheme.typography.labelSmall,
            )
        }
        TextField(
            value = if (voice.listening) voice.partial else text,
            onValueChange = { text = it },
            readOnly = voice.listening,
            placeholder = { Text(stringResource(if (voice.listening) R.string.voice_listening else R.string.input_hint)) },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
        Spacer(Modifier.width(6.dp))
        MicButton(listening = voice.listening, level = voice.level, onClick = onMic)
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = submit,
            enabled = text.isNotBlank() && !busy,
            modifier = Modifier.size(52.dp).clip(CircleShape).background(TalktoColors.Ink),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = TalktoColors.Sunflower, strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.send), tint = TalktoColors.Sunflower)
            }
        }
    }
}

/** Microphone with a ring that pulses with the voice level while listening. */
@Composable
private fun MicButton(listening: Boolean, level: Float, onClick: () -> Unit) {
    val ring by animateFloatAsState(if (listening) 1f + level * 0.5f else 1f, label = "mic")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
        if (listening) {
            Box(Modifier.size(52.dp).graphicsLayer { scaleX = ring; scaleY = ring }.clip(CircleShape).background(TalktoColors.Tomato.copy(alpha = 0.3f)))
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(46.dp).clip(CircleShape).background(if (listening) TalktoColors.Tomato else TalktoColors.Mint),
        ) {
            Icon(
                if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                contentDescription = stringResource(if (listening) R.string.voice_stop else R.string.voice_start),
                tint = TalktoColors.Ink,
            )
        }
    }
}

// ----------------------------------------------------------------------- sheets

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AvatarCreatorSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val pending by vm.pendingPhoto.collectAsStateWithLifecycle()
    val visual by vm.visual.collectAsStateWithLifecycle()
    var style by rememberSaveable { mutableStateOf(AvatarStyle.ANIME_2D) }
    var extra by rememberSaveable { mutableStateOf("") }
    // Saveable: the camera app often causes activity recreation, and the target URI must survive it.
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.onPhotoPicked(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) vm.onPhotoPicked(cameraUri) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.newCameraUri().also { cameraUri = it; camera.launch(it) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.avatar_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            Box(
                Modifier.fillMaxWidth().aspectRatio(1.6f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val thumb = rememberThumbnail(pending)
                if (thumb != null) {
                    Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Rounded.Face, contentDescription = null, modifier = Modifier.size(64.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.avatar_pick_gallery)) }
                OutlinedButton(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.avatar_take_photo))
                }
            }

            SectionLabel(stringResource(R.string.avatar_style))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarStyle.entries.forEach { s ->
                    FilterChip(selected = style == s, onClick = { style = s }, label = { Text(styleLabel(s)) })
                }
            }
            OutlinedTextField(
                value = extra,
                onValueChange = { extra = it.take(200) },
                label = { Text(stringResource(R.string.avatar_extra_prompt)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { vm.generateAvatar(style, extra); onDismiss() },
                enabled = pending != null && !visual.generating,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TalktoColors.Ink, contentColor = TalktoColors.Sunflower),
            ) { Text(stringResource(R.string.avatar_generate), fontWeight = FontWeight.Bold) }
            if (visual.bitmap != null) {
                TextButton(onClick = { vm.resetAvatar(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.avatar_reset))
                }
            }
        }
    }
}

/**
 * Bulgarian pronunciation: which engine and voice speak it, a way to download the voice when it is missing,
 * clear speech (the character's pitch and pace kept near natural for Bulgarian) and a sample to hear the difference.
 */
@Composable
private fun BulgarianVoiceCard(vm: MainViewModel, clear: Boolean) {
    val bg by vm.bulgarianVoice.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    // Back from Google Play or the voice download screen: look again.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.recheckBulgarianVoice() }
    fun open(intent: android.content.Intent) {
        try {
            ctx.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            runCatching { ctx.startActivity(android.content.Intent("com.android.settings.TTS_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(com.talkto.app.i18n.tr("🇧🇬 Произношение на български", "🇧🇬 Bulgarian pronunciation"), style = MaterialTheme.typography.titleSmall)
            val engine = bg.engineLabel ?: bg.engine ?: "?"
            Text(
                when (bg.status) {
                    com.talkto.app.avatar.SpeechEngine.Status.CHECKING -> com.talkto.app.i18n.tr("Проверявам гласа…", "Checking the voice…")
                    com.talkto.app.avatar.SpeechEngine.Status.READY -> com.talkto.app.i18n.tr(
                        "✅ Говори с $engine" + if (bg.offline) ", без интернет." else ", с интернет.",
                        "✅ Speaking with $engine" + if (bg.offline) ", offline." else ", online.",
                    )
                    com.talkto.app.avatar.SpeechEngine.Status.MISSING_DATA -> com.talkto.app.i18n.tr(
                        "⚠️ Българският глас не е изтеглен, затова звучи с чужд акцент. Изтеглете го от бутона долу.",
                        "⚠️ The Bulgarian voice is not downloaded, so it sounds foreign. Download it with the button below.",
                    )
                    com.talkto.app.avatar.SpeechEngine.Status.NOT_SUPPORTED, com.talkto.app.avatar.SpeechEngine.Status.NO_ENGINE -> com.talkto.app.i18n.tr(
                        "⚠️ Гласът на телефона ($engine) не говори български. Инсталирайте „Услуги за говор от Google“ и ZnaiKo ще ги ползва сам.",
                        "⚠️ The phone's voice ($engine) has no Bulgarian. Install \"Speech Services by Google\" and ZnaiKo will use it by itself.",
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (bg.status) {
                    com.talkto.app.avatar.SpeechEngine.Status.MISSING_DATA -> Button(onClick = { open(vm.bulgarianVoiceInstallIntent()) }) {
                        Text(com.talkto.app.i18n.tr("Изтегли гласа", "Download the voice"))
                    }
                    com.talkto.app.avatar.SpeechEngine.Status.NOT_SUPPORTED, com.talkto.app.avatar.SpeechEngine.Status.NO_ENGINE -> Button(onClick = {
                        open(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.tts")))
                    }) { Text(com.talkto.app.i18n.tr("Към Google Play", "Open Google Play")) }
                    else -> Unit
                }
                OutlinedButton(onClick = { open(android.content.Intent("com.android.settings.TTS_SETTINGS")) }) {
                    Text(com.talkto.app.i18n.tr("Настройки за говор", "Speech settings"))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(com.talkto.app.i18n.tr("Ясно произношение", "Clear pronunciation"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        com.talkto.app.i18n.tr(
                            "Гласът на героя остава, но на български е по-близо до естествения, за да се чува всяка дума.",
                            "The character's voice stays, but in Bulgarian it is closer to natural, so every word is heard.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = clear, onCheckedChange = vm::setClearBulgarian)
            }
            Row {
                TextButton(onClick = vm::sampleBulgarian) { Text(com.talkto.app.i18n.tr("🔊 Чуй пример", "🔊 Hear a sample")) }
                TextButton(onClick = { vm.recheckBulgarianVoice(force = true) }) { Text(com.talkto.app.i18n.tr("Провери пак", "Check again")) }
            }
        }
    }
}

/** Character voices (pitch and rate) and, optionally, a specific voice of the phone's TTS engine. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoicePicker(vm: MainViewModel, selected: VoicePreset, engineVoice: String?) {
    val voices by vm.engineVoices.collectAsStateWithLifecycle()
    var showEngine by remember { mutableStateOf(false) }
    Text(stringResource(R.string.settings_voice_character), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VoicePreset.entries.forEach { p ->
            FilterChip(selected = p == selected, onClick = { vm.setVoicePreset(p) }, label = { Text(p.label(screenLang())) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { vm.previewVoice(selected) }) { Text(stringResource(R.string.settings_voice_preview)) }
        TextButton(onClick = {
            showEngine = !showEngine
            if (showEngine) vm.loadEngineVoices()
        }) { Text(stringResource(R.string.settings_voice_engine)) }
    }
    if (showEngine) {
        if (voices.isEmpty()) {
            Text(stringResource(R.string.settings_voice_engine_none), style = MaterialTheme.typography.bodyMedium)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = engineVoice == null, onClick = { vm.setTtsVoice(null) }, label = { Text(stringResource(R.string.settings_voice_engine_auto)) })
                voices.forEachIndexed { i, v ->
                    val stars = when { v.quality >= 500 -> "★★★"; v.quality >= 400 -> "★★"; else -> "★" }
                    val label = "${v.language} #${i + 1} $stars" + if (v.offline) "" else " ☁"
                    FilterChip(selected = engineVoice == v.name, onClick = { vm.setTtsVoice(v.name) }, label = { Text(label) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    vm: MainViewModel,
    permissions: PermissionState,
    onDismiss: () -> Unit,
    onHistory: () -> Unit,
    onBackgrounds: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    // Keys are never pre-filled: an empty field means "keep the current key".
    var claudeKey by remember { mutableStateOf("") }
    var stabilityKey by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            LanguagePicker(vm)
            Spacer(Modifier.height(8.dp))
            KeyField(stringResource(R.string.settings_anthropic_key), claudeKey, settings.hasClaudeKey) { claudeKey = it }
            KeyField(stringResource(R.string.settings_stability_key), stabilityKey, !settings.stabilityKey.isNullOrBlank()) { stabilityKey = it }
            if (settings.hasClaudeKey) {
                OutlinedButton(onClick = vm::removeClaudeKey, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(stringResource(R.string.settings_remove_key))
                }
            } else {
                Text(stringResource(R.string.settings_offline_note), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
            }
            Text(stringResource(R.string.settings_keys_note), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
            Button(
                onClick = {
                    vm.saveKeys(claudeKey.takeIf { it.isNotBlank() }, stabilityKey.takeIf { it.isNotBlank() })
                    claudeKey = ""; stabilityKey = ""
                },
                enabled = claudeKey.isNotBlank() || stabilityKey.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_voice), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.voiceEnabled, onCheckedChange = vm::setVoice)
            }
            VoicePicker(vm, settings.voicePreset, settings.ttsVoice)
            BulgarianVoiceCard(vm, settings.clearBulgarian)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_hands_free), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.handsFree, onCheckedChange = vm::setHandsFree)
            }
            Text(stringResource(R.string.settings_hands_free_note), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { onDismiss(); vm.openStory() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(com.talkto.app.i18n.tr("📖 Историята на Знайко", "📖 The story of ZnaiKo"))
            }
            var feedback by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { feedback = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(com.talkto.app.i18n.tr("💌 Обратна връзка", "💌 Send feedback"))
            }
            if (feedback) com.talkto.app.ui.feedback.FeedbackDialog(onDismiss = { feedback = false })
            OutlinedButton(onClick = onBackgrounds, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(R.string.backgrounds_title))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_avatar_3d), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.avatar3d, onCheckedChange = vm::setAvatar3d)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_record), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = settings.recordConversations, onCheckedChange = vm::setRecordConversations)
            }
            Text(stringResource(R.string.settings_record_note), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.settings_history))
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(stringResource(R.string.settings_profile), style = MaterialTheme.typography.titleMedium)
            val facts by vm.facts.collectAsStateWithLifecycle()
            if (facts.isEmpty()) {
                Text(stringResource(R.string.settings_profile_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                facts.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(ProfileRepository.label(f, screenLang()), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { vm.forgetFact(f.key) }) { Text(stringResource(R.string.settings_forget)) }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            PermissionRow(stringResource(R.string.perm_storage_title), permissions.allFiles) { StorageAccess.openAllFilesAccess(ctx) }
            PermissionRow(stringResource(R.string.perm_accessibility), permissions.accessibility) { StorageAccess.openAccessibilitySettings(ctx) }
            PermissionRow(stringResource(R.string.perm_shizuku), permissions.shizuku) { vm.requestShizuku() }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(stringResource(R.string.settings_habits), style = MaterialTheme.typography.titleMedium)
            if (habits.isEmpty()) {
                Text(stringResource(R.string.settings_habits_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                habits.forEach { HabitRow(it, onForget = { vm.forgetHabit(it.key) }) }
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            OutlinedButton(onClick = { vm.newConversation(); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_new_chat))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val config by vm.backgroundConfig.collectAsStateWithLifecycle()
    val curating by vm.curating.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var pickingFor by remember { mutableStateOf<MoodScene?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(BackgroundLibrary.MAX_PER_SCENE)) { uris ->
        pickingFor?.let { scene -> if (uris.isNotEmpty()) vm.addBackgroundPhotos(scene, uris) }
        pickingFor = null
    }
    val mediaPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) vm.autoFillBackgrounds() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.backgrounds_title), style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.backgrounds_enabled), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(checked = config.enabled, onCheckedChange = vm::setBackgroundsEnabled)
            }
            Text(stringResource(R.string.backgrounds_note), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = {
                    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
                    if (StorageAccess.hasAllFilesAccess() || ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) vm.autoFillBackgrounds()
                    else mediaPermission.launch(perm)
                },
                enabled = curating == null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                val c = curating
                Text(if (c == null) stringResource(R.string.backgrounds_auto) else stringResource(R.string.backgrounds_scanning, c.first, c.second))
            }
            MoodScene.entries.forEach { scene ->
                val photos = config.photos[scene].orEmpty()
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(screenLang().pick(scene.bg, scene.en), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.backgrounds_mood_hint, moodHint(scene)), style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = {
                        pickingFor = scene
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(stringResource(R.string.backgrounds_add)) }
                }
                Box(Modifier.fillMaxWidth().height(90.dp).clip(MaterialTheme.shapes.small)) {
                    if (photos.isEmpty()) {
                        MoodBackdrop(scene, emptyList(), Modifier.fillMaxSize())
                    } else {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            photos.take(4).forEach { path ->
                                val thumb = rememberFileThumbnail(path)
                                Box(Modifier.weight(1f).fillMaxSize().clickable { vm.removeBackgroundPhoto(scene, path) }) {
                                    thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.backgrounds_remove), tint = Color.White,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun moodHint(scene: MoodScene): String = stringResource(
    when (scene) {
        MoodScene.BEACH -> R.string.mood_happy
        MoodScene.MEADOW -> R.string.mood_calm
        MoodScene.SUNSET -> R.string.mood_love
        MoodScene.RAIN -> R.string.mood_sad
        MoodScene.STORM -> R.string.mood_angry
        MoodScene.NIGHT -> R.string.mood_sleepy
        MoodScene.SPACE -> R.string.mood_thinking
        MoodScene.FOG -> R.string.mood_confused
        MoodScene.FIREWORKS -> R.string.mood_surprised
    },
)

/** Small preview of an imported background file, decoded off the main thread. */
@Composable
private fun rememberFileThumbnail(path: String): ImageBitmap? {
    val thumb by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }
                android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return thumb
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val items by vm.historyItems.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    LaunchedEffect(query) { vm.loadHistory(query) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.history_count, items.size), style = MaterialTheme.typography.labelSmall)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(100) },
                label = { Text(stringResource(R.string.history_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.shareHistory(ctx) }, enabled = items.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.history_share))
                }
                OutlinedButton(onClick = { confirmClear = true }, enabled = items.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.history_clear), color = TalktoColors.Tomato)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                    items(items.size) { i ->
                        val u = items[i]
                        val you = u.speaker == Speaker.USER
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(
                                (if (you) stringResource(R.string.history_you) else "ZnaiKo") + " · " + HISTORY_TIME.format(java.time.Instant.ofEpochMilli(u.atMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (you) TalktoColors.Denim else TalktoColors.Mint,
                            )
                            Text(u.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_body)) },
            confirmButton = {
                Button(onClick = { confirmClear = false; vm.clearHistory() }, colors = ButtonDefaults.buttonColors(containerColor = TalktoColors.Tomato)) {
                    Text(stringResource(R.string.confirm_delete_yes))
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.confirm_no)) } },
        )
    }
}

private val HISTORY_TIME: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(java.time.ZoneId.systemDefault())

@Composable
private fun KeyField(label: String, value: String, isSet: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (isSet) Text("••••••••  ✓") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (granted) {
            Text("✓", color = TalktoColors.Mint, fontWeight = FontWeight.Black)
        } else {
            TextButton(onClick = onClick) { Text(stringResource(R.string.perm_grant)) }
        }
    }
}

@Composable
private fun HabitRow(habit: Habit, onForget: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(habit.description, style = MaterialTheme.typography.bodyMedium)
            Text("×${habit.occurrences} · ${(habit.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onForget) { Text(stringResource(R.string.settings_forget)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

/** Small preview of the picked photo, decoded off the main thread. */
@Composable
private fun rememberThumbnail(uri: Uri?): ImageBitmap? {
    val ctx = LocalContext.current
    val thumb by produceState<ImageBitmap?>(null, uri) {
        value = uri?.let { u ->
            withContext(Dispatchers.IO) {
                runCatching {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, u)) { decoder, info, _ ->
                        val scale = 512f / maxOf(info.size.width, info.size.height)
                        if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return thumb
}

// ----------------------------------------------------------------------- confirmation

@Composable
private fun ConfirmationDialog(p: PendingConfirmation, onAnswer: (Long, Boolean) -> Unit) {
    val (title, body, yes) = when (val r = p.request) {
        is ConfirmationRequest.Delete -> Triple(
            stringResource(R.string.confirm_delete_title, r.plan.fileCount),
            stringResource(
                if (r.plan.permanent) R.string.confirm_delete_body_permanent else R.string.confirm_delete_body_trash,
                r.plan.fileCount, r.plan.directoryCount, formatBytes(r.plan.totalBytes),
            ) + "\n\n" + r.plan.sample.take(6).joinToString("\n") { "• " + it.substringAfterLast('/') },
            stringResource(R.string.confirm_delete_yes),
        )
        is ConfirmationRequest.Overwrite -> Triple(
            stringResource(R.string.confirm_overwrite_title),
            stringResource(R.string.confirm_overwrite_body, r.source.substringAfterLast('/'), r.destination.substringAfterLast('/')),
            stringResource(R.string.confirm_yes),
        )
        is ConfirmationRequest.Organize -> Triple(
            stringResource(R.string.confirm_organize_title),
            stringResource(R.string.confirm_organize_body, r.fileCount, r.folder.substringAfterLast('/')),
            stringResource(R.string.confirm_yes),
        )
        ConfirmationRequest.EmptyTrash -> Triple(
            stringResource(R.string.confirm_empty_trash_title),
            stringResource(R.string.confirm_empty_trash_body),
            stringResource(R.string.confirm_delete_yes),
        )
    }
    AlertDialog(
        onDismissRequest = { onAnswer(p.id, false) },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = { onAnswer(p.id, true) },
                colors = if (p.request.destructive) ButtonDefaults.buttonColors(containerColor = TalktoColors.Tomato) else ButtonDefaults.buttonColors(),
            ) { Text(yes) }
        },
        dismissButton = { TextButton(onClick = { onAnswer(p.id, false) }) { Text(stringResource(R.string.confirm_no)) } },
    )
}

// ----------------------------------------------------------------------- labels

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(Locale.getDefault(), "%.1f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(Locale.getDefault(), "%.1f MB", b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> String.format(Locale.getDefault(), "%.0f KB", b / (1L shl 10).toDouble())
    else -> "$b B"
}

@Composable
private fun toolLabel(tool: String) = when (tool) {
    ToolProtocol.MANAGE_FILE -> stringResource(R.string.tool_manage_file)
    ToolProtocol.LAUNCH_APP -> stringResource(R.string.tool_launch_app)
    ToolProtocol.TERMINATE_APP -> stringResource(R.string.tool_terminate_app)
    ToolProtocol.GENERATE_AVATAR -> stringResource(R.string.tool_generate_avatar)
    ToolProtocol.ANIMATE_AVATAR -> stringResource(R.string.tool_animate_avatar)
    ToolProtocol.DEVICE -> stringResource(R.string.tool_device)
    ToolProtocol.NOTES -> stringResource(R.string.tool_notes)
    ToolProtocol.REMINDERS -> stringResource(R.string.tool_reminders)
    else -> tool
}

@Composable
private fun styleLabel(s: AvatarStyle) = stringResource(
    when (s) {
        AvatarStyle.ANIME_2D -> R.string.style_anime_2d
        AvatarStyle.CARTOON_3D -> R.string.style_cartoon_3d
        AvatarStyle.PIXEL_ART -> R.string.style_pixel_art
        AvatarStyle.CHIBI -> R.string.style_chibi
        AvatarStyle.WATERCOLOR -> R.string.style_watercolor
    },
)
