package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.audio.AudioReactiveEngine
import com.example.graphics.CloudOrbRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface MicStatus {
    data object Idle : MicStatus
    data object Requesting : MicStatus
    data object Connected : MicStatus
    data object Denied : MicStatus
    data object PermanentlyDenied : MicStatus
}

class CloudOrbViewModel(application: Application) : AndroidViewModel(application) {

    val renderer = CloudOrbRenderer()

    private val _status = MutableStateFlow<MicStatus>(MicStatus.Idle)
    val status: StateFlow<MicStatus> = _status.asStateFlow()

    private val _liveAudioBoost = MutableStateFlow(0.0f)
    val liveAudioBoost: StateFlow<Float> = _liveAudioBoost.asStateFlow()

    private val audioEngine = AudioReactiveEngine(application.applicationContext) { boost ->
        renderer.setAudioBoost(boost)
        _liveAudioBoost.value = boost
    }

    fun onPromptOrOrbClicked(hasPermission: Boolean, requestPermissionLauncher: () -> Unit, openSettings: () -> Unit) {
        when (_status.value) {
            MicStatus.PermanentlyDenied -> {
                openSettings()
            }
            MicStatus.Connected -> {
                // If already connected, tapping confirms connection or toggles
                if (!audioEngine.isActive) {
                    val started = audioEngine.start()
                    if (started) {
                        _status.value = MicStatus.Connected
                    }
                }
            }
            else -> {
                if (hasPermission) {
                    val started = audioEngine.start()
                    _status.value = if (started) MicStatus.Connected else MicStatus.Denied
                } else {
                    _status.value = MicStatus.Requesting
                    requestPermissionLauncher()
                }
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean, shouldShowRationale: Boolean) {
        if (isGranted) {
            val started = audioEngine.start()
            _status.value = if (started) MicStatus.Connected else MicStatus.Denied
        } else {
            _status.value = if (shouldShowRationale) {
                MicStatus.Denied
            } else {
                MicStatus.PermanentlyDenied
            }
        }
    }

    fun onPause() {
        if (_status.value == MicStatus.Connected) {
            audioEngine.pause()
        }
    }

    fun onResume(hasPermission: Boolean) {
        if (_status.value == MicStatus.Connected) {
            if (hasPermission) {
                audioEngine.resume()
            } else {
                _status.value = MicStatus.Idle
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stop()
    }
}
