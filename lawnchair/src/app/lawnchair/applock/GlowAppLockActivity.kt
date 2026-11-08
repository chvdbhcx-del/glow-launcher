/*
 * Copyright 2026 Glow Launcher contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.applock

import android.app.Activity
import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Parcelable
import android.os.Process
import android.os.UserHandle
import android.widget.Toast
import com.android.launcher3.R

/**
 * Transparent activity that asks for biometrics or the device screen lock
 * before opening a locked app (see [GlowAppLock]).
 */
class GlowAppLockActivity : Activity() {

    private var cancellation: CancellationSignal? = null

    private val target: Intent? by lazy { intentExtra(EXTRA_TARGET) }
    private val lockedUser: UserHandle? by lazy { parcelableExtra(EXTRA_USER) }
    private val packageNameToOpen: String? by lazy { intent.getStringExtra(EXTRA_PACKAGE) }
    private val shortcutId: String? by lazy { intent.getStringExtra(EXTRA_SHORTCUT) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (target == null || packageNameToOpen == null) {
            finish()
            return
        }
        if (savedInstanceState == null) authenticate()
    }

    private fun appLabel(): CharSequence = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageNameToOpen!!, 0))
    }.getOrDefault(packageNameToOpen!!)

    private fun authenticate() {
        val title = getString(R.string.glow_app_lock_prompt, appLabel())
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val prompt = BiometricPrompt.Builder(this)
                    .setTitle(title)
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    )
                    .build()
                showPrompt(prompt)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                @Suppress("DEPRECATION")
                val prompt = BiometricPrompt.Builder(this)
                    .setTitle(title)
                    .setDeviceCredentialAllowed(true)
                    .build()
                showPrompt(prompt)
            }
            else -> {
                val keyguard = getSystemService(KeyguardManager::class.java)
                @Suppress("DEPRECATION")
                val confirm = keyguard.createConfirmDeviceCredentialIntent(title, null)
                if (confirm == null) {
                    onAuthenticated()
                } else {
                    @Suppress("DEPRECATION")
                    startActivityForResult(confirm, REQUEST_CONFIRM)
                }
            }
        }
    }

    private fun showPrompt(prompt: BiometricPrompt) {
        val signal = CancellationSignal().also { cancellation = it }
        prompt.authenticate(
            signal,
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    finish()
                }
            },
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIRM && resultCode == RESULT_OK) {
            onAuthenticated()
        } else {
            finish()
        }
    }

    private fun onAuthenticated() {
        val launch = target ?: return finish()
        GlowAppLock.markUnlocked(this, packageNameToOpen!!, lockedUser)
        try {
            val targetUser = lockedUser
            val shortcut = shortcutId
            if (shortcut != null) {
                // App shortcuts can only be started through LauncherApps
                getSystemService(LauncherApps::class.java).startShortcut(
                    packageNameToOpen!!,
                    shortcut,
                    null,
                    null,
                    targetUser ?: Process.myUserHandle(),
                )
            } else if (targetUser == null || targetUser == Process.myUserHandle()) {
                startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                getSystemService(LauncherApps::class.java)
                    .startMainActivity(launch.component, targetUser, null, null)
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        } catch (e: IllegalStateException) {
            // Shortcut disabled or the work profile is paused
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onDestroy() {
        cancellation?.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun intentExtra(name: String): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, Intent::class.java)
        } else {
            intent.getParcelableExtra(name)
        }

    @Suppress("DEPRECATION")
    private fun parcelableExtra(name: String): UserHandle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(name, UserHandle::class.java)
        } else {
            intent.getParcelableExtra(name)
        }

    companion object {
        private const val EXTRA_TARGET = "glow.extra.TARGET"
        private const val EXTRA_USER = "glow.extra.USER"
        private const val EXTRA_PACKAGE = "glow.extra.PACKAGE"
        private const val EXTRA_SHORTCUT = "glow.extra.SHORTCUT"
        private const val REQUEST_CONFIRM = 1

        fun createIntent(
            context: Context,
            target: Intent,
            user: UserHandle?,
            packageName: String,
            shortcutId: String? = null,
        ): Intent =
            Intent(context, GlowAppLockActivity::class.java)
                .putExtra(EXTRA_TARGET, Intent(target).apply { sourceBounds = null } as Parcelable)
                .putExtra(EXTRA_USER, user as Parcelable?)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_SHORTCUT, shortcutId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
