package com.emmah.inshape

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.emmah.inshape.data.newId
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [34])
class SaveRecoveryTest {
    @Test fun diskFailurePreservesWriteOrderUntilExplicitRetry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "save-test-${newId()}.db"
        val container = AppContainer(context, databaseName = name); val store = ViewModelStore(); val vm = JournalViewModel(container); store.put("journal", vm)
        try {
            container.storageReady.await()
            val order = mutableListOf<String>(); var attempts = 0
            vm.write { if (attempts++ == 0) throw IOException("Injected full disk"); order += "first" }
            vm.write { order += "second" }
            withTimeout(5000) { do { shadowOf(Looper.getMainLooper()).idle(); delay(10) } while (vm.saveState.value == SaveState.Idle || vm.saveState.value == SaveState.Saving) }
            assertTrue("A later success must not hide or overtake the failed write", order.isEmpty())
            assertTrue(vm.saveState.value is SaveState.Failed)
            vm.retry()
            withTimeout(5000) { while (order.size < 2) { shadowOf(Looper.getMainLooper()).idle(); delay(10) } }
            assertEquals(listOf("first", "second"), order); assertEquals(SaveState.Saved, vm.saveState.value)
        } finally { store.clear(); container.close(); context.deleteDatabase(name); container.storageRoot.deleteRecursively() }
    }
}
