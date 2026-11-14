package com.taxiapp.ui.onboarding

import android.graphics.Color as AndroidColor
import androidx.annotation.OptIn
import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * A muted, looping, full-bleed video from res/raw. Pauses when the app is in the
 * background and releases the player when it leaves the screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoBackground(@RawRes videoRes: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(videoRes) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/$videoRes"))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                this.player = player
            }
        },
    )
}
