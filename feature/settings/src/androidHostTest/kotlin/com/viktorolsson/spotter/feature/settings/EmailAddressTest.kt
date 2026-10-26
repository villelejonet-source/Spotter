package com.viktorolsson.spotter.feature.settings

import android.util.Patterns
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The shared email check accepts exactly what Android's `Patterns.EMAIL_ADDRESS` did. */
@RunWith(AndroidJUnit4::class)
class EmailAddressTest {
    private val samples = listOf(
        "anna@example.com", "anna.svensson+gym@mail.example.se", "a_b%c-d@x.io", "A1@B2.C3",
        "", "anna", "anna@", "@example.com", "anna@example", "anna@.com", "anna@-example.com",
        "anna@example..com", "anna example@x.se", "anna@exa_mple.com", "åsa@example.se", "anna@example.com.",
        "x".repeat(256) + "@example.com", "x".repeat(257) + "@example.com",
    )

    @Test
    fun matchesAndroidPatterns() {
        samples.forEach { email ->
            assertEquals(email, Patterns.EMAIL_ADDRESS.matcher(email).matches(), EMAIL_ADDRESS.matches(email))
        }
    }
}
