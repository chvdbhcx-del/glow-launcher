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

package app.lawnchair.gestures.handlers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.LauncherState
import com.android.launcher3.R

/** Glow: turns the flashlight on or off. */
class FlashlightGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        val manager = launcher.getSystemService(CameraManager::class.java) ?: return
        val cameraId = runCatching {
            manager.cameraIdList.firstOrNull {
                manager.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
        if (cameraId == null) {
            Toast.makeText(launcher, R.string.glow_gesture_no_flashlight, Toast.LENGTH_SHORT).show()
            return
        }
        TorchState.register(manager)
        runCatching { manager.setTorchMode(cameraId, !TorchState.isOn(cameraId)) }
    }

    private object TorchState {
        private val states = mutableMapOf<String, Boolean>()
        private var registered = false

        fun register(manager: CameraManager) {
            if (registered) return
            registered = true
            manager.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        states[cameraId] = enabled
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }

        fun isOn(cameraId: String) = states[cameraId] == true
    }
}

/** Glow: takes a screenshot through the accessibility service (Android 9+). */
class ScreenshotGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        GestureWithAccessibilityHandler.onTrigger(
            launcher,
            R.string.glow_gesture_screenshot_a11y_hint,
            AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
        )
    }
}

/** Glow: opens the home screen edit mode. */
class EditHomeScreenGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        if (launcher.isInState(LauncherState.NORMAL)) {
            launcher.stateManager.goToState(LauncherState.EDIT_MODE)
        }
    }
}
