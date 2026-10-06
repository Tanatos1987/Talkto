package com.talkto.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.talkto.app.AppContainer
import com.talkto.app.R
import com.talkto.app.TalktoApp
import com.talkto.app.agent.AgentService
import com.talkto.app.apps.ShizukuBridge
import com.talkto.app.apps.TalktoAccessibilityService
import com.talkto.core.look.CreatureLook
import com.talkto.core.look.HouseLook
import com.talkto.core.look.OutfitConfig
import com.talkto.app.quiz.QuizController
import com.talkto.core.commands.AppCommand
import com.talkto.core.pet.KnowledgeSource
import com.talkto.core.profile.AboutYou
import com.talkto.core.shop.ShopItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.talkto.app.files.StorageAccess
import com.talkto.app.games.GameController
import com.talkto.app.learn.LearnData
import com.talkto.app.learn.LessonController
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.AvatarStyle
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import com.talkto.app.background.BackgroundConfig
import com.talkto.app.avatar.SpeechEngine
import com.talkto.app.voice.VoiceLanguage
import com.talkto.core.voice.VoicePreset
import com.talkto.app.voice.VoiceState
import com.talkto.core.history.Utterance
import com.talkto.core.scene.MoodScene
import com.talkto.core.memory.Habit
import com.talkto.core.profile.Fact
import com.talkto.core.games.GameKind
import com.talkto.core.i18n.Lang
import com.talkto.core.learn.Topic
import com.talkto.core.pet.ZnaiKoUpdate
import com.talkto.core.touch.BodyLocator
import com.talkto.core.touch.Touch
import com.talkto.core.touch.TouchKind
import com.talkto.core.touch.TwirlInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A line for the speech bubble that does not come from the assistant: a string resource, or ready [text]. */
data class SystemLine(@StringRes val res: Int, val atMs: Long, val args: List<Any> = emptyList(), val text: String? = null)

data class PermissionState(
    val allFiles: Boolean = false,
    val accessibility: Boolean = false,
    val shizuku: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val c: AppContainer = (app as TalktoApp).container

    val pet = c.pet.state
    val pose = c.avatar.pose
    val visual = c.avatar.visual
    val pendingPhoto = c.avatar.pendingPhoto
    val agent = c.agentSession.state
    val confirmation = c.confirmations.pending
    val settings = c.settings.settings
    val outfit: StateFlow<OutfitConfig> = c.petStore.outfit.stateIn(viewModelScope, SharingStarted.Eagerly, OutfitConfig())
    /** How this ZnaiKo looks (the creator) and its house. */
    val look: StateFlow<CreatureLook> = c.petStore.look.stateIn(viewModelScope, SharingStarted.Eagerly, CreatureLook())
    val house: StateFlow<HouseLook> = c.petStore.house.stateIn(viewModelScope, SharingStarted.Eagerly, HouseLook())
    /** Coins just earned, for the "+5" that floats up. */
    val coinGains = c.pet.coinGains

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _habits = MutableStateFlow<List<Habit>>(emptyList())
    val habits: StateFlow<List<Habit>> = _habits.asStateFlow()

    /** Friendly lines for the speech bubble that do not come from Claude (errors, greetings). */
    private val _systemLine = MutableStateFlow<SystemLine?>(null)
    val systemLine: StateFlow<SystemLine?> = _systemLine.asStateFlow()

    /** Board and card games against ZnaiKo. */
    val games = GameController(c.pet, c.avatar, { c.settings.settings.value.voiceEnabled }, viewModelScope, lang = { c.language.current })

    /** Language lessons with ZnaiKo. */
    val lessons = LessonController(
        c.learning, c.pet, c.avatar, { c.settings.settings.value.voiceEnabled }, { c.language.current }, viewModelScope,
        onActivity = { c.activity.record(it) },
    )

    /** Maths tasks and trivia. */
    val quiz = QuizController(
        c.petStore, c.pet, c.avatar, c.profile, { c.settings.settings.value.voiceEnabled }, { c.language.current }, viewModelScope,
        onActivity = { c.activity.record(it) },
        ask = { system, prompt -> askClaude(system, prompt) },
    )

    /** One question to Claude, or null when Claude is off, unreachable or declines. */
    private suspend fun askClaude(system: String, prompt: String): String? {
        if (!c.settings.settings.value.claudeOn) return null
        return runCatching { c.oneShot.ask(system, prompt) }.getOrNull()
    }

    /** Claude is on: the screens show the buttons that need it. */
    fun claudeOn(): Boolean = c.settings.settings.value.claudeOn

    /** Today's question of the day was answered already. */
    fun dailyDone(): Boolean = quiz.data.value.dailyDay == c.activity.today()

    fun startDaily() {
        comeOut()
        quiz.startDaily(c.activity.today())
    }

    /** Five questions Claude writes about what the child likes (from the profile); the built-in quiz without Claude. */
    fun startSmartTrivia() {
        comeOut()
        val l = c.language.current
        sayText(l.pick("Измислям въпроси специално за теб…", "I'm making up questions just for you…"), Expression.THINKING)
        viewModelScope.launch {
            val likes = runCatching { c.profile.all() }.getOrDefault(emptyList())
                .filter { it.key.startsWith("likes:") || it.key.startsWith("favourite:") }
                .map { it.value }
            val age = c.settings.settings.value.childAge.takeIf { it > 0 }
            val text = askClaude(com.talkto.core.quiz.SmartQuiz.system(l, age), com.talkto.core.quiz.SmartQuiz.prompt(l, likes))
            val questions = text?.let { com.talkto.core.quiz.SmartQuiz.parse(it, l) }.orEmpty()
            if (questions.isEmpty()) sayText(l.pick("Сега ще играем с моите въпроси.", "Let's play with my own questions this time."), Expression.HAPPY)
            quiz.startTriviaWith(questions)
        }
    }

    /** Fables, fairy tales and riddles read by ZnaiKo. */
    val tales = com.talkto.app.story.TaleController(
        c.pet, c.avatar, { c.settings.settings.value.voiceEnabled }, { c.language.current }, viewModelScope,
        onActivity = { c.activity.record(it) },
    )

    private val _bedtime = MutableStateFlow(false)
    /** ZnaiKo is going to bed: offer a bedtime story. */
    val bedtime: StateFlow<Boolean> = _bedtime.asStateFlow()

    fun dismissBedtime() { _bedtime.value = false }

    private val _screens = MutableSharedFlow<AppCommand>(extraBufferCapacity = 4)
    /** Places asked for by voice or text (house, shop, creator, "get to know me"); the screen opens them. */
    val screens: SharedFlow<AppCommand> = _screens.asSharedFlow()

    /** ZnaiKo's language; changing it rebuilds the screen in that language. */
    val language: StateFlow<Lang> = c.language.lang
    val learnData: StateFlow<LearnData> = c.learning.data
    /** Chat practice in this language is on. */
    val practice: StateFlow<Lang?> = c.agentSession.practice
    /** "Научи ме на английски" said in the chat: open the lessons. */
    val lessonRequests = c.agentSession.lessonRequests

    /**
     * Switches everything to [lang]: the screens (the activity is rebuilt with the new resources), ZnaiKo's voice and
     * replies, and what the microphone listens for. ZnaiKo says so in the new language, so the change is heard at once.
     */
    fun setLanguage(lang: Lang) {
        if (lang == c.language.current) return
        c.avatar.stopSpeaking()
        c.language.set(lang)
        viewModelScope.launch {
            val listening = c.settings.settings.value.voiceLanguage
            if (listening != VoiceLanguage.AUTO.name) c.settings.setVoiceLanguage(if (lang == Lang.EN) VoiceLanguage.EN.name else VoiceLanguage.BG.name)
            say(R.string.language_switched, Expression.HAPPY)
        }
    }

    /** The flag on the main screen: Bulgarian <-> English. */
    fun toggleLanguage() = setLanguage(if (c.language.current == Lang.BG) Lang.EN else Lang.BG)

    fun setLearnTarget(lang: Lang) = c.learning.setTarget(lang)

    fun learnTarget(): Lang = c.learning.target

    fun startLesson(topic: Topic?, speaking: Boolean) = lessons.start(topic, speaking)

    /** Listens for a "say it" answer in the language being learned. */
    fun lessonListen(dialogOnly: Boolean = false) {
        val target = lessons.state.value?.target ?: return
        c.avatar.stopSpeaking()
        if (dialogOnly) c.voice.preferDialog = true
        c.voice.start(if (target == Lang.EN) VoiceLanguage.EN else VoiceLanguage.BG) { heard -> lessons.onHeard(heard) }
    }

    fun startPractice() = viewModelScope.launch { c.agentSession.beginPractice(c.learning.target) }

    fun stopPractice() = c.agentSession.stopPractice()

    private val _pendingUpdates = MutableStateFlow<List<ZnaiKoUpdate>>(emptyList())
    /** Updates waiting to be shown, oldest first. */
    val pendingUpdates: StateFlow<List<ZnaiKoUpdate>> = _pendingUpdates.asStateFlow()

    init {
        val crashed = c.errors.consumeCrashMarker()
        viewModelScope.launch {
            val hasKey = c.settings.awaitLoaded().claudeOn
            val first = when {
                crashed -> R.string.crashed_last_time
                hasKey -> R.string.greeting
                else -> R.string.greeting_offline
            }
            _systemLine.value = SystemLine(first, System.currentTimeMillis())
        }
        viewModelScope.launch {
            c.errors.notices.collect { n ->
                if (n.reason != null) say(R.string.err_api_rejected_reason, n.expression, n.reason) else say(n.message, n.expression)
            }
        }
        viewModelScope.launch {
            c.pet.levelUps.collect { level -> say(R.string.level_up, Expression.LOVE, level) }
        }
        viewModelScope.launch {
            c.agentSession.gameRequests.collect { kind -> comeOut(); games.start(kind) }
        }
        viewModelScope.launch {
            c.agentSession.feedRequests.collect { food -> feed(food, speak = false) }
        }
        viewModelScope.launch {
            c.agentSession.storyRequests.collect { req ->
                comeOut()
                if (req.riddle) tales.riddle() else tales.read(kind = req.kind, bedtime = req.bedtime)
            }
        }
        // A long reply from Claude is a tale it made up: show it on a full page as well.
        viewModelScope.launch {
            c.agentSession.state.collect { s ->
                val last = s.messages.lastOrNull() ?: return@collect
                if (!last.fromUser && last.text.length > LONG_REPLY && last.atMs > shownTaleAt && System.currentTimeMillis() - last.atMs < 10_000) {
                    shownTaleAt = last.atMs
                    tales.show(last.text)
                }
            }
        }
        viewModelScope.launch {
            c.agentSession.appRequests.collect { cmd ->
                when (cmd) {
                    AppCommand.GoHome -> goHome()
                    AppCommand.ComeOut -> comeOut()
                    is AppCommand.Math -> {
                        comeOut()
                        quiz.startMath(
                            cmd.grade,
                            topic = when {
                                cmd.geometry -> com.talkto.core.quiz.MathTopic.GEOMETRY
                                cmd.algebra -> com.talkto.core.quiz.MathTopic.ALGEBRA
                                else -> com.talkto.core.quiz.MathTopic.MIXED
                            },
                        )
                    }
                    is AppCommand.Trivia -> { comeOut(); quiz.startTrivia(cmd.category) }
                    AppCommand.Tetris -> openArcade(Arcade.TETRIS)
                    AppCommand.Sweets -> openArcade(Arcade.SWEETS)
                    else -> _screens.tryEmit(cmd)
                }
            }
        }
        viewModelScope.launch {
            c.pet.updates.collect { u ->
                _pendingUpdates.update { it + u }
                val line = c.language.text(R.string.update_said, u.version, u.title(c.language.current))
                _systemLine.value = SystemLine(0, System.currentTimeMillis(), text = line)
                c.avatar.play(AnimationCommand(Expression.LOVE, Gesture.SPIN, holdMs = 3_000))
                c.avatar.speak(line, voice = c.settings.settings.value.voiceEnabled)
            }
        }
        refreshPermissions()
    }

    private suspend fun say(@StringRes res: Int, expression: Expression, vararg args: Any) {
        _systemLine.value = SystemLine(res, System.currentTimeMillis(), args.toList())
        val gesture = when (expression) {
            Expression.HAPPY -> Gesture.BOUNCE
            Expression.LOVE -> Gesture.SPIN
            else -> Gesture.SHAKE
        }
        c.avatar.play(AnimationCommand(expression, gesture))
        c.avatar.speak(c.language.text(res, *args), voice = c.settings.settings.value.voiceEnabled)
    }

    fun onVisible(visible: Boolean) {
        c.confirmations.uiVisible = visible
        if (visible) refreshPermissions()
        countMinutes(visible)
    }

    // ------------------------------------------------- flagged answers

    /** Answers from Claude the child flagged with 🚩. */
    val flags: StateFlow<com.talkto.core.safety.FlagLog> = c.flags.log

    /** This build sends flags to the authors by itself; otherwise a parent e-mails them from the parents' corner. */
    val flagsAutomatic: Boolean get() = c.flags.automatic

    /**
     * The child flagged something Claude said. It is kept for the parents (and sent to the authors when this build
     * can), ZnaiKo stops reading it and says thank you, and its line takes the answer's place in the speech bubble.
     * [question] is what the child said before it; by default the chat line just before the answer.
     */
    fun flagReply(text: String, reason: com.talkto.core.safety.FlagReason, question: String? = null) {
        if (text.isBlank()) return
        val asked = question ?: questionBefore(text)
        c.flags.flag(text, asked, reason, c.settings.settings.value.claudeModel, c.language.current)
        c.avatar.stopSpeaking()
        val line = c.language.current.pick(
            "Благодаря, че ми каза! Скрих този отговор и ще внимавам повече.",
            "Thank you for telling me! I've hidden that answer and I'll be more careful.",
        )
        _systemLine.value = SystemLine(0, System.currentTimeMillis(), text = line)
        viewModelScope.launch {
            c.avatar.play(AnimationCommand(Expression.SAD, Gesture.NOD, holdMs = 2_000))
            c.avatar.speak(line, voice = c.settings.settings.value.voiceEnabled)
        }
    }

    /** What the child said right before [answer] in the chat on screen. */
    private fun questionBefore(answer: String): String? {
        val messages = c.agentSession.state.value.messages
        val i = messages.indexOfLast { !it.fromUser && it.text == answer }
        if (i <= 0) return null
        return messages.subList(0, i).lastOrNull { it.fromUser }?.text
    }

    /** A line of the conversation log flagged from the history: the question is the child's line before it. */
    fun flagFromHistory(u: Utterance, reason: com.talkto.core.safety.FlagReason) {
        val items = _historyItems.value
        val i = items.indexOfFirst { it.id == u.id }
        // The whole log is shown newest first, so what was said before the answer is the next line in the list.
        // Search results skip lines, so there the neighbour may belong to another talk.
        val asked = if (i < 0 || historyQuery.isNotBlank()) null
        else items.getOrNull(i + 1)?.takeIf { it.speaker == com.talkto.core.history.Speaker.USER }?.text
        flagReply(u.text, reason, asked ?: "")
    }

    fun removeFlag(id: Long) = c.flags.remove(id)

    fun clearFlags() = c.flags.clear()

    /** The flags not passed on yet, as an e-mail the parent reads before sending. */
    fun emailFlags(context: Context) {
        val waiting = c.flags.log.value.waiting
        if (waiting.isEmpty()) return
        val lang = c.language.current
        com.talkto.app.ui.feedback.sendEmail(
            context,
            com.talkto.core.safety.FlagReport.subject(lang),
            com.talkto.core.safety.FlagReport.email(waiting, com.talkto.app.BuildConfig.VERSION_NAME, lang),
        )
        c.flags.markEmailed(waiting.map { it.atMs })
    }

    // ------------------------------------------------- parents' corner and screen time

    /** What the child did, day by day. */
    val activityLog: StateFlow<com.talkto.core.parent.ActivityLog> = c.activity.log

    fun today(): Long = c.activity.today()

    /** Today's time is used up: ZnaiKo rests until tomorrow or until a parent adds time. */
    val timeUp: StateFlow<Boolean> = combine(c.activity.log, c.settings.settings) { log, s ->
        com.talkto.core.parent.ScreenTime.timeUp(s.dailyLimitMinutes, log.on(c.activity.today()))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var minuteCounter: Job? = null
    private var restSaid = false

    /** Counts the minutes the app is on screen; stops when it is not. */
    private fun countMinutes(visible: Boolean) {
        if (!visible) {
            minuteCounter?.cancel()
            minuteCounter = null
            return
        }
        if (minuteCounter?.isActive == true) return
        minuteCounter = viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                c.activity.record(com.talkto.core.parent.Activity.MINUTE)
                checkScreenTime()
            }
        }
    }

    private fun checkScreenTime() {
        val limit = c.settings.settings.value.dailyLimitMinutes
        val today = c.activity.todayActivity()
        val l = c.language.current
        when {
            com.talkto.core.parent.ScreenTime.timeUp(limit, today) -> if (!restSaid) {
                restSaid = true
                lessons.close(); quiz.close(); games.close(); closeArcade(); tales.close(); _bedtime.value = false
                c.pet.setSleeping(true)
                sayText(l.pick("Р’СЂРµРјРµ Рµ Р·Р° РїРѕС‡РёРІРєР°! Р§СѓРґРµСЃРЅРѕ СЃРё РїРѕРёРіСЂР°С…РјРµ. Р•Р»Р° РїР°Рє СѓС‚СЂРµ!", "Time for a rest! We had a lovely time. Come back tomorrow!"), Expression.SLEEPY)
            }
            com.talkto.core.parent.ScreenTime.warnNow(limit, today) ->
                sayText(l.pick("РћС‰Рµ РїРµС‚ РјРёРЅСѓС‚РєРё Рё С‰Рµ С‚СЂСЏР±РІР° РґР° СЃРё РїРѕС‡РёРЅР°.", "Five more minutes, then I need a rest."), Expression.SLEEPY)
            else -> restSaid = false
        }
    }

    private fun sayText(text: String, expression: Expression) {
        _systemLine.value = SystemLine(0, System.currentTimeMillis(), text = text)
        c.avatar.play(AnimationCommand(expression, Gesture.NOD, holdMs = 2_000))
        viewModelScope.launch { c.avatar.speak(text, voice = c.settings.settings.value.voiceEnabled) }
    }

    /** True when [pin] is the parents' PIN. */
    fun checkPin(pin: String): Boolean {
        val s = c.settings.settings.value
        return com.talkto.core.parent.ParentPin.matches(pin, s.parentPinSalt, s.parentPinHash)
    }

    fun setPin(pin: String) = viewModelScope.launch {
        if (!com.talkto.core.parent.ParentPin.valid(pin)) return@launch
        val salt = com.talkto.core.parent.ParentPin.newSalt()
        c.settings.setParentPin(com.talkto.core.parent.ParentPin.hash(pin, salt), salt)
    }

    /** A forgotten PIN: the PIN, the keys and the family server are removed, and ZnaiKo goes offline. */
    fun resetParent() = viewModelScope.launch {
        c.settings.resetParent()
        c.agentSession.newConversation()
    }

    /** Extra minutes for today, given by a parent from the rest screen. */
    fun addMinutes(minutes: Int) {
        c.activity.addBonus(minutes)
        restSaid = false
        c.pet.setSleeping(false)
    }

    fun setAiEnabled(enabled: Boolean) = viewModelScope.launch { c.settings.setAiEnabled(enabled) }

    fun setDailyLimit(minutes: Int) = viewModelScope.launch {
        c.settings.setDailyLimit(minutes)
        restSaid = false
    }

    fun setChildAge(age: Int) = viewModelScope.launch { c.settings.setChildAge(age) }

    fun setClaudeModel(model: String) = viewModelScope.launch { c.settings.setClaudeModel(model) }

    /** Saves the family server; only https addresses are accepted. Returns false for anything else. */
    fun setProxyUrl(url: String): Boolean {
        val u = url.trim()
        if (u.isNotEmpty() && (!u.startsWith("https://") || u.length < 12)) return false
        viewModelScope.launch {
            c.settings.setProxyUrl(u)
            c.agentSession.newConversation()
        }
        return true
    }

    /** Words learned so far in the language being practised. */
    fun wordsLearned(): Int = c.learning.data.value.state.learned(c.learning.target)

    fun setLearnWriting(on: Boolean) = c.learning.setWriting(on)

    // ------------------------------------------------------- badges and the weekly praise

    init {
        // Badges: whenever progress changes, any newly earned badge is kept, paid and celebrated.
        viewModelScope.launch {
            c.activity.ready.first { it }
            c.pet.ready.first { it }
            combine(c.learning.data, quiz.data, c.pet.state, c.activity.log) { learn, q, p, log ->
                val days = log.days
                com.talkto.core.pet.BadgeStats(
                    wordsLearned = Lang.entries.sumOf { learn.state.learned(it) },
                    lessons = learn.state.lessons,
                    learnStreak = learn.state.streak,
                    mathSolved = q.mathSolved,
                    triviaRight = q.triviaRight,
                    level = p.level,
                    visitStreak = p.streakDays,
                    games = days.sumOf { it.games },
                    stories = days.sumOf { it.stories },
                ) to p.badges
            }.collect { (stats, have) ->
                val fresh = com.talkto.core.pet.Badge.newOnes(stats, have)
                if (fresh.isEmpty()) return@collect
                c.pet.award(fresh)
                val l = c.language.current
                val first = fresh.first()
                val more = fresh.size - 1
                val line = l.pick("РќРѕРІР° Р·РЅР°С‡РєР°: ${first.emoji} ${first.bg}!", "New badge: ${first.emoji} ${first.en}!") +
                    if (more > 0) l.pick(" Р РѕС‰Рµ $more!", " And $more more!") else ""
                sayText(line, Expression.LOVE)
            }
        }
        // Once a week ZnaiKo says what the child did in the last seven days.
        viewModelScope.launch {
            c.activity.ready.first { it }
            c.pet.ready.first { it }
            val today = c.activity.today()
            val last = c.pet.state.value.weeklyPraiseDay
            if (last <= 0L) { c.pet.setWeeklyPraiseDay(today); return@launch }
            if (today - last < 7) return@launch
            c.pet.setWeeklyPraiseDay(today)
            val week = c.activity.log.value.weekTotal(today - 1)
            com.talkto.core.pet.WeeklyPraise.text(week, c.language.current)?.let { line ->
                delay(4_000) // after the greeting
                sayText(line, Expression.LOVE)
            }
        }
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        _permissions.value = PermissionState(
            allFiles = StorageAccess.hasAllFilesAccess(),
            accessibility = TalktoAccessibilityService.isEnabled(ctx),
            shizuku = ShizukuBridge.hasPermission(),
        )
    }

    // ------------------------------------------------------------------ chat

    fun send(text: String) {
        if (text.isBlank()) return
        lastInputWasVoice = false
        c.avatar.stopSpeaking()
        AgentService.submit(getApplication(), text)
    }

    fun newConversation() = viewModelScope.launch { c.agentSession.newConversation() }

    fun answerConfirmation(id: Long, approved: Boolean) = c.confirmations.respond(id, approved)

    // -------------------------------------------------------------- tamagotchi

    fun feed() = feed(com.talkto.core.pet.Food.APPLE)

    private val _meals = MutableSharedFlow<com.talkto.core.pet.Food>(extraBufferCapacity = 4)
    /** Each food as it is given, for the bite flying to ZnaiKo on screen. */
    val meals: SharedFlow<com.talkto.core.pet.Food> = _meals

    /**
     * Gives [food]: it flies to ZnaiKo, who chews, then says how it feels; the body changes with what it eats.
     * When Claude fed it ([speak] false), Claude's own reply is the words, so ZnaiKo only chews.
     */
    fun feed(food: com.talkto.core.pet.Food, speak: Boolean = true) {
        comeOut()
        _meals.tryEmit(food)
        viewModelScope.launch {
            delay(900) // the bite reaches the mouth
            c.pet.eat(food)
            val s = c.pet.state.value
            val sick = !food.healthy && s.fat > 70f
            val (face, move) = when {
                food.healthy -> Expression.HAPPY to Gesture.BOUNCE
                sick -> Expression.SAD to Gesture.SHAKE
                else -> Expression.TONGUE to Gesture.NOD
            }
            c.avatar.play(AnimationCommand(face, move, holdMs = 1_500))
            if (speak) c.avatar.speak(com.talkto.core.pet.Nutrition.line(food, s.fat, s.vitality, c.language.current), voice = c.settings.settings.value.voiceEnabled)
        }
    }

    fun dismissUpdate() = _pendingUpdates.update { it.drop(1) }

    // -------------------------------------------------------------- arcade: 3D Tetris and sweets

    enum class Arcade { TETRIS, SWEETS }

    private val _arcade = MutableStateFlow<Arcade?>(null)
    /** The arcade game on screen, or null. */
    val arcade: StateFlow<Arcade?> = _arcade

    fun openArcade(game: Arcade) {
        comeOut()
        _arcade.value = game
    }

    fun closeArcade() { _arcade.value = null }

    /** A round is over: coins for playing, more for lines or a won level, and ZnaiKo is happy about it. */
    fun arcadeFinished(game: Arcade, points: Int, lines: Int = 0, won: Boolean = false) = viewModelScope.launch {
        c.pet.play()
        c.pet.earn(com.talkto.core.shop.CoinReason.GAME_PLAYED)
        if (lines > 0) c.pet.earn(com.talkto.core.shop.CoinReason.TASK, lines.coerceAtMost(20))
        if (won) { c.pet.earn(com.talkto.core.shop.CoinReason.GAME_WON); c.pet.gameWon() }
        val l = c.language.current
        val line = when {
            won -> l.pick("Р‘СЂР°РІРѕ! РњРёРЅР° РЅРёРІРѕС‚Рѕ СЃ $points С‚РѕС‡РєРё!", "Well done! You passed the level with $points points!")
            game == Arcade.TETRIS -> l.pick("РљСЂР°Р№! $points С‚РѕС‡РєРё Рё $lines СЂРµРґР°. РҐР°Р№РґРµ РїР°Рє?", "Game over! $points points and $lines lines. Again?")
            else -> l.pick("РҐРѕРґРѕРІРµС‚Рµ СЃРІСЉСЂС€РёС…Р°. $points С‚РѕС‡РєРё! РћРїРёС‚Р°Р№ РїР°Рє.", "Out of moves. $points points! Try again.")
        }
        c.avatar.play(AnimationCommand(if (won) Expression.HAPPY else Expression.THINKING, Gesture.BOUNCE, holdMs = 1_200))
        c.avatar.speak(line, voice = c.settings.settings.value.voiceEnabled)
    }

    fun startGame(kind: GameKind, players: Int = 2) {
        comeOut()
        games.start(kind, players)
    }

    fun play() {
        comeOut()
        c.pet.play()
        c.avatar.play(AnimationCommand(Expression.HAPPY, Gesture.SPIN, holdMs = 1_500))
    }

    /** Bedtime offers a story first; waking up just wakes. */
    fun toggleSleep() {
        val goingToBed = !c.pet.state.value.sleeping
        c.pet.toggleSleep()
        if (goingToBed && !timeUp.value) _bedtime.value = true
    }

    /** When the last long reply was put into the reader. */
    private var shownTaleAt = 0L

    // ------------------------------------------------------------- house, shop

    /** ZnaiKo walks into its house. */
    fun goHome() {
        if (!c.pet.state.value.atHome) c.pet.setHome(true)
    }

    /** ZnaiKo comes out (and wakes up if it was sleeping inside). */
    fun comeOut() {
        if (c.pet.state.value.atHome) c.pet.setHome(false)
    }

    /** Buys [item] with coins. ZnaiKo says thank you, or how many coins are still missing. */
    fun buy(item: ShopItem): Boolean {
        val l = c.language.current
        val ok = c.pet.buy(item)
        val line = if (ok) l.pick("Р‘Р»Р°РіРѕРґР°СЂСЏ! РљСѓРїРёС… ${item.label(l)}.", "Thank you! I bought the ${item.label(l).lowercase()}.")
        else l.pick("РўСЂСЏР±РІР°С‚ РјРё РѕС‰Рµ ${item.price - c.pet.state.value.coins} РјРѕРЅРµС‚Рё.", "I need ${item.price - c.pet.state.value.coins} more coins.")
        _systemLine.value = SystemLine(0, System.currentTimeMillis(), text = line)
        c.avatar.play(AnimationCommand(if (ok) Expression.LOVE else Expression.SAD, if (ok) Gesture.SPIN else Gesture.SHAKE, holdMs = 1_500))
        viewModelScope.launch { c.avatar.speak(line, voice = c.settings.settings.value.voiceEnabled) }
        return ok
    }

    fun saveLook(l: CreatureLook) = viewModelScope.launch { c.petStore.saveLook(l) }

    fun saveHouse(h: HouseLook) = viewModelScope.launch { c.petStore.saveHouse(h) }

    // -------------------------------------------------------------- quizzes

    fun startMath(grade: Int? = null, algebra: Boolean = false, topic: com.talkto.core.quiz.MathTopic? = null) {
        comeOut()
        quiz.startMath(grade, algebra, topic ?: if (algebra) com.talkto.core.quiz.MathTopic.ALGEBRA else com.talkto.core.quiz.MathTopic.MIXED)
    }

    fun startTrivia(category: com.talkto.core.quiz.TriviaCategory? = null) {
        comeOut()
        quiz.startTrivia(category)
    }

    /** Listens for a quiz answer in ZnaiKo's language. */
    fun quizListen(dialogOnly: Boolean = false) {
        c.avatar.stopSpeaking()
        if (dialogOnly) c.voice.preferDialog = true
        c.voice.start(if (c.language.current == Lang.EN) VoiceLanguage.EN else VoiceLanguage.BG) { heard -> quiz.onHeard(heard) }
    }

    /** Listens once and hands over what was heard (answers in "get to know me"). */
    fun listenOnce(dialogOnly: Boolean = false, onHeard: (String) -> Unit) {
        c.avatar.stopSpeaking()
        if (dialogOnly) c.voice.preferDialog = true
        c.voice.start(if (c.language.current == Lang.EN) VoiceLanguage.EN else VoiceLanguage.BG) { heard -> onHeard(heard) }
    }

    private val _lookAt = MutableStateFlow<Pair<Float, Float>?>(null)
    /** Where the finger last touched the pet, for the 3D eyes. */
    val lookAt: StateFlow<Pair<Float, Float>?> = _lookAt.asStateFlow()

    val reactions = c.avatar.reactions

    /** Where the 3D body is on screen (written by the renderer), so a touch knows which part it hit. */
    val body = BodyLocator()

    /** Sideways drags that turn the 3D pet. */
    val twirl = TwirlInput()

    fun onTouchDown(nx: Float, ny: Float) {
        _lookAt.value = nx to ny
    }

    /** Slap, hit, pat, caress or poke: the pet's temperament decides how it feels about it. */
    fun onTouch(touch: Touch) {
        val happy = c.pet.state.value.happiness > 60f && !c.pet.state.value.sleeping
        val r = c.temperament.react(touch, petHappy = happy)
        c.pet.touched(r.happinessDelta, r.bondDelta)
        c.avatar.react(r, touch)
        // The flat 2D pet cannot be turned by the finger, so a twirl plays its spin instead.
        if (touch.kind == TouchKind.TWIRL && !c.settings.settings.value.avatar3d) c.avatar.play(AnimationCommand(r.expression, Gesture.SPIN))
        r.line?.let { line ->
            _systemLine.value = SystemLine(0, System.currentTimeMillis(), text = line)
            viewModelScope.launch { c.avatar.speak(line, voice = c.settings.settings.value.voiceEnabled) }
        }
    }

    // ------------------------------------------------------------------ avatar

    fun onPhotoPicked(uri: Uri?) = c.avatar.setPendingPhoto(uri)

    fun newCameraUri(): Uri = c.avatar.newCameraUri()

    /** Direct generation from the avatar sheet, without a round-trip through Claude. */
    fun generateAvatar(style: AvatarStyle, extraPrompt: String?) {
        viewModelScope.launch {
            runCatching { c.avatar.generateFromPendingPhoto(style, extraPrompt?.takeIf { it.isNotBlank() }) }
                .onSuccess { say(R.string.avatar_ready, Expression.HAPPY) }
                .onFailure { c.errors.report(it, "avatar") }
        }
    }

    fun resetAvatar() = c.avatar.resetToCreature()

    fun saveOutfit(o: OutfitConfig) = viewModelScope.launch { c.petStore.saveOutfit(o) }

    // ---------------------------------------------------------------- settings

    fun saveKeys(anthropic: String?, stability: String?) = viewModelScope.launch { c.settings.saveKeys(anthropic, stability) }

    /** Forgets the Claude key: ZnaiKo goes back to offline mode at once. */
    fun removeClaudeKey() = viewModelScope.launch {
        c.settings.saveKeys(anthropic = "", stability = null)
        c.agentSession.newConversation()
        say(R.string.settings_key_removed, Expression.HAPPY)
    }

    fun setVoice(enabled: Boolean) = viewModelScope.launch { c.settings.setVoice(enabled) }

    fun loadHabits() = viewModelScope.launch { _habits.value = runCatching { c.memory.detectHabits() }.getOrDefault(emptyList()) }

    fun forgetHabit(key: String) = viewModelScope.launch {
        c.memory.dismiss(key)
        loadHabits()
    }

    fun requestShizuku() = ShizukuBridge.requestPermission()

    // ---------------------------------------------------------------------- voice

    val voice: StateFlow<VoiceState> = c.voice.state

    /** True when the last message was spoken; hands-free mode then listens again after ZnaiKo answers. */
    @Volatile private var lastInputWasVoice = false

    init {
        viewModelScope.launch {
            var wasSpeaking = false
            c.speech.speaking.collect { speaking ->
                val finished = wasSpeaking && !speaking
                wasSpeaking = speaking
                val s = c.settings.settings.value
                val busyElsewhere = lessons.state.value != null || games.state.value != null || quiz.state.value != null
                if (finished && s.handsFree && lastInputWasVoice && !agent.value.busy && !busyElsewhere) startListening()
            }
        }
    }

    init {
        viewModelScope.launch {
            c.voice.state.collect { v ->
                v.error?.let { code ->
                    val line = when (code) {
                        android.speech.SpeechRecognizer.ERROR_NETWORK, android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.voice_err_network
                        android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_err_permission
                        android.speech.SpeechRecognizer.ERROR_CLIENT -> R.string.voice_err_client
                        else -> R.string.voice_err_other
                    }
                    _systemLine.value = SystemLine(line, System.currentTimeMillis())
                }
            }
        }
    }

    /** [dialogOnly]: skip the in-app recogniser (e.g. the microphone permission was denied). */
    fun startListening(dialogOnly: Boolean = false) {
        c.avatar.stopSpeaking() // barge-in: talking over ZnaiKo interrupts it
        val lang = runCatching { VoiceLanguage.valueOf(c.settings.settings.value.voiceLanguage) }.getOrDefault(VoiceLanguage.AUTO)
        if (dialogOnly) c.voice.preferDialog = true
        c.voice.start(lang) { heard ->
            lastInputWasVoice = true
            AgentService.submit(getApplication(), heard)
        }
    }

    /** The UI opens the system speech dialog for each emission. */
    val voiceDialogRequests = c.voice.dialogRequests

    fun voiceDialogIntent(lang: VoiceLanguage) = c.voice.dialogIntent(lang)

    fun onVoiceDialogResult(results: List<String>?) = c.voice.deliverDialogResult(results)

    fun onVoiceDialogUnavailable() = c.voice.dialogUnavailable()

    fun stopListening() = c.voice.stop()

    fun setVoiceLanguage(lang: VoiceLanguage) = viewModelScope.launch { c.settings.setVoiceLanguage(lang.name) }

    fun setHandsFree(enabled: Boolean) = viewModelScope.launch { c.settings.setHandsFree(enabled) }

    fun setVoicePreset(preset: VoicePreset) = viewModelScope.launch {
        c.settings.setVoicePreset(preset)
        c.speech.setVoice(preset, c.settings.settings.value.ttsVoice)
        c.avatar.speak(preset.sample(c.language.current), voice = true)
    }

    fun setTtsVoice(name: String?) = viewModelScope.launch {
        c.settings.setTtsVoice(name)
        val preset = c.settings.settings.value.voicePreset
        c.speech.setVoice(preset, name)
        c.avatar.speak(preset.sample(c.language.current), voice = true)
    }

    /** Plays a preset's sample without saving it. The saved voice returns on the next settings change. */
    fun previewVoice(preset: VoicePreset) = viewModelScope.launch {
        val s = c.settings.settings.value
        c.speech.setVoice(preset, s.ttsVoice)
        c.avatar.speak(preset.sample(c.language.current), voice = true)
        c.speech.setVoice(s.voicePreset, s.ttsVoice)
    }

    private val _engineVoices = MutableStateFlow<List<SpeechEngine.VoiceOption>>(emptyList())
    /** Voices installed in the phone's TTS engine (bg and en). */
    val engineVoices: StateFlow<List<SpeechEngine.VoiceOption>> = _engineVoices.asStateFlow()

    fun loadEngineVoices() = viewModelScope.launch { _engineVoices.value = c.speech.availableVoices() }

    /** How Bulgarian can be spoken on this phone. */
    val bulgarianVoice: StateFlow<SpeechEngine.BulgarianVoice> = c.speech.bulgarian

    fun recheckBulgarianVoice(force: Boolean = false) = c.speech.recheck(force)

    fun setClearBulgarian(enabled: Boolean) = viewModelScope.launch {
        c.settings.setClearBulgarian(enabled)
        c.speech.clearBulgarian = enabled
        c.avatar.speak(com.talkto.core.voice.BulgarianSpeech.SAMPLE, voice = true)
    }

    /** A Bulgarian sentence with the things phones get wrong: a date, a year, a class, a unit. */
    fun sampleBulgarian() = viewModelScope.launch { c.avatar.speak(com.talkto.core.voice.BulgarianSpeech.SAMPLE, voice = true) }

    fun bulgarianVoiceInstallIntent(): Intent = c.speech.installVoiceIntent()

    // -------------------------------------------------------------- the story

    private val _storyReplay = MutableStateFlow(false)
    /** The story opened again from Settings. */
    val storyReplay: StateFlow<Boolean> = _storyReplay

    fun openStory() { _storyReplay.value = true }

    /** Reads one page aloud, in the language of the app. */
    fun readStoryPage(page: com.talkto.core.story.StoryPage) = viewModelScope.launch {
        c.avatar.speak(page.text(c.language.current), voice = c.settings.settings.value.voiceEnabled)
    }

    fun closeStory() {
        _storyReplay.value = false
        c.speech.stop()
        viewModelScope.launch { c.settings.setStorySeen() }
    }

    // -------------------------------------------------------------- backgrounds

    val backgroundConfig: StateFlow<BackgroundConfig> = c.backgrounds.config.stateIn(viewModelScope, SharingStarted.Eagerly, BackgroundConfig())

    private val _curating = MutableStateFlow<Pair<Int, Int>?>(null)
    /** (done, total) while photos are being auto-selected from the gallery. */
    val curating: StateFlow<Pair<Int, Int>?> = _curating.asStateFlow()

    fun setBackgroundsEnabled(enabled: Boolean) = viewModelScope.launch { c.backgrounds.setEnabled(enabled) }

    fun addBackgroundPhotos(scene: MoodScene, uris: List<Uri>) = viewModelScope.launch {
        runCatching { c.backgrounds.addPhotos(scene, uris) }.onFailure { c.errors.report(it, "backgrounds") }
    }

    fun removeBackgroundPhoto(scene: MoodScene, path: String) = viewModelScope.launch { c.backgrounds.removePhoto(scene, path) }

    fun autoFillBackgrounds() = viewModelScope.launch {
        _curating.value = 0 to 0
        runCatching { c.backgrounds.autoFill { done, total -> _curating.value = done to total } }
            .onSuccess { n ->
                _systemLine.value = if (n > 0) SystemLine(R.string.backgrounds_filled, System.currentTimeMillis(), listOf(n))
                else SystemLine(R.string.backgrounds_none, System.currentTimeMillis())
            }
            .onFailure { c.errors.report(it, "backgrounds") }
        _curating.value = null
    }

    // ------------------------------------------------------- 3D, history, profile

    fun setAvatar3d(enabled: Boolean) = viewModelScope.launch { c.settings.setAvatar3d(enabled) }

    fun setRecordConversations(enabled: Boolean) = viewModelScope.launch {
        c.settings.setRecordConversations(enabled)
        c.history.enabled = enabled
    }

    private val _facts = MutableStateFlow<List<Fact>>(emptyList())
    val facts: StateFlow<List<Fact>> = _facts.asStateFlow()

    fun loadProfile() = viewModelScope.launch { _facts.value = runCatching { c.profile.all() }.getOrDefault(emptyList()) }

    fun forgetFact(key: String) = viewModelScope.launch {
        c.profile.forget(key)
        loadProfile()
    }

    /** Keeps an answer about the user; new facts teach ZnaiKo a little. */
    fun rememberFact(key: String, answer: String) = viewModelScope.launch {
        val value = AboutYou.normalize(key, answer) ?: return@launch
        val known = runCatching { c.profile.get(key) }.getOrNull()
        runCatching { c.profile.remember(key, value, source = "about") }
        if (known == null) c.pet.learn(KnowledgeSource.FACT)
        loadProfile()
    }

    /** ZnaiKo asks a question and waits for the answer (typed or said). */
    fun ask(text: String) = viewModelScope.launch {
        _systemLine.value = SystemLine(0, System.currentTimeMillis(), text = text)
        c.avatar.play(AnimationCommand(Expression.HAPPY, Gesture.NOD, holdMs = 1_200))
        c.avatar.speak(text, voice = c.settings.settings.value.voiceEnabled)
    }

    private val _historyItems = MutableStateFlow<List<Utterance>>(emptyList())
    val historyItems: StateFlow<List<Utterance>> = _historyItems.asStateFlow()

    private var historyQuery = ""

    /** Newest first, filtered by [query] when given. */
    fun loadHistory(query: String = "") = viewModelScope.launch {
        historyQuery = query
        _historyItems.value = runCatching {
            if (query.isBlank()) c.history.recent(500).asReversed() else c.history.search(query, 200)
        }.getOrDefault(emptyList())
    }

    fun clearHistory() = viewModelScope.launch {
        c.agentSession.clearHistory()
        loadHistory()
    }

    /** Shares the transcript as plain text through the Android share sheet. */
    fun shareHistory(context: Context) = viewModelScope.launch {
        val text = runCatching { c.history.export(lang = c.language.current) }.getOrDefault("")
        if (text.isBlank()) return@launch
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "ZnaiKo")
            .putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null))
    }

    private companion object {
        /** A reply this long is a tale, not a chat line. */
        const val LONG_REPLY = 400
    }
}
