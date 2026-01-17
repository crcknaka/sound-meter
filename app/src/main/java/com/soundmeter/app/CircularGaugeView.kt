package com.soundmeter.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class CircularGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentDb = 0f
    private var targetDb = 0f
    private var minDb = Float.MAX_VALUE
    private var maxDb = 0f

    private val animationSpeed = 0.18f

    // Paints
    private val ringBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#18181B")
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val dbTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 170f
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private val unitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 56f
        color = Color.parseColor("#71717A")
        textAlign = Paint.Align.CENTER
    }

    private val levelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 46f
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
        textSize = 56f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    // Colors - Modern vibrant palette
    private val colorGreen = Color.parseColor("#22C55E")
    private val colorYellow = Color.parseColor("#EAB308")
    private val colorOrange = Color.parseColor("#F97316")
    private val colorRed = Color.parseColor("#EF4444")
    private val colorBackground = Color.parseColor("#18181B")

    private val minDbRange = 20f
    private val maxDbRange = 100f

    private val ringRect = RectF()

    // Ring configuration
    private val ringCount = 4
    private val ringGap = 12f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Animate towards target
        currentDb += (targetDb - currentDb) * animationSpeed

        val centerX = w / 2
        val centerY = h * 0.42f
        val maxRadius = min(w, h * 0.8f) * 0.38f

        val normalizedDb = ((currentDb - minDbRange) / (maxDbRange - minDbRange)).coerceIn(0f, 1f)

        // Draw rings from outer to inner
        for (i in 0 until ringCount) {
            val ringWidth = maxRadius * 0.12f
            val radius = maxRadius - i * (ringWidth + ringGap)

            ringBackgroundPaint.strokeWidth = ringWidth
            ringPaint.strokeWidth = ringWidth

            ringRect.set(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius
            )

            // Background ring (full circle)
            canvas.drawArc(ringRect, 135f, 270f, false, ringBackgroundPaint)

            // Calculate how much of this ring should be filled
            val ringThreshold = i.toFloat() / ringCount
            val ringProgress = ((normalizedDb - ringThreshold) / (1f / ringCount)).coerceIn(0f, 1f)

            if (ringProgress > 0) {
                // Color based on ring index
                ringPaint.color = when (i) {
                    0 -> colorGreen
                    1 -> colorYellow
                    2 -> colorOrange
                    else -> colorRed
                }

                // Add glow for active rings
                if (ringProgress > 0.5f) {
                    ringPaint.setShadowLayer(15f, 0f, 0f, ringPaint.color)
                } else {
                    ringPaint.clearShadowLayer()
                }

                val sweepAngle = 270f * ringProgress
                canvas.drawArc(ringRect, 135f, sweepAngle, false, ringPaint)
            }
        }

        ringPaint.clearShadowLayer()

        // Draw dB value in center
        dbTextPaint.color = getColorForDb(currentDb)
        canvas.drawText("${currentDb.toInt()}", centerX, centerY + 20f, dbTextPaint)
        canvas.drawText("dB", centerX, centerY + 70f, unitTextPaint)

        // Draw level description
        val levelText = getLevelText(currentDb)
        levelTextPaint.color = getColorForDb(currentDb)
        canvas.drawText(levelText, centerX, h * 0.78f, levelTextPaint)

        // Draw MIN / AVG / MAX stats at bottom
        val statsY = h - 35f
        val statSpacing = w / 4

        // MIN
        canvas.drawText("MIN", statSpacing, statsY - 38f, statsLabelPaint)
        statsValuePaint.color = colorGreen
        val minText = if (minDb == Float.MAX_VALUE) "--" else "${minDb.toInt()}"
        canvas.drawText(minText, statSpacing, statsY + 12f, statsValuePaint)

        // Current level indicator (center)
        canvas.drawText("LEVEL", w / 2, statsY - 38f, statsLabelPaint)
        statsValuePaint.color = getColorForDb(currentDb)
        canvas.drawText(getLevelShort(currentDb), w / 2, statsY + 12f, statsValuePaint)

        // MAX
        canvas.drawText("MAX", w - statSpacing, statsY - 38f, statsLabelPaint)
        statsValuePaint.color = colorRed
        val maxText = if (maxDb == 0f) "--" else "${maxDb.toInt()}"
        canvas.drawText(maxText, w - statSpacing, statsY + 12f, statsValuePaint)

        // Continue animation
        if (kotlin.math.abs(targetDb - currentDb) > 0.5f) {
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
            db < 70 -> "Busy Environment"
            db < 80 -> "Loud Traffic Noise"
            db < 90 -> "Hearing Damage Risk"
            else -> "Dangerous - Protect Ears!"
        }
    }

    private fun getLevelShort(db: Float): String {
        return when {
            db < 40 -> "LOW"
            db < 60 -> "MED"
            db < 80 -> "HIGH"
            else -> "MAX!"
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
        minDb = Float.MAX_VALUE
        maxDb = 0f
        invalidate()
    }
}
