package com.viktorolsson.spotter.core.data.sync

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/** The requests [SupabaseSyncRemote] sends: every read of sync_rows names its owner. */
class SupabaseSyncRemoteTest {
    private val requests = mutableListOf<Url>()
    private val bodies = mutableListOf<String>()

    private val remote = SupabaseSyncRemote {
        createSupabaseClient("https://example.supabase.co", "publishable-key") {
            httpEngine = MockEngine { request ->
                requests += request.url
                bodies += (request.body as? TextContent)?.text.orEmpty()
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

    @Test
    fun checkInCallsTheFunctionWithOnlyTheVersions() = runTest {
        remote.checkIn(DeviceCheckIn(deviceId = DEVICE, versionCode = 7, schemaVersion = 8))
        assertEquals("/rest/v1/rpc/check_in_device", requests.single().encodedPath)
        val body = Json.parseToJsonElement(bodies.single()).jsonObject
        assertEquals(setOf("device_id", "version_code", "schema_version"), body.keys)
        assertEquals(DEVICE, body["device_id"]!!.jsonPrimitive.content)
        assertEquals(7, body["version_code"]!!.jsonPrimitive.int)
        assertEquals(8, body["schema_version"]!!.jsonPrimitive.int)
    }

    private companion object {
        const val DEVICE = "10000000-0000-4000-8000-000000000001"
        const val USER = "00000000-0000-4000-8000-00000000000a"
    }
}
