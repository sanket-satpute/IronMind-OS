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
import com.sanket_satpute_20.ironmind.domain.usecase.goal.EditGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.reflection.GetReflectionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.GetMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.history.GetEventUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.search.LocalSearchUseCase
import com.sanket_satpute_20.ironmind.domain.repository.AuthRepository
import com.sanket_satpute_20.ironmind.data.repository.AuthRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.usecase.auth.SignInAnonymouslyUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.auth.ObserveAuthUserUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.auth.SignOutUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sanket_satpute_20.ironmind.domain.repository.OutboxRepository
import com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.repository.SyncRepository
import com.sanket_satpute_20.ironmind.data.repository.FirestoreSyncRepository
import com.sanket_satpute_20.ironmind.data.sync.SyncOrchestrator
import com.sanket_satpute_20.ironmind.domain.usecase.sync.SyncUseCase
import com.sanket_satpute_20.ironmind.domain.repository.ObservationRepository
import com.sanket_satpute_20.ironmind.data.repository.ObservationRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.engine.ContextEngine
import com.sanket_satpute_20.ironmind.domain.engine.ContextEngineImpl
import java.util.UUID

import com.sanket_satpute_20.ironmind.domain.engine.PatternEngine
import com.sanket_satpute_20.ironmind.domain.engine.PatternEngineImpl
import com.sanket_satpute_20.ironmind.domain.repository.PatternRepository
import com.sanket_satpute_20.ironmind.data.repository.PatternRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.ai.IronMindAI
import com.sanket_satpute_20.ironmind.data.ai.GeminiIronMindAI
import com.sanket_satpute_20.ironmind.domain.usecase.ai.ExtractIntentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.GeneratePlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.RecommendInterventionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.UnderstandBarriersUseCase
import com.sanket_satpute_20.ironmind.domain.repository.AutonomySettingsRepository
import com.sanket_satpute_20.ironmind.data.repository.AutonomySettingsRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.repository.DecisionRecordRepository
import com.sanket_satpute_20.ironmind.data.repository.DecisionRecordRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.engine.DecisionEngine
import com.sanket_satpute_20.ironmind.domain.engine.DecisionEngineImpl

interface AppContainer {
    val goalRepository: GoalRepository
    val commitmentRepository: CommitmentRepository
    val outcomeRepository: OutcomeRepository
    val reflectionRepository: ReflectionRepository
    val eventRepository: EventRepository
    val getTimelineUseCase: GetTimelineUseCase
    val createCommitmentUseCase: CreateCommitmentUseCase
    val editCommitmentUseCase: EditCommitmentUseCase
    val editGoalUseCase: EditGoalUseCase
    val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    val getCommitmentsForDateRangeUseCase: GetCommitmentsForDateRangeUseCase
    val getCommitmentUseCase: GetCommitmentUseCase
    val getReflectionUseCase: GetReflectionUseCase
    val getMemoryUseCase: GetMemoryUseCase
    val getEventUseCase: GetEventUseCase
    val localSearchUseCase: LocalSearchUseCase
    val authRepository: AuthRepository
    val signInAnonymouslyUseCase: SignInAnonymouslyUseCase
    val observeAuthUserUseCase: ObserveAuthUserUseCase
    val signOutUseCase: SignOutUseCase
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
    val outboxRepository: OutboxRepository
    val syncRepository: SyncRepository
    val syncOrchestrator: SyncOrchestrator
    val syncUseCase: SyncUseCase
    val observationRepository: ObservationRepository
    val contextEngine: ContextEngine
    val patternRepository: PatternRepository
    val patternEngine: PatternEngine
    val extractIntentUseCase: ExtractIntentUseCase
    val generatePlanUseCase: GeneratePlanUseCase
    val understandBarriersUseCase: UnderstandBarriersUseCase
    val recommendInterventionUseCase: RecommendInterventionUseCase
    val handleInterventionResultUseCase: HandleInterventionResultUseCase
    val ironMindAI: IronMindAI
    val autonomySettingsRepository: AutonomySettingsRepository
    val decisionRecordRepository: DecisionRecordRepository
    val notificationRecordRepository: com.sanket_satpute_20.ironmind.domain.repository.NotificationRecordRepository
    val notificationEngine: com.sanket_satpute_20.ironmind.domain.engine.NotificationEngine
    val decisionEngine: DecisionEngine
    val autoSchedulingEngine: com.sanket_satpute_20.ironmind.domain.engine.AutoSchedulingEngine
    val autoProtectionEngine: com.sanket_satpute_20.ironmind.domain.engine.AutoProtectionEngine
    val interventionRepository: com.sanket_satpute_20.ironmind.domain.repository.InterventionRepository
    val interventionExecutionPipeline: com.sanket_satpute_20.ironmind.domain.engine.InterventionExecutionPipeline
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
        ).addMigrations(
            IronMindDatabase.MIGRATION_1_2,
            IronMindDatabase.MIGRATION_2_3,
            IronMindDatabase.MIGRATION_3_4,
            IronMindDatabase.MIGRATION_4_5,
            IronMindDatabase.MIGRATION_5_6,
            IronMindDatabase.MIGRATION_6_7,
            IronMindDatabase.MIGRATION_7_8,
            IronMindDatabase.MIGRATION_8_9
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

    override val eventRepository: EventRepository by lazy {
        EventRepositoryImpl(database.ironMindDao())
    }

    override val goalRepository: GoalRepository by lazy {
        GoalRepositoryImpl(database.ironMindDao())
    }

    override val memoryRepository: MemoryRepository by lazy {
        MemoryRepositoryImpl(database.ironMindDao())
    }

    override val autonomySettingsRepository: AutonomySettingsRepository by lazy {
        AutonomySettingsRepositoryImpl(database.autonomySettingsDao(), clock)
    }

    override val decisionRecordRepository: DecisionRecordRepository by lazy {
        DecisionRecordRepositoryImpl(database.decisionRecordDao())
    }

    override val notificationRecordRepository: com.sanket_satpute_20.ironmind.domain.repository.NotificationRecordRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.NotificationRecordRepositoryImpl(database.notificationRecordDao())
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
        EditCommitmentUseCase(commitmentRepository, reminderScheduler, clock, idGenerator, eventRepository)
    }

    override val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase by lazy {
        GetActiveCommitmentsUseCase(commitmentRepository)
    }

    override val getCommitmentsForDateRangeUseCase: GetCommitmentsForDateRangeUseCase by lazy {
        GetCommitmentsForDateRangeUseCase(commitmentRepository)
    }

    override val getCommitmentUseCase: GetCommitmentUseCase by lazy {
        GetCommitmentUseCase(commitmentRepository)
    }

    override val getReflectionUseCase: GetReflectionUseCase by lazy {
        GetReflectionUseCase(reflectionRepository)
    }

    override val getMemoryUseCase: GetMemoryUseCase by lazy {
        GetMemoryUseCase(memoryRepository)
    }

    override val getEventUseCase: GetEventUseCase by lazy {
        GetEventUseCase(eventRepository)
    }

    override val localSearchUseCase: LocalSearchUseCase by lazy {
        LocalSearchUseCase(goalRepository, commitmentRepository, reflectionRepository, memoryRepository, eventRepository)
    }

    override val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(FirebaseAuth.getInstance())
    }
    
    override val signInAnonymouslyUseCase: SignInAnonymouslyUseCase by lazy {
        SignInAnonymouslyUseCase(authRepository)
    }
    
    override val observeAuthUserUseCase: ObserveAuthUserUseCase by lazy {
        ObserveAuthUserUseCase(authRepository)
    }
    
    override val signOutUseCase: SignOutUseCase by lazy {
        SignOutUseCase(authRepository)
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

    override val editGoalUseCase: EditGoalUseCase by lazy {
        EditGoalUseCase(goalRepository, clock, idGenerator, eventRepository)
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

    override val outboxRepository: OutboxRepository by lazy {
        OutboxRepositoryImpl(database.outboxDao())
    }

    override val syncRepository: SyncRepository by lazy {
        FirestoreSyncRepository(FirebaseFirestore.getInstance())
    }

    override val syncOrchestrator: SyncOrchestrator by lazy {
        SyncOrchestrator(outboxRepository, syncRepository)
    }

    override val syncUseCase: SyncUseCase by lazy {
        SyncUseCase(syncOrchestrator)
    }

    override val observationRepository: ObservationRepository by lazy {
        ObservationRepositoryImpl(database.observationDao())
    }

    override val contextEngine: ContextEngine by lazy {
        ContextEngineImpl(
            clock = clock,
            commitmentRepository = commitmentRepository,
            eventRepository = eventRepository,
            observationRepository = observationRepository,
            reflectionRepository = reflectionRepository,
            protectionRepository = protectionRepository,
            goalRepository = goalRepository
        )
    }

    override val patternRepository: PatternRepository by lazy {
        PatternRepositoryImpl(database.patternDao())
    }

    override val patternEngine: PatternEngine by lazy {
        PatternEngineImpl(
            clock = clock,
            patternRepository = patternRepository
        )
    }

    override val decisionEngine: DecisionEngine by lazy {
        DecisionEngineImpl(
            autonomySettingsRepository = autonomySettingsRepository,
            decisionRecordRepository = decisionRecordRepository,
            logger = logger
        )
    }

    override val notificationEngine: com.sanket_satpute_20.ironmind.domain.engine.NotificationEngine by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.NotificationEngineImpl(
            notificationRecordRepository = notificationRecordRepository,
            notificationProvider = notificationProvider,
            logger = logger
        )
    }

    override val autoSchedulingEngine: com.sanket_satpute_20.ironmind.domain.engine.AutoSchedulingEngine by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.AutoSchedulingEngineImpl(
            decisionEngine = decisionEngine,
            commitmentRepository = commitmentRepository,
            logger = logger
        )
    }

    override val autoProtectionEngine: com.sanket_satpute_20.ironmind.domain.engine.AutoProtectionEngine by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.AutoProtectionEngineImpl(
            decisionEngine = decisionEngine,
            startProtectionSessionUseCase = startProtectionSessionUseCase,
            clock = com.sanket_satpute_20.ironmind.infrastructure.common.SystemClock(),
            logger = logger
        )
    }

    override val interventionRepository: com.sanket_satpute_20.ironmind.domain.repository.InterventionRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.InterventionRepositoryImpl(database.interventionDao())
    }

    val interventionPolicyEngine: com.sanket_satpute_20.ironmind.domain.engine.InterventionPolicyEngine by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.InterventionPolicyEngineImpl(
            interventionRepository = interventionRepository,
            clock = clock
        )
    }

    override val interventionExecutionPipeline: com.sanket_satpute_20.ironmind.domain.engine.InterventionExecutionPipeline by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.InterventionExecutionPipelineImpl(
            decisionEngine = decisionEngine,
            interventionRepository = interventionRepository,
            interventionPolicyEngine = interventionPolicyEngine,
            idGenerator = idGenerator,
            clock = clock,
            logger = logger
        )
    }

    override val ironMindAI: IronMindAI by lazy {
        // TODO: Inject actual API key from BuildConfig or secure storage
        GeminiIronMindAI(apiKey = "API_KEY_PLACEHOLDER")
    }

    override val extractIntentUseCase: ExtractIntentUseCase by lazy {
        ExtractIntentUseCase(ironMindAI)
    }

    override val generatePlanUseCase: GeneratePlanUseCase by lazy {
        GeneratePlanUseCase(ironMindAI)
    }

    override val understandBarriersUseCase: UnderstandBarriersUseCase by lazy {
        UnderstandBarriersUseCase(ironMindAI)
    }

    override val recommendInterventionUseCase: RecommendInterventionUseCase by lazy {
        RecommendInterventionUseCase(ironMindAI)
    }

    override val handleInterventionResultUseCase: HandleInterventionResultUseCase by lazy {
        HandleInterventionResultUseCase(
            eventRepository = eventRepository,
            clock = clock,
            idGenerator = idGenerator
        )
    }
}

