package br.com.yurimedeiros.meufinanceiro

import android.app.KeyguardManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.provider.Settings
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
    private var authorizingGoogle = false
    private var deviceSecure by mutableStateOf(false)

    private val googleAuthorization = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        authorizingGoogle = false
        if (result.resultCode != RESULT_OK) {
            model.showImportError("Autorização Google cancelada. Seus registros foram preservados.")
        } else try {
            val authorization = Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(checkNotNull(result.data))
            authorization.accessToken?.let(model::syncGmail) ?: model.showImportError("O Google não concedeu acesso de leitura ao Gmail.")
        } catch (_: Exception) { model.showImportError("Não foi possível autorizar o Gmail. Tente novamente.") }
    }

    private fun authorizeGmail() {
        if (!model.state.value.unlocked || model.state.value.syncing || authorizingGoogle) return
        authorizingGoogle = true
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/gmail.readonly"))).build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                if (!model.state.value.unlocked) { authorizingGoogle = false; return@addOnSuccessListener }
                if (result.hasResolution()) {
                    result.pendingIntent?.let { googleAuthorization.launch(IntentSenderRequest.Builder(it).build()) }
                } else {
                    authorizingGoogle = false
                    result.accessToken?.let(model::syncGmail) ?: model.showImportError("O Google não concedeu acesso de leitura ao Gmail.")
                }
            }
            .addOnFailureListener { authorizingGoogle = false; model.showImportError("Não foi possível autorizar o Gmail. Confira a configuração Google e tente novamente.") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        model = ViewModelProvider(this)[FinanceViewModel::class.java]
        setContent {
            FinanceApp(
                model = model,
                onUnlock = ::authenticate,
                onRefresh = ::authorizeGmail,
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
                        } catch (error: Exception) {
                            if (!isDestroyed) model.showError("Não foi possível desbloquear seus dados (código ${prepared.failureCode(error)}). Seus arquivos foram preservados. Tente novamente.")
                        } finally {
                            release(prepared)
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        release(prepared)
                        if (!isDestroyed) {
                            if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) {
                                model.showError("Não foi possível autenticar. Tente acessar novamente.")
                            }
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
