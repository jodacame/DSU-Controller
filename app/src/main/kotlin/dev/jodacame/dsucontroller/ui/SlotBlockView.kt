// SPDX-License-Identifier: MIT

package dev.jodacame.dsucontroller.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import dev.jodacame.dsucontroller.R
import dev.jodacame.dsucontroller.controller.JoyConSide
import kotlin.math.max
import kotlin.math.min

/**
 * One slot of the dashboard, drawn as in the approved colour-block design
 * (docs/contracts/dashboard-ui.md): the giant player label, then the title and the status line
 * with the battery icon, then the Joy-Con at the block's right edge, all centred vertically.
 * A block taller than it is wide (portrait) puts the label and the Joy-Con on top and the text
 * under them.
 *
 * @param column 0 for the left column, 1 for the right; with [row] it decides the rounded corner
 */
@SuppressLint("ViewConstructor") // Built in code only, never inflated from a layout file.
class SlotBlockView(
    context: Context,
    private val fonts: DashboardFonts,
    private val column: Int,
    private val row: Int,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val fontScale = resources.configuration.fontScale
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
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = fonts.label }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val statusPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()
    private val joyCon = Path()
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
        val outer = dp(OUTER_RADIUS)
        val inner = dp(INNER_RADIUS)
        val topLeft = if (column == 0 && row == 0) outer else inner
        val topRight = if (column == 1 && row == 0) outer else inner
        shape.reset()
        shape.addRoundRect(
            bounds,
            floatArrayOf(topLeft, topLeft, topRight, topRight, inner, inner, inner, inner),
            Path.Direction.CW,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val free = model.look == SlotLook.FREE
        var background = if (free) freeColor else slotColors[model.slot]
        var foreground = if (free) freeTextColor else slotTextColors[model.slot]
        if (model.inverted) background = foreground.also { foreground = background }

        fill.color = background
        canvas.drawPath(shape, fill)
        if (model.look == SlotLook.NO_MOTION) drawHatch(canvas, foreground)

        titlePaint.apply {
            typeface = fonts.title
            textSize = sp(TITLE_SP)
            color = foreground
        }
        statusPaint.apply {
            typeface = if (free) fonts.quiet else fonts.text
            textSize = sp(STATUS_SP)
            color = foreground
        }
        labelPaint.color = if (free) freeLabelColor else foreground

        val padX = dp(PADDING_X)
        val padY = dp(PADDING_Y)
        val gap = dp(GAP)
        val side = model.side
        val iconWidth = if (side == null) 0f else dp(ICON_WIDTH)
        val iconHeight = if (side == null) 0f else dp(ICON_HEIGHT)
        val innerWidth = width - 2 * padX
        val innerHeight = height - 2 * padY

        // The label is as large as the design asks, but never taller than the block allows.
        labelPaint.textSize = min(dp(LABEL_SIZE), innerHeight / LABEL_LINE_HEIGHT)
        var labelWidth = labelPaint.measureText(model.label)
        val iconSpace = if (side == null) 0f else gap + iconWidth
        // A wide block keeps the design's single row and shrinks the label to leave the text its
        // room; only a block taller than it is wide (portrait) stacks the text under the label.
        val oneRow = width >= height
        if (oneRow) {
            // Sized for the widest label beside a Joy-Con, so all four labels match.
            val widest = LABELS.maxOf { labelPaint.measureText(it) }
            val maxLabelWidth = innerWidth - gap - (gap + dp(ICON_WIDTH)) - dp(MIN_TEXT_WIDTH)
            if (widest > maxLabelWidth) {
                labelPaint.textSize *= maxLabelWidth / widest
                labelWidth = labelPaint.measureText(model.label)
            }
            val textLeft = padX + labelWidth + gap
            val textWidth = innerWidth - labelWidth - gap - iconSpace
            val text = layoutText(textWidth, titleLines = 1)
            val centre = height / 2f
            drawLabel(canvas, padX, centre)
            drawTextBlock(canvas, text, textLeft, centre - text.height / 2, foreground, background)
            if (side != null) drawJoyCon(canvas, side, width - padX - iconWidth, centre - iconHeight / 2, foreground, background)
        } else {
            // Portrait: label and Joy-Con on top, text under them, the group centred.
            val maxLabelWidth = innerWidth - iconSpace
            if (labelWidth > maxLabelWidth) {
                labelPaint.textSize *= maxLabelWidth / labelWidth
                labelWidth = maxLabelWidth
            }
            val text = layoutText(innerWidth, titleLines = MAX_STACKED_TITLE_LINES)
            val topHeight = max(labelPaint.textSize * LABEL_LINE_HEIGHT, iconHeight)
            val groupHeight = topHeight + gap + text.height
            val top = max(padY, (height - groupHeight) / 2)
            drawLabel(canvas, padX, top + topHeight / 2)
            if (side != null) drawJoyCon(canvas, side, width - padX - iconWidth, top + (topHeight - iconHeight) / 2, foreground, background)
            drawTextBlock(canvas, text, padX, top + topHeight + gap, foreground, background)
        }
    }

    private class TextBlock(val title: StaticLayout, val status: StaticLayout, val gap: Float) {
        val height: Float get() = title.height + gap + status.height
    }

    private fun layoutText(width: Float, titleLines: Int): TextBlock {
        val textWidth = width.toInt().coerceAtLeast(1)
        val battery = model.battery
        val title = StaticLayout.Builder.obtain(titleText, 0, titleText.length, titlePaint, textWidth)
            .setMaxLines(titleLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
        // The battery icon follows the status text, so its room is kept on the last line.
        val reserve = if (battery == null) 0 else (dp(BATTERY_GAP) + dp(BATTERY_WIDTH) + chargingWidth(battery)).toInt()
        val status = StaticLayout.Builder.obtain(statusText, 0, statusText.length, statusPaint, textWidth)
            .setMaxLines(MAX_STATUS_LINES)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIndents(null, intArrayOf(0, reserve))
            .build()
        return TextBlock(title, status, dp(TEXT_GAP))
    }

    private fun drawLabel(canvas: Canvas, left: Float, centreY: Float) {
        val metrics = labelPaint.fontMetrics
        // Centre the capitals, which carry no descender, on the given line.
        val capHeight = -metrics.ascent * CAP_TO_ASCENT
        canvas.drawText(model.label, left, centreY + capHeight / 2, labelPaint)
    }

    private fun drawTextBlock(canvas: Canvas, text: TextBlock, left: Float, top: Float, foreground: Int, background: Int) {
        canvas.save()
        canvas.translate(left, top)
        text.title.draw(canvas)
        canvas.translate(0f, text.title.height + text.gap)
        text.status.draw(canvas)
        canvas.restore()
        val battery = model.battery ?: return
        val last = text.status.lineCount - 1
        val lineTop = top + text.title.height + text.gap + text.status.getLineTop(last)
        val lineBottom = top + text.title.height + text.gap + text.status.getLineBottom(last)
        val batteryLeft = left + text.status.getLineRight(last) + dp(BATTERY_GAP)
        val batteryTop = (lineTop + lineBottom) / 2 - dp(BATTERY_HEIGHT) / 2
        drawBattery(canvas, battery, batteryLeft, batteryTop, foreground, background)
    }

    private fun drawHatch(canvas: Canvas, color: Int) {
        stroke.color = color
        stroke.alpha = HATCH_ALPHA
        stroke.strokeWidth = dp(HATCH_STROKE)
        val step = dp(HATCH_STEP)
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

    /**
     * The Joy-Con as in the design (a 40 x 96 drawing): the rounded side faces outwards from
     * the console, the left one has its stick above the buttons, the right one below.
     */
    private fun drawJoyCon(canvas: Canvas, side: JoyConSide, left: Float, top: Float, foreground: Int, background: Int) {
        val scale = dp(ICON_WIDTH) / ICON_VIEW_WIDTH
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        joyCon.reset()
        if (side == JoyConSide.LEFT) {
            joyCon.moveTo(36f, 4f)
            joyCon.lineTo(22f, 4f)
            joyCon.arcTo(4f, 4f, 40f, 40f, 270f, -90f, false)
            joyCon.lineTo(4f, 74f)
            joyCon.arcTo(4f, 56f, 40f, 92f, 180f, -90f, false)
            joyCon.lineTo(36f, 92f)
        } else {
            joyCon.moveTo(4f, 4f)
            joyCon.lineTo(18f, 4f)
            joyCon.arcTo(0f, 4f, 36f, 40f, 270f, 90f, false)
            joyCon.lineTo(36f, 74f)
            joyCon.arcTo(0f, 56f, 36f, 92f, 0f, 90f, false)
            joyCon.lineTo(4f, 92f)
        }
        joyCon.close()
        fill.color = foreground
        canvas.drawPath(joyCon, fill)
        fill.color = background
        val stickX = if (side == JoyConSide.LEFT) 18f else 22f
        val stickY = if (side == JoyConSide.LEFT) 30f else 60f
        val buttonsY = if (side == JoyConSide.LEFT) 58f else 28f
        canvas.drawCircle(stickX, stickY, STICK_RADIUS, fill)
        canvas.drawCircle(stickX, buttonsY - BUTTON_SPREAD, BUTTON_RADIUS, fill)
        canvas.drawCircle(stickX, buttonsY + BUTTON_SPREAD, BUTTON_RADIUS, fill)
        canvas.drawCircle(stickX - BUTTON_SPREAD, buttonsY, BUTTON_RADIUS, fill)
        canvas.drawCircle(stickX + BUTTON_SPREAD, buttonsY, BUTTON_RADIUS, fill)
        canvas.restore()
    }

    /** The four-segment battery of the design (a 40 x 18 drawing), plus a bolt when charging. */
    private fun drawBattery(canvas: Canvas, battery: BatteryIcon, left: Float, top: Float, foreground: Int, background: Int) {
        val scale = dp(BATTERY_WIDTH) / BATTERY_VIEW_WIDTH
        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        scratch.set(1f, 1f, 35f, 17f)
        if (battery.low) {
            // Low differs by shape too: a filled pill with one hollow segment.
            fill.color = foreground
            canvas.drawRoundRect(scratch, 3f, 3f, fill)
        } else {
            stroke.color = foreground
            stroke.strokeWidth = 2f
            canvas.drawRoundRect(scratch, 3f, 3f, stroke)
        }
        fill.color = foreground
        scratch.set(36f, 6f, 39f, 12f)
        canvas.drawRoundRect(scratch, 1f, 1f, fill)
        fill.color = if (battery.low) background else foreground
        for (index in 0 until battery.segments) {
            val segmentLeft = SEGMENT_START + index * SEGMENT_STEP
            scratch.set(segmentLeft, 4f, segmentLeft + SEGMENT_WIDTH, 14f)
            canvas.drawRoundRect(scratch, 1f, 1f, fill)
        }
        if (battery.charging) {
            fill.color = foreground
            joyCon.reset()
            joyCon.moveTo(51f, 0f)
            joyCon.lineTo(43f, 10f)
            joyCon.lineTo(48f, 10f)
            joyCon.lineTo(45f, 18f)
            joyCon.lineTo(53f, 7f)
            joyCon.lineTo(48f, 7f)
            joyCon.close()
            canvas.drawPath(joyCon, fill)
        }
        canvas.restore()
    }

    private fun chargingWidth(battery: BatteryIcon): Float =
        if (battery.charging) dp(BATTERY_WIDTH) * BOLT_EXTRA / BATTERY_VIEW_WIDTH else 0f

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float = value * density * fontScale

    private companion object {
        val LABELS = listOf("P1", "P2", "P3", "P4")

        // Sizes in dp (sp for text), taken from the approved design.
        const val OUTER_RADIUS = 24f
        const val INNER_RADIUS = 6f
        const val PADDING_X = 22f
        const val PADDING_Y = 14f
        const val GAP = 16f
        const val TEXT_GAP = 4f
        const val LABEL_SIZE = 128f
        const val LABEL_LINE_HEIGHT = 0.8f

        /** Archivo's capital height as a share of its ascent. */
        const val CAP_TO_ASCENT = 0.72f
        const val TITLE_SP = 22f
        const val STATUS_SP = 14f
        const val MAX_STATUS_LINES = 2
        const val MAX_STACKED_TITLE_LINES = 2
        const val MIN_TEXT_WIDTH = 160f
        const val ICON_WIDTH = 44f
        const val ICON_HEIGHT = 106f
        const val ICON_VIEW_WIDTH = 40f
        const val STICK_RADIUS = 7f
        const val BUTTON_RADIUS = 2.6f
        const val BUTTON_SPREAD = 6f
        const val BATTERY_WIDTH = 40f
        const val BATTERY_HEIGHT = 18f
        const val BATTERY_GAP = 10f
        const val BATTERY_VIEW_WIDTH = 40f
        const val BOLT_EXTRA = 14f
        const val SEGMENT_START = 4f
        const val SEGMENT_STEP = 7.5f
        const val SEGMENT_WIDTH = 6f
        const val HATCH_ALPHA = 45
        const val HATCH_STROKE = 3f
        const val HATCH_STEP = 18f
        const val OPAQUE = 255
    }
}
