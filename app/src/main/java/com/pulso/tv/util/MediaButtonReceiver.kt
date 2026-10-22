package com.pulso.tv.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import com.pulso.tv.PulsoApp
import com.pulso.tv.ui.player.ExoPlayerService
import com.pulso.tv.ui.player.MediaPlayerService
import kotlinx.coroutines.runBlocking

class MediaButtonReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent != null && intent.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                        || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                        || keyEvent.keyCode == KeyEvent.KEYCODE_HEADSETHOOK) {
                        val savedStates = runBlocking {
                            (context.applicationContext as PulsoApp).pulsoModule.playerRepository.getPlaybackStates()
                        }
                        if (savedStates.isNotEmpty()) {
                            when (context.prefs().getString(C.PLAYER, C.EXOPLAYER)) {
                                C.MEDIA_PLAYER -> {
                                    context.startForegroundService(Intent(context, MediaPlayerService::class.java).apply {
                                        fillIn(intent, 0)
                                    })
                                }
                                else -> {
                                    context.startForegroundService(Intent(context, ExoPlayerService::class.java).apply {
                                        fillIn(intent, 0)
                                    })
                                }
                            }
                        }
                    }
                } else {
                    val savedStates = runBlocking {
                        (context.applicationContext as PulsoApp).pulsoModule.playerRepository.getPlaybackStates()
                    }
                    if (savedStates.isNotEmpty()) {
                        when (context.prefs().getString(C.PLAYER, C.EXOPLAYER)) {
                            C.MEDIA_PLAYER -> {
                                context.startService(Intent(context, MediaPlayerService::class.java).apply {
                                    fillIn(intent, 0)
                                })
                            }
                            else -> {
                                context.startService(Intent(context, ExoPlayerService::class.java).apply {
                                    fillIn(intent, 0)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}