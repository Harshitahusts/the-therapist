package app.haven.companion.voice

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import app.haven.companion.core.audio.SampleBank
import java.nio.ByteOrder
import kotlin.concurrent.thread

/** Decodes the bundled sound recordings (OGG) into the [SampleBank], off the main thread. */
object AssetAudio {
    private const val TAG = "AssetAudio"

    fun preload(context: Context) {
        thread(name = "haven-sound-load", isDaemon = true) {
            runCatching {
                SampleBank.birdsSong = decodeMono(context, "sounds/birds_song.ogg")
                SampleBank.birdsAmbience = decodeMono(context, "sounds/birds_ambience.ogg")
            }.onFailure { Log.w(TAG, "could not load bird recordings; using the synthesised chorus", it) }
        }
    }

    /** Decodes an asset to mono floats in [-1, 1]. Assets are prepared at 24 kHz mono. */
    fun decodeMono(context: Context, path: String): FloatArray {
        val extractor = MediaExtractor()
        context.assets.openFd(path).use { afd -> extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
        val format = extractor.getTrackFormat(0)
        extractor.selectTrack(0)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        val out = FloatArrayBuilder()
        val info = MediaCodec.BufferInfo()
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    o >= 0 -> {
                        val buf = codec.getOutputBuffer(o)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val shorts = buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        while (shorts.remaining() >= channels) {
                            var sum = 0f
                            repeat(channels) { sum += shorts.get() / 32768f }
                            out.add(sum / channels)
                        }
                        codec.releaseOutputBuffer(o, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        return out.toArray()
    }

    private class FloatArrayBuilder {
        private var data = FloatArray(1 shl 16)
        private var size = 0
        fun add(v: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }
}
