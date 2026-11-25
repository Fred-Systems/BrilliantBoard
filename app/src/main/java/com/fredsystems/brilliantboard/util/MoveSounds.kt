package com.fredsystems.brilliantboard.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

/** Lightweight move/capture feedback that respects the in-app sound toggle. */
class MoveSounds(context: Context) {
    private val generator = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 35) }.getOrNull()

    fun play(capture: Boolean) {
        runCatching {
            generator?.startTone(
                if (capture) ToneGenerator.TONE_SUP_CONFIRM else ToneGenerator.TONE_PROP_BEEP,
                60,
            )
        }
    }

    fun release() {
        runCatching { generator?.release() }
    }
}
