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
                    // Можна додати відображення прогресу завантаження
                }
                
                override fun onDownloadComplete() {
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        whisperModel.loadModel()
                    }
                }
                
                override fun onDownloadFailed(error: String) {
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, R.string.download_failed, Toast.LENGTH_LONG).show()
                    }
                }
            })
        } else {
            whisperModel.loadModel()
        }
    }
    
    private fun startRecording() {
        binding.transcriptionText.text.clear()
        binding.copyButton.visibility = View.GONE
        binding.waveformAnimation.visibility = View.VISIBLE
        
        audioFile = File(cacheDir, "recording.pcm")
        audioRecorder.startRecording(audioFile!!)
    }
    
    private fun stopRecordingAndTranscribe() {
        binding.waveformAnimation.visibility = View.INVISIBLE
        val recordedFile = audioRecorder.stopRecording()
        
        if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
            binding.progressBar.visibility = View.VISIBLE
            Toast.makeText(this, R.string.processing, Toast.LENGTH_SHORT).show()
            
            executorService.execute {
                val transcription = whisperModel.transcribeAudio(recordedFile.absolutePath)
                
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.transcriptionText.setText(transcription)
                    binding.copyButton.visibility = View.VISIBLE
                }
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Дозвіл надано
            } else {
                Toast.makeText(this, R.string.permission_required, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        whisperModel.unloadModel()
        executorService.shutdown()
    }
}
