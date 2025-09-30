// file: core/src/main/java/com/dsatm/guardianai/security/SecurityManager.kt

package com.dsatm.guardianai.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.MasterKey
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityManager(private val context: Context) {

    private val KEY_ALIAS = "my_app_key"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val TRANSFORMATION = "AES/GCM/NoPadding"
    val IV_SIZE = 12 // Made public to be used by other modules
    private val TAG = "SecurityManager"

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private fun getSecretKey(): SecretKey {
        return try {
            val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            existingKey?.secretKey ?: createKey()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun createKey(): SecretKey {
        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        ).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true)
                    .build()
            )
        }.generateKey()
    }

    fun isBiometricReady(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows a biometric prompt for simple authentication without a cryptographic object.
     * This is an excellent use case for app startup or simple access control.
     *
     * @param activity The host FragmentActivity.
     * @param onSuccess Callback for successful authentication.
     * @param onFailure Callback for authentication errors.
     */
    fun authenticateForAppAccess(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (errorMessage: CharSequence) -> Unit
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("App Access")
            .setSubtitle("Authenticate to open the app")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Use Password") // <<< ADDED THIS LINE
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onFailure(errString)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailure("Authentication failed. Please try again.")
                }
            }
        )
        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Shows a biometric prompt to authenticate the user for a cryptographic operation.
     * The Cipher is passed to the biometric prompt, which unlocks it upon successful authentication.
     *
     * @param activity The host FragmentActivity.
     * @param data The ByteArray to be encrypted or decrypted.
     * @param mode The cryptographic operation mode (Cipher.ENCRYPT_MODE or Cipher.DECRYPT_MODE).
     * @param iv The Initialization Vector (IV) required for decryption. Null for encryption.
     * @param onSuccess Callback for a successful authentication and crypto operation.
     * @param onFailure Callback for authentication errors.
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        data: ByteArray,
        mode: Int,
        iv: ByteArray?,
        onSuccess: (resultData: ByteArray, newIv: ByteArray?) -> Unit,
        onFailure: (errorMessage: CharSequence) -> Unit
    ) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Authentication")
            .setSubtitle("Authenticate to access secure data")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel") // <<< ADDED THIS LINE
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onFailure(errString)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val cryptoObject = result.cryptoObject
                    val cipher = cryptoObject?.cipher

                    if (cipher != null) {
                        try {
                            val resultData = cipher.doFinal(data)
                            val newIv = if (mode == Cipher.ENCRYPT_MODE) cipher.iv else iv
                            onSuccess(resultData, newIv)
                        } catch (e: Exception) {
                            onFailure("Error performing crypto operation: ${e.message}")
                        }
                    } else {
                        onFailure("CryptoObject or Cipher is null")
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailure("Authentication failed. Please try again.")
                }
            }
        )

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            try {
                if (mode == Cipher.ENCRYPT_MODE) {
                    init(Cipher.ENCRYPT_MODE, getSecretKey())
                } else {
                    if (iv == null || iv.size != IV_SIZE) {
                        throw InvalidAlgorithmParameterException("Invalid IV provided for decryption.")
                    }
                    init(Cipher.DECRYPT_MODE, getSecretKey(), GCMParameterSpec(128, iv))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }
}