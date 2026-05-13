package com.example.sleppify

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioProcessor that downmixes stereo audio to mono when enabled.
 * Can be toggled at runtime via the companion [enabled] flag.
 * The change takes effect on the next call to [flush].
 */
@UnstableApi
class MonoAudioProcessor : BaseAudioProcessor() {

    companion object {
        /** Global toggle — when true, stereo audio is mixed to mono. */
        @JvmStatic
        @Volatile
        var enabled: Boolean = false
    }

    private var active: Boolean = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        active = enabled && inputAudioFormat.channelCount == 2 && inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT
        return if (active) {
            AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 1, inputAudioFormat.encoding)
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active) return
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Each stereo frame = 4 bytes (2 × 16-bit), output mono frame = 2 bytes
        val frameCount = remaining / 4
        val output = replaceOutputBuffer(frameCount * 2)

        val input = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            val left = input.short.toInt()
            val right = input.short.toInt()
            val mono = ((left + right) / 2).toShort()
            output.putShort(mono)
        }
        output.flip()
    }
}
