package com.sanket_satpute_20.ironmind.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sanket_satpute_20.ironmind.data.local.entity.UserProfileEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IronMindDatabaseTest {

    private lateinit var db: IronMindDatabase
    private lateinit var dao: com.sanket_satpute_20.ironmind.data.local.dao.IronMindDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, IronMindDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.ironMindDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun writeAndReadUserProfile() = runBlocking {
        val profile = UserProfileEntity(
            id = "user-123",
            createdAt = 1000L,
            updatedAt = 2000L,
            displayName = "Sanket",
            timezone = "UTC",
            createdFrom = "APP",
            status = "ACTIVE",
            schemaVersion = 1
        )
        dao.insertUserProfile(profile)

        val loaded = dao.getUserProfile("user-123")
        assertNotNull(loaded)
        assertEquals(profile.displayName, loaded?.displayName)
    }
}
