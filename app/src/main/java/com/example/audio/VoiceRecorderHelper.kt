package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class VoiceRecorderHelper(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordingStartTimeMs: Long = 0L
    var isRecording: Boolean = false
        private set

    @Suppress("DEPRECATION")
    fun startRecording(): File? {
        try {
            val audioDir = File(context.cacheDir, "audio_warnings").apply { mkdirs() }
            val outputFile = File(audioDir, "warning_${System.currentTimeMillis()}.m4a")
            currentOutputFile = outputFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            recordingStartTimeMs = System.currentTimeMillis()
            mediaRecorder = recorder
            isRecording = true
            return outputFile
        } catch (e: Exception) {
            Log.e("VoiceRecorderHelper", "Failed to start recording: ${e.message}", e)
            releaseRecorder()
            return null
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        return try {
            val elapsed = System.currentTimeMillis() - recordingStartTimeMs
            if (elapsed < 350) {
                // Wait briefly if released almost instantaneously to prevent MediaRecorder stop failed (-1007)
                try {
                    Thread.sleep(350 - elapsed)
                } catch (_: InterruptedException) {}
            }
            mediaRecorder?.stop()
            val file = currentOutputFile
            releaseRecorder()
            file
        } catch (e: Exception) {
            Log.e("VoiceRecorderHelper", "Caught and handled audio recording stop exception: ${e.message}")
            releaseRecorder()
            null
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {}
        releaseRecorder()
        currentOutputFile?.delete()
        currentOutputFile = null
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null
        isRecording = false
    }
}
