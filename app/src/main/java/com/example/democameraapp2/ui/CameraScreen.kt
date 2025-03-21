package com.example.democameraapp2.ui

import android.os.Build
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.view.CameraController
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
){
    //権限関係
    val permissions = if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.P){
        listOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.RECORD_AUDIO,
        )
    }else{
        listOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
        )
    }

    val permissionState = rememberMultiplePermissionsState(permissions = permissions)

    if (permissionState.allPermissionsGranted){
        CameraPreviewContent()
    }else{
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ){
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ){
                val text = if (permissionState.shouldShowRationale) {
                    "このアプリを使用するためにはカメラ機能が必須です\n" +
                            "下のボタンを押して許可を下さい"
                } else {
                    "はじめまして！\n" +
                            "このアプリを使用するためにはカメラ機能が必須です\n" +
                            "下のボタンを押して許可を下さい"
                }
                Text(
                    text = text,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height( 20.dp ))
                Button(
                    modifier = modifier,
                    onClick = { permissionState.launchMultiplePermissionRequest() }
                ) {
                    Text("権限をリクエストする")
                }
            }
        }
    }
}

@Composable
fun CameraPreviewContent(
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = viewModel(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
){
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val cameraState by viewModel.cameraState.collectAsState()
    val context = LocalContext.current
    var zoomRatio by remember { mutableStateOf(1f) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(lifecycleOwner, cameraState.cameraSelector, cameraState.useCase) {
        viewModel.bindToCamera(
            context.applicationContext,
            lifecycleOwner,
            cameraState.cameraSelector,
            cameraState.useCase
        )
    }

    DisposableEffect(cameraExecutor) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    surfaceRequest?.let { request ->
        Scaffold(
            topBar = {
                if (cameraState.useCase == CameraController.IMAGE_CAPTURE){
                    CameraTopBar(
                        modifier = modifier,
                        isFlash = cameraState.isFlash,
                        isTorch = cameraState.isTorch,
                        flashButtonClicked = { viewModel.changeFlash() },
                        torchButtonClicked = { viewModel.changeTorch() }
                    )
                }
            },
            bottomBar = {
                CameraBottomBar(
                    modifier = modifier,
                    useCase = cameraState.useCase,
                    isRecording = cameraState.isRecording,
                    previewPhotoUri = cameraState.previewPhotoUri,
                    previewVideoBitmap = cameraState.previewVideoBitmap,
                    takePhoto = { viewModel.takePhoto( context, cameraState.isFlash, cameraExecutor,) },
                    captureVideo = { viewModel.captureVideo(context, cameraExecutor) },
                    cameraSelectorButtonClicked = { viewModel.changeCameraSelector()},
                    imageCaptureButtonClicked = { viewModel.clickedImageCaptureButton()},
                    videoCaptureButtonClicked = { viewModel.clickedVideoCaptureButton() }
                )
            }
        ){
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = modifier
                    .padding(it)
                    .pointerInput(Unit) {
                        detectTransformGestures{ _, _, zoom, _ ->
                            zoomRatio *= zoom
                            viewModel.zoom( zoomRatio )
                        }
                    }
            )
        }
    }
}