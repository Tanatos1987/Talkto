package com.talkto.app.i18n

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.talkto.app.R
import com.talkto.core.i18n.Lang

/** The language the current screen is shown in (from its resources, so it always matches the other texts). */
@Composable
fun screenLang(): Lang = Lang.of(stringResource(R.string.lang_code))
