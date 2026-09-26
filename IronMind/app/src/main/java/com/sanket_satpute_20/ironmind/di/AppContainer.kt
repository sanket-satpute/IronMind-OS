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
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.ScheduleCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.CancelCommitmentScheduleUseCase
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
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.CreateGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlansUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.CreatePlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetTasksUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.commitment.GetCommitmentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.reflection.GetReflectionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.GetMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.GetActiveMemoriesUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.LearnInterventionResponsePatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.LearnInterventionTimingPatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.SelectInterventionChannelUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.experiment.ProposeExperimentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.model.EvolvePersonalModelUseCase
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
import com.sanket_satpute_20.ironmind.domain.usecase.context.SynthesizePersonalContextUseCase
import java.util.UUID

import com.sanket_satpute_20.ironmind.domain.engine.PatternEngine
import com.sanket_satpute_20.ironmind.domain.engine.PatternEngineImpl
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutor
import com.sanket_satpute_20.ironmind.execution.background.BackgroundExecutorImpl
import com.sanket_satpute_20.ironmind.domain.repository.DataManagementRepository
import com.sanket_satpute_20.ironmind.data.repository.DataManagementRepositoryImpl
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
import com.sanket_satpute_20.ironmind.domain.repository.AppUsageObservationSettingsRepository
import com.sanket_satpute_20.ironmind.data.repository.AppUsageObservationSettingsRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.provider.AppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.provider.NotificationObservationProvider
import com.sanket_satpute_20.ironmind.data.provider.AndroidNotificationObservationProvider
import com.sanket_satpute_20.ironmind.domain.repository.NotificationObservationSettingsRepository
import com.sanket_satpute_20.ironmind.data.repository.NotificationObservationSettingsRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetNotificationObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetNotificationObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.HandleIncomingNotificationUseCase
import com.sanket_satpute_20.ironmind.data.provider.AndroidAppUsageObservationProvider
import com.sanket_satpute_20.ironmind.domain.usecase.observation.GetAppUsageObservationSettingsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.SetAppUsageObservationEnabledUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectAppUsageObservationsUseCase
import com.sanket_satpute_20.ironmind.domain.repository.ExperimentRepository

interface AppContainer {
    val userProfileRepository: com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository
    val goalRepository: GoalRepository
    val commitmentRepository: CommitmentRepository
    val outcomeRepository: OutcomeRepository
    val devControlRepository: com.sanket_satpute_20.ironmind.domain.repository.DevControlRepository
    val reflectionRepository: ReflectionRepository
    val eventRepository: EventRepository
    val getTimelineUseCase: GetTimelineUseCase
    val createCommitmentUseCase: CreateCommitmentUseCase
    val editCommitmentUseCase: EditCommitmentUseCase
    val scheduleCommitmentUseCase: ScheduleCommitmentUseCase
    val cancelCommitmentScheduleUseCase: CancelCommitmentScheduleUseCase
    val getGoalsUseCase: GetGoalsUseCase
    val getGoalUseCase: GetGoalUseCase
    val createGoalUseCase: CreateGoalUseCase
    val editGoalUseCase: EditGoalUseCase
    val getPlansUseCase: GetPlansUseCase
    val getPlanUseCase: GetPlanUseCase
    val createPlanUseCase: CreatePlanUseCase
    val getTasksUseCase: GetTasksUseCase
    val createTaskUseCase: CreateTaskUseCase
    val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    val getCommitmentsForDateRangeUseCase: GetCommitmentsForDateRangeUseCase
    val getCommitmentUseCase: GetCommitmentUseCase
    val getReflectionUseCase: GetReflectionUseCase
    val getMemoryUseCase: GetMemoryUseCase
    val getActiveMemoriesUseCase: GetActiveMemoriesUseCase
    val getEventUseCase: GetEventUseCase
    val localSearchUseCase: LocalSearchUseCase
    val authRepository: AuthRepository
    val signInAnonymouslyUseCase: SignInAnonymouslyUseCase
    val observeAuthUserUseCase: ObserveAuthUserUseCase
    val signOutUseCase: SignOutUseCase
    val updateCommitmentStatusUseCase: UpdateCommitmentStatusUseCase
    val learnInterventionResponsePatternUseCase: LearnInterventionResponsePatternUseCase
    val learnInterventionTimingPatternUseCase: LearnInterventionTimingPatternUseCase
    val selectInterventionChannelUseCase: SelectInterventionChannelUseCase
    val proposeExperimentUseCase: ProposeExperimentUseCase
    val evolvePersonalModelUseCase: EvolvePersonalModelUseCase
    val saveReflectionUseCase: SaveReflectionUseCase
    val backgroundExecutor: BackgroundExecutor
    val protectionRepository: ProtectionRepository
    val startProtectionSessionUseCase: StartProtectionSessionUseCase
    val stopProtectionSessionUseCase: StopProtectionSessionUseCase
    val getActiveProtectionSessionUseCase: com.sanket_satpute_20.ironmind.domain.usecase.protection.GetActiveProtectionSessionUseCase
    val dataManagementRepository: DataManagementRepository
    val dataLifecycleEngine: com.sanket_satpute_20.ironmind.domain.engine.DataLifecycleEngine
    val exportUserDataUseCase: com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataUseCase
    val deleteUserDataUseCase: com.sanket_satpute_20.ironmind.domain.usecase.data.DeleteUserDataUseCase
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
    val synthesizePersonalContextUseCase: SynthesizePersonalContextUseCase
    val patternRepository: PatternRepository
    val patternEngine: PatternEngine
    val extractIntentUseCase: ExtractIntentUseCase
    val generatePlanUseCase: GeneratePlanUseCase
    val understandBarriersUseCase: UnderstandBarriersUseCase
    val recommendInterventionUseCase: RecommendInterventionUseCase
    val handleInterventionResultUseCase: HandleInterventionResultUseCase
    val ironMindAI: IronMindAI
    val autonomySettingsRepository: AutonomySettingsRepository
    val getAutonomySettingsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.autonomy.GetAutonomySettingsUseCase
    val updateAutonomyLevelUseCase: com.sanket_satpute_20.ironmind.domain.usecase.autonomy.UpdateAutonomyLevelUseCase
    val toggleGlobalPauseUseCase: com.sanket_satpute_20.ironmind.domain.usecase.autonomy.ToggleGlobalPauseUseCase
    val decisionRecordRepository: DecisionRecordRepository
    val notificationRecordRepository: com.sanket_satpute_20.ironmind.domain.repository.NotificationRecordRepository
    val notificationEngine: com.sanket_satpute_20.ironmind.domain.engine.NotificationEngine
    val decisionEngine: DecisionEngine
    val autoSchedulingEngine: com.sanket_satpute_20.ironmind.domain.engine.AutoSchedulingEngine
    val autoProtectionEngine: com.sanket_satpute_20.ironmind.domain.engine.AutoProtectionEngine
    val autonomousReflectionEngine: com.sanket_satpute_20.ironmind.domain.engine.AutonomousReflectionEngine
    val interventionRepository: com.sanket_satpute_20.ironmind.domain.repository.InterventionRepository
    val experimentRepository: ExperimentRepository
    val taskRepository: com.sanket_satpute_20.ironmind.domain.repository.TaskRepository
    val interventionExecutionPipeline: com.sanket_satpute_20.ironmind.domain.engine.InterventionExecutionPipeline
    // V4.1 App Usage Observation
    val appUsageObservationSettingsRepository: AppUsageObservationSettingsRepository
    val appUsageObservationProvider: AppUsageObservationProvider
    val getAppUsageObservationSettingsUseCase: GetAppUsageObservationSettingsUseCase
    val setAppUsageObservationEnabledUseCase: SetAppUsageObservationEnabledUseCase
    val collectAppUsageObservationsUseCase: CollectAppUsageObservationsUseCase

    // V4.2 Notification Observation
    val notificationObservationSettingsRepository: NotificationObservationSettingsRepository
    val notificationObservationProvider: NotificationObservationProvider
    val getNotificationObservationSettingsUseCase: GetNotificationObservationSettingsUseCase
    val setNotificationObservationEnabledUseCase: SetNotificationObservationEnabledUseCase
    val handleIncomingNotificationUseCase: HandleIncomingNotificationUseCase

    // V4.3 Calendar Context
    val calendarObservationSettingsRepository: com.sanket_satpute_20.ironmind.domain.repository.CalendarObservationSettingsRepository
    val calendarObservationProvider: com.sanket_satpute_20.ironmind.domain.provider.CalendarObservationProvider
    val getCalendarObservationSettingsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.GetCalendarObservationSettingsUseCase
    val setCalendarObservationEnabledUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.SetCalendarObservationEnabledUseCase
    val collectCalendarObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectCalendarObservationsUseCase

    // V4.4 Location / Environment Context
    val locationObservationSettingsRepository: com.sanket_satpute_20.ironmind.domain.repository.LocationObservationSettingsRepository
    val locationObservationProvider: com.sanket_satpute_20.ironmind.domain.provider.LocationObservationProvider
    val getLocationObservationSettingsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.GetLocationObservationSettingsUseCase
    val setLocationObservationEnabledUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.SetLocationObservationEnabledUseCase
    val collectLocationObservationUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectLocationObservationUseCase

    // V4.5 Activity / Energy Context
    val activityObservationSettingsRepository: com.sanket_satpute_20.ironmind.domain.repository.ActivityObservationSettingsRepository
    val activityObservationProvider: com.sanket_satpute_20.ironmind.domain.provider.ActivityObservationProvider
    val getActivityObservationSettingsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.GetActivityObservationSettingsUseCase
    val setActivityObservationEnabledUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.SetActivityObservationEnabledUseCase
    val collectActivityObservationUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectActivityObservationUseCase

    // V4.6 Orchestration
    val collectObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectObservationsUseCase
    val executeObservationCollectionUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase

    // Services
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
            IronMindDatabase.MIGRATION_8_9,
            IronMindDatabase.MIGRATION_9_10,
            IronMindDatabase.MIGRATION_10_11,
            IronMindDatabase.MIGRATION_11_12,
            IronMindDatabase.MIGRATION_12_13,
            IronMindDatabase.MIGRATION_13_14,
            IronMindDatabase.MIGRATION_14_15,
            IronMindDatabase.MIGRATION_15_16,
            IronMindDatabase.MIGRATION_16_17
        ).build()
    }

    private val globalAutonomyStateDao: com.sanket_satpute_20.ironmind.data.local.dao.GlobalAutonomyStateDao by lazy {
        database.globalAutonomyStateDao()
    }

    private val appUsageObservationSettingsDao: com.sanket_satpute_20.ironmind.data.local.dao.AppUsageObservationSettingsDao by lazy {
        database.appUsageObservationSettingsDao()
    }

    private val notificationObservationSettingsDao: com.sanket_satpute_20.ironmind.data.local.dao.NotificationObservationSettingsDao by lazy {
        database.notificationObservationSettingsDao()
    }

    private val calendarObservationSettingsDao: com.sanket_satpute_20.ironmind.data.local.dao.CalendarObservationSettingsDao by lazy {
        database.calendarObservationSettingsDao()
    }

    private val locationObservationSettingsDao: com.sanket_satpute_20.ironmind.data.local.dao.LocationObservationSettingsDao by lazy {
        database.locationObservationSettingsDao()
    }

    private val activityObservationSettingsDao: com.sanket_satpute_20.ironmind.data.local.dao.ActivityObservationSettingsDao by lazy {
        database.activityObservationSettingsDao()
    }

    private val experimentDao: com.sanket_satpute_20.ironmind.data.local.dao.ExperimentDao by lazy {
        database.experimentDao()
    }

    override val clock: Clock = object : Clock {
        override fun currentTimeMillis(): Long = System.currentTimeMillis()
    }

    private val idGenerator = object : IdGenerator {
        override fun generateId(): String = UUID.randomUUID().toString()
    }

    override val userProfileRepository: com.sanket_satpute_20.ironmind.domain.repository.UserProfileRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.UserProfileRepositoryImpl(database.ironMindDao())
    }

    override val commitmentRepository: CommitmentRepository by lazy {
        CommitmentRepositoryImpl(database.ironMindDao())
    }

    override val outcomeRepository: OutcomeRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.OutcomeRepositoryImpl(database.ironMindDao())
    }

    override val devControlRepository: com.sanket_satpute_20.ironmind.domain.repository.DevControlRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.DevControlRepositoryImpl(
            ironMindDao = database.ironMindDao(),
            observationDao = database.observationDao(),
            patternDao = database.patternDao(),
            decisionRecordDao = database.decisionRecordDao(),
            interventionDao = database.interventionDao(),
            experimentDao = experimentDao,
            outboxDao = database.outboxDao()
        )
    }

    override val reflectionRepository: ReflectionRepository by lazy {
        ReflectionRepositoryImpl(database.ironMindDao())
    }

    override val eventRepository: EventRepository by lazy {
        EventRepositoryImpl(database.ironMindDao(), logger)
    }

    override val goalRepository: GoalRepository by lazy {
        GoalRepositoryImpl(database.ironMindDao())
    }

    private val planRepository: com.sanket_satpute_20.ironmind.domain.repository.PlanRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.PlanRepositoryImpl(database.ironMindDao())
    }

    override val taskRepository: com.sanket_satpute_20.ironmind.domain.repository.TaskRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.TaskRepositoryImpl(database.ironMindDao())
    }

    override val memoryRepository: MemoryRepository by lazy {
        MemoryRepositoryImpl(database.ironMindDao())
    }

    override val autonomySettingsRepository: AutonomySettingsRepository by lazy {
        AutonomySettingsRepositoryImpl(database.autonomySettingsDao(), globalAutonomyStateDao, clock)
    }

    override val getAutonomySettingsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.autonomy.GetAutonomySettingsUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.autonomy.GetAutonomySettingsUseCase(autonomySettingsRepository)
    }

    override val updateAutonomyLevelUseCase: com.sanket_satpute_20.ironmind.domain.usecase.autonomy.UpdateAutonomyLevelUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.autonomy.UpdateAutonomyLevelUseCase(autonomySettingsRepository, logger)
    }

    override val toggleGlobalPauseUseCase: com.sanket_satpute_20.ironmind.domain.usecase.autonomy.ToggleGlobalPauseUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.autonomy.ToggleGlobalPauseUseCase(autonomySettingsRepository)
    }

    override val decisionRecordRepository: DecisionRecordRepository by lazy {
        DecisionRecordRepositoryImpl(database.decisionRecordDao())
    }

    override val notificationRecordRepository: com.sanket_satpute_20.ironmind.domain.repository.NotificationRecordRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.NotificationRecordRepositoryImpl(database.notificationRecordDao())
    }


    override val getTimelineUseCase: GetTimelineUseCase by lazy {
        GetTimelineUseCase(eventRepository, commitmentRepository, reflectionRepository, goalRepository, planRepository, taskRepository, logger)
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

    override val scheduleCommitmentUseCase: ScheduleCommitmentUseCase by lazy {
        ScheduleCommitmentUseCase(commitmentRepository, reminderScheduler, clock, idGenerator, eventRepository)
    }

    override val cancelCommitmentScheduleUseCase: CancelCommitmentScheduleUseCase by lazy {
        CancelCommitmentScheduleUseCase(commitmentRepository, reminderScheduler, clock, idGenerator, eventRepository)
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

    override val getActiveMemoriesUseCase: GetActiveMemoriesUseCase by lazy {
        GetActiveMemoriesUseCase(memoryRepository)
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

    override val getGoalsUseCase: GetGoalsUseCase by lazy {
        GetGoalsUseCase(goalRepository)
    }

    override val getGoalUseCase: GetGoalUseCase by lazy {
        GetGoalUseCase(goalRepository)
    }

    override val createGoalUseCase: CreateGoalUseCase by lazy {
        CreateGoalUseCase(goalRepository, idGenerator, clock, eventRepository)
    }

    override val getPlansUseCase: GetPlansUseCase by lazy {
        GetPlansUseCase(planRepository)
    }

    override val getPlanUseCase: GetPlanUseCase by lazy {
        GetPlanUseCase(planRepository)
    }

    override val createPlanUseCase: CreatePlanUseCase by lazy {
        CreatePlanUseCase(planRepository, idGenerator, clock, eventRepository)
    }

    override val getTasksUseCase: GetTasksUseCase by lazy {
        GetTasksUseCase(taskRepository)
    }

    override val createTaskUseCase: CreateTaskUseCase by lazy {
        CreateTaskUseCase(taskRepository, idGenerator, clock, eventRepository)
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

    override val getActiveProtectionSessionUseCase: com.sanket_satpute_20.ironmind.domain.usecase.protection.GetActiveProtectionSessionUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.protection.GetActiveProtectionSessionUseCase(protectionRepository)
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
            goalRepository = goalRepository,
            patternRepository = patternRepository
        )
    }

    override val synthesizePersonalContextUseCase: SynthesizePersonalContextUseCase by lazy {
        SynthesizePersonalContextUseCase(
            contextEngine = contextEngine,
            ironMindAI = ironMindAI
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

    override val backgroundExecutor: BackgroundExecutor by lazy {
        BackgroundExecutorImpl(
            context = context,
            workManager = androidx.work.WorkManager.getInstance(context)
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

    override val autonomousReflectionEngine: com.sanket_satpute_20.ironmind.domain.engine.AutonomousReflectionEngine by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.AutonomousReflectionEngineImpl(
            reflectionRepository = reflectionRepository,
            ironMindAI = ironMindAI,
            autonomySettingsRepository = autonomySettingsRepository,
            memoryRepository = memoryRepository,
            patternRepository = patternRepository,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    override val interventionRepository: com.sanket_satpute_20.ironmind.domain.repository.InterventionRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.InterventionRepositoryImpl(database.interventionDao())
    }

    override val experimentRepository: ExperimentRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.ExperimentRepositoryImpl(experimentDao)
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

    val goalResurfacingEngine: com.sanket_satpute_20.ironmind.domain.engine.GoalResurfacingEngine by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.GoalResurfacingEngineImpl(
            goalRepository = goalRepository,
            taskRepository = taskRepository,
            interventionExecutionPipeline = interventionExecutionPipeline,
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

    // V4.1 — App Usage Observation
    override val appUsageObservationSettingsRepository: AppUsageObservationSettingsRepository by lazy {
        AppUsageObservationSettingsRepositoryImpl(appUsageObservationSettingsDao, clock)
    }

    override val appUsageObservationProvider: AppUsageObservationProvider by lazy {
        AndroidAppUsageObservationProvider(context)
    }

    override val getAppUsageObservationSettingsUseCase: GetAppUsageObservationSettingsUseCase by lazy {
        GetAppUsageObservationSettingsUseCase(appUsageObservationSettingsRepository)
    }

    override val setAppUsageObservationEnabledUseCase: SetAppUsageObservationEnabledUseCase by lazy {
        SetAppUsageObservationEnabledUseCase(appUsageObservationSettingsRepository)
    }

    override val collectAppUsageObservationsUseCase: CollectAppUsageObservationsUseCase by lazy {
        CollectAppUsageObservationsUseCase(
            settingsRepository = appUsageObservationSettingsRepository,
            observationRepository = observationRepository,
            appUsageObservationProvider = appUsageObservationProvider,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    // V4.2 — Notification Observation
    override val notificationObservationSettingsRepository: NotificationObservationSettingsRepository by lazy {
        NotificationObservationSettingsRepositoryImpl(notificationObservationSettingsDao, clock)
    }

    override val notificationObservationProvider: NotificationObservationProvider by lazy {
        AndroidNotificationObservationProvider(context)
    }

    override val getNotificationObservationSettingsUseCase: GetNotificationObservationSettingsUseCase by lazy {
        GetNotificationObservationSettingsUseCase(notificationObservationSettingsRepository)
    }

    override val setNotificationObservationEnabledUseCase: SetNotificationObservationEnabledUseCase by lazy {
        SetNotificationObservationEnabledUseCase(notificationObservationSettingsRepository)
    }

    override val handleIncomingNotificationUseCase: HandleIncomingNotificationUseCase by lazy {
        HandleIncomingNotificationUseCase(
            notificationObservationSettingsRepository = notificationObservationSettingsRepository,
            observationRepository = observationRepository,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    override val learnInterventionResponsePatternUseCase: LearnInterventionResponsePatternUseCase by lazy {
        LearnInterventionResponsePatternUseCase(
            interventionRepository = interventionRepository,
            patternRepository = patternRepository,
            ironMindAI = ironMindAI,
            clock = clock,
            idGenerator = idGenerator
        )
    }

    override val learnInterventionTimingPatternUseCase: LearnInterventionTimingPatternUseCase by lazy {
        LearnInterventionTimingPatternUseCase(
            interventionRepository = interventionRepository,
            patternRepository = patternRepository,
            ironMindAI = ironMindAI,
            clock = clock,
            idGenerator = idGenerator
        )
    }

    override val selectInterventionChannelUseCase: SelectInterventionChannelUseCase by lazy {
        SelectInterventionChannelUseCase(
            ironMindAI = ironMindAI
        )
    }

    override val proposeExperimentUseCase: ProposeExperimentUseCase by lazy {
        ProposeExperimentUseCase(
            ironMindAI = ironMindAI,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    override val evolvePersonalModelUseCase: EvolvePersonalModelUseCase by lazy {
        EvolvePersonalModelUseCase(
            ironMindAI = ironMindAI,
            contextEngine = contextEngine,
            patternRepository = patternRepository
        )
    }

    // V4.3 — Calendar Context
    override val calendarObservationSettingsRepository: com.sanket_satpute_20.ironmind.domain.repository.CalendarObservationSettingsRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.CalendarObservationSettingsRepositoryImpl(calendarObservationSettingsDao, clock)
    }

    override val calendarObservationProvider: com.sanket_satpute_20.ironmind.domain.provider.CalendarObservationProvider by lazy {
        com.sanket_satpute_20.ironmind.data.provider.AndroidCalendarObservationProvider(context)
    }

    override val getCalendarObservationSettingsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.GetCalendarObservationSettingsUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.GetCalendarObservationSettingsUseCase(calendarObservationSettingsRepository)
    }

    override val setCalendarObservationEnabledUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.SetCalendarObservationEnabledUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.SetCalendarObservationEnabledUseCase(calendarObservationSettingsRepository, getCalendarObservationSettingsUseCase)
    }

    override val collectCalendarObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectCalendarObservationsUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectCalendarObservationsUseCase(
            settingsRepository = calendarObservationSettingsRepository,
            observationRepository = observationRepository,
            calendarObservationProvider = calendarObservationProvider,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    // V4.4 — Location / Environment Context
    override val locationObservationSettingsRepository: com.sanket_satpute_20.ironmind.domain.repository.LocationObservationSettingsRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.LocationObservationSettingsRepositoryImpl(locationObservationSettingsDao, clock)
    }

    override val locationObservationProvider: com.sanket_satpute_20.ironmind.domain.provider.LocationObservationProvider by lazy {
        com.sanket_satpute_20.ironmind.data.provider.AndroidLocationObservationProvider(context)
    }

    override val getLocationObservationSettingsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.GetLocationObservationSettingsUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.GetLocationObservationSettingsUseCase(locationObservationSettingsRepository)
    }

    override val setLocationObservationEnabledUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.SetLocationObservationEnabledUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.SetLocationObservationEnabledUseCase(locationObservationSettingsRepository, getLocationObservationSettingsUseCase)
    }

    override val collectLocationObservationUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectLocationObservationUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectLocationObservationUseCase(
            settingsRepository = locationObservationSettingsRepository,
            observationRepository = observationRepository,
            locationObservationProvider = locationObservationProvider,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    // V4.5 — Activity / Energy Context
    override val activityObservationSettingsRepository: com.sanket_satpute_20.ironmind.domain.repository.ActivityObservationSettingsRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.ActivityObservationSettingsRepositoryImpl(activityObservationSettingsDao, clock)
    }

    override val activityObservationProvider: com.sanket_satpute_20.ironmind.domain.provider.ActivityObservationProvider by lazy {
        com.sanket_satpute_20.ironmind.data.provider.AndroidActivityObservationProvider(context)
    }

    override val getActivityObservationSettingsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.GetActivityObservationSettingsUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.GetActivityObservationSettingsUseCase(activityObservationSettingsRepository)
    }

    override val setActivityObservationEnabledUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.SetActivityObservationEnabledUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.SetActivityObservationEnabledUseCase(activityObservationSettingsRepository, getActivityObservationSettingsUseCase)
    }

    override val collectActivityObservationUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectActivityObservationUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectActivityObservationUseCase(
            settingsRepository = activityObservationSettingsRepository,
            observationRepository = observationRepository,
            activityObservationProvider = activityObservationProvider,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    override val collectObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectObservationsUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.CollectObservationsUseCase(
            collectors = listOf(
                collectAppUsageObservationsUseCase,
                collectCalendarObservationsUseCase,
                collectLocationObservationUseCase,
                collectActivityObservationUseCase
            )
        )
    }

    override val executeObservationCollectionUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.ExecuteObservationCollectionUseCase(
            collectObservationsUseCase = collectObservationsUseCase
        )
    }

    override val dataManagementRepository: DataManagementRepository by lazy {
        DataManagementRepositoryImpl(
            ironMindDao = database.ironMindDao(),
            patternDao = database.patternDao(),
            observationDao = database.observationDao(),
            interventionDao = database.interventionDao(),
            experimentDao = experimentDao,
            autonomySettingsDao = database.autonomySettingsDao(),
            globalAutonomyStateDao = globalAutonomyStateDao
        )
    }

    override val dataLifecycleEngine: com.sanket_satpute_20.ironmind.domain.engine.DataLifecycleEngine by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.DataLifecycleEngineImpl(
            dataManagementRepository = dataManagementRepository,
            clock = clock,
            logger = logger
        )
    }

    override val exportUserDataUseCase: com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataUseCase(
            userProfileRepository = userProfileRepository,
            goalRepository = goalRepository,
            taskRepository = taskRepository,
            commitmentRepository = commitmentRepository,
            patternRepository = patternRepository,
            memoryRepository = memoryRepository,
            observationRepository = observationRepository,
            eventRepository = eventRepository,
            interventionRepository = interventionRepository,
            clock = clock
        )
    }

    override val deleteUserDataUseCase: com.sanket_satpute_20.ironmind.domain.usecase.data.DeleteUserDataUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.data.DeleteUserDataUseCase(
            dataManagementRepository = dataManagementRepository
        )
    }
}

