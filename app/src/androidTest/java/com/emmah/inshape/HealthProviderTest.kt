package com.emmah.inshape

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.data.WorkoutEntity
import com.emmah.inshape.data.newId
import com.emmah.inshape.health.AndroidHealthGateway
import com.emmah.inshape.health.SharedWorkout
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class HealthProviderTest {
    @Test fun aDeniedHealthPermissionLeavesLocalJournalUsable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(
            "Requires Android 14+ Health Connect provider",
            Build.VERSION.SDK_INT >= 34 &&
                HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        )
        assertFalse(
            "Run this check with exercise-write permission denied",
            AndroidHealthGateway(context).canWrite()
        )
        val name = "provider-local-${newId()}.db"
        val local = AppContainer(context, databaseName = name, integrationsEnabled = false)
        try {
            local.storageReady.await()
            val active = local.repository.startWorkout(com.emmah.inshape.data.StartSource.Empty)
            assertEquals(
                active,
                local.repository
                    .snapshot()
                    .active!!
                    .row.id
            )
        } finally {
            local.close()
            context.deleteDatabase(name)
            local.storageRoot.deleteRecursively()
        }
    }

    @Test fun bRealProviderStoresUpdatesAndDeletesOneIdentity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(
            "Requires Android 14+ Health Connect provider",
            Build.VERSION.SDK_INT >= 34 &&
                HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        )
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val write = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
        val read = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        automation.grantRuntimePermission(context.packageName, write)
        automation.grantRuntimePermission(context.packageName, read)
        val gateway = AndroidHealthGateway(context)
        val id = newId()
        val clientId = "inshape:workout:$id"
        val end = Instant.now().minusSeconds(60)
        val start = end.minusSeconds(600)
        val workout =
            WorkoutEntity(
                id,
                journalDate = start.atZone(java.time.ZoneOffset.UTC).toLocalDate().toString(),
                start = start.toString(),
                end = end.toString(),
                zoneId = "UTC",
                createdAt = start.toString(),
                modifiedAt = end.toString()
            )
        try {
            assertTrue(gateway.canWrite())
            val first = gateway.upsert(
                SharedWorkout(clientId, 1, start.toString(), end.toString(), "UTC", false)
            )
            val second = gateway.upsert(
                SharedWorkout(
                    clientId,
                    2,
                    start.toString(),
                    end.plusSeconds(10).toString(),
                    "UTC",
                    false
                )
            )
            assertEquals(first, second)
            assertEquals(2L, gateway.existingVersion(workout, clientId))
        } finally {
            automation.grantRuntimePermission(context.packageName, write)
            gateway.delete(clientId)
            assertEquals(0L, gateway.existingVersion(workout, clientId))
        }
    }
}
