/*
 * Copyright 2022, Lawnchair
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

package app.lawnchair.ui.preferences.about

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.AboutLicenses
import app.lawnchair.update.GlowUpdater
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

/**
 * About Glow: version, updates, release notes and licenses. Glow is based on
 * Lawnchair, which is credited under "Acknowledgements".
 */
@Composable
fun About(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    PreferenceLayout(
        label = stringResource(id = R.string.about_label),
        modifier = modifier,
        backArrowVisible = !LocalIsExpandedScreen.current,
    ) {
        Spacer(Modifier.padding(top = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_home_comp),
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(id = R.string.derived_app_name),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))

        PreferenceGroup {
            if (GlowUpdater.isSupported()) {
                ClickablePreference(
                    label = stringResource(id = R.string.glow_update_check),
                    onClick = { GlowUpdater.checkFromSettings(context) },
                )
            }
            ClickablePreference(
                label = stringResource(id = R.string.glow_about_release_notes),
                onClick = { openUrl(context, RELEASES_URL) },
            )
            ClickablePreference(
                label = stringResource(id = R.string.glow_about_source),
                onClick = { openUrl(context, SOURCE_URL) },
            )
        }

        PreferenceGroup(
            heading = stringResource(R.string.legal),
            description = stringResource(R.string.glow_about_based_on),
        ) {
            NavigationActionPreference(
                label = stringResource(id = R.string.acknowledgements),
                destination = AboutLicenses,
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private const val SOURCE_URL = "https://github.com/chvdbhcx-del/glow-launcher"
private const val RELEASES_URL = "$SOURCE_URL/releases"
