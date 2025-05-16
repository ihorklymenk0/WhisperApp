package com.example.whispervox

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.whispervox.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val audioRecorder = AudioRecorder()
    private lateinit var whisperModel: WhisperModel
    private var audioFile: File? = null
    private val executorService = Executors.newSingleThreadExecutor()
    
    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        whisperModel = WhisperModel(applicationContext)
        
        setupMicButton()
        setupCopyButton()
        checkOrRequestPermissions()
        checkAndDownloadModel()
    }
    
    private fun setupMicButton() {
        binding.micButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (checkOrRequestPermissions()) {
                        startRecording()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecordingAndTranscribe()
                }
            }
            true
        }
    }
    
    private fun setupCopyButton() {
        binding.copyButton.setOnClickListener {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("transcribed_text", binding.transcriptionText.text.toString())
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(this, R.string.text_copied, Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkOrRequestPermissions(): Boolean {
        val permission = Manifest.permission.RECORD_AUDIO
        
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_RECORD_AUDIO_PERMISSION)
            return false
        }
        
        return true
    }
    
    private fun checkAndDownloadModel() {
        if (!whisperModel.isModelDownloaded()) {
            whisperModel.downloadModel(object : WhisperModel.ModelDownloadListener {
                override fun onDownloadStart() {
                    runOnUiThread {
                        binding.progressBar.visibility = View.VISIBLE
                        Toast.makeText(this@MainActivity, R.string.downloading_model, Toast.LENGTH_SHORT).show()
                    }
                }
                
                override fun onDownloadProgress(progress: Int) {