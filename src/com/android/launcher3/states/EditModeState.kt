/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.states

import android.content.Context
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.views.ActivityContext

/**
 * Glow: iOS-style "jiggle" edit mode. The home screen keeps its full size and the
 * dock stays in place; icons wiggle and show remove badges (see GlowEditMode), and
 * the Edit / Done buttons live at the top (GlowEditOverlay).
 */
class EditModeState(id: Int) : LauncherState(id, StatsLogManager.LAUNCHER_STATE_HOME, STATE_FLAGS) {

    companion object {
        const val DEPTH_15_PERCENT = 0.15f

        private val STATE_FLAGS =
            // Glow: the workspace stays accessible so TalkBack reaches the "Remove" actions
            (FLAG_MULTI_PAGE or
                FLAG_DISABLE_RESTORE or
                FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED)
    }

    override fun getTransitionDuration(context: ActivityContext, isToState: Boolean) = 220

    override fun <T> getDepthUnchecked(context: T): Float where T : Context?, T : ActivityContext? = 0f

    override fun getWorkspaceScaleAndTranslation(launcher: Launcher): ScaleAndTranslation =
        ScaleAndTranslation(1f, 0f, 0f)

    override fun getHotseatScaleAndTranslation(launcher: Launcher): ScaleAndTranslation =
        ScaleAndTranslation(1f, 0f, 0f)

    override fun getVisibleElements(launcher: Launcher): Int =
        HOTSEAT_ICONS or WORKSPACE_PAGE_INDICATOR

    override fun getWorkspaceBackgroundAlpha(launcher: Launcher): Float = 0f

    override fun onLeavingState(launcher: Launcher?, toState: LauncherState?) {
        // cleanup any changes to workspace
    }
}
