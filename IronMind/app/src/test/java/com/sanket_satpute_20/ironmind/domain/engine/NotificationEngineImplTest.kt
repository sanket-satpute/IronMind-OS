package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationCandidate
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationDeliveryStatus
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationPriority
import com.sanket_satpute_20.ironmind.domain.model.notification.NotificationRecord
import com.sanket_satpute_20.ironmind.testutil.fake.FakeIronLogger
import com.sanket_satpute_20.ironmind.testutil.fake.FakeNotificationProvider
import com.sanket_satpute_20.ironmind.testutil.fake.FakeNotificationRecordRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class NotificationEngineImplTest {

    private lateinit var repository: FakeNotificationRecordRepository
    private lateinit var provider: FakeNotificationProvider
    private lateinit var logger: FakeIronLogger
    
    private var currentTimeMs: Long = 0L

    private lateinit var engine: NotificationEngineImpl

    @Before
    fun setup() {
        repository = FakeNotificationRecordRepository()
        provider = FakeNotificationProvider()
        logger = FakeIronLogger()

        engine = NotificationEngineImpl(
            notificationRecordRepository = repository,
            notificationProvider = provider,
            logger = logger,
            timeProvider = { currentTimeMs }
        )
    }

    private fun setTime(hour: Int) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        currentTimeMs = calendar.timeInMillis
    }

    @Test
    fun `delivers normal notification during active hours`() = runTest {
        setTime(14) // 2 PM
        
        val candidate = NotificationCandidate(
            id = "c1",
            title = "Test",
            message = "Message",
            priority = NotificationPriority.DEFAULT,
            channelId = "channel"
        )
        
        val result = engine.process(candidate)
        
        assertEquals(NotificationDeliveryStatus.DELIVERED, result)
        assertEquals(1, provider.shownNotifications.size)
    }

    @Test
    fun `suppresses non-high priority during quiet hours`() = runTest {
        setTime(23) // 11 PM
        
        val candidate = NotificationCandidate(
            id = "c1",
            title = "Test",
            message = "Message",
            priority = NotificationPriority.DEFAULT,
            channelId = "channel"
        )
        
        val result = engine.process(candidate)
        
        assertEquals(NotificationDeliveryStatus.SUPPRESSED, result)
        assertEquals(0, provider.shownNotifications.size)
    }

    @Test
    fun `delivers high priority during quiet hours`() = runTest {
        setTime(23) // 11 PM
        
        val candidate = NotificationCandidate(
            id = "c1",
            title = "Test",
            message = "Message",
            priority = NotificationPriority.HIGH,
            channelId = "channel"
        )
        
        val result = engine.process(candidate)
        
        assertEquals(NotificationDeliveryStatus.DELIVERED, result)
        assertEquals(1, provider.shownNotifications.size)
    }

    @Test
    fun `suppresses during cooldown`() = runTest {
        setTime(14) // 2 PM
        
        // Add a delivered record just 5 minutes ago
        repository.saveRecord(
            NotificationRecord(
                id = "r1",
                timestamp = currentTimeMs - (5 * 60 * 1000L),
                deduplicationKey = null,
                deliveryStatus = NotificationDeliveryStatus.DELIVERED
            )
        )
        
        val candidate = NotificationCandidate(
            id = "c1",
            title = "Test",
            message = "Message",
            priority = NotificationPriority.DEFAULT,
            channelId = "channel"
        )
        
        val result = engine.process(candidate)
        
        assertEquals(NotificationDeliveryStatus.SUPPRESSED, result)
        assertEquals(0, provider.shownNotifications.size)
    }

    @Test
    fun `suppresses duplicate within deduplication window`() = runTest {
        setTime(14) // 2 PM
        
        // Add a delivered record with same deduplication key 30 minutes ago
        repository.saveRecord(
            NotificationRecord(
                id = "r1",
                timestamp = currentTimeMs - (30 * 60 * 1000L),
                deduplicationKey = "dedup-key",
                deliveryStatus = NotificationDeliveryStatus.DELIVERED
            )
        )
        
        val candidate = NotificationCandidate(
            id = "c1",
            title = "Test",
            message = "Message",
            priority = NotificationPriority.DEFAULT, // default priority would pass cooldown if not for deduplication, wait, cooldown is 15 mins.
            channelId = "channel",
            deduplicationKey = "dedup-key"
        )
        
        val result = engine.process(candidate)
        
        assertEquals(NotificationDeliveryStatus.SUPPRESSED, result)
        assertEquals(0, provider.shownNotifications.size)
    }
}
