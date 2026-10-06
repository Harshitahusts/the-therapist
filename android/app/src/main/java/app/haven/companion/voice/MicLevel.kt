package app.haven.companion.voice

import kotlin.math.sqrt

/** RMS of 16-bit little-endian PCM, scaled to 0..1 for the orb animation. */
fun micLevel(pcm: ByteArray): Float {
    if (pcm.size < 2) return 0f
    var sum = 0.0
    var n = 0
    var i = 0
    while (i + 1 < pcm.size) {
        val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xff)).toShort().toDouble()
        sum += s * s
        n++
        i += 2
    }
    val rms = sqrt(sum / n) / 32768.0
    return (rms * 6).coerceIn(0.0, 1.0).toFloat()
}
