package com.example.whispervox

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.microsoft.onnxruntime.OnnxTensor
import com.microsoft.onnxruntime.OrtEnvironment
import com.microsoft.onnxruntime.OrtSession
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.FloatBuffer
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.math.min

class WhisperModel(private val context: Context) {
    companion object {
        private const val TAG = "WhisperModel"
        private const val MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin"
        private const val MODEL_FILE_NAME = "whisper-base-ua.onnx"
        private const val SAMPLE_RATE = 16000
        private const val MAX_AUDIO_SEC = 30
        private const val VOCAB_SIZE = 51865 // Розмір словника Whisper (може відрізнятись для різних моделей)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var ortEnv: OrtEnvironment? = null
    private var session: OrtSession? = null

    interface ModelDownloadListener {
        fun onDownloadStart()
        fun onDownloadProgress(progress: Int)
        fun onDownloadComplete()
        fun onDownloadFailed(error: String)
    }

    fun isModelDownloaded(): Boolean {
        val modelFile = File(context.filesDir, MODEL_FILE_NAME)
        return modelFile.exists() && modelFile.length() > 0
    }

    fun downloadModel(listener: ModelDownloadListener) {
        Thread {
            try {
                listener.onDownloadStart()
                
                val modelFile = File(context.filesDir, MODEL_FILE_NAME)
                if (!modelFile.exists()) {
                    val request = Request.Builder()
                        .url(MODEL_URL)
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("Failed to download model: ${response.code}")
                        }

                        val contentLength = response.body?.contentLength() ?: -1
                        var bytesRead = 0L
                        
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(modelFile).use { output ->
                                val buffer = ByteArray(8192)
                                var bytes: Int
                                
                                while (input.read(buffer).also { bytes = it } != -1) {
                                    output.write(buffer, 0, bytes)
                                    bytesRead += bytes
                                    
                                    if (contentLength > 0) {
                                        val progress = (bytesRead * 100 / contentLength).toInt()
                                        listener.onDownloadProgress(progress)
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Конвертація моделі з формату GGML у формат ONNX - на практиці це складніше
                // Тут ми припускаємо, що ця частина вже виконана або не потрібна
                
                listener.onDownloadComplete()
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading model", e)
                listener.onDownloadFailed(e.message ?: "Невідома помилка")
            }
        }.start()
    }

    fun loadModel() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelFile = File(context.filesDir, MODEL_FILE_NAME)
            
            val sessionOptions = OrtSession.SessionOptions()
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            
            session = ortEnv?.createSession(modelFile.absolutePath, sessionOptions)
            Log.d(TAG, "Model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
        }
    }

    fun unloadModel() {
        try {
            session?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        }
    }

    fun transcribeAudio(audioFilePath: String): String {
        try {
            // Конвертація аудіо у WAV формат з потрібною частотою дискретизації
            val wavFile = File(context.cacheDir, "temp_audio.wav")
            val ffmpegCommand = "-i $audioFilePath -ar $SAMPLE_RATE -ac 1 -c:a pcm_s16le ${wavFile.absolutePath}"
            
            val session = FFmpegKit.execute(ffmpegCommand)

            if (!ReturnCode.isSuccess(session.returnCode)) {
                Log.e(TAG, "FFmpeg process failed with rc: ${session.returnCode}")
                return "Помилка конвертації аудіо"
            }
            
            // Завантаження і обробка аудіо
            val audioData = loadAudioFile(wavFile.absolutePath)
            
            // Підготовка вхідних даних для моделі
            val dims = longArrayOf(1, audioData.size.toLong())
            val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(audioData), dims)
            
            // Створення вхідних даних для моделі
            val inputs = mapOf("input" to inputTensor)
            
            // Виконання інференсу
            val output = session?.run(inputs)
            
            // Обробка результатів
            val logits = output?.get("output")?.value as Array<*>
            
            // Декодування результатів у текст (спрощено)
            // Реальна реалізація потребує tokenizer для конвертації з індексів у текст
            val text = decodeOutput(logits)
            
            // Очищення тимчасових файлів
            wavFile.delete()
            
            return text
        } catch (e: Exception) {
            Log.e(TAG, "Error during transcription", e)
            return "Помилка розпізнавання: ${e.message}"
        }
    }
    
    private fun loadAudioFile(filePath: String): FloatArray {
        // Зчитування WAV файлу та конвертація у масив float значень
        // Це спрощена реалізація
        val file = File(filePath)
        val bytes = file.readBytes()
        
        // Пропускаємо заголовок WAV (44 байти)
        val headerSize = 44
        val samples = min((bytes.size - headerSize) / 2, MAX_AUDIO_SEC * SAMPLE_RATE)
        val audioData = FloatArray(samples)
        
        for (i in 0 until samples) {
            val idx = headerSize + i * 2
            val sample = (bytes[idx + 1].toInt() shl 8) or (bytes[idx].toInt() and 0xFF)
            audioData[i] = if (sample < 32768) sample / 32768.0f else (sample - 65536) / 32768.0f
        }
        
        return audioData
    }
    
    private fun decodeOutput(logits: Array<*>): String {
        // Спрощена реалізація декодування виходу моделі
        // На практиці тут має бути використання tokenizer для перетворення індексів токенів у текст
        // Це лише заглушка для демонстрації
        return "Це результат транскрипції. Реальна реалізація декодування потребує використання токенайзера з моделі Whisper."
    }
}
