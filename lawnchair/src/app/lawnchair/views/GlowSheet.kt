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

package app.lawnchair.views

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * The Glow bottom sheet: dark frosted glass with a grabber, a bold title and big
 * rounded choices. Used instead of stock AlertDialogs everywhere in Glow.
 */
class GlowSheet(private val activity: Activity) {

    private val dialog = Dialog(activity)
    private val accent = GlowGlass.accent(activity)
    private val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        activity.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(10), dp(18), dp(22))
        background = GradientDrawable().apply {
            val r = dp(28).toFloat()
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setColor(if (canBlur) Color.argb(165, 34, 30, 44) else Color.argb(245, 34, 30, 44))
            setStroke(dp(1).coerceAtLeast(1), Color.argb(36, 255, 255, 255))
        }
    }

    /** Body below the grabber; [clear] empties it for the next step of a flow. */
    private val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    init {
        root.addView(
            View(activity).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(3).toFloat()
                    setColor(Color.argb(90, 255, 255, 255))
                }
            },
            LinearLayout.LayoutParams(dp(36), dp(5)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(16)
            },
        )
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun dp(value: Int) = GlowGlass.dp(activity, value)

    fun clear(): GlowSheet = apply { body.removeAllViews() }

    fun title(text: CharSequence, subtitle: CharSequence? = null): GlowSheet = apply {
        body.addView(TextView(activity).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 21f)
            typeface = Typeface.DEFAULT_BOLD
            GlowGlass.asHeading(this)
        })
        if (subtitle != null) {
            body.addView(TextView(activity).apply {
                this.text = subtitle
                setTextColor(Color.argb(180, 255, 255, 255))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                setLineSpacing(0f, 1.15f)
                setPadding(0, dp(4), 0, 0)
            })
        }
        body.addView(View(activity), LinearLayout.LayoutParams(1, dp(16)))
    }

    /** A line of text with a leading glyph, for short how-to lists. */
    fun point(glyph: String, text: CharSequence): GlowSheet = apply {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(activity).apply {
            this.text = glyph
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.argb(40, 255, 255, 255))
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
        row.addView(TextView(activity).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setLineSpacing(0f, 1.1f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        body.addView(row)
    }

    /** A full-width choice; [primary] fills it with the accent. */
    fun choice(
        label: CharSequence,
        primary: Boolean = false,
        swatch: Int? = null,
        onClick: () -> Unit,
    ): GlowSheet = apply {
        val chip = TextView(activity).apply {
            text = label
            if (swatch != null) {
                val dot = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(swatch)
                    setStroke(dp(2), Color.argb(150, 255, 255, 255))
                    setBounds(0, 0, dp(22), dp(22))
                }
                setCompoundDrawablesRelative(dot, null, null, null)
                compoundDrawablePadding = dp(14)
            }
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            if (primary) gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(18), 0, dp(18), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                if (primary) {
                    setColor(accent)
                } else {
                    setColor(Color.argb(34, 255, 255, 255))
                    setStroke(dp(1).coerceAtLeast(1), Color.argb(30, 255, 255, 255))
                }
            }
            foreground = RippleDrawable(
                ColorStateList.valueOf(Color.argb(50, 255, 255, 255)),
                null,
                GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(Color.WHITE)
                },
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
            GlowGlass.asButton(this)
        }
        body.addView(
            chip,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(CHOICE_HEIGHT_DP)).apply {
                bottomMargin = dp(10)
            },
        )
    }

    /** A spinner with a short label (non-cancelable while it shows). */
    fun progress(label: CharSequence): GlowSheet = apply {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), dp(4), dp(10))
        }
        row.addView(ProgressBar(activity).apply {
            indeterminateTintList = ColorStateList.valueOf(accent)
        }, LinearLayout.LayoutParams(dp(36), dp(36)))
        row.addView(TextView(activity).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(16), 0, 0, 0)
        })
        body.addView(row)
        dialog.setCancelable(false)
    }

    /** Any custom content, full width. */
    fun view(content: View): GlowSheet = apply {
        body.addView(
            content,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            },
        )
    }

    /** The accent colour the sheet uses for primary choices. */
    val accentColor: Int get() = accent

    private var dismissAction: (() -> Unit)? = null

    fun onDismiss(action: () -> Unit): GlowSheet = apply { dismissAction = action }

    fun show(cancelable: Boolean = true): GlowSheet = apply {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.setCancelable(cancelable)
        dialog.setCanceledOnTouchOutside(cancelable)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply {
                dimAmount = 0.3f
                windowAnimations = android.R.style.Animation_InputMethod
            }
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (canBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setBackgroundBlurRadius(dp(30))
            }
        }
        dialog.setOnDismissListener {
            if (open.remove(this)) dismissAction?.invoke()
        }
        if (!activity.isFinishing && !activity.isDestroyed) {
            dialog.show()
            open.add(this)
        }
    }

    fun dismiss() {
        runCatching { dialog.dismiss() }
    }

    companion object {
        private const val CHOICE_HEIGHT_DP = 54
        private val open = mutableSetOf<GlowSheet>()

        /** True while any Glow sheet is on screen, so nothing else pops up over it. */
        @JvmStatic
        fun isAnyShowing(): Boolean = open.isNotEmpty()
    }
}
