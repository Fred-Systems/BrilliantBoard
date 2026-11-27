package com.fredsystems.brilliantboard.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.fredsystems.brilliantboard.R

/** Short synthesized chess sounds: quiet move click, deeper capture thock, check chime. */
class MoveSounds(context: Context) {
    private val ids = mutableMapOf<Int, Int>()
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    init {
        listOf(R.raw.move_sound, R.raw.capture_sound, R.raw.check_sound).forEach { res ->
            ids[res] = pool.load(context, res, 1)
        }
    }

    fun play(capture: Boolean, check: Boolean = false) {
        val res = when {
            check -> R.raw.check_sound
            capture -> R.raw.capture_sound
            else -> R.raw.move_sound
        }
        val soundId = ids[res] ?: return
        runCatching { pool.play(soundId, 0.75f, 0.75f, 1, 0, 1f) }
    }

    fun release() = runCatching { pool.release() }
}
