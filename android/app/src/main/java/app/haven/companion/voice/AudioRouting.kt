package app.haven.companion.voice

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/** Puts the phone in "voice call" mode on the loudspeaker for the conversation, and restores it after. */
class AudioRouting(context: Context) {
    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode = AudioManager.MODE_NORMAL
    private var focus: AudioFocusRequest? = null

    fun start() {
        previousMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).build().also {
            am.requestAudioFocus(it)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Prefer a connected headset; otherwise the loudspeaker.
            val devices = am.availableCommunicationDevices
            val preferred = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (preferred != null) am.setCommunicationDevice(preferred)
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = !am.isWiredHeadsetOn
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
        }
        focus?.let { am.abandonAudioFocusRequest(it) }
        focus = null
        am.mode = previousMode
    }
}
