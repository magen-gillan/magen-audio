package player.phonograph.ui.modules.auxiliary.welcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import player.phonograph.R

/** Magen Audio welcome. Permissions: music, album art (photos), notifications. */
@Composable
fun AudioWelcome(onFinished: () -> Unit) {
    MagenWelcomeScreen(
        brandName = R.string.mwelcome_brand,
        tagline = R.string.mwelcome_tag,
        helloTitle = R.string.mwelcome_hi_t,
        helloBody = R.string.mwelcome_hi_b,
        logo = R.drawable.magen_gillan_logo,
        accent = Color(0xFFFF7AB6),
        permissions = remember { listOf(MagenPermissions.audio(), MagenPermissions.images(), MagenPermissions.notifications()) },
        onFinished = { onFinished() },
    )
}
