/*
 *  Copyright (c) 2022~2026 chr_56
 */

package player.phonograph.ui.views

import player.phonograph.model.ui.DiscStyle
import player.phonograph.service.MusicPlayerRemote
import androidx.appcompat.widget.AppCompatImageView
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import kotlin.math.max
import kotlin.math.min

/**
 * An [AppCompatImageView] that can show its image as a spinning vinyl disc.
 *
 * When [DiscStyle.enabled] is false it behaves exactly like a normal ImageView.
 * The disc only spins while [songId] is the current song and the player is playing.
 * (Original implementation; ideas about customization come from Music-Cover-View,
 * music-cover-view and MusicPlayerView, see [DiscStyle].)
 */
class DiscImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var style: DiscStyle = DiscStyle.OFF
    private var accentColor: Int = Color.WHITE

    /** id of the song of this cover */
    var songId: Long = -1

    private var angle = 0f
    private var progress = 0f
    private var lastFrameNanos = 0L
    private var running = false
    private var attached = false

    private val density = resources.displayMetrics.density

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF101010.toInt() }
    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 22
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF050505.toInt() }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
        alpha = 60
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val clipPath = Path()
    private val arcRect = RectF()

    fun applyStyle(newStyle: DiscStyle, accent: Int) {
        style = newStyle
        accentColor = accent
        updateTicker()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        updateTicker()
    }

    override fun onDetachedFromWindow() {
        attached = false
        running = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    private fun updateTicker() {
        if (attached && style.enabled && !running) {
            running = true
            lastFrameNanos = 0L
            postOnAnimation(frame)
        }
    }

    private val frame: Runnable = object : Runnable {
        override fun run() {
            if (!attached || !style.enabled) {
                running = false
                return
            }
            val now = System.nanoTime()
            val dt = if (lastFrameNanos == 0L) 0f else (now - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = now

            val isCurrent = songId >= 0 && MusicPlayerRemote.currentSong?.id == songId
            if (isCurrent && MusicPlayerRemote.isPlaying) {
                angle = (angle + dt * 360f / style.secondsPerTurn) % 360f
            }
            progress = if (isCurrent) {
                val total = MusicPlayerRemote.songDurationMillis
                if (total > 0) (MusicPlayerRemote.songProgressMillis.toFloat() / total).coerceIn(0f, 1f) else 0f
            } else 0f

            invalidate()
            postOnAnimation(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!style.enabled) {
            super.onDraw(canvas)
            return
        }

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val outer = min(w, h) / 2f - 6 * density
        if (outer <= 0f) return

        // progress ring (does not rotate)
        val arcWidth = 4 * density
        val discRadius = if (style.progressRing) outer - arcWidth - 4 * density else outer
        if (style.progressRing) {
            val r = outer - arcWidth / 2f
            arcRect.set(cx - r, cy - r, cx + r, cy + r)
            trackPaint.strokeWidth = arcWidth
            progressPaint.strokeWidth = arcWidth
            progressPaint.color = accentColor
            canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)
            if (progress > 0f) canvas.drawArc(arcRect, -90f, 360f * progress, false, progressPaint)
        }
        if (discRadius <= 0f) return

        val ringColor = style.ringColor(accentColor)
        val ringWidth = 3 * density

        // the disc itself (rotates)
        canvas.save()
        canvas.rotate(angle, cx, cy)

        canvas.drawCircle(cx, cy, discRadius, basePaint)

        val coverRadius = if (style.grooves) discRadius * 0.86f else discRadius * 0.97f
        if (style.grooves) {
            var r = coverRadius + 2.5f * density
            while (r < discRadius - density) {
                canvas.drawCircle(cx, cy, r, groovePaint)
                r += 3f * density
            }
        }

        drawCover(canvas, cx, cy, coverRadius)

        if (ringColor != 0) {
            ringPaint.color = ringColor
            ringPaint.strokeWidth = ringWidth
            canvas.drawCircle(cx, cy, coverRadius, ringPaint)
        }

        // spindle hole
        val holeRadius = max(discRadius * 0.07f, 4 * density)
        canvas.drawCircle(cx, cy, holeRadius, holePaint)
        if (ringColor != 0) {
            ringPaint.color = ringColor
            ringPaint.strokeWidth = max(density, ringWidth / 2f)
            canvas.drawCircle(cx, cy, holeRadius, ringPaint)
        }

        canvas.restore()
    }

    /**
     * draw the drawable as "center crop" inside a circle
     */
    private fun drawCover(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val d = drawable ?: return
        val dw = d.intrinsicWidth
        val dh = d.intrinsicHeight
        if (dw <= 0 || dh <= 0) return

        val scale = max(2 * radius / dw, 2 * radius / dh)
        canvas.save()
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        canvas.translate(cx - dw * scale / 2f, cy - dh * scale / 2f)
        canvas.scale(scale, scale)
        d.setBounds(0, 0, dw, dh)
        d.draw(canvas)
        canvas.restore()
    }
}
