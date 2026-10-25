package com.viktorolsson.spotter.core.data.sync

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The requests [SupabaseSyncRemote] sends: every read of sync_rows names its owner. */
class SupabaseSyncRemoteTest {
    private val requests = mutableListOf<Url>()

    private val remote = SupabaseSyncRemote {
        createSupabaseClient("https://example.supabase.co", "publishable-key") {
            httpEngine = MockEngine { request ->
                requests += request.url
                respond("[]", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
            install(Postgrest)
        }
    }

    @Test
    fun hasRowsFiltersOnTheOwner() = runTest {
        remote.hasRows(USER)
        val url = requests.single()
        assertEquals("/rest/v1/sync_rows", url.encodedPath)
        assertEquals("eq.$USER", url.parameters["user_id"])
    }

    @Test
    fun pullFiltersOnTheOwner() = runTest {
        remote.pull(USER, since = null, offset = 0, limit = 500)
        remote.pull(USER, since = "2026-10-27T10:00:00Z", offset = 500, limit = 500)
        requests.forEach { assertEquals("eq.$USER", it.parameters["user_id"]) }
        assertEquals("gt.2026-10-27T10:00:00Z", requests[1].parameters["server_updated_at"])
    }

    private companion object {
        const val USER = "00000000-0000-4000-8000-00000000000a"
    }
}
