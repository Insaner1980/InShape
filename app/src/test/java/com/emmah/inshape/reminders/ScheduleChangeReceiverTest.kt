package com.emmah.inshape.reminders

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
class ScheduleChangeReceiverTest {
    @Test fun unrelatedAndMissingActionsDoNotScheduleWork() {
        val context = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = error("Unexpected scheduling")
        }
        val receiver = ScheduleChangeReceiver()
        receiver.onReceive(context, Intent("com.emmah.inshape.UNRELATED"))
        receiver.onReceive(context, Intent())
    }
}
