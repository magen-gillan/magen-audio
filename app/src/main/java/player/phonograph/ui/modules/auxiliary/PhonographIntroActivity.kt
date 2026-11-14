/*
 * Copyright (c) 2022~2026 chr_56
 */
package player.phonograph.ui.modules.auxiliary

import player.phonograph.settings.PrerequisiteSettings
import player.phonograph.ui.modules.auxiliary.welcome.AudioWelcome
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * Single-screen first-run experience (Compose). Alisha asks for every permission with one
 * button, then the app opens through the normal launcher so migration checks are never skipped.
 * Replaces the old XML layout (activity_intro_welcome.xml can be deleted).
 */
class PhonographIntroActivity : AppCompatActivity() {
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { AudioWelcome(onFinished = ::completeIntro) }
    }

    private fun completeIntro() {
        if (leaving) return
        leaving = true
        PrerequisiteSettings.instance(this).introShown = true
        startActivity(Intent(this, LauncherActivity::class.java))
        finish()
    }
}
