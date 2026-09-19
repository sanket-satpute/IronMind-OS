package com.sanket_satpute_20.ironmind.di

import android.content.Context
import androidx.room.Room
import com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
import com.sanket_satpute_20.ironmind.data.repository.CommitmentRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.common.Clock
import com.sanket_satpute_20.ironmind.domain.common.IdGenerator
import com.sanket_satpute_20.ironmind.domain.repository.CommitmentRepository
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
import com.sanket_satpute_20.ironmind.data.provider.AndroidReminderScheduler
import com.sanket_satpute_20.ironmind.data.provider.AndroidNotificationProvider
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CreateCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.EditCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetCommitmentsForDateRangeUseCase
import com.sanket_satpute_20.ironmind.domain.repository.ReflectionRepository
import com.sanket_satpute_20.ironmind.data.repository.ReflectionRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.usecase.reflection.SaveReflectionUseCase
import java.util.UUID

interface AppContainer {
    val commitmentRepository: CommitmentRepository
    val outcomeRepository: OutcomeRepository
    val reflectionRepository: ReflectionRepository
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
    val clock: Clock
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    
    private val database: IronMindDatabase by lazy {
        Room.databaseBuilder(
            context,
            IronMindDatabase::class.java,
            "ironmind_database"
        ).build()
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

    override val reminderScheduler: ReminderScheduler by lazy {
        AndroidReminderScheduler(context)
    }

    override val notificationProvider: NotificationProvider by lazy {
        AndroidNotificationProvider(context)
    }
    
    override val createCommitmentUseCase: CreateCommitmentUseCase by lazy {
        CreateCommitmentUseCase(commitmentRepository, reminderScheduler, idGenerator, clock)
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
            idGenerator
        )
    }

    override val saveReflectionUseCase: SaveReflectionUseCase by lazy {
        SaveReflectionUseCase(reflectionRepository, idGenerator, clock)
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
            clock
        )
    }

    override val stopProtectionSessionUseCase: StopProtectionSessionUseCase by lazy {
        StopProtectionSessionUseCase(
            protectionRepository, 
            appProtectionProvider,
            clock
        )
    }
}
