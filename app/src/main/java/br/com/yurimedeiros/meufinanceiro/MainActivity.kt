package br.com.yurimedeiros.meufinanceiro

import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import br.com.yurimedeiros.meufinanceiro.security.KeyVault
import br.com.yurimedeiros.meufinanceiro.security.UnlockOperation
import br.com.yurimedeiros.meufinanceiro.ui.FinanceApp
import br.com.yurimedeiros.meufinanceiro.ui.FinanceViewModel

class MainActivity : ComponentActivity() {
    private lateinit var model: FinanceViewModel
    private var operation: UnlockOperation? = null
    private var cancellation: CancellationSignal? = null
    private var autoPrompt = true
    private var deviceSecure by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        model = ViewModelProvider(this)[FinanceViewModel::class.java]
        setContent {
            FinanceApp(
                model = model,
                onUnlock = ::authenticate,
                onSecuritySettings = { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) },
                deviceSecure = deviceSecure,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        deviceSecure = getSystemService(KeyguardManager::class.java).isDeviceSecure
        model.onForeground()
        if (autoPrompt && !model.state.value.unlocked && !model.state.value.opening) authenticate()
    }

    override fun onStop() {
        if (!isChangingConfigurations) model.onBackground()
        super.onStop()
    }

    override fun onDestroy() {
        cancellation?.cancel()
        operation?.close()
        operation = null
        super.onDestroy()
    }

    private fun authenticate() {
        if (operation != null || model.state.value.opening || model.state.value.unlocked) return
        autoPrompt = false
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (getSystemService(BiometricManager::class.java).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            model.showError("Configure um PIN, senha ou biometria no Android para proteger seus dados.")
            return
        }
        val prepared = try {
            KeyVault(applicationContext).prepareUnlock()
        } catch (_: Exception) {
            model.showError("Não foi possível acessar a proteção dos seus dados. Seus arquivos foram preservados.")
            return
        }
        operation = prepared
        val signal = CancellationSignal()
        cancellation = signal
        val prompt = BiometricPrompt.Builder(this)
            .setTitle("Abrir Meu Financeiro")
            .setSubtitle("Confirme sua identidade para acessar seus dados")
            .setAllowedAuthenticators(authenticators)
            .build()
        try {
            prompt.authenticate(
                BiometricPrompt.CryptoObject(prepared.cipher),
                signal,
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        try {
                            if (!isDestroyed && operation === prepared) {
                                val cipher = checkNotNull(result.cryptoObject?.cipher)
                                model.openWith(prepared.finish(cipher))
                            }
                        } catch (_: Exception) {
                            if (!isDestroyed) model.showError("Não foi possível desbloquear seus dados. Tente novamente.")
                        } finally {
                            release(prepared)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        release(prepared)
                        if (!isDestroyed) {
                            model.showError("Seus dados continuam protegidos. Toque em Desbloquear para tentar novamente.")
                        }
                    }

                    override fun onAuthenticationFailed() {
                        // System prompt remains active; no credential or cipher is released.
                    }
                },
            )
        } catch (_: Exception) {
            release(prepared)
            model.showError("Não foi possível iniciar a autenticação. Tente novamente.")
        }
    }

    private fun release(prepared: UnlockOperation) {
        prepared.close()
        if (operation === prepared) {
            operation = null
            cancellation = null
        }
    }
}
