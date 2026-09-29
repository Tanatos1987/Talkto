package com.talkto.app.avatar3d

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.talkto.app.avatar.AvatarPose
import com.talkto.app.avatar.OutfitConfig
import com.talkto.core.pet.LifeStage
import com.talkto.core.touch.BodyLocator
import com.talkto.core.touch.Touch
import com.talkto.core.touch.TouchReaction
import com.talkto.core.touch.TwirlInput
import kotlinx.coroutines.flow.Flow

/**
 * The 3D pet. A transparent [GLTextureView] (OpenGL ES 2.0) hosted in Compose; the renderer runs on its own GL thread
 * and reads the latest [SceneState] every frame, so recomposition never blocks rendering.
 * Paused with the lifecycle to save battery when ZnaiKo is not on screen.
 */
@Composable
fun Avatar3DView(
    pose: AvatarPose,
    outfit: OutfitConfig,
    stage: LifeStage,
    sleeping: Boolean,
    reactions: Flow<Pair<TouchReaction, Touch>>,
    lookAt: Pair<Float, Float>?,
    modifier: Modifier = Modifier,
    body: BodyLocator? = null,
    twirl: TwirlInput? = null,
) {
    val renderer = remember(body, twirl) { Creature3DRenderer(body, twirl) }
    SideEffect { renderer.scene = SceneState(pose, outfit, stage, sleeping) }
    LaunchedEffect(lookAt) { lookAt?.let { (x, y) -> renderer.lookAt(x, y) } }
    LaunchedEffect(reactions) { reactions.collect { (r, t) -> renderer.react(r, t) } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = remember { arrayOfNulls<GLTextureView>(1) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            GLTextureView(ctx, renderer).also { holder[0] = it }
        },
    )
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder[0]?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
