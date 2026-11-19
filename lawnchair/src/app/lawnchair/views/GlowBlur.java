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

package app.lawnchair.views;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.android.internal.graphics.drawable.BackgroundBlurDrawable;

/**
 * Real "frosted glass": a drawable that blurs whatever is behind the launcher window
 * (the wallpaper) inside its bounds, using the platform's background-blur regions.
 *
 * Works on Android 12+ when the device has cross-window blur turned on (not in battery
 * saver, not on low-end GPUs). Everywhere else {@link #create} returns null and the glass
 * falls back to its translucent gradient.
 */
public final class GlowBlur {

    private GlowBlur() { }

    /** True when the system will actually draw background blur for this window right now. */
    public static boolean isAvailable(View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        try {
            WindowManager wm = view.getContext().getSystemService(WindowManager.class);
            return wm != null && wm.isCrossWindowBlurEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Creates a blur drawable for the window of the attached {@code view}, or null when
     * blur is not available. The caller sets its bounds and draws it under the glass fill.
     */
    @Nullable
    public static Drawable create(View view, int blurRadiusPx, float cornerRadiusPx) {
        if (!isAvailable(view)) return null;
        try {
            ViewRootImpl root = view.getViewRootImpl();
            if (root == null) return null;
            BackgroundBlurDrawable blur = root.createBackgroundBlurDrawable();
            blur.setBlurRadius(blurRadiusPx);
            blur.setCornerRadius(cornerRadiusPx);
            blur.setColor(Color.TRANSPARENT);
            return blur;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Updates the corner radius of a drawable made by {@link #create}. */
    public static void setCornerRadius(@Nullable Drawable blur, float cornerRadiusPx) {
        if (blur instanceof BackgroundBlurDrawable b) {
            try {
                b.setCornerRadius(cornerRadiusPx);
            } catch (Throwable ignored) {
            }
        }
    }
}
