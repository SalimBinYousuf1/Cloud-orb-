package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.net.Uri
import android.opengl.GLSurfaceView
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.PureWhite
import com.example.ui.theme.StatusPromptBg
import com.example.ui.theme.StatusPromptHoverBg
import com.example.ui.theme.StatusPromptText

@Composable
fun CloudOrbScreen(
    viewModel: CloudOrbViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val micStatus by viewModel.status.collectAsStateWithLifecycle()

    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    fun checkHasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val shouldShowRationale = if (activity != null) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.RECORD_AUDIO
            )
        } else {
            false
        }
        viewModel.onPermissionResult(isGranted, shouldShowRationale)
    }

    val openSettingsAction = {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    val onActivateClicked = {
        viewModel.onPromptOrOrbClicked(
            hasPermission = checkHasPermission(),
            requestPermissionLauncher = {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            openSettings = openSettingsAction
        )
    }

    // Lifecycle observer to pause/resume GL surface and audio recording
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    glSurfaceViewRef?.onResume()
                    viewModel.onResume(checkHasPermission())
                }
                Lifecycle.Event.ON_PAUSE -> {
                    glSurfaceViewRef?.onPause()
                    viewModel.onPause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceViewRef?.onPause()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PureWhite),
        contentAlignment = Alignment.Center
    ) {
        val maxAvailable = minOf(maxWidth * 0.85f, maxHeight * 0.55f)
        val orbSize = if (maxAvailable < 280.dp) maxAvailable else 280.dp

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        ) {
            // Status prompt pill
            StatusPromptPill(
                status = micStatus,
                onClick = onActivateClicked
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Exact 280dp Circular Cloud Orb with 0 12px 35px rgba(43, 122, 246, 0.22) shadow
            val orbAccessibilityDesc = stringResource(R.string.orb_content_description)

            Box(
                modifier = Modifier
                    .size(orbSize)
                    .drawBehind {
                        // Render exact CSS box-shadow: 0 12px 35px rgba(43, 122, 246, 0.22)
                        val shadowPaint = Paint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.argb(
                                (0.22f * 255).toInt(),
                                43,
                                122,
                                246
                            )
                            maskFilter = BlurMaskFilter(
                                35.dp.toPx(),
                                BlurMaskFilter.Blur.NORMAL
                            )
                        }
                        val radiusPx = size.minDimension / 2f
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawCircle(
                                size.width / 2f,
                                size.height / 2f + 12.dp.toPx(),
                                radiusPx,
                                shadowPaint
                            )
                        }
                    }
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onActivateClicked
                    )
                    .testTag("cloud_orb")
                    .semantics {
                        contentDescription = orbAccessibilityDesc
                    },
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        GLSurfaceView(ctx).apply {
                            setEGLContextClientVersion(2)
                            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                            holder.setFormat(PixelFormat.TRANSLUCENT)
                            setZOrderMediaOverlay(true)
                            setRenderer(viewModel.renderer)
                            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            glSurfaceViewRef = this
                        }
                    },
                    update = { view ->
                        glSurfaceViewRef = view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun StatusPromptPill(
    status: MicStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedBg by animateColorAsState(
        targetValue = if (isPressed) StatusPromptHoverBg else StatusPromptBg,
        animationSpec = tween(durationMillis = 200),
        label = "pill_background"
    )

    val promptText = when (status) {
        MicStatus.Idle -> stringResource(R.string.prompt_idle)
        MicStatus.Requesting -> stringResource(R.string.prompt_requesting)
        MicStatus.Connected -> stringResource(R.string.prompt_connected)
        MicStatus.Denied -> stringResource(R.string.prompt_denied)
        MicStatus.PermanentlyDenied -> stringResource(R.string.prompt_permanently_denied)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(animatedBg)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("mic_prompt"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = promptText,
            color = StatusPromptText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
            textAlign = TextAlign.Center
        )
    }
}
