package com.soundmeter.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SoundWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val readings = mutableListOf<Float>()
    private val maxReadings = 100

    private var minDb = Float.MAX_VALUE
    private var maxDb = 0f
    private var avgDb = 0f

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#2A2A2A")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = Color.parseColor("#666666")
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val minMaxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val minMaxTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val wavePath = Path()
    private val fillPath = Path()

    private val minDbRange = 20f
    private val maxDbRange = 120f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 60f

        // Draw grid lines
        drawGrid(canvas, w, h, padding)

        if (readings.isEmpty()) {
            return
        }

        // Draw min/max lines
        drawMinMaxLines(canvas, w, h, padding)

        // Draw the wave
        drawWave(canvas, w, h, padding)
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float, padding: Float) {
        val graphHeight = h - padding * 2
        val graphWidth = w - padding

        // Horizontal grid lines (dB levels)
        val dbLevels = listOf(30, 50, 70, 90, 110)
        for (db in dbLevels) {
            val y = padding + graphHeight * (1 - (db - minDbRange) / (maxDbRange - minDbRange))
            canvas.drawLine(padding, y, w - 10, y, gridPaint)
            canvas.drawText("$db", 8f, y + 8f, textPaint)
        }
    }

    private fun drawMinMaxLines(canvas: Canvas, w: Float, h: Float, padding: Float) {
        if (minDb == Float.MAX_VALUE || maxDb == 0f) return

        val graphHeight = h - padding * 2

        // Min line (green)
        if (minDb >= minDbRange) {
            val minY = padding + graphHeight * (1 - (minDb - minDbRange) / (maxDbRange - minDbRange))
            minMaxPaint.color = Color.parseColor("#00E676")
            canvas.drawLine(padding, minY, w - 10, minY, minMaxPaint)

            minMaxTextPaint.color = Color.parseColor("#00E676")
            canvas.drawText("MIN ${minDb.toInt()}", w - 120, minY - 8, minMaxTextPaint)
        }

        // Max line (red)
        if (maxDb > minDbRange) {
            val maxY = padding + graphHeight * (1 - (maxDb - minDbRange) / (maxDbRange - minDbRange))
            minMaxPaint.color = Color.parseColor("#FF1744")
            canvas.drawLine(padding, maxY, w - 10, maxY, minMaxPaint)

            minMaxTextPaint.color = Color.parseColor("#FF1744")
            canvas.drawText("MAX ${maxDb.toInt()}", w - 120, maxY - 8, minMaxTextPaint)
        }
    }

    private fun drawWave(canvas: Canvas, w: Float, h: Float, padding: Float) {
        val graphWidth = w - padding - 10
        val graphHeight = h - padding * 2
        val stepX = graphWidth / maxReadings

        wavePath.reset()
        fillPath.reset()

        readings.forEachIndexed { index, db ->
            val x = padding + index * stepX
            val normalizedDb = ((db - minDbRange) / (maxDbRange - minDbRange)).coerceIn(0f, 1f)
            val y = padding + graphHeight * (1 - normalizedDb)

            if (index == 0) {
                wavePath.moveTo(x, y)
                fillPath.moveTo(x, h - padding)
                fillPath.lineTo(x, y)
            } else {
                wavePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        // Complete fill path
        if (readings.isNotEmpty()) {
            val lastX = padding + (readings.size - 1) * stepX
            fillPath.lineTo(lastX, h - padding)
            fillPath.close()
        }

        // Get color based on current level
        val currentDb = readings.lastOrNull() ?: 0f
        val color = getColorForDb(currentDb)

        // Draw gradient fill
        fillPaint.shader = LinearGradient(
            0f, padding, 0f, h - padding,
            Color.argb(80, Color.red(color), Color.green(color), Color.blue(color)),
            Color.argb(10, Color.red(color), Color.green(color), Color.blue(color)),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(fillPath, fillPaint)

        // Draw wave line
        wavePaint.color = color
        canvas.drawPath(wavePath, wavePaint)

        // Draw current value dot
        if (readings.isNotEmpty()) {
            val lastIndex = readings.size - 1
            val lastX = padding + lastIndex * stepX
            val lastDb = readings[lastIndex]
            val normalizedDb = ((lastDb - minDbRange) / (maxDbRange - minDbRange)).coerceIn(0f, 1f)
            val lastY = padding + graphHeight * (1 - normalizedDb)

            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }
            canvas.drawCircle(lastX, lastY, 8f, dotPaint)

            // Glow effect
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.argb(100, Color.red(color), Color.green(color), Color.blue(color))
                style = Paint.Style.FILL
                maskFilter = BlurMaskFilter(15f, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawCircle(lastX, lastY, 12f, glowPaint)
        }
    }

    private fun getColorForDb(db: Float): Int {
        return when {
            db < 50 -> Color.parseColor("#00E676")  // Green
            db < 70 -> Color.parseColor("#FFD600")  // Yellow
            db < 85 -> Color.parseColor("#FF9100")  // Orange
            else -> Color.parseColor("#FF1744")     // Red
        }
    }

    fun addReading(db: Float) {
        readings.add(db)
        if (readings.size > maxReadings) {
            readings.removeAt(0)
        }

        // Update min/max/avg
        if (db < minDb) minDb = db
        if (db > maxDb) maxDb = db
        avgDb = readings.sum() / readings.size

        invalidate()
    }

    fun getMinDb(): Float = if (minDb == Float.MAX_VALUE) 0f else minDb
    fun getMaxDb(): Float = maxDb
    fun getAvgDb(): Float = avgDb

    fun reset() {
        readings.clear()
        minDb = Float.MAX_VALUE
        maxDb = 0f
        avgDb = 0f
        invalidate()
    }
}
