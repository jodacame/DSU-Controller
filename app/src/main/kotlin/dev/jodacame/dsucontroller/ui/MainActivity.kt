// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import dev.jodacame.dsucontroller.R

/**
 * Entry point of the app. It will host the four-slot dashboard described in
 * docs/contracts/dashboard-ui.md; until that exists it only opens the diagnostics screen.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openDiagnostics = Button(this).apply {
            setText(R.string.open_diagnostics)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, DiagnosticsActivity::class.java))
            }
        }
        setContentView(
            FrameLayout(this).apply {
                addView(openDiagnostics, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER))
            },
        )
    }
}
