package com.qadaa.app

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel

fun lockAuthenticators(): Int =
    if (Build.VERSION.SDK_INT >= 30) {
        BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    }

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)
    private var lastStop = 0L
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.init(this)
        locked = Settings.lockOn && savedInstanceState == null
        Reminders.ensureChannel(this)
        Reminders.scheduleAll(this)
        BackupWorker.schedule(this)
        setContent {
            QadaaTheme {
                val vm: Vm = viewModel()
                App(vm, locked) { authenticate() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (Settings.lockOn && !locked && lastStop != 0L &&
            SystemClock.elapsedRealtime() - lastStop > 15_000
        ) locked = true
        if (locked) authenticate()
    }

    override fun onStop() {
        super.onStop()
        lastStop = SystemClock.elapsedRealtime()
    }

    private fun authenticate() {
        if (prompting) return
        val auth = lockAuthenticators()
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            locked = false
            return
        }
        prompting = true
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    locked = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (Settings.arabic) "فتح قضاء" else "Unlock Qadaa")
            .setAllowedAuthenticators(auth)
        if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText(if (Settings.arabic) "إلغاء" else "Cancel")
        prompt.authenticate(info.build())
    }
}
