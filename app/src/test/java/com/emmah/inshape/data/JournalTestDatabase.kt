package com.emmah.inshape.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

fun createTestDatabase(): JournalDatabase = Room.inMemoryDatabaseBuilder(
    ApplicationProvider.getApplicationContext<Context>(),
    JournalDatabase::class.java
).allowMainThreadQueries().build()
