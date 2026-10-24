package com.viktorolsson.spotter.core.data.sync

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Only a PKCE code on our own scheme and host signs the phone in; tokens in a fragment never do. */
@RunWith(AndroidJUnit4::class)
class SignInLinksTest {
    @Test
    fun aPkceLinkIsAccepted() {
        assertEquals(SignInLink("abc-123", recovery = false), SignInLinks.parse(Uri.parse("spotter://login?code=abc-123")))
        assertEquals(SignInLink("abc", recovery = true), SignInLinks.parse(Uri.parse("spotter://login?code=abc&type=recovery")))
    }

    @Test
    fun aFragmentOnlyLinkIsIgnored() {
        // The implicit flow's shape: a crafted link could carry another account's tokens here.
        assertNull(SignInLinks.parse(Uri.parse("spotter://login#access_token=eyJ.x.y&refresh_token=r&type=recovery")))
        assertNull(SignInLinks.parse(Uri.parse("spotter://login#code=abc")))
        assertNull(SignInLinks.parse(Uri.parse("spotter://login")))
        assertNull(SignInLinks.parse(Uri.parse("spotter://login?code=")))
        assertNull(SignInLinks.parse(Uri.parse("spotter://login?error=access_denied&error_code=otp_expired")))
    }

    @Test
    fun otherSchemesAndHostsAreIgnored() {
        assertNull(SignInLinks.parse(null))
        assertNull(SignInLinks.parse(Uri.parse("https://login/?code=abc")))
        assertNull(SignInLinks.parse(Uri.parse("spotter://evil?code=abc")))
        assertNull(SignInLinks.parse(Uri.parse("spotter:login?code=abc")))
    }
}
