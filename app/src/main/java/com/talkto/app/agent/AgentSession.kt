package com.talkto.app.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.talkto.app.avatar.AvatarEngine
import com.talkto.app.data.prefs.SettingsRepository
import com.talkto.app.error.GlobalErrorHandler
import com.talkto.app.i18n.LanguageRepository
import com.talkto.app.learn.LearnRepository
import com.talkto.app.pet.PetEngine
import com.talkto.core.agent.AgentEvent
import com.talkto.core.agent.Assistant
import com.talkto.core.agent.OfflineAgent
import com.talkto.core.avatar.AnimationCommand
import com.talkto.core.avatar.Expression
import com.talkto.core.avatar.Gesture
import com.talkto.core.error.ErrorMapper
import com.talkto.core.error.TalktoError
import com.talkto.core.games.GameCommands
import com.talkto.core.games.GameKind
import com.talkto.core.i18n.Lang
import com.talkto.core.learn.LearnCommand
import com.talkto.core.learn.LearnCommands
import com.talkto.core.commands.AppCommand
import com.talkto.core.commands.AppCommands
import com.talkto.core.learn.wordOfTheDay
import com.talkto.core.history.HistoryRepository
import com.talkto.core.history.Speaker
import com.talkto.core.pet.Food
import com.talkto.core.story.StoryCommands
import com.talkto.core.story.StoryRequest
import com.talkto.core.pet.KnowledgeSource
import com.talkto.core.profile.ProfileRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration

/** [fromClaude]: the reply was written by Claude, so the child can flag it with 🚩. */
data class ChatMessage(val fromUser: Boolean, val text: String, val atMs: Long, val fromClaude: Boolean = false)

data class AgentUiState(
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
    /** Tool currently running, for the "working on…" chip. */
    val activeTool: String? = null,
)

/** Builds (and rebuilds on key change) the Anthropic client from the encrypted settings. */
class AnthropicClientHolder(private val settings: SettingsRepository) {
    private var key: String? = null
    private var client: AnthropicClient? = null

    @Synchronized
    fun get(): AnthropicClient {
        val s = settings.settings.value
        val proxy = s.proxyUrl?.trim()?.takeIf { it.startsWith("https://") }
        // A family server holds the real key; the field then carries the code that server asks for (or nothing).
        val apiKey = s.anthropicKey?.takeIf { it.isNotBlank() } ?: if (proxy != null) "family-server" else throw TalktoError.ApiKeyMissing("Claude")
        val current = apiKey + "|" + proxy.orEmpty()
        client?.takeIf { key == current }?.let { return it }
        client?.close()
        return AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .apply { if (proxy != null) baseUrl(proxy) }
            .maxRetries(2)
            .timeout(Duration.ofSeconds(90))
            .build()
            .also { client = it; key = current }
    }
}

/**
 * One conversation with ZnaiKo. Owned by the application container and driven by [AgentService],
 * so a turn survives the user switching away (for example while Recents is being swiped).
 *
 * Routing: with a Claude key every message goes to Claude. Without one, [offline] handles simple commands.
 * With a key but no network, a message the offline parser recognises is still carried out locally.
 */
class AgentSession(
    private val claude: Assistant,
    private val offline: OfflineAgent,
    private val avatar: AvatarEngine,
    private val pet: PetEngine,
    private val settings: SettingsRepository,
    private val errors: GlobalErrorHandler,
    private val profile: ProfileRepository,
    private val history: HistoryRepository,
    private val language: LanguageRepository,
    private val learning: LearnRepository,
    /** Counts conversations for the parents' report. */
    private val onActivity: (com.talkto.core.parent.Activity) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _gameRequests = MutableSharedFlow<GameKind>(extraBufferCapacity = 2)
    /** Games asked for by voice or text; the screen opens them. */
    val gameRequests: SharedFlow<GameKind> = _gameRequests.asSharedFlow()

    private val _lessonRequests = MutableSharedFlow<Lang?>(extraBufferCapacity = 2)
    /** "Научи ме на английски": the screen opens the lessons (for this language, when one was named). */
    val lessonRequests: SharedFlow<Lang?> = _lessonRequests.asSharedFlow()

    private val _appRequests = MutableSharedFlow<AppCommand>(extraBufferCapacity = 4)
    /** "Прибери се", "магазин", "тривия", "задачи за 3 клас": the screen does them. */
    val appRequests: SharedFlow<AppCommand> = _appRequests.asSharedFlow()

    private val _feedRequests = MutableSharedFlow<Food>(extraBufferCapacity = 4)
    /** Food Claude gave ZnaiKo through the pet tool; the screen shows the bite flying in. */
    val feedRequests: SharedFlow<Food> = _feedRequests.asSharedFlow()

    /** The pet tool: open a place, an arcade game or a quiz, as if the child had asked for it. */
    fun requestApp(command: AppCommand) {
        _appRequests.tryEmit(command)
    }

    fun requestGame(kind: GameKind) {
        _gameRequests.tryEmit(kind)
    }

    fun requestLessons() {
        _lessonRequests.tryEmit(null)
    }

    private val _storyRequests = MutableSharedFlow<StoryRequest>(extraBufferCapacity = 2)
    /** A built-in tale or riddle to open in the reader (asked offline, or by Claude through the pet tool). */
    val storyRequests: SharedFlow<StoryRequest> = _storyRequests.asSharedFlow()

    /** Set when a tale started during this turn: ZnaiKo is reading it, so the reply is shown but not spoken over it. */
    @Volatile private var storyThisTurn = false

    fun requestStory(request: StoryRequest) {
        storyThisTurn = true
        _storyRequests.tryEmit(request)
    }

    /** With the screen open the bite flies in; without it ZnaiKo simply eats. */
    fun requestFeed(food: Food) {
        if (_feedRequests.subscriptionCount.value == 0) pet.eat(food) else _feedRequests.tryEmit(food)
    }

    private val _practice = MutableStateFlow<Lang?>(null)
    /** Chat practice in this language is on (Claude then speaks it simply and corrects gently). */
    val practice: StateFlow<Lang?> = _practice.asStateFlow()

    fun stopPractice() {
        _practice.value = null
    }

    private val _state = MutableStateFlow(AgentUiState())
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    /** Claude gets the recorded conversation once per process, so it remembers what was said before a restart. */
    @Volatile private var claudeSeeded = false

    /** Shows the last recorded lines after an app restart. */
    suspend fun restore() {
        val lines = runCatching { history.recent(RESTORE_LINES) }.getOrDefault(emptyList())
        if (lines.isEmpty() || _state.value.messages.isNotEmpty()) return
        _state.update {
            it.copy(messages = lines.map { u -> ChatMessage(u.speaker == Speaker.USER, u.text, u.atMs, fromClaude = u.speaker == Speaker.TALKTO && u.mode == "claude") })
        }
    }

    suspend fun run(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        // A turn started right after a cold start (e.g. from a notification) must see the saved API key.
        val cfg = settings.awaitLoaded()
        history.enabled = cfg.recordConversations
        onActivity(com.talkto.core.parent.Activity.CHAT)
        val online = cfg.claudeOn
        // Personal shortcuts ("кино" -> "тихо") and learning happen before routing, so both brains benefit.
        val command = runCatching { profile.expandAlias(trimmed) }.getOrNull() ?: trimmed
        val facts = runCatching { profile.learnFrom(trimmed) }.getOrDefault(emptyList())
        // Every conversation teaches ZnaiKo a little; learning something about the user teaches it more.
        pet.learn(KnowledgeSource.CHAT)
        pet.learn(KnowledgeSource.FACT, facts.size)
        // What is not for the child's age stays closed, whoever asks for it; ZnaiKo says so instead of opening it.
        val rules = cfg.ageRules()
        fun tooYoung(feature: com.talkto.core.age.Feature?): String? = feature?.takeIf { !rules.allows(it) }?.let { f ->
            val l = lang()
            l.pick("„${f.label(l)}“ е за по-големи деца. Хайде да изберем нещо друго!", "\"${f.label(l)}\" is for bigger children. Let's pick something else!")
        }
        // "Да играем шах" opens the game in both modes; there is nothing for Claude to add.
        GameCommands.parse(command)?.let { kind ->
            tooYoung(com.talkto.core.age.Feature.of(kind))?.let { return localReply(trimmed, it, cfg.voiceEnabled) }
            _gameRequests.tryEmit(kind)
            return localReply(trimmed, lang().pick("Отварям „${kind.bg}“. Да видим кой ще спечели!", "Opening ${kind.en}. Let's see who wins!"), cfg.voiceEnabled)
        }
        // Language switches, lessons, chat practice and the word of the day are the app's own business.
        LearnCommands.parse(command)?.let { cmd -> return localReply(trimmed, learn(cmd, online), cfg.voiceEnabled) }
        // The house, the shop, the creator and the quizzes are the app's own places too.
        AppCommands.parse(command)?.let { cmd ->
            tooYoung(com.talkto.core.age.Feature.of(cmd))?.let { return localReply(trimmed, it, cfg.voiceEnabled) }
            _appRequests.tryEmit(cmd)
            return localReply(trimmed, appReply(cmd), cfg.voiceEnabled)
        }
        // Offline, "разкажи ми приказка" opens the built-in library; online Claude tells the tale (or opens the library).
        if (!online) StoryCommands.parse(command)?.let { req ->
            if (req.riddle) tooYoung(com.talkto.core.age.Feature.RIDDLES)?.let { return localReply(trimmed, it, cfg.voiceEnabled) }
            _storyRequests.tryEmit(req)
            val line = if (req.riddle) lang().pick("Ето една гатанка!", "Here's a riddle!") else lang().pick("Слушай!", "Listen!")
            runCatching { history.record(Speaker.USER, trimmed, "offline") }
            runCatching { history.record(Speaker.TALKTO, line, "offline") }
            _state.update { it.copy(messages = it.messages + ChatMessage(true, trimmed, clock()) + ChatMessage(false, line, clock())) }
            return
        }
        storyThisTurn = false
        if (online && !claudeSeeded) {
            claudeSeeded = true
            runCatching { claude.seed(history.recent(SEED_LINES).map { (it.speaker == Speaker.USER) to it.text }) }
        }
        runCatching { history.record(Speaker.USER, trimmed, if (online) "claude" else "offline") }
        _state.update { it.copy(messages = it.messages + ChatMessage(true, trimmed, clock()), busy = true) }
        avatar.play(AnimationCommand(Expression.THINKING, Gesture.NONE, holdMs = 30_000))
        var toolErrors = 0
        // Set only when Claude itself answered; a command the offline brain carried out instead cannot be flagged.
        var byClaude = false
        try {
            val onEvent: suspend (AgentEvent) -> Unit = { event ->
                when (event) {
                    AgentEvent.Thinking -> _state.update { it.copy(activeTool = null) }
                    is AgentEvent.ToolStarted -> _state.update { it.copy(activeTool = event.name) }
                    is AgentEvent.ToolFinished -> if (event.isError) toolErrors++
                }
            }
            val reply = if (!online) {
                offline.send(command, onEvent)
            } else {
                try {
                    claude.send(command, onEvent).also { byClaude = true }
                } catch (t: Throwable) {
                    val err = ErrorMapper.map(t)
                    // Whatever stops Claude (no network, no credit, a bad key, an overloaded server), a command
                    // the offline brain knows is still carried out, and the reason is said once.
                    if (err.kind !in FALLBACK_KINDS || !offline.recognizes(command)) throw err
                    offline.send(command, onEvent).let { it.copy(text = fallbackPrefix(err.kind, lang()) + it.text) }
                }
            }
            runCatching { history.record(Speaker.TALKTO, reply.text, if (byClaude) "claude" else "offline") }
            if (_practice.value != null && online) pet.learn(KnowledgeSource.LESSON_ANSWER)
            _state.update { it.copy(messages = it.messages + ChatMessage(false, reply.text, clock(), fromClaude = byClaude)) }
            pet.rewardTask(success = toolErrors == 0 && !reply.refused)
            if (reply.text.isNotBlank()) {
                // Claude may already have set an expression via animate_avatar; only nudge it when it did not.
                if (avatar.pose.value.expression == Expression.THINKING) {
                    avatar.play(AnimationCommand(if (toolErrors == 0) Expression.HAPPY else Expression.CONFUSED, Gesture.NOD))
                }
                // A tale opened during this turn is being read aloud; the reply only shows in the bubble.
                if (!storyThisTurn) avatar.speak(reply.text, voice = settings.settings.value.voiceEnabled)
            }
        } catch (t: Throwable) {
            avatar.play(AnimationCommand(Expression.CONFUSED, Gesture.SHAKE))
            errors.report(t, "agent")
        } finally {
            _state.update { it.copy(busy = false, activeTool = null) }
        }
    }

    private fun lang(): Lang = language.current

    /** A turn the app answers itself, without either brain. */
    private suspend fun localReply(said: String, reply: String, voice: Boolean) {
        runCatching { history.record(Speaker.USER, said, "offline") }
        runCatching { history.record(Speaker.TALKTO, reply, "offline") }
        _state.update { it.copy(messages = it.messages + ChatMessage(true, said, clock()) + ChatMessage(false, reply, clock())) }
        avatar.play(AnimationCommand(Expression.HAPPY, Gesture.BOUNCE))
        avatar.speak(reply, voice = voice)
    }

    private fun appReply(cmd: AppCommand): String {
        val l = lang()
        return when (cmd) {
            AppCommand.GoHome -> l.pick("Прибирам се в къщичката си.", "I'm going into my little house.")
            AppCommand.ComeOut -> l.pick("Идвам!", "Coming!")
            AppCommand.OpenHouse -> l.pick("Ето моята къщичка.", "Here is my little house.")
            AppCommand.OpenShop -> l.pick("Да пазаруваме! Имам ${pet.state.value.coins} монети.", "Let's go shopping! I have ${pet.state.value.coins} coins.")
            AppCommand.OpenCreator -> l.pick("Направи ме, какъвто искаш!", "Make me any way you like!")
            AppCommand.AboutMe -> l.pick("Искам да те опозная! Ще те питам нещо.", "I'd like to get to know you! Let me ask you something.")
            is AppCommand.Math -> when {
                cmd.geometry -> l.pick("Да мерим и чертаем! Геометрия!", "Let's measure and draw! Geometry!")
                cmd.algebra -> l.pick("Да решаваме задачи с букви!", "Let's solve some algebra!")
                cmd.grade != null -> l.pick("Да смятаме! Задачи за ${cmd.grade} клас.", "Let's do maths! Tasks for year ${cmd.grade}.")
                else -> l.pick("Да смятаме!", "Let's do maths!")
            }
            AppCommand.Tetris -> l.pick("Да редим кубчета! 3D Тетрис!", "Let's stack some cubes! 3D Tetris!")
            AppCommand.Sweets -> l.pick("Бонбонки! Нареди три еднакви!", "Sweets! Line up three of a kind!")
            is AppCommand.Trivia -> cmd.category?.let { c -> l.pick("Викторина с въпроси на тема ${c.emoji} ${c.bg}!", "A quiz with ${c.emoji} ${c.en} questions!") }
                ?: l.pick("Викторина! Ще ти задам десет въпроса.", "Quiz time! Ten questions for you.")
        }
    }

    private fun learn(cmd: LearnCommand, online: Boolean): String {
        val l = lang()
        return when (cmd) {
            is LearnCommand.SwitchLanguage -> {
                language.set(cmd.to)
                cmd.to.pick("Добре! Вече говоря на български.", "OK! I'm speaking English now.")
            }
            is LearnCommand.OpenLessons -> {
                cmd.target?.let(learning::setTarget)
                _lessonRequests.tryEmit(cmd.target)
                l.pick("Отварям уроците. Да учим заедно!", "Opening the lessons. Let's learn together!")
            }
            is LearnCommand.Practice -> if (!settings.settings.value.ageRules().allows(com.talkto.core.age.Feature.LANGUAGE_CHAT)) {
                // Too young to chat in another language: words with pictures instead.
                learning.setTarget(cmd.target)
                _lessonRequests.tryEmit(cmd.target)
                l.pick("Хайде първо да научим думи с картинки!", "Let's learn some words with pictures first!")
            } else if (online) {
                startPractice(cmd.target)
                cmd.target.pick(
                    "Чудесно, да упражняваме български! Ще говоря просто и бавно. Как си днес?",
                    "Great, let's practise English! I'll keep it simple. How are you today?",
                )
            } else {
                learning.setTarget(cmd.target)
                _lessonRequests.tryEmit(cmd.target)
                l.pick(
                    "За свободен разговор ми трябва Claude ключ. Дотогава да направим урок!",
                    "Free conversation practice needs a Claude key. Until then, let's do a lesson!",
                )
            }
            LearnCommand.StopPractice -> {
                stopPractice()
                l.pick("Край на упражнението. Браво!", "Practice over. Well done!")
            }
            LearnCommand.WordOfTheDay -> {
                val target = learning.target
                val w = wordOfTheDay(learning.today(), target)
                val word = if (target == Lang.BG) "„${w.bg}“" else "\"${w.en}\""
                val meaning = if (target == Lang.BG) "\"${w.en}\"" else "„${w.bg}“"
                l.pick("Думата на деня е $word ${w.emoji}, тоест $meaning.", "The word of the day is $word ${w.emoji}, which means $meaning.")
            }
        }
    }

    /** Starts chat practice from the lessons screen: ZnaiKo greets in the practised language (or explains the key). */
    suspend fun beginPractice(target: Lang) {
        val cfg = settings.awaitLoaded()
        val reply = learn(LearnCommand.Practice(target), cfg.claudeOn)
        _state.update { it.copy(messages = it.messages + ChatMessage(false, reply, clock())) }
        runCatching { history.record(Speaker.TALKTO, reply, "offline") }
        avatar.play(AnimationCommand(Expression.HAPPY, Gesture.BOUNCE))
        avatar.speak(reply, voice = cfg.voiceEnabled)
    }

    fun startPractice(target: Lang) {
        _practice.value = target
        learning.setTarget(target)
    }

    /** Starts a fresh chat on screen and for Claude. The recorded log is kept (clear it from Settings). */
    suspend fun newConversation() {
        claude.reset()
        offline.reset()
        claudeSeeded = true // a new conversation means: do not pull the old one back in
        _state.value = AgentUiState()
    }

    suspend fun clearHistory() {
        history.clear()
        newConversation()
    }

    private companion object {
        fun fallbackPrefix(kind: TalktoError.Kind, lang: Lang) = when (kind) {
            TalktoError.Kind.NETWORK -> lang.pick("Нямам връзка с Claude, затова го направих сам. ", "I can't reach Claude, so I did it myself. ")
            TalktoError.Kind.API_NO_CREDIT -> lang.pick("В профила за Claude няма кредит, затова го направих сам. ", "The Claude account has no credit, so I did it myself. ")
            TalktoError.Kind.API_KEY_INVALID, TalktoError.Kind.API_KEY_MISSING -> lang.pick("Ключът за Claude не става, затова го направих сам. ", "The Claude key doesn't work, so I did it myself. ")
            else -> lang.pick("Claude не ми отговори, затова го направих сам. ", "Claude didn't answer, so I did it myself. ")
        }
        val FALLBACK_KINDS = setOf(
            TalktoError.Kind.NETWORK, TalktoError.Kind.RATE_LIMITED, TalktoError.Kind.API_REJECTED,
            TalktoError.Kind.API_NO_CREDIT, TalktoError.Kind.API_KEY_INVALID, TalktoError.Kind.API_KEY_MISSING,
        )
        const val RESTORE_LINES = 30
        const val SEED_LINES = 20
    }
}
