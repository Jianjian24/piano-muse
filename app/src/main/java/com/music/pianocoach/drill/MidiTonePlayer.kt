package com.music.pianocoach.drill

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import io.github.lemcoder.mikrosoundfont.MikroSoundFont
import io.github.lemcoder.mikrosoundfont.SoundFont
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.tanh

/**
 * SoundFont 钢琴采样（TinySoundFont）→ 单声道 AudioTrack。
 * 使用 MONO 避免立体声交错配置导致的「怪声」。
 */
class MidiTonePlayer(
    private val context: Context,
    private val sampleRate: Int = 44_100,
) {
    private val running = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private var soundFont: SoundFont? = null
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private val channel = 0

    @Synchronized
    fun start() {
        if (running.get() && ready.get()) return
        runCatching {
            if (soundFont == null) {
                val bytes = context.assets.open(SF2_NAME).use { it.readBytes() }
                require(bytes.size > 1_000_000) { "SoundFont 不完整: ${bytes.size}" }
                val sf = MikroSoundFont.load(bytes)
                // 第三参是 gain dB；0 = 不额外衰减
                sf.setOutput(SoundFont.OutputMode.MONO, sampleRate, -3f)
                sf.setVolume(0.9f)
                sf.setMaxVoices(64)
                // 通道 0 → Acoustic Grand Piano
                val ch = sf.channels.getOrNull(channel)
                if (ch != null) {
                    ch.setPresetNumber(0, false)
                    ch.setVolume(1f)
                    ch.setPan(0.5f)
                } else {
                    sf.setBankPreset(channel, 0, 0)
                }
                soundFont = sf
            }
            if (track == null) {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(sampleRate / 5) // ~200ms，减少断续
                val t = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                track = t
                t.play()
            }
            ready.set(true)
            if (!running.get()) {
                running.set(true)
                thread = Thread({ renderLoop() }, "sf2-piano").also {
                    it.isDaemon = true
                    it.priority = Thread.MAX_PRIORITY
                    it.start()
                }
            }
            Log.i(TAG, "SoundFont piano ready (mono)")
        }.onFailure { e ->
            Log.e(TAG, "SoundFont init failed", e)
            ready.set(false)
        }
    }

    fun noteOn(midi: Int, velocity: Int = 80) {
        ensureStarted()
        val sf = soundFont ?: return
        // 略抬力度，避免 SF2 钢琴听起来发闷
        val vel = (velocity.coerceIn(1, 127) / 127f).coerceIn(0.15f, 1f)
        synchronized(sf) {
            sf.noteOn(channel, midi.coerceIn(21, 108), vel)
        }
    }

    fun noteOff(midi: Int) {
        val sf = soundFont ?: return
        synchronized(sf) {
            sf.noteOff(channel, midi.coerceIn(0, 127))
        }
    }

    fun allNotesOff() {
        val sf = soundFont ?: return
        synchronized(sf) {
            sf.noteOffAll()
        }
    }

    @Synchronized
    fun release() {
        running.set(false)
        ready.set(false)
        thread?.join(400)
        thread = null
        track?.let {
            runCatching {
                it.pause()
                it.flush()
                it.release()
            }
        }
        track = null
        soundFont = null
    }

    private fun ensureStarted() {
        if (!ready.get() || !running.get()) start()
    }

    private fun renderLoop() {
        val frames = 1024
        val shorts = ShortArray(frames)
        while (running.get()) {
            val sf = soundFont
            val t = track
            if (sf == null || t == null) {
                Thread.sleep(10)
                continue
            }
            val floats = synchronized(sf) {
                sf.renderFloat(frames, 1, false)
            }
            val n = minOf(floats.size, shorts.size)
            for (i in 0 until n) {
                // 软限幅，避免刺耳削波
                val sample = tanh(floats[i].toDouble() * 1.6).toFloat()
                shorts[i] = (sample * 30000f).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            if (n < shorts.size) shorts.fill(0, n, shorts.size)
            t.write(shorts, 0, shorts.size)
        }
    }

    companion object {
        private const val TAG = "MidiTonePlayer"
        const val SF2_NAME = "TimGM6mb.sf2"
    }
}
