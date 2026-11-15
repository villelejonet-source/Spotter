package com.viktorolsson.spotter

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri

/**
 * Health Connect's permission screen links to "how this app handles your data", and an app
 * must answer that link to be allowed to ask for access. The answer is the privacy policy on
 * the website, the same page Google Play links to.
 */
class HealthPrivacyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY.toUri()))
        } catch (_: ActivityNotFoundException) {
            // No browser: nothing to show.
        }
        finish()
    }

    private companion object {
        const val PRIVACY_POLICY = "https://spotter.northstarsolutions.se/privacy"
    }
}
