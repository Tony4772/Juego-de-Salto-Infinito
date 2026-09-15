package com.ebyzom.skydreams

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool

/**
 * Central audio hub: SoundPool for low-latency SFX + MediaPlayer for the
 * seamless looping background track. Respects a persisted mute preference.
 */
class SoundManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var soundOn: Boolean = prefs.getBoolean(KEY_SOUND, true)
        private set

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids: Map<Int, Int> = mapOf(
        S_JUMP to pool.load(context, R.raw.sfx_jump, 1),
        S_STAR to pool.load(context, R.raw.sfx_star, 1),
        S_POWER to pool.load(context, R.raw.sfx_powerup, 1),
        S_HIT to pool.load(context, R.raw.sfx_hit, 1),
        S_OVER to pool.load(context, R.raw.sfx_gameover, 1),
        S_CLICK to pool.load(context, R.raw.sfx_click, 1),
        S_BEST to pool.load(context, R.raw.sfx_best, 1),
    )

    private var music: MediaPlayer? = try {
        MediaPlayer.create(context, R.raw.music_loop)?.apply {
            isLooping = true
            setVolume(0.55f, 0.55f)
        }
    } catch (_: Exception) {
        null
    }

    private var musicStarted = false

    fun toggleSound(): Boolean {
        soundOn = !soundOn
        prefs.edit().putBoolean(KEY_SOUND, soundOn).apply()
        if (!soundOn) music?.setVolume(0f, 0f) else music?.setVolume(0.55f, 0.55f)
        if (soundOn && musicStarted) playMusic()
        return soundOn
    }

    fun play(which: Int, vol: Float = 1f) {
        if (!soundOn) return
        ids[which]?.let { pool.play(it, vol, vol, 1, 0, 1f) }
    }

    fun startMusic() {
        if (!soundOn) return
        music?.let {
            if (!it.isPlaying) {
                it.isLooping = true
                it.start()
            }
            musicStarted = true
        }
    }

    fun playMusic() = startMusic()

    fun pauseMusic() {
        music?.let { if (it.isPlaying) it.pause() }
    }

    fun duckMusic(low: Boolean) {
        music?.setVolume(if (low) 0.15f else 0.55f, if (low) 0.15f else 0.55f)
    }

    fun release() {
        runCatching { pool.release() }
        runCatching { music?.release() }
        music = null
    }

    companion object {
        private const val PREFS = "sky"
        private const val KEY_SOUND = "sound_on"
        const val S_JUMP = 1
        const val S_STAR = 2
        const val S_POWER = 3
        const val S_HIT = 4
        const val S_OVER = 5
        const val S_CLICK = 6
        const val S_BEST = 7
    }
}
