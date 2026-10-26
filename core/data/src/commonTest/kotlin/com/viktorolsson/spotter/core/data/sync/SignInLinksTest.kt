package com.viktorolsson.spotter.core.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Only a PKCE code on our own scheme and host signs the phone in; tokens in a fragment never do. */
class SignInLinksTest {
    @Test
    fun aPkceLinkIsAccepted() {
        assertEquals(SignInLink("abc-123", recovery = false), SignInLinks.parse("spotter://login?code=abc-123"))
        assertEquals(SignInLink("abc", recovery = true), SignInLinks.parse("spotter://login?code=abc&type=recovery"))
    }

    @Test
    fun aFragmentOnlyLinkIsIgnored() {
        // The implicit flow's shape: a crafted link could carry another account's tokens here.
        assertNull(SignInLinks.parse("spotter://login#access_token=eyJ.x.y&refresh_token=r&type=recovery"))
        assertNull(SignInLinks.parse("spotter://login#code=abc"))
        assertNull(SignInLinks.parse("spotter://login"))
        assertNull(SignInLinks.parse("spotter://login?code="))
        assertNull(SignInLinks.parse("spotter://login?error=access_denied&error_code=otp_expired"))
    }

    @Test
    fun otherSchemesAndHostsAreIgnored() {
        assertNull(SignInLinks.parse(null))
        assertNull(SignInLinks.parse("https://login/?code=abc"))
        assertNull(SignInLinks.parse("spotter://evil?code=abc"))
        assertNull(SignInLinks.parse("spotter:login?code=abc"))
        assertNull(SignInLinks.parse("SPOTTER://login?code=abc"))
        assertNull(SignInLinks.parse("spotter://login.evil.com?code=abc"))
        assertNull(SignInLinks.parse("spotter://evil/login?code=abc"))
    }

    /** Parsed like Android's Uri did: a trailing path, user info or port don't change the host. */
    @Test
    fun theQueryIsDecodedAndTheFirstValueWins() {
        assertEquals(SignInLink("a b/c", recovery = false), SignInLinks.parse("spotter://login/?code=a+b%2Fc"))
        assertEquals(SignInLink("abc", recovery = false), SignInLinks.parse("spotter://login?code=abc&code=other"))
        assertEquals(SignInLink("abc", recovery = false), SignInLinks.parse("spotter://login?code=abc#type=recovery"))
    }
}
