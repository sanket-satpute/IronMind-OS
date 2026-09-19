package com.sanket_satpute_20.ironmind.di

import android.content.Context
import androidx.room.Room
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.repository.CommitmentRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
import com.sanket_satpute_20.ironmind.domain.repository.GoalRepository
import com.sanket_satpute_20.ironmind.data.repository.GoalRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.repository.OutcomeRepository
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetActiveCommitmentsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.UpdateCommitmentStatusUseCase
import com.sanket_satpute_20.ironmind.domain.repository.ProtectionRepository
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StartProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.protection.StopProtectionSessionUseCase
import com.sanket_satpute_20.ironmind.domain.provider.AppProtectionProvider
import com.sanket_satpute_20.ironmind.data.provider.AndroidAppProtectionProvider
import com.sanket_satpute_20.ironmind.domain.provider.ReminderScheduler
import com.sanket_satpute_20.ironmind.domain.provider.NotificationProvider
import com.sanket_satpute_20.ironmind.domain.provider.SpeechToTextProvider
import com.sanket_satpute_20.ironmind.domain.logging.IronLogger
import com.sanket_satpute_20.ironmind.infrastructure.logging.DebugIronLogger
import com.sanket_satpute_20.ironmind.data.provider.AndroidReminderScheduler
import com.sanket_satpute_20.ironmind.data.provider.AndroidNotificationProvider
import com.sanket_satpute_20.ironmind.data.provider.AndroidSpeechToTextProvider
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.EditCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetCommitmentsForDateRangeUseCase
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.data.repository.ReflectionRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.usecase.reflection.SaveReflectionUseCase
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository
import com.sanket_satpute_20.ironmind.data.repository.EventRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.usecase.history.GetTimelineUseCase
import com.sanket_satpute_20.ironmind.domain.repository.MemoryRepository
import com.sanket_satpute_20.ironmind.data.repository.MemoryRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.usecase.memory.ProposeMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.ConfirmMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.CorrectMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.WeakenMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.ExpireMemoryUseCase
import java.util.UUID

interface AppContainer {
    val goalRepository: GoalRepository
    val commitmentRepository: CommitmentRepository
    val outcomeRepository: OutcomeRepository
    val reflectionRepository: ReflectionRepository
    val eventRepository: EventRepository
    val getTimelineUseCase: GetTimelineUseCase
    val createCommitmentUseCase: CreateCommitmentUseCase
    val editCommitmentUseCase: EditCommitmentUseCase
    val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    val getCommitmentsForDateRangeUseCase: GetCommitmentsForDateRangeUseCase
    val updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase
    val saveReflectionUseCase: SaveReflectionUseCase
    val protectionRepository: ProtectionRepository
    val startProtectionSessionUseCase: StartProtectionSessionUseCase
    val stopProtectionSessionUseCase: StopProtectionSessionUseCase
    val appProtectionProvider: AppProtectionProvider
    val reminderScheduler: ReminderScheduler
    val notificationProvider: NotificationProvider
    val memoryRepository: MemoryRepository
    val proposeMemoryUseCase: ProposeMemoryUseCase
    val confirmMemoryUseCase: ConfirmMemoryUseCase
    val correctMemoryUseCase: CorrectMemoryUseCase
    val weakenMemoryUseCase: WeakenMemoryUseCase
    val expireMemoryUseCase: ExpireMemoryUseCase
    val speechToTextProvider: SpeechToTextProvider
    val logger: IronLogger
    val clock: Clock
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val logger: IronLogger by lazy {
        DebugIronLogger()
    }
    
    private val database: IronMindDatabase by lazy {
        Room.databaseBuilder(
            context,
            IronMindDatabase::class.java,
            "ironmind_database"
        ).addMigrations(IronMindDatabase.MIGRATION_1_2).build()
    }
    
    override val clock: Clock = object : Clock {
        override fun currentTimeMillis(): Long = System.currentTimeMillis()
    }
    
    private val idGenerator = object : IdGenerator {
        override fun generateId(): String = UUID.randomUUID().toString()
    }
    
    override val commitmentRepository: CommitmentRepository by lazy {
        CommitmentRepositoryImpl(database.ironMindDao())
    }

    override val outcomeRepository: OutcomeRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.OutcomeRepositoryImpl(database.ironMindDao())
    }

    override val reflectionRepository: ReflectionRepository by lazy {
        ReflectionRepositoryImpl(database.ironMindDao())
    }

    override val eventRepository: EventRepository by lazy {
        EventRepositoryImpl(database.ironMindDao())
    }

    override val goalRepository: GoalRepository by lazy {
        GoalRepositoryImpl(database.ironMindDao())
    }

    override val memoryRepository: MemoryRepository by lazy {
        MemoryRepositoryImpl(database.ironMindDao())
    }

    override val getTimelineUseCase: GetTimelineUseCase by lazy {
        GetTimelineUseCase(eventRepository, commitmentRepository, reflectionRepository, goalRepository)
    }

    override val reminderScheduler: ReminderScheduler by lazy {
        AndroidReminderScheduler(context)
    }

    override val notificationProvider: NotificationProvider by lazy {
        AndroidNotificationProvider(context)
    }

    override val speechToTextProvider: SpeechToTextProvider by lazy {
        AndroidSpeechToTextProvider(context, logger)
    }
    
    override val createCommitmentUseCase: CreateCommitmentUseCase by lazy {
        CreateCommitmentUseCase(commitmentRepository, reminderScheduler, idGenerator, clock, eventRepository)
    }

    override val editCommitmentUseCase: EditCommitmentUseCase by lazy {
        EditCommitmentUseCase(commitmentRepository, reminderScheduler, clock)
    }

    override val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase by lazy {
        GetActiveCommitmentsUseCase(commitmentRepository)
    }

    override val getCommitmentsForDateRangeUseCase: GetCommitmentsForDateRangeUseCase by lazy {
        GetCommitmentsForDateRangeUseCase(commitmentRepository)
    }

    override val updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase by lazy {
        UpdateCommitmentStatusUseCase(
            commitmentRepository, 
            outcomeRepository, 
            reminderScheduler,
            clock, 
            idGenerator,
            eventRepository
        )
    }

    override val saveReflectionUseCase: SaveReflectionUseCase by lazy {
        SaveReflectionUseCase(reflectionRepository, idGenerator, clock, eventRepository)
    }

    override val proposeMemoryUseCase: ProposeMemoryUseCase by lazy {
        ProposeMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
    }

    override val confirmMemoryUseCase: ConfirmMemoryUseCase by lazy {
        ConfirmMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
    }

    override val correctMemoryUseCase: CorrectMemoryUseCase by lazy {
        CorrectMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
    }

    override val weakenMemoryUseCase: WeakenMemoryUseCase by lazy {
        WeakenMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
    }

    override val expireMemoryUseCase: ExpireMemoryUseCase by lazy {
        ExpireMemoryUseCase(memoryRepository, eventRepository, idGenerator, clock)
    }

    override val protectionRepository: ProtectionRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.ProtectionRepositoryImpl(database.ironMindDao())
    }

    override val appProtectionProvider: AppProtectionProvider by lazy {
        AndroidAppProtectionProvider(context)
    }

    override val startProtectionSessionUseCase: StartProtectionSessionUseCase by lazy {
        StartProtectionSessionUseCase(
            protectionRepository, 
            appProtectionProvider,
            idGenerator, 
            clock,
            eventRepository
        )
    }

    override val stopProtectionSessionUseCase: StopProtectionSessionUseCase by lazy {
        StopProtectionSessionUseCase(
            protectionRepository, 
            appProtectionProvider,
            clock,
            eventRepository
        )
    }
}
