package com.dancaai.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.dancaai.app.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), PoseLandmarkerHelper.LandmarkerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private val stepCounter = StepCounter()
    private var isFrontCamera = false

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "Permissão de câmera necessária", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        stepCounter.onStageChanged = { stage, count ->
            runOnUiThread {
                binding.tvCounter.text = count.toString()
                binding.tvStage.text = stage
            }
        }
        stepCounter.onThreeRepsCompleted = {
            runOnUiThread {
                binding.tvMessage.text = "3 REPS CONCLUÍDAS!"
            }
        }

        // Botão alternar câmera
        binding.fabSwitchCamera.setOnClickListener {
            isFrontCamera = !isFrontCamera
            startCamera()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.fabToggleGuide.setOnClickListener {
            binding.cameraGuideView.toggleVisibility()
        }
    }

    private fun startCamera() {
        // Fecha o landmarker anterior antes de recriar
        if (::poseLandmarkerHelper.isInitialized) {
            poseLandmarkerHelper.clearPoseLandmarker()
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            // Só cria o novo landmarker depois de unbindAll
            poseLandmarkerHelper = PoseLandmarkerHelper(context = this, listener = this)

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        poseLandmarkerHelper.detectLiveStream(
                            imageProxy = imageProxy,
                            isFrontCamera = isFrontCamera
                        )
                    }
                }

            val cameraSelector = if (isFrontCamera)
                CameraSelector.DEFAULT_FRONT_CAMERA
            else
                CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        }, ContextCompat.getMainExecutor(this))
    }
    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            binding.overlayView.setResults(
                poseLandmarkerResults = resultBundle.results,
                imageWidth = resultBundle.inputImageWidth,
                imageHeight = resultBundle.inputImageHeight,
                isFrontCamera = isFrontCamera
            )
            stepCounter.process(resultBundle.results)

            // Calcula ângulos articulares
            if (resultBundle.results.landmarks().isNotEmpty()) {
                val landmarks = resultBundle.results.landmarks()[0]
                val angles = AngleCalculator.compute(landmarks)
                if (angles != null) {
                    binding.overlayView.updateAngles(angles)
                }
                val postureResult = PostureValidator.validate(landmarks)
                binding.overlayView.updatePosture(postureResult)

                val guideResult = CameraGuide.evaluate(landmarks)
                binding.cameraGuideView.update(guideResult)
            }



        }
    }
    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this, "Erro: $error", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        poseLandmarkerHelper.clearPoseLandmarker()
        cameraExecutor.shutdown()
    }
}