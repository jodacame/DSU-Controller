// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.TextView
import dev.jodacame.dsucontroller.R

/** Small shared helpers for the framework Views the screens are built from. */
internal fun Activity.dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

/** Keeps a full-screen root clear of the status bar, navigation bar and display cutout. */
internal fun View.padForSystemBars(extra: Int = 0) {
    setOnApplyWindowInsetsListener { view, insets ->
        val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        view.setPadding(bars.left + extra, bars.top + extra, bars.right + extra, bars.bottom + extra)
        WindowInsets.CONSUMED
    }
}

internal fun Activity.label(textRes: Int = 0, sizeSp: Float = 16f, secondary: Boolean = false): TextView =
    TextView(this).apply {
        if (textRes != 0) setText(textRes)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(getColor(if (secondary) R.color.text_secondary else R.color.text_primary))
    }

/** A flat button on the dark ground; [primary] fills it with the accent colour. */
internal fun Activity.flatButton(textRes: Int, primary: Boolean = false, onClick: () -> Unit): Button =
    Button(this, null, 0, android.R.style.Widget_Material_Button_Borderless).apply {
        setText(textRes)
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        minHeight = dp(48f)
        minimumHeight = dp(48f)
        setPadding(dp(16f), 0, dp(16f), 0)
        setTextColor(getColor(if (primary) R.color.ground else R.color.text_primary))
        background = GradientDrawable().apply {
            cornerRadius = dp(12f).toFloat()
            setColor(getColor(if (primary) R.color.text_primary else R.color.panel))
        }
        setOnClickListener { onClick() }
    }
