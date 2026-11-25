// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.ui

import android.content.res.AssetManager
import android.graphics.Typeface

/**
 * The cuts of the bundled Archivo variable font the dashboard uses (docs/decisions/0009).
 * Each cut is one variation of the same file: weight and width are picked here only.
 */
class DashboardFonts(assets: AssetManager) {
    /** The giant player label: heaviest weight, condensed. */
    val label: Typeface = cut(assets, weight = 900, width = 75)

    /** Slot titles ("Left Joy-Con"). */
    val title: Typeface = cut(assets, weight = 800, width = 100)

    /** Status lines and the status bar. */
    val text: Typeface = cut(assets, weight = 600, width = 100)

    /** Secondary text of a free slot. */
    val quiet: Typeface = cut(assets, weight = 500, width = 100)

    /** The app name and the primary button. */
    val heavy: Typeface = cut(assets, weight = 900, width = 100)

    private companion object {
        const val PATH = "fonts/Archivo.ttf"

        fun cut(assets: AssetManager, weight: Int, width: Int): Typeface =
            Typeface.Builder(assets, PATH)
                .setFontVariationSettings("'wght' $weight, 'wdth' $width")
                .build()
    }
}
