package com.talkto.app.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import com.talkto.core.i18n.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * ZnaiKo's language: the screens, its voice and everything it says. Kept in plain SharedPreferences, because
 * [MainActivity.attachBaseContext] needs it synchronously, before anything else of the app has started.
 * Bulgarian by default, so updating from an older version changes nothing.
 */
class LanguageRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _lang = MutableStateFlow(read(context))
    val lang: StateFlow<Lang> = _lang.asStateFlow()
    val current: Lang get() = _lang.value

    @Volatile private var cached: Pair<Lang, Context>? = null

    fun set(lang: Lang) {
        if (lang == current) return
        prefs.edit().putString(KEY, lang.code).apply()
        cached = null
        _lang.value = lang
    }

    /** A context whose resources are in the chosen language, for text built outside Compose. */
    fun context(): Context {
        val lang = current
        cached?.takeIf { it.first == lang }?.let { return it.second }
        return localized(context, lang).also { cached = lang to it }
    }

    fun text(@StringRes res: Int, vararg args: Any): String = context().getString(res, *args)

    companion object {
        private const val PREFS = "znaiko_language"
        private const val KEY = "lang"

        fun read(context: Context): Lang =
            Lang.of(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

        /** [base] with its resources switched to [lang] (values-en for English, the default values for Bulgarian). */
        fun localized(base: Context, lang: Lang): Context {
            val locale = Locale.forLanguageTag(lang.tag)
            val config = Configuration(base.resources.configuration)
            config.setLocales(LocaleList(locale))
            return base.createConfigurationContext(config)
        }
    }
}
