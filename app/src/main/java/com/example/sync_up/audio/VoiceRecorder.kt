package com.example.sync_up.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Manages one foreground microphone recording and its temporary output file. */
internal class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    val isRecording: Boolean
        get() = recorder != null

    fun start(onTimeLimit: () -> Unit) {
        check(recorder == null) { "A recording is already in progress" }
        val file = File.createTempFile("voice_", ".m4a", context.cacheDir)
        var instance: MediaRecorder? = null
        try {
            instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            instance.setAudioSource(MediaRecorder.AudioSource.MIC)
            instance.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            instance.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            instance.setMaxDuration(MAX_DURATION_MS)
            instance.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    onTimeLimit()
                }
            }
            instance.setOutputFile(file.absolutePath)
            instance.prepare()
            instance.start()
            output = file
            recorder = instance
        } catch (error: Exception) {
            instance?.release()
            file.delete()
            throw error
        }
    }

    /** Returns a valid recording, or null when it ended before audio was captured. */
    fun stop(): File? {
        val instance = recorder ?: return null
        val file = output
        recorder = null
        output = null
        return try {
            instance.stop()
            file
        } catch (_: RuntimeException) {
            file?.delete()
            null
        } finally {
            instance.release()
        }
    }

    fun cancel() {
        stop()?.delete()
    }

    private companion object {
        const val MAX_DURATION_MS = 10_000
    }
}
