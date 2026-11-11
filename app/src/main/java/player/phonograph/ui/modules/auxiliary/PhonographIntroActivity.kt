/*
 * Copyright (c) 2022~2026 chr_56
 */
package player.phonograph.ui.modules.auxiliary

import lib.activityresultcontract.IRequestMultiplePermission
import lib.activityresultcontract.RequestMultiplePermissionsDelegate
import lib.activityresultcontract.registerActivityResultLauncherDelegate
import player.phonograph.R
import player.phonograph.foundation.permission.hasPermission
import player.phonograph.foundation.permission.necessaryPermissions
import player.phonograph.settings.PrerequisiteSettings
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Single-screen first-run experience. The old blue AppIntro and separate
 * permission slide are intentionally replaced by one localized screen.
 */
class PhonographIntroActivity : AppCompatActivity(), IRequestMultiplePermission {
    override val requestMultiplePermissionsDelegate: RequestMultiplePermissionsDelegate =
        RequestMultiplePermissionsDelegate()

    private lateinit var permissionHint: TextView
    private lateinit var grantButton: Button
    private var leaving = false

    private val requiredPermissions
        get() = necessaryPermissions.filter { it.required }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_intro_welcome)
        registerActivityResultLauncherDelegate(requestMultiplePermissionsDelegate)

        permissionHint = findViewById(R.id.intro_permission_hint)
        grantButton = findViewById(R.id.intro_grant_button)
        grantButton.setOnClickListener { requestRequiredPermissions() }
        updatePermissionState()
    }

    override fun onResume() {
        super.onResume()
        if (!leaving) updatePermissionState()
    }

    private fun requestRequiredPermissions() {
        val permissions = requiredPermissions.map { it.permission }.toTypedArray()
        if (permissions.isEmpty() || permissions.all { hasPermission(this, it) }) {
            completeIntro()
            return
        }
        requestMultiplePermissionsDelegate.launch(permissions) {
            updatePermissionState()
        }
    }

    private fun updatePermissionState() {
        val granted = requiredPermissions.all { hasPermission(this, it.permission) }
        if (granted) {
            completeIntro()
        } else {
            permissionHint.setText(R.string.intro_permission_hint)
            grantButton.setText(R.string.action_grant_all)
        }
    }

    private fun completeIntro() {
        if (leaving) return
        leaving = true
        PrerequisiteSettings.instance(this).introShown = true
        // Return through the normal launcher so migration checks are never skipped.
        startActivity(Intent(this, LauncherActivity::class.java))
        finish()
    }
}
