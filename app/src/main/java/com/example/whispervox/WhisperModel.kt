package com.example.whispervox

import android.content.Context
import android.util.Log
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.FFmpeg
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
        private const val VOCAB_SIZE = 51865