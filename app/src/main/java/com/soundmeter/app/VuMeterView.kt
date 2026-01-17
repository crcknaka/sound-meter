package com.soundmeter.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class VuMeterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentDb = 0f
    private var targetDb = 0f
    private var peakDb = 0f
    private var peakHoldTime = 0L
    private var minDb = Float.MAX_VALUE
    private var maxDb = 0f

    private val animationSpeed = 0.2f
    private val peakDecay = 0.02f
    private val peakHoldDuration = 1500L

    // Paints
    private val barBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#18181B")
    }

    private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 42f
        color = Color.parseColor("#71717A")
        textAlign = Paint.Align.CENTER
    }

    private val dbTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 165f
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 48f
        color = Color.parseColor("#71717A")
        textAlign = Paint.Align.CENTER
    }

    private val levelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 44f
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
    }

    private val statsLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        color = Color.parseColor("#71717A")
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.05f
    }

    private val statsValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 58f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    // Colors - Modern vibrant palette
    private val colorGreen = Color.parseColor("#22C55E")
    private val colorYellow = Color.parseColor("#EAB308")
    private val colorOrange = Color.parseColor("#F97316")
    private val colorRed = Color.parseColor("#EF4444")
    private val colorBackground = Color.parseColor("#18181B")
    private val colorSurface = Color.parseColor("#27272A")

    private val minDbRange = 20f
    private val maxDbRange = 100f

    private val segmentCount = 40
    private val segmentGap = 3f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Animate towards target
        currentDb += (targetDb - currentDb) * animationSpeed

        // Peak hold logic
        val now = System.currentTimeMillis()
        if (currentDb > peakDb) {
            peakDb = currentDb
            peakHoldTime = now
        } else if (now - peakHoldTime > peakHoldDuration) {
            peakDb -= peakDecay * (maxDbRange - minDbRange)
            if (peakDb < currentDb) peakDb = currentDb
        }

        val padding = 30f
        val barHeight = h * 0.15f
        val barTop = h * 0.50f
        val barWidth = w - padding * 2

        // Draw dB value at top
        dbTextPaint.color = getColorForDb(currentDb)
        canvas.drawText("${currentDb.toInt()}", w / 2, h * 0.26f, dbTextPaint)
        labelPaint.color = Color.parseColor("#888888")
        canvas.drawText("dB SPL", w / 2, h * 0.26f + 40f, labelPaint)

        // Draw level description
        val levelText = getLevelText(currentDb)
        levelTextPaint.color = getColorForDb(currentDb)
        canvas.drawText(levelText, w / 2, h * 0.44f, levelTextPaint)

        // Draw segmented bar background
        val segmentWidth = (barWidth - (segmentCount - 1) * segmentGap) / segmentCount

        for (i in 0 until segmentCount) {
            val x = padding + i * (segmentWidth + segmentGap)
            barBackgroundPaint.color = colorBackground
            canvas.drawRoundRect(x, barTop, x + segmentWidth, barTop + barHeight, 5f, 5f, barBackgroundPaint)
        }

        // Draw active segments
        val normalizedDb = ((currentDb - minDbRange) / (maxDbRange - minDbRange)).coerceIn(0f, 1f)
        val activeSegments = (normalizedDb * segmentCount).toInt()

        for (i in 0 until activeSegments) {
            val x = padding + i * (segmentWidth + segmentGap)
            val segmentProgress = i.toFloat() / segmentCount

            segmentPaint.color = when {
                segmentProgress < 0.25f -> colorGreen
                segmentProgress < 0.5f -> colorYellow
                segmentProgress < 0.75f -> colorOrange
                else -> colorRed
            }

            // Add glow effect to top segments
            if (i >= activeSegments - 3 && activeSegments > 0) {
                segmentPaint.setShadowLayer(8f, 0f, 0f, segmentPaint.color)
            } else {
                segmentPaint.clearShadowLayer()
            }

            canvas.drawRoundRect(x, barTop, x + segmentWidth, barTop + barHeight, 5f, 5f, segmentPaint)
        }

        // Draw peak indicator
        if (peakDb > minDbRange) {
            val normalizedPeak = ((peakDb - minDbRange) / (maxDbRange - minDbRange)).coerceIn(0f, 1f)
            val peakSegment = (normalizedPeak * segmentCount).toInt().coerceIn(0, segmentCount - 1)
            val peakX = padding + peakSegment * (segmentWidth + segmentGap)
            peakPaint.color = Color.WHITE
            canvas.drawRoundRect(peakX, barTop - 2f, peakX + segmentWidth, barTop + barHeight + 2f, 5f, 5f, peakPaint)
        }

        // Draw scale labels
        val dbValues = intArrayOf(20, 40, 60, 80, 100)
        for (db in dbValues) {
            val normalized = (db - minDbRange) / (maxDbRange - minDbRange)
            val x = padding + normalized * barWidth
            canvas.drawText("$db", x, barTop + barHeight + 40f, textPaint)
        }

        // Draw MIN / PEAK / MAX stats at bottom
        val statsY = h - 30f
        val statSpacing = w / 4

        // MIN
        statsLabelPaint.textAlign = Paint.Align.CENTER
        statsValuePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("MIN", statSpacing, statsY - 36f, statsLabelPaint)
        statsValuePaint.color = colorGreen
        val minText = if (minDb == Float.MAX_VALUE) "--" else "${minDb.toInt()}"
        canvas.drawText(minText, statSpacing, statsY + 10f, statsValuePaint)

        // PEAK (current peak hold)
        canvas.drawText("PEAK", w / 2, statsY - 36f, statsLabelPaint)
        statsValuePaint.color = colorYellow
        val peakText = if (peakDb < minDbRange) "--" else "${peakDb.toInt()}"
        canvas.drawText(peakText, w / 2, statsY + 10f, statsValuePaint)

        // MAX
        canvas.drawText("MAX", w - statSpacing, statsY - 36f, statsLabelPaint)
        statsValuePaint.color = colorRed
        val maxText = if (maxDb == 0f) "--" else "${maxDb.toInt()}"
        canvas.drawText(maxText, w - statSpacing, statsY + 10f, statsValuePaint)

        // Continue animation
        if (kotlin.math.abs(targetDb - currentDb) > 0.5f || peakDb > currentDb + 1) {
            postInvalidateOnAnimation()
        }
    }

    private fun getColorForDb(db: Float): Int {
        return when {
            db < 40 -> colorGreen
            db < 60 -> colorYellow
            db < 80 -> colorOrange
            else -> colorRed
        }
    }

    private fun getLevelText(db: Float): String {
        return when {
            db < 30 -> "Very Quiet Environment"
            db < 40 -> "Quiet Background Noise"
            db < 50 -> "Calm Indoor Space"
            db < 60 -> "Normal Conversation"
            db < 70 -> "Busy Office Noise"
            db < 80 -> "Heavy Traffic Level"
            db < 90 -> "Hearing Damage Risk"
            else -> "Dangerous - Protect Ears!"
        }
    }

    fun updateDb(db: Float) {
        targetDb = db
        if (db < minDb) minDb = db
        if (db > maxDb) maxDb = db
        invalidate()
    }

    fun reset() {
        currentDb = 0f
        targetDb = 0f
        peakDb = 0f
        minDb = Float.MAX_VALUE
        maxDb = 0f
        invalidate()
    }
}
