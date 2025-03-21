package com.example.democameraapp2.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaActionSound
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.core.app.ActivityCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor

class CameraViewModel : ViewModel () {

    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> =  _cameraState.asStateFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest
    private var cameraControl: CameraControl? = null

    private val cameraPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequest.update { newSurfaceRequest }
        }
    }

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    val mediaActionSound = MediaActionSound()

    suspend fun bindToCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        cameraSelector: CameraSelector,
        useCase: Int
    ) {
        val processCameraProvider = ProcessCameraProvider.awaitInstance(context)
        if (useCase == CameraController.IMAGE_CAPTURE){
            imageCapture = ImageCapture.Builder().build()
            val camera = processCameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, cameraPreviewUseCase, this.imageCapture
            )
            cameraControl = camera.cameraControl
        } else{
            val recorder = Recorder
                .Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            val camera = processCameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, cameraPreviewUseCase, this.videoCapture
            )
            cameraControl = camera.cameraControl
        }

        try { awaitCancellation() } finally {
            processCameraProvider.unbindAll()
            cameraControl = null
        }
    }

    fun zoom(zoomRatio: Float) {
        cameraControl?.setZoomRatio( zoomRatio )
    }

    fun changeCameraSelector() {
        _cameraState.update { cameraState ->
            if ( cameraState.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA ){
                cameraState.copy(
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                )
            } else{
                cameraState.copy(
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                )
            }
        }
    }

    fun changeTorch() {
        _cameraState.update { cameraState ->
            cameraState.copy(
                isTorch = !cameraState.isTorch
            )
        }
        cameraControl?.enableTorch(_cameraState.value.isTorch)
    }

    fun changeFlash() {
        _cameraState.update { cameraState ->
            cameraState.copy(
                isFlash = if ( cameraState.isFlash == ImageCapture.FLASH_MODE_OFF){
                    ImageCapture.FLASH_MODE_ON
                }else {
                    ImageCapture.FLASH_MODE_OFF
                }
            )
        }
        Log.d("Flash", "Flashの値${_cameraState.value.isFlash}")
    }

    fun clickedVideoCaptureButton() {
        _cameraState.update { cameraState ->
            cameraState.copy(
                useCase = CameraController.VIDEO_CAPTURE
            )
        }
    }

    fun clickedImageCaptureButton() {
        _cameraState.update { cameraState ->
            cameraState.copy(
                useCase = CameraController.IMAGE_CAPTURE
            )
        }
    }

    fun takePhoto(
        context: Context,
        isFlash: Int,
        executor: Executor,
    ) {
        val imageCapture = imageCapture ?:return
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.JAPAN)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DemoCameraApp2")
            }
        }
        val saveCollection = if (Build.VERSION_CODES.Q <= Build.VERSION.SDK_INT) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                context.contentResolver,
                saveCollection,
                contentValues
            )
            .build()

        imageCapture.flashMode = isFlash
        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
                    _cameraState.update { cameraState ->
                        cameraState.copy(
                            previewPhotoUri = output.savedUri
                        )
                    }
                }
                override fun onError(e: ImageCaptureException) {
                    Log.e("DemoCameraApp2", "Photo capture failed: ${e.message}",e)
                }
            }
        )
    }

    fun captureVideo(
        context: Context,
        executor: Executor,
    ){
        val videoCapture = videoCapture ?:return
        val curRecording = recording
        if (curRecording != null) {
            curRecording.stop()
            recording = null
            return
        }
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.JAPAN)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DemoCamera2-Video")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        val listener = Consumer<VideoRecordEvent>{ event->
            when(event){
                is VideoRecordEvent.Start -> {
                    // 録画開始時の処理
                    mediaActionSound.play(MediaActionSound.START_VIDEO_RECORDING)
                    _cameraState.update { cameraState ->
                        cameraState.copy(
                            isRecording = true
                        )
                    }
                }
                is VideoRecordEvent.Finalize -> {
                    mediaActionSound.play(MediaActionSound.STOP_VIDEO_RECORDING)
                    _cameraState.update { cameraState ->
                        cameraState.copy(
                            isRecording = false
                        )
                    }

                    // 録画終了時の処理
                    if (!event.hasError()){
                        Log.d("OK", "Video capture succeeded: " +
                                "${event.outputResults.outputUri}")
                        _cameraState.update { cameraState ->
                            cameraState.copy(
                                previewVideoBitmap = getVideoFrame(context = context, videoUri = event.outputResults.outputUri)
                            )
                        }
                    }else{
                        recording?.close()
                        recording = null
                    }
                }
            }
        }
        recording = videoCapture.output
            .prepareRecording(context, mediaStoreOutputOptions)
            .apply {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ){
                    withAudioEnabled()
                }
            }
            .start(executor,listener)
    }

    fun getVideoFrame(context: Context, videoUri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            retriever.getFrameAtTime(0)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            retriever.release()
        }
    }
}

data class CameraState(
    val cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    val isFlash: Int = ImageCapture.FLASH_MODE_OFF,
    val isRecording: Boolean = false,
    val isTorch: Boolean = false,
    val useCase: Int = CameraController.IMAGE_CAPTURE,
    val previewPhotoUri: Uri? = null,
    val previewVideoBitmap: Bitmap? = null,
)