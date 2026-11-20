package com.emmah.inshape.health

import android.app.Application
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.Preferences
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class AndroidHealthGatewayTest {
    @Test fun withoutAProviderNoHealthValuesAreAvailable() = runBlocking {
        val gateway = AndroidHealthGateway(ApplicationProvider.getApplicationContext<Context>())
        val now = ZonedDateTime.parse("2026-11-13T08:00:00Z")
        assertTrue(gateway.availability() != HealthConnectClient.SDK_AVAILABLE)
        assertTrue(gateway.grants().isEmpty())
        assertFalse(gateway.canWrite())

        val context = gateway.readContext(Preferences(readSteps = true, readHeartRate = true), now)
        assertEquals(HealthContext(), context)
    }

    @Test fun eachCapabilityChangesOnlyItsOwnPreference() {
        val defaults = Preferences()
        val expected = mapOf(
            HealthCapability.SHARE to defaults.copy(shareWorkouts = true),
            HealthCapability.STEPS to defaults.copy(readSteps = true),
            HealthCapability.SLEEP to defaults.copy(readSleep = true),
            HealthCapability.HEART to defaults.copy(readHeartRate = true)
        )
        for ((capability, enabled) in expected) {
            assertFalse(capability.enabled(defaults))
            assertTrue(capability.enabled(enabled))
            assertEquals(enabled, capability.change(defaults, true))
            assertEquals(defaults, capability.change(enabled, false))
        }
    }
}
