// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import dev.jodacame.dsucontroller.R
import dev.jodacame.dsucontroller.controller.JoyConSide
import kotlin.math.min

/**
 * One slot of the dashboard, drawn on a canvas: the giant player label, the left/right title,
 * the status line with the battery icon, and a Joy-Con silhouette at the block's outer edge.
 *
 * @param column 0 for the left column, 1 for the right; it decides which corners are outer and
 *   on which edge the silhouette sits
 * @param row 0 for the top row, 1 for the bottom
 */
@SuppressLint("ViewConstructor") // Built in code only, never inflated from a layout file.
class SlotBlockView(context: Context, private val column: Int, private val row: Int) : View(context) {
    private val density = resources.displayMetrics.density
    private val scaledDensity = density * resources.configuration.fontScale
    private val slotColors = intArrayOf(
        context.getColor(R.color.slot_1), context.getColor(R.color.slot_2),
        context.getColor(R.color.slot_3), context.getColor(R.color.slot_4),
    )
    private val slotTextColors = intArrayOf(
        context.getColor(R.color.slot_1_text), context.getColor(R.color.slot_2_text),
        context.getColor(R.color.slot_3_text), context.getColor(R.color.slot_4_text),
    )
    private val freeColor = context.getColor(R.color.panel)
    private val freeLabelColor = context.getColor(R.color.free_label)
    private val freeTextColor = context.getColor(R.color.text_secondary)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.create("sans-serif-condensed", Typeface.NORMAL), LABEL_WEIGHT, false)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, TITLE_WEIGHT, false)
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()
    private val bounds = RectF()
    private val scratch = RectF()

    private var model: SlotModel = DashboardModel.slot(row * 2 + column, null, streaming = false)
    private var statusText = ""
    private var titleText = ""

    /** Shows a new state; redraws only when something visible changed. */
    fun bind(newModel: SlotModel, title: String, status: String, description: String) {
        if (newModel == model && title == titleText && status == statusText) return
        model = newModel
        titleText = title
        statusText = status
        contentDescription = description
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        val outer = OUTER_RADIUS_DP * density
        val inner = INNER_RADIUS_DP * density
        val topLeft = if (column == 0 && row == 0) outer else inner
        val topRight = if (column == 1 && row == 0) outer else inner
        val bottomRight = if (column == 1 && row == 1) outer else inner
        val bottomLeft = if (column == 0 && row == 1) outer else inner
        shape.reset()
        shape.addRoundRect(
            bounds,
            floatArrayOf(topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft, bottomLeft),
            Path.Direction.CW,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val slot = model.slot
        val free = model.look == SlotLook.FREE
        var background = if (free) freeColor else slotColors[slot]
        var foreground = if (free) freeTextColor else slotTextColors[slot]
        if (model.inverted) background = foreground.also { foreground = background }
        val labelColor = if (free) freeLabelColor else foreground

        fill.color = background
        canvas.drawPath(shape, fill)
        if (model.look == SlotLook.NO_MOTION) drawHatch(canvas, foreground)

        val padding = PADDING_DP * density
        val silhouetteWidth = if (free) 0f else width * SILHOUETTE_WIDTH_RATIO
        if (!free) drawSilhouette(canvas, foreground, background, padding, silhouetteWidth)

        // Title and status sit under the silhouette and use the full width; only the label
        // shares its band with the silhouette.
        val statusSize = STATUS_SP * scaledDensity
        val titleSize = TITLE_SP * scaledDensity
        statusPaint.textSize = statusSize
        statusPaint.color = foreground
        titlePaint.textSize = titleSize
        titlePaint.color = foreground
        val textLeft = padding
        val textWidth = width - 2 * padding
        val battery = model.battery
        val batteryWidth = if (battery == null) 0f else statusSize * BATTERY_WIDTH_EM + padding / 2
        val statusLines = wrap(statusText, statusPaint, textWidth, lastLineReserve = batteryWidth)

        val lastBaseline = height - padding
        val firstStatusBaseline = lastBaseline - (statusLines.size - 1) * statusSize * LINE_GAP
        for ((index, line) in statusLines.withIndex()) {
            canvas.drawText(line, textLeft, firstStatusBaseline + index * statusSize * LINE_GAP, statusPaint)
        }
        if (battery != null) {
            val batteryLeft = textLeft + statusPaint.measureText(statusLines.last()) + padding / 2
            drawBattery(canvas, battery, batteryLeft, lastBaseline, statusSize, foreground, background)
        }
        val titleBaseline = firstStatusBaseline - statusSize * LINE_GAP
        canvas.drawText(ellipsize(titleText, titlePaint, textWidth), textLeft, titleBaseline, titlePaint)

        val labelLeft = if (column == 0 && !free) padding + silhouetteWidth + padding else padding
        val labelRight = if (column == 1 && !free) width - padding - silhouetteWidth - padding else width - padding
        val labelBottom = titleBaseline - titleSize * LINE_GAP
        labelPaint.color = labelColor
        labelPaint.textSize = REFERENCE_TEXT_SIZE
        val byWidth = REFERENCE_TEXT_SIZE * (labelRight - labelLeft) / labelPaint.measureText(model.label)
        val byHeight = (labelBottom - padding) / LABEL_CAP_HEIGHT
        labelPaint.textSize = min(min(byWidth, byHeight), height * LABEL_HEIGHT_RATIO / LABEL_CAP_HEIGHT)
        canvas.drawText(model.label, labelLeft, padding + labelPaint.textSize * LABEL_CAP_HEIGHT, labelPaint)
    }

    /** Breaks [text] at spaces into at most [MAX_STATUS_LINES] lines, ellipsizing the last. */
    private fun wrap(text: String, paint: Paint, width: Float, lastLineReserve: Float): List<String> {
        val lines = ArrayList<String>()
        var rest = text
        while (rest.isNotEmpty() && lines.size < MAX_STATUS_LINES - 1) {
            if (paint.measureText(rest) <= width - lastLineReserve) break
            val fit = paint.breakText(rest, true, width, null)
            val cut = rest.lastIndexOf(' ', fit).takeIf { it > 0 } ?: break
            lines += rest.substring(0, cut)
            rest = rest.substring(cut + 1)
        }
        lines += ellipsize(rest, paint, width - lastLineReserve)
        return lines
    }

    private fun drawHatch(canvas: Canvas, color: Int) {
        stroke.color = color
        stroke.alpha = HATCH_ALPHA
        stroke.strokeWidth = HATCH_STROKE_DP * density
        val step = HATCH_STEP_DP * density
        canvas.save()
        canvas.clipPath(shape)
        var x = -height.toFloat()
        while (x < width) {
            canvas.drawLine(x, height.toFloat(), x + height, 0f, stroke)
            x += step
        }
        canvas.restore()
        stroke.alpha = OPAQUE
    }

    /** A rounded Joy-Con body with its stick, on the block's outer edge. */
    private fun drawSilhouette(canvas: Canvas, foreground: Int, background: Int, padding: Float, width: Float) {
        val left = if (column == 0) padding else this.width - padding - width
        val height = min(this.height * SILHOUETTE_HEIGHT_RATIO, width * SILHOUETTE_ASPECT)
        val top = (this.height - height) / 2
        scratch.set(left, top, left + width, top + height)
        fill.color = foreground
        fill.alpha = SILHOUETTE_ALPHA
        canvas.drawRoundRect(scratch, width / 2, width / 2, fill)
        fill.alpha = OPAQUE
        val stickY = if (model.side == JoyConSide.LEFT) top + height * STICK_POSITION else top + height * (1 - STICK_POSITION)
        fill.color = background
        canvas.drawCircle(left + width / 2, stickY, width * STICK_RADIUS_RATIO, fill)
    }

    private fun drawBattery(canvas: Canvas, battery: BatteryIcon, left: Float, baseline: Float, size: Float, foreground: Int, background: Int) {
        val height = size * BATTERY_HEIGHT_EM
        val width = size * BATTERY_BODY_EM
        val top = baseline - height
        stroke.color = foreground
        stroke.strokeWidth = size * BATTERY_STROKE_EM
        scratch.set(left, top, left + width, baseline)
        val radius = height * BATTERY_RADIUS_RATIO
        fill.color = foreground
        if (battery.low) {
            canvas.drawRoundRect(scratch, radius, radius, fill)
        } else {
            canvas.drawRoundRect(scratch, radius, radius, stroke)
        }
        val tipWidth = size * BATTERY_TIP_EM
        scratch.set(left + width, top + height * 0.3f, left + width + tipWidth, baseline - height * 0.3f)
        canvas.drawRect(scratch, fill)

        val inset = size * BATTERY_INSET_EM
        val gap = size * BATTERY_GAP_EM
        val segmentWidth = (width - 2 * inset - (SEGMENTS - 1) * gap) / SEGMENTS
        fill.color = if (battery.low) background else foreground
        for (index in 0 until battery.segments) {
            val segmentLeft = left + inset + index * (segmentWidth + gap)
            scratch.set(segmentLeft, top + inset, segmentLeft + segmentWidth, baseline - inset)
            canvas.drawRect(scratch, fill)
        }
        if (battery.charging) {
            val boltLeft = left + width + tipWidth + inset
            val bolt = Path().apply {
                moveTo(boltLeft + size * 0.35f, top)
                lineTo(boltLeft, top + height * 0.55f)
                lineTo(boltLeft + size * 0.2f, top + height * 0.55f)
                lineTo(boltLeft + size * 0.1f, baseline)
                lineTo(boltLeft + size * 0.45f, top + height * 0.4f)
                lineTo(boltLeft + size * 0.25f, top + height * 0.4f)
                close()
            }
            fill.color = foreground
            canvas.drawPath(bolt, fill)
        }
    }

    private fun ellipsize(text: String, paint: Paint, width: Float): String {
        if (width <= 0f) return ""
        if (paint.measureText(text) <= width) return text
        val count = paint.breakText(text, true, width - paint.measureText(ELLIPSIS), null)
        return text.take(count.coerceAtLeast(0)) + ELLIPSIS
    }

    private companion object {
        const val OUTER_RADIUS_DP = 24f
        const val INNER_RADIUS_DP = 6f
        const val PADDING_DP = 16f
        const val TITLE_SP = 20f
        const val STATUS_SP = 15f
        const val LINE_GAP = 1.35f
        const val LABEL_WEIGHT = 900
        const val TITLE_WEIGHT = 700

        /** Cap height of the condensed face as a share of its text size. */
        const val LABEL_CAP_HEIGHT = 0.72f
        const val LABEL_HEIGHT_RATIO = 0.6f
        const val SILHOUETTE_WIDTH_RATIO = 0.13f
        const val SILHOUETTE_HEIGHT_RATIO = 0.5f
        const val SILHOUETTE_ASPECT = 3.2f
        const val SILHOUETTE_ALPHA = 70
        const val STICK_POSITION = 0.3f
        const val STICK_RADIUS_RATIO = 0.28f
        const val HATCH_ALPHA = 45
        const val HATCH_STROKE_DP = 3f
        const val HATCH_STEP_DP = 18f
        const val OPAQUE = 255
        const val SEGMENTS = 4
        const val BATTERY_WIDTH_EM = 2.6f
        const val BATTERY_BODY_EM = 1.9f
        const val BATTERY_HEIGHT_EM = 0.85f
        const val BATTERY_TIP_EM = 0.12f
        const val BATTERY_STROKE_EM = 0.1f
        const val BATTERY_INSET_EM = 0.16f
        const val BATTERY_GAP_EM = 0.08f
        const val BATTERY_RADIUS_RATIO = 0.25f
        const val ELLIPSIS = "…"
        const val MAX_STATUS_LINES = 2
        const val REFERENCE_TEXT_SIZE = 100f
    }
}
