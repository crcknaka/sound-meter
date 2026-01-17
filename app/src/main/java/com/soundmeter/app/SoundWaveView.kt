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

    private val pointCount = 100
    private val readings = FloatArray(pointCount)
    private var currentDb = 0f

    private var minDb = Float.MAX_VALUE
    private var maxDb = 0f

    // Paints
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#27272A")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = Color.parseColor("#52525B")
    }

    private val minMaxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }

    private val minMaxTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val wavePath = Path()
    private val fillPath = Path()

    // Colors - Modern vibrant palette
    private val colorGreen = Color.parseColor("#22C55E")
    private val colorYellow = Color.parseColor("#EAB308")
    private val colorOrange = Color.parseColor("#F97316")
    private val colorRed = Color.parseColor("#EF4444")

    private val minDbRange = 20f
    private val maxDbRange = 100f

    // Grid settings
    private val dbLevels = intArrayOf(20, 40, 60, 80, 100)
    private val verticalGridCount = 10

    private var cachedGradient: LinearGradient? = null
    private var lastColor = 0
    private var lastHeight = 0f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padding = 45f

        drawGrid(canvas, w, h, padding)
        drawMinMaxLines(canvas, w, h, padding)
        drawWave(canvas, w, h, padding)
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float, padding: Float) {
        val graphWidth = w - padding - 10
        val graphHeight = h - padding * 2

        // Draw horizontal lines for dB levels
        for (db in dbLevels) {
            val y = padding + graphHeight * (1 - (db - minDbRange) / (maxDbRange - minDbRange))
            canvas.drawLine(padding, y, w - 10, y, gridPaint)
            canvas.drawText("$db", 6f, y + 7f, textPaint)
        }

        // Draw vertical lines to create grid rectangles
        val stepX = graphWidth / verticalGridCount
        for (i in 0..verticalGridCount) {
            val x = padding + i * stepX
            canvas.drawLine(x, padding, x, h - padding, gridPaint)
        }
    }

    private fun drawMinMaxLines(canvas: Canvas, w: Float, h: Float, padding: Float) {
        if (minDb == Float.MAX_VALUE || maxDb == 0f) return

        val graphHeight = h - padding * 2

        if (minDb >= minDbRange) {
            val minY = padding + graphHeight * (1 - (minDb - minDbRange) / (maxDbRange - minDbRange))
            minMaxPaint.color = colorGreen
            canvas.drawLine(padding, minY, w - 10, minY, minMaxPaint)
            minMaxTextPaint.color = colorGreen
            canvas.drawText("MIN ${minDb.toInt()}", w - 100, minY - 5, minMaxTextPaint)
        }

        if (maxDb > minDbRange) {
            val maxY = padding + graphHeight * (1 - (maxDb - minDbRange) / (maxDbRange - minDbRange))
            minMaxPaint.color = colorRed
            canvas.drawLine(padding, maxY, w - 10, maxY, minMaxPaint)
            minMaxTextPaint.color = colorRed
            canvas.drawText("MAX ${maxDb.toInt()}", w - 100, maxY - 5, minMaxTextPaint)
        }
    }

    private fun drawWave(canvas: Canvas, w: Float, h: Float, padding: Float) {
        val graphWidth = w - padding - 10
        val graphHeight = h - padding * 2
        val stepX = graphWidth / (pointCount - 1)

        wavePath.reset()
        fillPath.reset()

        var lastX = padding
        var lastY = h - padding

        for (i in 0 until pointCount) {
            val db = readings[i]
            val x = padding + i * stepX

            val normalizedDb = ((db - minDbRange) / (maxDbRange - minDbRange)).coerceIn(0f, 1f)
            val y = padding + graphHeight * (1 - normalizedDb)

            if (i == 0) {
                wavePath.moveTo(x, y)
                fillPath.moveTo(x, h - padding)
                fillPath.lineTo(x, y)
            } else {
                // Direct lines - no smoothing
                wavePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }

            lastX = x
            lastY = y
        }

        fillPath.lineTo(lastX, h - padding)
        fillPath.close()

        val color = getColorForDb(currentDb)

        // Update gradient only when needed
        if (color != lastColor || h != lastHeight) {
            cachedGradient = LinearGradient(
                0f, padding, 0f, h - padding,
                Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)),
                Shader.TileMode.CLAMP
            )
            lastColor = color
            lastHeight = h
        }

        fillPaint.shader = cachedGradient
        canvas.drawPath(fillPath, fillPaint)

        wavePaint.color = color
        canvas.drawPath(wavePath, wavePaint)

        // Current point dot
        dotPaint.color = color
        canvas.drawCircle(lastX, lastY, 6f, dotPaint)
    }

    private fun getColorForDb(db: Float): Int {
        return when {
            db < 40 -> colorGreen
            db < 60 -> colorYellow
            db < 80 -> colorOrange
            else -> colorRed
        }
    }

    // Instant update - shifts history and adds new reading
    fun addReading(db: Float) {
        // Shift all readings left
        System.arraycopy(readings, 1, readings, 0, pointCount - 1)
        // Add new reading at the end
        readings[pointCount - 1] = db
        currentDb = db

        if (db < minDb) minDb = db
        if (db > maxDb) maxDb = db

        invalidate()
    }

    fun reset() {
        readings.fill(0f)
        currentDb = 0f
        minDb = Float.MAX_VALUE
        maxDb = 0f
        cachedGradient = null
        invalidate()
    }
}
