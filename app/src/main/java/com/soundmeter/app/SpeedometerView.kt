package com.soundmeter.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SpeedometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentDb = 0f
    private var targetDb = 0f
    private var minDb = Float.MAX_VALUE
    private var maxDb = 0f

    // Animation
    private val animationSpeed = 0.15f

    // Paints
    private val arcBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 45f
        strokeCap = Paint.Cap.ROUND
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 45f
        strokeCap = Paint.Cap.ROUND
    }

    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val needleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(80, 0, 0, 0)
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#444444")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 40f
        color = Color.parseColor("#888888")
        textAlign = Paint.Align.CENTER
    }

    private val dbTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 120f
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val unitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 40f
        color = Color.parseColor("#888888")
        textAlign = Paint.Align.CENTER
    }

    private val levelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.05f
    }

    private val statsTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = Color.parseColor("#888888")
        textAlign = Paint.Align.CENTER
    }

    private val statsValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 44f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    // Colors
    private val colorGreen = Color.parseColor("#00E676")
    private val colorYellow = Color.parseColor("#FFEA00")
    private val colorOrange = Color.parseColor("#FF9100")
    private val colorRed = Color.parseColor("#FF1744")

    private val minDbRange = 20f
    private val maxDbRange = 100f

    private val arcRect = RectF()
    private val needlePath = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Animate towards target
        currentDb += (targetDb - currentDb) * animationSpeed

        val centerX = w / 2
        val centerY = h * 0.58f
        val radius = min(w, h * 1.2f) * 0.38f

        // Arc bounds
        arcRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        // Draw background arc
        arcBackgroundPaint.color = Color.parseColor("#1A1A1A")
        canvas.drawArc(arcRect, 180f, 180f, false, arcBackgroundPaint)

        // Draw colored arc segments
        drawColoredArc(canvas, radius, centerX, centerY)

        // Draw ticks and labels
        drawTicks(canvas, centerX, centerY, radius)

        // Draw needle shadow
        drawNeedle(canvas, centerX, centerY, radius * 0.75f, true)

        // Draw needle
        drawNeedle(canvas, centerX, centerY, radius * 0.75f, false)

        // Draw center circle
        val gradient = RadialGradient(
            centerX, centerY, 30f,
            Color.parseColor("#3A3A3A"),
            Color.parseColor("#1A1A1A"),
            Shader.TileMode.CLAMP
        )
        centerPaint.shader = gradient
        canvas.drawCircle(centerX, centerY, 25f, centerPaint)

        // Draw dB value
        dbTextPaint.color = getColorForDb(currentDb)
        canvas.drawText("${currentDb.toInt()}", centerX, centerY - radius * 0.30f, dbTextPaint)
        canvas.drawText("dB", centerX, centerY - radius * 0.30f + 45f, unitTextPaint)

        // Draw level description
        val levelText = getLevelText(currentDb)
        levelTextPaint.color = getColorForDb(currentDb)
        canvas.drawText(levelText, centerX, centerY + radius + 60f, levelTextPaint)

        // Draw MIN/MAX stats at bottom
        val statsY = h - 35f

        // MIN on left
        statsTextPaint.textAlign = Paint.Align.LEFT
        statsValuePaint.textAlign = Paint.Align.LEFT
        canvas.drawText("MIN", 30f, statsY - 35f, statsTextPaint)
        statsValuePaint.color = colorGreen
        val minText = if (minDb == Float.MAX_VALUE) "--" else "${minDb.toInt()}"
        canvas.drawText(minText, 30f, statsY + 10f, statsValuePaint)

        // MAX on right
        statsTextPaint.textAlign = Paint.Align.RIGHT
        statsValuePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("MAX", w - 30f, statsY - 35f, statsTextPaint)
        statsValuePaint.color = colorRed
        val maxText = if (maxDb == 0f) "--" else "${maxDb.toInt()}"
        canvas.drawText(maxText, w - 30f, statsY + 10f, statsValuePaint)

        // Continue animation
        if (kotlin.math.abs(targetDb - currentDb) > 0.5f) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawColoredArc(canvas: Canvas, radius: Float, centerX: Float, centerY: Float) {
        val normalizedDb = ((currentDb - minDbRange) / (maxDbRange - minDbRange)).coerceIn(0f, 1f)
        val sweepAngle = normalizedDb * 180f

        // Create gradient for active arc
        val colors = intArrayOf(colorGreen, colorYellow, colorOrange, colorRed)
        val positions = floatArrayOf(0f, 0.33f, 0.66f, 1f)

        arcPaint.shader = SweepGradient(centerX, centerY, colors, positions).apply {
            setLocalMatrix(Matrix().apply {
                postRotate(180f, centerX, centerY)
            })
        }

        canvas.drawArc(arcRect, 180f, sweepAngle, false, arcPaint)
    }

    private fun drawTicks(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val dbValues = intArrayOf(20, 40, 60, 80, 100)

        for (db in dbValues) {
            val normalized = (db - minDbRange) / (maxDbRange - minDbRange)
            val angle = Math.toRadians((180 + normalized * 180).toDouble())

            val innerRadius = radius + 20f
            val outerRadius = radius + 35f

            val startX = centerX + innerRadius * cos(angle).toFloat()
            val startY = centerY + innerRadius * sin(angle).toFloat()
            val endX = centerX + outerRadius * cos(angle).toFloat()
            val endY = centerY + outerRadius * sin(angle).toFloat()

            canvas.drawLine(startX, startY, endX, endY, tickPaint)

            // Draw label
            val labelRadius = radius + 55f
            val labelX = centerX + labelRadius * cos(angle).toFloat()
            val labelY = centerY + labelRadius * sin(angle).toFloat() + 10f
            canvas.drawText("$db", labelX, labelY, textPaint)
        }
    }

    private fun drawNeedle(canvas: Canvas, centerX: Float, centerY: Float, length: Float, isShadow: Boolean) {
        val normalizedDb = ((currentDb - minDbRange) / (maxDbRange - minDbRange)).coerceIn(0f, 1f)
        val angle = Math.toRadians((180 + normalizedDb * 180).toDouble())

        val offsetX = if (isShadow) 3f else 0f
        val offsetY = if (isShadow) 3f else 0f

        val tipX = centerX + length * cos(angle).toFloat() + offsetX
        val tipY = centerY + length * sin(angle).toFloat() + offsetY

        needlePath.reset()
        needlePath.moveTo(tipX, tipY)

        // Needle base width
        val baseAngle1 = angle + Math.PI / 2
        val baseAngle2 = angle - Math.PI / 2
        val baseRadius = 8f

        needlePath.lineTo(
            centerX + baseRadius * cos(baseAngle1).toFloat() + offsetX,
            centerY + baseRadius * sin(baseAngle1).toFloat() + offsetY
        )
        needlePath.lineTo(
            centerX + baseRadius * cos(baseAngle2).toFloat() + offsetX,
            centerY + baseRadius * sin(baseAngle2).toFloat() + offsetY
        )
        needlePath.close()

        canvas.drawPath(needlePath, if (isShadow) needleShadowPaint else needlePaint)
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
            db < 50 -> "Calm Environment"
            db < 60 -> "Normal Conversation"
            db < 70 -> "Busy Street Noise"
            db < 80 -> "Loud Traffic Noise"
            db < 90 -> "Very Loud - Use Caution"
            else -> "Dangerous Noise Level!"
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
