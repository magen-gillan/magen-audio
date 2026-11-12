/*
 *  Copyright (c) 2022~2026 chr_56
 */

package player.phonograph.model.ui

import player.phonograph.settings.Keys
import player.phonograph.settings.Settings
import android.content.Context
import android.graphics.Color

/**
 * Appearance of the spinning disc album cover (Now Playing screen).
 *
 * The four customizations are inspired by these open source (Apache License 2.0) projects:
 * - andremion/Music-Cover-View: rectangle <-> circle shape, "track" grooves ([grooves])
 * - death14stroke/music-cover-view: ring and stroke around the disc ([ring])
 * - iammert/MusicPlayerView: rotation velocity ([speed]), progress ring around the cover ([progressRing])
 */
data class DiscStyle(
    val enabled: Boolean,
    val grooves: Boolean,
    val ring: String,
    val speed: String,
    val progressRing: Boolean,
) {

    /** seconds of one full turn */
    val secondsPerTurn: Float
        get() = when (speed) {
            SPEED_SLOW   -> 14f
            SPEED_FAST   -> 4f
            SPEED_RECORD -> 1.8f // 33 1/3 rpm, a real record
            else         -> 8f
        }

    /** @return color of the ring, or 0 if no ring */
    fun ringColor(accent: Int): Int = when (ring) {
        RING_WHITE  -> Color.WHITE
        RING_BLACK  -> Color.BLACK
        RING_ACCENT -> accent
        RING_GOLD   -> 0xFFFFC107.toInt()
        else        -> 0
    }

    companion object {
        const val RING_NONE = "none"
        const val RING_WHITE = "white"
        const val RING_BLACK = "black"
        const val RING_ACCENT = "accent"
        const val RING_GOLD = "gold"

        const val SPEED_SLOW = "slow"
        const val SPEED_NORMAL = "normal"
        const val SPEED_FAST = "fast"
        const val SPEED_RECORD = "record"

        val OFF = DiscStyle(false, true, RING_WHITE, SPEED_NORMAL, true)

        fun from(context: Context): DiscStyle {
            val settings = Settings(context)
            return DiscStyle(
                enabled = settings[Keys.discCoverEnabled].data,
                grooves = settings[Keys.discCoverGrooves].data,
                ring = settings[Keys.discCoverRing].data,
                speed = settings[Keys.discCoverSpeed].data,
                progressRing = settings[Keys.discCoverProgressRing].data,
            )
        }
    }
}
