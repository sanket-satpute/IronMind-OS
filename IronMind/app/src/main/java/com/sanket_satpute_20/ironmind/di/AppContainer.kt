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
import java.util.UUID

interface AppContainer {
    val commitmentRepository: CommitmentRepository
    val outcomeRepository: OutcomeRepository
    val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    val updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase
    val protectionRepository: ProtectionRepository
    val startProtectionSessionUseCase: StartProtectionSessionUseCase
    val stopProtectionSessionUseCase: StopProtectionSessionUseCase
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    
    private val database: IronMindDatabase by lazy {
        Room.databaseBuilder(
            context,
            IronMindDatabase::class.java,
            "ironmind_database"
        ).build()
    }
    
    private val clock = object : Clock {
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

    override val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase by lazy {
        GetActiveCommitmentsUseCase(commitmentRepository)
    }

    override val updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase by lazy {
        UpdateCommitmentStatusUseCase(commitmentRepository, outcomeRepository, clock, idGenerator)
    }

    override val protectionRepository: ProtectionRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.ProtectionRepositoryImpl(database.ironMindDao())
    }

    override val startProtectionSessionUseCase: StartProtectionSessionUseCase by lazy {
        StartProtectionSessionUseCase(protectionRepository, idGenerator, clock)
    }

    override val stopProtectionSessionUseCase: StopProtectionSessionUseCase by lazy {
        StopProtectionSessionUseCase(protectionRepository, clock)
    }
}
