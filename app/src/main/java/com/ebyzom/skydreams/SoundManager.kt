package com.ebyzom.skydreams

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import java.util.concurrent.ConcurrentHashMap

/**
 * Central audio hub: SoundPool for low-latency SFX + MediaPlayer for the
 * seamless looping background track. Respects a persisted mute preference.
 *
 * Loads sound effects lazily on demand. Zero startup decodes eliminates
 * system decoder resource collisions on Android devices and emulators.
 */
class SoundManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var soundOn: Boolean = prefs.getBoolean(KEY_SOUND, true)
        private set

    private var pool: SoundPool? = try {
        SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    } catch (_: Exception) {
        null
    }

    private val ids = ConcurrentHashMap<Int, Int>()
    private val loadedIds = ConcurrentHashMap.newKeySet<Int>()
    private val pendingVol = ConcurrentHashMap<Int, Float>()

    private val soundResMap = mapOf(
        S_JUMP to R.raw.sfx_jump,
        S_STAR to R.raw.sfx_star,
        S_POWER to R.raw.sfx_powerup,
        S_HIT to R.raw.sfx_hit,
        S_OVER to R.raw.sfx_gameover,
        S_CLICK to R.raw.sfx_click,
        S_BEST to R.raw.sfx_best
    )

    init {
        pool?.setOnLoadCompleteListener { sp, sampleId, status ->
            if (status == 0) {
                loadedIds.add(sampleId)
                val vol = pendingVol.remove(sampleId)
                if (soundOn && vol != null && vol > 0f) {
                    try {
                        sp.play(sampleId, vol, vol, 1, 0, 1f)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private var music: MediaPlayer? = try {
        MediaPlayer.create(appContext, R.raw.music_loop)?.apply {
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
        val p = pool ?: return
        val existingId = ids[which]
        if (existingId != null) {
            if (loadedIds.contains(existingId)) {
                try {
                    p.play(existingId, vol, vol, 1, 0, 1f)
                } catch (_: Exception) {}
            }
        } else {
            // Lazy on-demand loading: only decode the sound when it is actually needed!
            val resId = soundResMap[which] ?: return
            try {
                val newId = p.load(appContext, resId, 1)
                if (newId > 0) {
                    ids[which] = newId
                    pendingVol[newId] = vol
                }
            } catch (_: Exception) {}
        }
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
        try {
            pool?.release()
        } catch (_: Exception) {}
        pool = null
        ids.clear()
        loadedIds.clear()
        pendingVol.clear()

        try {
            music?.stop()
            music?.release()
        } catch (_: Exception) {}
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
