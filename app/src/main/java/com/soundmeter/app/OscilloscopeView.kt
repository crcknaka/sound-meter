package com.soundmeter.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class OscilloscopeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentDb = 0f
    private var targetDb = 0f
    private var minDb = Float.MAX_VALUE
    private var maxDb = 0f

    private val animationSpeed = 0.15f

    // Wave history for smooth scrolling effect
    private val waveHistory = FloatArray(120) { 0f }
    private var waveIndex = 0
    private var phase = 0f

    // Paints
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#1E3A2F")
        strokeWidth = 1f
    }

    private val gridBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#22C55E")
        strokeWidth = 2f
    }

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val waveGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#22C55E")
    }

    private val dbTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 140f
        color = Color.parseColor("#22C55E")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val unitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 48f
        color = Color.parseColor("#166534")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }

    private val levelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 42f
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.12f
        typeface = Typeface.MONOSPACE
    }

    private val statsLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = Color.parseColor("#166534")
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.08f
        typeface = Typeface.MONOSPACE
    }

    private val statsValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 52f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val scopeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 24f
        color = Color.parseColor("#166534")
        typeface = Typeface.MONOSPACE
    }

    // Colors - Phosphor green oscilloscope theme
    private val colorGreen = Color.parseColor("#22C55E")
    private val colorGreenDark = Color.parseColor("#166534")
    private val colorGreenBright = Color.parseColor("#4ADE80")
    private val colorYellow = Color.parseColor("#FACC15")
    private val colorOrange = Color.parseColor("#FB923C")
    private val colorRed = Color.parseColor("#F87171")
    private val colorBackground = Color.parseColor("#0A1F14")

    private val minDbRange = 20f
    private val maxDbRange = 100f

    private val wavePath = Path()
    private val scopeRect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Animate towards target
        currentDb += (targetDb - currentDb) * animationSpeed
        phase += 0.15f

        // Update wave history
        waveHistory[waveIndex] = currentDb
        waveIndex = (waveIndex + 1) % waveHistory.size

        val normalizedDb = ((currentDb - minDbRange) / (maxDbRange - minDbRange)).coerceIn(0f, 1f)

        // Define scope area (upper portion)
        val scopeMargin = 28f
        val scopeTop = 20f
        val scopeBottom = h * 0.52f
        val scopeLeft = scopeMargin
        val scopeRight = w - scopeMargin
        scopeRect.set(scopeLeft, scopeTop, scopeRight, scopeBottom)

        // Draw dark scope background
        val bgPaint = Paint().apply {
            color = colorBackground
        }
        canvas.drawRoundRect(scopeRect, 12f, 12f, bgPaint)

        // Draw grid
        drawGrid(canvas, scopeRect)

        // Draw waveform
        drawWaveform(canvas, scopeRect, normalizedDb)

        // Draw scope labels
        drawScopeLabels(canvas, scopeRect)

        // Draw scope border
        canvas.drawRoundRect(scopeRect, 12f, 12f, gridBorderPaint)

        // Draw dB value below scope
        val dbY = h * 0.66f
        dbTextPaint.color = getColorForDb(currentDb)
        canvas.drawText("${currentDb.toInt()}", w / 2, dbY, dbTextPaint)

        unitTextPaint.color = getColorForDb(currentDb).withAlpha(0.6f)
        canvas.drawText("dB SPL", w / 2, dbY + 45f, unitTextPaint)

        // Draw level description
        val levelText = getLevelText(currentDb)
        levelTextPaint.color = getColorForDb(currentDb)
        canvas.drawText(levelText, w / 2, h * 0.82f, levelTextPaint)

        // Draw MIN / PEAK / MAX stats at bottom
        val statsY = h - 30f
        val statSpacing = w / 4

        // MIN
        canvas.drawText("MIN", statSpacing, statsY - 35f, statsLabelPaint)
        statsValuePaint.color = colorGreen
        val minText = if (minDb == Float.MAX_VALUE) "--" else "${minDb.toInt()}"
        canvas.drawText(minText, statSpacing, statsY + 12f, statsValuePaint)

        // PEAK indicator
        canvas.drawText("PEAK", w / 2, statsY - 35f, statsLabelPaint)
        statsValuePaint.color = getColorForDb(currentDb)
        canvas.drawText(getLevelShort(currentDb), w / 2, statsY + 12f, statsValuePaint)

        // MAX
        canvas.drawText("MAX", w - statSpacing, statsY - 35f, statsLabelPaint)
        statsValuePaint.color = colorRed
        val maxText = if (maxDb == 0f) "--" else "${maxDb.toInt()}"
        canvas.drawText(maxText, w - statSpacing, statsY + 12f, statsValuePaint)

        // Continue animation
        postInvalidateOnAnimation()
    }

    private fun drawGrid(canvas: Canvas, rect: RectF) {
        val gridColumns = 10
        val gridRows = 6

        val cellWidth = rect.width() / gridColumns
        val cellHeight = rect.height() / gridRows

        // Draw vertical lines
        for (i in 1 until gridColumns) {
            val x = rect.left + i * cellWidth
            gridPaint.strokeWidth = if (i == gridColumns / 2) 1.5f else 1f
            canvas.drawLine(x, rect.top, x, rect.bottom, gridPaint)
        }

        // Draw horizontal lines
        for (i in 1 until gridRows) {
            val y = rect.top + i * cellHeight
            gridPaint.strokeWidth = if (i == gridRows / 2) 1.5f else 1f
            canvas.drawLine(rect.left, y, rect.right, y, gridPaint)
        }

        // Draw center crosshair dots
        val dotSpacing = cellWidth / 5
        val centerY = rect.centerY()
        val centerX = rect.centerX()

        gridPaint.strokeWidth = 2f
        for (i in -20..20) {
            val x = centerX + i * dotSpacing
            if (x > rect.left && x < rect.right) {
                canvas.drawPoint(x, centerY, gridPaint)
            }
        }

        for (i in -10..10) {
            val y = centerY + i * dotSpacing
            if (y > rect.top && y < rect.bottom) {
                canvas.drawPoint(centerX, y, gridPaint)
            }
        }
    }

    private fun drawWaveform(canvas: Canvas, rect: RectF, normalizedDb: Float) {
        wavePath.reset()

        val points = waveHistory.size
        val stepX = rect.width() / points

        val amplitude = rect.height() * 0.35f * normalizedDb
        val centerY = rect.centerY()

        var firstPoint = true

        for (i in 0 until points) {
            val historyIndex = (waveIndex + i) % waveHistory.size
            val histDb = waveHistory[historyIndex]
            val histNormalized = ((histDb - minDbRange) / (maxDbRange - minDbRange)).coerceIn(0f, 1f)

            val x = rect.left + i * stepX

            // Create wave effect combining sine wave with amplitude based on dB
            val waveAmplitude = rect.height() * 0.35f * histNormalized
            val frequency = 2f + histNormalized * 3f
            val y = centerY + sin((i * 0.15f + phase) * frequency) * waveAmplitude

            if (firstPoint) {
                wavePath.moveTo(x, y)
                firstPoint = false
            } else {
                wavePath.lineTo(x, y)
            }
        }

        // Draw glow effect
        val waveColor = getColorForDb(currentDb)
        waveGlowPaint.color = waveColor.withAlpha(0.3f)
        waveGlowPaint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(wavePath, waveGlowPaint)

        // Draw main wave
        wavePaint.color = waveColor
        wavePaint.shader = LinearGradient(
            rect.left, 0f, rect.right, 0f,
            intArrayOf(waveColor.withAlpha(0.3f), waveColor, waveColor, waveColor.withAlpha(0.3f)),
            floatArrayOf(0f, 0.2f, 0.8f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(wavePath, wavePaint)
        wavePaint.shader = null

        // Draw scan line effect at the end
        val scanX = rect.right - 10f
        val gradient = LinearGradient(
            scanX - 60f, 0f, scanX, 0f,
            intArrayOf(Color.TRANSPARENT, colorGreen.withAlpha(0.5f)),
            null,
            Shader.TileMode.CLAMP
        )
        scanLinePaint.shader = gradient
        canvas.drawLine(scanX - 60f, rect.top, scanX - 60f, rect.bottom, scanLinePaint)
        scanLinePaint.shader = null
    }

    private fun drawScopeLabels(canvas: Canvas, rect: RectF) {
        // Top left - frequency indicator
        canvas.drawText("SOUND LEVEL", rect.left + 10f, rect.top + 20f, scopeLabelPaint)

        // Top right - time base
        canvas.drawText("REAL-TIME", rect.right - 90f, rect.top + 20f, scopeLabelPaint)

        // dB scale on right side
        val scaleLabels = listOf("100", "80", "60", "40", "20")
        val spacing = rect.height() / (scaleLabels.size + 1)
        scaleLabels.forEachIndexed { index, label ->
            val y = rect.top + (index + 1) * spacing
            canvas.drawText(label, rect.right - 30f, y + 8f, scopeLabelPaint)
        }
    }

    private fun Int.withAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(this), Color.green(this), Color.blue(this))
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
            db < 30 -> "▸ VERY QUIET ◂"
            db < 40 -> "▸ QUIET ◂"
            db < 50 -> "▸ MODERATE ◂"
            db < 60 -> "▸ CONVERSATION ◂"
            db < 70 -> "▸ BUSY ◂"
            db < 80 -> "▸ LOUD ◂"
            db < 90 -> "▸ WARNING ◂"
            else -> "▸▸ DANGER ◂◂"
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
        waveHistory.fill(0f)
        waveIndex = 0
        phase = 0f
        invalidate()
    }
}
