package com.mysql.app

import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.io.File
import kotlin.system.exitProcess

class MainActivity : FragmentActivity() {

    private lateinit var store: ServerStore
    private var unlocked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashFile = File(filesDir, "crash.txt")
        if (crashFile.exists()) {
            val text = crashFile.readText()
            setContent {
                MaterialTheme {
                    Column(
                        Modifier.fillMaxSize().statusBarsPadding()
                            .verticalScroll(rememberScrollState()).padding(12.dp)
                    ) {
                        Text("Error de la app (mándame captura):")
                        Text(text.take(2500), fontSize = 10.sp)
                        Button(onClick = { crashFile.delete(); recreate() }) { Text("Reintentar") }
                    }
                }
            }
            return
        }
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try { crashFile.writeText(Log.getStackTraceString(e)) } catch (_: Throwable) {}
            exitProcess(10)
        }

        enableEdgeToEdge()
        store = ServerStore(this)

        if (store.lockEnabled) askAuth() else unlocked = true

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    if (!unlocked) {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("MySQL bloqueado")
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { askAuth() }) { Text("Desbloquear") }
                        }
                    } else {
                        var current by remember { mutableStateOf<Server?>(null) }
                        val c = current
                        if (c == null) {
                            ServerListScreen(store) { current = it }
                        } else {
                            BrowserScreen(c) { current = null }
                        }
                    }
                }
            }
        }
    }

    private fun askAuth() {
        val auth = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(auth) != BiometricManager.BIOMETRIC_SUCCESS) {
            unlocked = true
            return
        }
        val prompt = BiometricPrompt(
            this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                }
            }
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("MySQL")
                .setSubtitle("Verifica tu identidad")
                .setAllowedAuthenticators(auth)
                .build()
        )
    }
}
