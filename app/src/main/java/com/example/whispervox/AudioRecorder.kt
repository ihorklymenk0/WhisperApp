package com.example.whispervox

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AudioRecorder {
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var outputFile: File? = null

    fun startRecording(outputFile: File) {
        if (isRecording) {
            return
        }

        try {
            this.outputFile = outputFile
            
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            recordingThread = Thread {
                writeAudioDataToFile(bufferSize)
            }
            recordingThread?.start()
            
            Log.d(TAG, "Started recording to ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) {
            return outputFile
        }

        try {
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            recordingThread?.join()
            recordingThread = null
            
            Log.d(TAG, "Stopped recording")
            return outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            return null
        }
    }

    private fun writeAudioDataToFile(bufferSize: Int) {
        val data = ByteArray(bufferSize)
        var read: Int
        
        try {
            FileOutputStream(outputFile).use { fos ->
                while (isRecording) {
                    read = audioRecord?.read(data, 0, bufferSize) ?: -1
                    
                    if (read > 0) {
                        fos.write(data, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing audio data", e)
        }
    }
}