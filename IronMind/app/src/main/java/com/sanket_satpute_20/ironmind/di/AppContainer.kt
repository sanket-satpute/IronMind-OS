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
import com.sanket_satpute_20.ironmind.domain.usecase.memory.GetMemoriesUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.RejectMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.memory.DeleteMemoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.GetPatternsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.ConfirmPatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.RefutePatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.pattern.DismissPatternUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.GetBarriersUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.ConfirmBarrierUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.RefuteBarrierUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.barrier.DismissBarrierUseCase
import com.sanket_satpute_20.ironmind.domain.repository.InsightRepository
import com.sanket_satpute_20.ironmind.data.repository.InsightRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.engine.PersonalInsightDiscoveryEngine
import com.sanket_satpute_20.ironmind.domain.engine.PersonalIntelligenceOrchestrator
import com.sanket_satpute_20.ironmind.domain.usecase.insight.GetPersonalInsightsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.insight.ObservePersonalInsightsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.insight.ReviewPersonalInsightUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.insight.OrchestratePersonalIntelligenceUseCase
import com.sanket_satpute_20.ironmind.domain.engine.ActionPlanningEngine
import com.sanket_satpute_20.ironmind.domain.engine.DailyBriefingEngine
import com.sanket_satpute_20.ironmind.domain.repository.ActionRepository
import com.sanket_satpute_20.ironmind.data.repository.ActionRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.usecase.action.ActionLifecycleUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.briefing.AssembleDailyBriefingUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.EditGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.CreateGoalUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.UpdateGoalStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.goal.GetGoalProgressUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlansUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.CreatePlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.EditPlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.UpdatePlanStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.plan.GetPlanProgressUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetTasksUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.CreateTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.EditTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.UpdateTaskStatusUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.DeleteTaskUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.task.GetDueTasksUseCase
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
import com.sanket_satpute_20.ironmind.domain.session.UserSessionProvider
import com.sanket_satpute_20.ironmind.domain.session.UserIdentityReconciler
import com.sanket_satpute_20.ironmind.data.session.UserSessionProviderImpl
import com.sanket_satpute_20.ironmind.data.session.RoomUserIdentityReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.sanket_satpute_20.ironmind.domain.repository.OutboxRepository
import com.sanket_satpute_20.ironmind.data.repository.OutboxRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.repository.SyncRepository
import com.sanket_satpute_20.ironmind.data.repository.FirestoreSyncRepository
import com.sanket_satpute_20.ironmind.domain.repository.SyncCheckpointStore
import com.sanket_satpute_20.ironmind.data.repository.SharedPreferencesSyncCheckpointStore
import com.sanket_satpute_20.ironmind.data.repository.RoomSyncCheckpointStore
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
import com.sanket_satpute_20.ironmind.data.ai.CloudflareWorkerIronMindAI
import com.sanket_satpute_20.ironmind.data.ai.CloudflareWorkerDiagnostic
import com.sanket_satpute_20.ironmind.domain.usecase.ai.ExtractIntentUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.GeneratePlanUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.HandleInterventionResultUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.GetIntelligenceHistoryUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.RecommendInterventionUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.OrchestrateInterventionGenerationUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.intervention.AssembleRecommendationContextUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.UnderstandBarriersUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.GetPendingInterventionRecommendationsUseCase
import com.sanket_satpute_20.ironmind.domain.usecase.ai.UpdateInterventionRecommendationStatusUseCase
import com.sanket_satpute_20.ironmind.domain.repository.BarrierRepository
import com.sanket_satpute_20.ironmind.data.repository.BarrierRepositoryImpl
import com.sanket_satpute_20.ironmind.domain.engine.BarrierUnderstandingOrchestrator
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
import com.sanket_satpute_20.ironmind.BuildConfig
import com.sanket_satpute_20.ironmind.data.ai.DisabledIronMindAI

enum class AIProviderType {
    GEMINI,
    CLOUDFLARE_WORKER,
    DISABLED,
    MOCK
}

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
    val updateGoalStatusUseCase: UpdateGoalStatusUseCase
    val getGoalProgressUseCase: GetGoalProgressUseCase
    val getPlansUseCase: GetPlansUseCase
    val getPlanUseCase: GetPlanUseCase
    val createPlanUseCase: CreatePlanUseCase
    val editPlanUseCase: EditPlanUseCase
    val updatePlanStatusUseCase: UpdatePlanStatusUseCase
    val getPlanProgressUseCase: GetPlanProgressUseCase
    val getTasksUseCase: GetTasksUseCase
    val createTaskUseCase: CreateTaskUseCase
    val editTaskUseCase: EditTaskUseCase
    val updateTaskStatusUseCase: UpdateTaskStatusUseCase
    val deleteTaskUseCase: DeleteTaskUseCase
    val getDueTasksUseCase: GetDueTasksUseCase
    val getActiveCommitmentsUseCase: GetActiveCommitmentsUseCase
    val getCommitmentsForDateRangeUseCase: GetCommitmentsForDateRangeUseCase
    val getCommitmentUseCase: GetCommitmentUseCase
    val getReflectionUseCase: GetReflectionUseCase
    val getMemoryUseCase: GetMemoryUseCase
    val getActiveMemoriesUseCase: GetActiveMemoriesUseCase
    val getEventUseCase: GetEventUseCase
    val localSearchUseCase: LocalSearchUseCase
    val authRepository: AuthRepository
    val userSessionProvider: UserSessionProvider
    val userIdentityReconciler: UserIdentityReconciler
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
    val dataExportRepository: com.sanket_satpute_20.ironmind.domain.repository.DataExportRepository
    val userDataExportCacheManager: com.sanket_satpute_20.ironmind.domain.repository.UserDataExportCacheManager
    val exportUserDataUseCase: com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataUseCase
    val userDataExportFileWriter: com.sanket_satpute_20.ironmind.domain.repository.UserDataExportFileWriter
    val exportUserDataToFileUseCase: com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataToFileUseCase
    val userDataExportShareManager: com.sanket_satpute_20.ironmind.ui.screens.settings.UserDataExportShareManager
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
    val getMemoriesUseCase: GetMemoriesUseCase
    val rejectMemoryUseCase: RejectMemoryUseCase
    val deleteMemoryUseCase: DeleteMemoryUseCase
    val getPatternsUseCase: GetPatternsUseCase
    val confirmPatternUseCase: ConfirmPatternUseCase
    val refutePatternUseCase: RefutePatternUseCase
    val dismissPatternUseCase: DismissPatternUseCase
    val getBarriersUseCase: GetBarriersUseCase
    val confirmBarrierUseCase: ConfirmBarrierUseCase
    val refuteBarrierUseCase: RefuteBarrierUseCase
    val dismissBarrierUseCase: DismissBarrierUseCase
    val speechToTextProvider: SpeechToTextProvider
    val logger: IronLogger
    val clock: Clock
    val outboxRepository: OutboxRepository
    val syncRepository: SyncRepository
    val syncConflictRepository: com.sanket_satpute_20.ironmind.domain.repository.SyncConflictRepository
    val syncCheckpointStore: SyncCheckpointStore
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
    val assembleRecommendationContextUseCase: AssembleRecommendationContextUseCase
    val orchestrateInterventionGenerationUseCase: OrchestrateInterventionGenerationUseCase
    val getPendingInterventionRecommendationsUseCase: GetPendingInterventionRecommendationsUseCase
    val updateInterventionRecommendationStatusUseCase: UpdateInterventionRecommendationStatusUseCase
    val handleInterventionResultUseCase: HandleInterventionResultUseCase
    val evaluateCorrectionEligibilityUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.EvaluateCorrectionEligibilityUseCase
    val evaluateCorrectionReviewDecisionUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.EvaluateCorrectionReviewDecisionUseCase
    val getCorrectionReviewCandidatesUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.GetCorrectionReviewCandidatesUseCase
    val correctionReviewRepository: com.sanket_satpute_20.ironmind.domain.repository.CorrectionReviewRepository
    val approveCorrectionReviewUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.ApproveCorrectionReviewUseCase
    val refuteCorrectionReviewUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.RefuteCorrectionReviewUseCase
    val dismissCorrectionReviewUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.DismissCorrectionReviewUseCase
    val getPendingCorrectionReviewsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.GetPendingCorrectionReviewsUseCase
    val getIntelligenceHistoryUseCase: GetIntelligenceHistoryUseCase
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
    val interventionRecommendationRepository: com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository
    val experimentRepository: ExperimentRepository
    val taskRepository: com.sanket_satpute_20.ironmind.domain.repository.TaskRepository
    val interventionExecutionPipeline: com.sanket_satpute_20.ironmind.domain.engine.InterventionExecutionPipeline
    val goalResurfacingEngine: com.sanket_satpute_20.ironmind.domain.engine.GoalResurfacingEngine
    
    val barrierRepository: BarrierRepository
    val barrierUnderstandingOrchestrator: BarrierUnderstandingOrchestrator
    val barrierHypothesisDiscoveryEngine: com.sanket_satpute_20.ironmind.domain.engine.BarrierHypothesisDiscoveryEngine
    val interventionReasoningEngine: com.sanket_satpute_20.ironmind.domain.engine.InterventionReasoningEngine
    val barrierUnderstandingAndInterventionOrchestrator: com.sanket_satpute_20.ironmind.domain.engine.BarrierUnderstandingAndInterventionOrchestrator
    
    val insightRepository: InsightRepository
    val personalInsightDiscoveryEngine: PersonalInsightDiscoveryEngine
    val personalIntelligenceOrchestrator: PersonalIntelligenceOrchestrator
    val getPersonalInsightsUseCase: GetPersonalInsightsUseCase
    val observePersonalInsightsUseCase: ObservePersonalInsightsUseCase
    val reviewPersonalInsightUseCase: ReviewPersonalInsightUseCase
    val orchestratePersonalIntelligenceUseCase: OrchestratePersonalIntelligenceUseCase
    val actionRepository: ActionRepository
    val actionPlanningEngine: ActionPlanningEngine
    val actionLifecycleUseCase: ActionLifecycleUseCase
    val dailyBriefingEngine: DailyBriefingEngine
    val assembleDailyBriefingUseCase: AssembleDailyBriefingUseCase
    
    val patternAcceptanceUseCase: com.sanket_satpute_20.ironmind.domain.usecase.pattern.PatternAcceptanceUseCase
    val evaluatePatternCandidateUseCase: com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidateUseCase
    val evaluatePatternCandidatesUseCase: com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidatesUseCase
    val evidenceDiscovery: com.sanket_satpute_20.ironmind.domain.engine.EvidenceDiscovery
    val evidenceResolver: com.sanket_satpute_20.ironmind.domain.engine.EvidenceResolver
    val evidenceValidator: com.sanket_satpute_20.ironmind.domain.engine.EvidenceValidator
    val evidenceSufficiencyEvaluator: com.sanket_satpute_20.ironmind.domain.engine.EvidenceSufficiencyEvaluator
    val patternAcceptanceEvaluator: com.sanket_satpute_20.ironmind.domain.engine.PatternAcceptanceEvaluator

    // V4.1 App Usage Observation
    val appUsageObservationSettingsRepository: AppUsageObservationSettingsRepository
    val appUsageObservationProvider: AppUsageObservationProvider
    val getAppUsageObservationSettingsUseCase: GetAppUsageObservationSettingsUseCase
    val setAppUsageObservationEnabledUseCase: SetAppUsageObservationEnabledUseCase
    val collectAppUsageObservationsUseCase: CollectAppUsageObservationsUseCase
    val appUsageObservationCheckpointDao: com.sanket_satpute_20.ironmind.data.local.dao.AppUsageObservationCheckpointDao
    val appUsageCheckpointStore: com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpointStore

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
    val observationScheduler: com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationScheduler
    val intelligenceOrchestrationScheduler: com.sanket_satpute_20.ironmind.infrastructure.worker.IntelligenceOrchestrationScheduler
    val observationSchedulingPolicyUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingPolicyUseCase
    val activateObservationSchedulingUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.ActivateObservationSchedulingUseCase
    val reconcileObservationSchedulingUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.ReconcileObservationSchedulingUseCase
    val observationSchedulingCoordinator: com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingCoordinator

    // V4.7 Factual Context Assembly
    val aggregateAppUsageObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateAppUsageObservationsUseCase
    val aggregateActivityObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateActivityObservationsUseCase
    val aggregateCalendarObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateCalendarObservationsUseCase
    val aggregateLocationObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateLocationObservationsUseCase
    val aggregateNotificationObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateNotificationObservationsUseCase
    val buildFactualContextSnapshotUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.BuildFactualContextSnapshotUseCase

    // Services
    val learnerEventStatusDao: com.sanket_satpute_20.ironmind.data.local.dao.LearnerEventStatusDao
    val eventProcessor: com.sanket_satpute_20.ironmind.execution.background.EventProcessor
    val database: com.sanket_satpute_20.ironmind.data.local.IronMindDatabase
    val cloudflareWorkerDiagnostic: CloudflareWorkerDiagnostic
    val appSessionStateDao: com.sanket_satpute_20.ironmind.data.local.dao.AppSessionStateDao
    val syncWorkerLeaseDao: com.sanket_satpute_20.ironmind.data.local.dao.SyncWorkerLeaseDao
    val syncCheckpointDao: com.sanket_satpute_20.ironmind.data.local.dao.SyncCheckpointDao
    val localTombstoneDao: com.sanket_satpute_20.ironmind.data.local.dao.LocalTombstoneDao
}

class DefaultAppContainer(
    private val context: Context,
    private val aiProviderType: AIProviderType = if (BuildConfig.DEBUG) AIProviderType.DISABLED else AIProviderType.GEMINI,
    private val cloudflareWorkerEndpointUrl: String? = null,
    private val cloudflareWorkerRequireAppCheck: Boolean? = null
) : AppContainer {

    override val logger: IronLogger by lazy {
        DebugIronLogger()
    }

    override val database: IronMindDatabase by lazy {
        val dbName = if (BuildConfig.DEBUG) "ironmind_database_debug" else "ironmind_database"
        Room.databaseBuilder(
            context,
            IronMindDatabase::class.java,
            dbName
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
            IronMindDatabase.MIGRATION_16_17,
            IronMindDatabase.MIGRATION_17_18,
            IronMindDatabase.MIGRATION_18_19,
            IronMindDatabase.MIGRATION_19_20,
            IronMindDatabase.MIGRATION_20_21,
            IronMindDatabase.MIGRATION_21_22,
            IronMindDatabase.MIGRATION_22_23,
            IronMindDatabase.MIGRATION_23_24,
            IronMindDatabase.MIGRATION_24_25,
            IronMindDatabase.MIGRATION_25_26,
            IronMindDatabase.MIGRATION_26_27,
            IronMindDatabase.MIGRATION_27_28,
            IronMindDatabase.MIGRATION_28_29,
            IronMindDatabase.MIGRATION_29_30
        ).build()
    }

    override val appSessionStateDao: com.sanket_satpute_20.ironmind.data.local.dao.AppSessionStateDao by lazy {
        database.appSessionStateDao()
    }

    override val syncWorkerLeaseDao: com.sanket_satpute_20.ironmind.data.local.dao.SyncWorkerLeaseDao by lazy {
        database.syncWorkerLeaseDao()
    }

    override val syncCheckpointDao: com.sanket_satpute_20.ironmind.data.local.dao.SyncCheckpointDao by lazy {
        database.syncCheckpointDao()
    }

    override val localTombstoneDao: com.sanket_satpute_20.ironmind.data.local.dao.LocalTombstoneDao by lazy {
        database.localTombstoneDao()
    }

    private val globalAutonomyStateDao: com.sanket_satpute_20.ironmind.data.local.dao.GlobalAutonomyStateDao by lazy {
        database.globalAutonomyStateDao()
    }

    override val learnerEventStatusDao: com.sanket_satpute_20.ironmind.data.local.dao.LearnerEventStatusDao by lazy {
        database.learnerEventStatusDao()
    }

    override val eventProcessor: com.sanket_satpute_20.ironmind.execution.background.EventProcessor by lazy {
        com.sanket_satpute_20.ironmind.execution.background.DefaultEventProcessor(
            evaluateCorrectionReviewDecisionUseCase = evaluateCorrectionReviewDecisionUseCase,
            correctionReviewRepository = correctionReviewRepository,
            interventionRepository = interventionRepository,
            logger = logger
        )
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
        CommitmentRepositoryImpl(
            dao = database.ironMindDao(),
            outboxDao = database.outboxDao(),
            database = database,
            idGenerator = idGenerator,
            backgroundExecutor = backgroundExecutor
        )
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
        ReflectionRepositoryImpl(
            dao = database.ironMindDao(),
            outboxDao = database.outboxDao(),
            database = database,
            idGenerator = idGenerator,
            backgroundExecutor = backgroundExecutor
        )
    }

    override val eventRepository: EventRepository by lazy {
        EventRepositoryImpl(
            dao = database.ironMindDao(),
            logger = logger,
            database = database,
            learnerEventStatusDao = learnerEventStatusDao
        )
    }

    override val goalRepository: GoalRepository by lazy {
        GoalRepositoryImpl(
            dao = database.ironMindDao(),
            outboxDao = database.outboxDao(),
            database = database,
            idGenerator = idGenerator,
            backgroundExecutor = backgroundExecutor
        )
    }

    private val planRepository: com.sanket_satpute_20.ironmind.domain.repository.PlanRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.PlanRepositoryImpl(
            dao = database.ironMindDao(),
            outboxDao = database.outboxDao(),
            database = database,
            idGenerator = idGenerator,
            backgroundExecutor = backgroundExecutor
        )
    }

    override val taskRepository: com.sanket_satpute_20.ironmind.domain.repository.TaskRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.TaskRepositoryImpl(
            dao = database.ironMindDao(),
            outboxDao = database.outboxDao(),
            database = database,
            idGenerator = idGenerator,
            backgroundExecutor = backgroundExecutor
        )
    }

    override val memoryRepository: MemoryRepository by lazy {
        MemoryRepositoryImpl(
            dao = database.ironMindDao(),
            database = database,
            outboxDao = database.outboxDao(),
            idGenerator = idGenerator,
            backgroundExecutor = backgroundExecutor
        )
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
        com.sanket_satpute_20.ironmind.domain.usecase.autonomy.ToggleGlobalPauseUseCase(autonomySettingsRepository, observationSchedulingCoordinator)
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
        try {
            val auth = FirebaseAuth.getInstance()
            if (BuildConfig.USE_FIREBASE_EMULATORS) {
                try {
                    auth.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, BuildConfig.FIREBASE_AUTH_EMULATOR_PORT)
                } catch (_: IllegalStateException) {
                    // Ignored if emulator was already configured for this instance
                } catch (t: Throwable) {
                    throw IllegalStateException("Failed to configure Firebase Auth emulator on port ${BuildConfig.FIREBASE_AUTH_EMULATOR_PORT}: ${t.message}", t)
                }
            } else if (BuildConfig.DEBUG) {
                throw IllegalStateException("Debug builds must not connect to production Firebase Auth.")
            }
            AuthRepositoryImpl(auth)
        } catch (_: Throwable) {
            object : AuthRepository {
                override val currentUser: kotlinx.coroutines.flow.Flow<com.sanket_satpute_20.ironmind.domain.model.AuthUser?> =
                    kotlinx.coroutines.flow.MutableStateFlow(null)
                override val authStatus: kotlinx.coroutines.flow.Flow<com.sanket_satpute_20.ironmind.domain.model.AuthStatus> =
                    kotlinx.coroutines.flow.MutableStateFlow(com.sanket_satpute_20.ironmind.domain.model.AuthStatus.UNAUTHENTICATED)
                override fun getCurrentUser(): com.sanket_satpute_20.ironmind.domain.model.AuthUser? = null
                override suspend fun signInAnonymously(): com.sanket_satpute_20.ironmind.domain.common.Result<com.sanket_satpute_20.ironmind.domain.model.AuthUser, Exception> =
                    com.sanket_satpute_20.ironmind.domain.common.Result.Failure(IllegalStateException("Local Firebase Auth emulator unavailable. Production fallback is strictly blocked."))
                override suspend fun signOut(): com.sanket_satpute_20.ironmind.domain.common.Result<Unit, Exception> =
                    com.sanket_satpute_20.ironmind.domain.common.Result.Success(Unit)
            }
        }
    }

    override val userIdentityReconciler: UserIdentityReconciler by lazy {
        RoomUserIdentityReconciler(database)
    }

    override val userSessionProvider: UserSessionProvider by lazy {
        val bgExec = try {
            backgroundExecutor
        } catch (e: Exception) {
            null
        }
        UserSessionProviderImpl(
            authRepository = authRepository,
            reconciler = userIdentityReconciler,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            autoPermitOfflineFallback = true,
            signInAnonymouslyUseCase = signInAnonymouslyUseCase,
            backgroundExecutor = bgExec,
            appSessionStateDao = appSessionStateDao,
            syncWorkerLeaseDao = syncWorkerLeaseDao
        )
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
            eventRepository,
            taskRepository,
            backgroundExecutor
        )
    }

    override val saveReflectionUseCase: SaveReflectionUseCase by lazy {
        SaveReflectionUseCase(reflectionRepository, idGenerator, clock, eventRepository, backgroundExecutor)
    }

    override val editGoalUseCase: EditGoalUseCase by lazy {
        EditGoalUseCase(goalRepository, clock, idGenerator, eventRepository)
    }

    override val updateGoalStatusUseCase: UpdateGoalStatusUseCase by lazy {
        UpdateGoalStatusUseCase(goalRepository, clock)
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

    override val getGoalProgressUseCase: GetGoalProgressUseCase by lazy {
        GetGoalProgressUseCase(planRepository, taskRepository)
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

    override val editPlanUseCase: EditPlanUseCase by lazy {
        EditPlanUseCase(planRepository, clock, idGenerator, eventRepository)
    }

    override val updatePlanStatusUseCase: UpdatePlanStatusUseCase by lazy {
        UpdatePlanStatusUseCase(planRepository, clock, idGenerator, eventRepository)
    }

    override val getPlanProgressUseCase: GetPlanProgressUseCase by lazy {
        GetPlanProgressUseCase(taskRepository)
    }

    override val getTasksUseCase: GetTasksUseCase by lazy {
        GetTasksUseCase(taskRepository)
    }

    override val createTaskUseCase: CreateTaskUseCase by lazy {
        CreateTaskUseCase(taskRepository, idGenerator, clock, eventRepository)
    }

    override val editTaskUseCase: EditTaskUseCase by lazy {
        EditTaskUseCase(taskRepository, clock, eventRepository, idGenerator)
    }

    override val updateTaskStatusUseCase: UpdateTaskStatusUseCase by lazy {
        UpdateTaskStatusUseCase(taskRepository, clock, eventRepository, idGenerator)
    }

    override val deleteTaskUseCase: DeleteTaskUseCase by lazy {
        DeleteTaskUseCase(taskRepository)
    }

    override val getDueTasksUseCase: GetDueTasksUseCase by lazy {
        GetDueTasksUseCase(taskRepository, planRepository, clock)
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

    override val getMemoriesUseCase: GetMemoriesUseCase by lazy {
        GetMemoriesUseCase(memoryRepository = memoryRepository)
    }

    override val rejectMemoryUseCase: RejectMemoryUseCase by lazy {
        RejectMemoryUseCase(
            memoryRepository = memoryRepository,
            eventRepository = eventRepository,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    override val deleteMemoryUseCase: DeleteMemoryUseCase by lazy {
        DeleteMemoryUseCase(
            memoryRepository = memoryRepository,
            eventRepository = eventRepository,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    override val getPatternsUseCase: GetPatternsUseCase by lazy {
        GetPatternsUseCase(patternRepository = patternRepository)
    }

    override val confirmPatternUseCase: ConfirmPatternUseCase by lazy {
        ConfirmPatternUseCase(
            patternRepository = patternRepository,
            eventRepository = eventRepository,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    override val refutePatternUseCase: RefutePatternUseCase by lazy {
        RefutePatternUseCase(
            patternRepository = patternRepository,
            eventRepository = eventRepository,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    override val dismissPatternUseCase: DismissPatternUseCase by lazy {
        DismissPatternUseCase(
            patternRepository = patternRepository,
            eventRepository = eventRepository,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    override val getBarriersUseCase: GetBarriersUseCase by lazy {
        GetBarriersUseCase(barrierRepository = barrierRepository)
    }

    override val confirmBarrierUseCase: ConfirmBarrierUseCase by lazy {
        ConfirmBarrierUseCase(
            barrierRepository = barrierRepository,
            eventRepository = eventRepository,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    override val refuteBarrierUseCase: RefuteBarrierUseCase by lazy {
        RefuteBarrierUseCase(
            barrierRepository = barrierRepository,
            eventRepository = eventRepository,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    override val dismissBarrierUseCase: DismissBarrierUseCase by lazy {
        DismissBarrierUseCase(
            barrierRepository = barrierRepository,
            eventRepository = eventRepository,
            idGenerator = idGenerator,
            clock = clock
        )
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
            eventRepository,
            idGenerator
        )
    }

    override val getActiveProtectionSessionUseCase: com.sanket_satpute_20.ironmind.domain.usecase.protection.GetActiveProtectionSessionUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.protection.GetActiveProtectionSessionUseCase(
            protectionRepository = protectionRepository,
            clock = clock,
            stopProtectionSessionUseCase = stopProtectionSessionUseCase
        )
    }

    override val outboxRepository: OutboxRepository by lazy {
        OutboxRepositoryImpl(database.outboxDao())
    }

    override val syncRepository: SyncRepository by lazy {
        val firestore = try {
            val instance = FirebaseFirestore.getInstance()
            if (BuildConfig.USE_FIREBASE_EMULATORS) {
                try {
                    instance.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, BuildConfig.FIREBASE_FIRESTORE_EMULATOR_PORT)
                } catch (_: IllegalStateException) {
                    // Ignored if emulator was already configured for this instance
                } catch (t: Throwable) {
                    throw IllegalStateException("Failed to configure Firestore emulator on port ${BuildConfig.FIREBASE_FIRESTORE_EMULATOR_PORT}: ${t.message}", t)
                }
            } else if (BuildConfig.DEBUG) {
                throw IllegalStateException("Debug builds must not connect to production Firestore.")
            }
            instance
        } catch (_: Throwable) {
            null
        }

        if (firestore != null) {
            FirestoreSyncRepository(firestore)
        } else {
            object : SyncRepository {
                override suspend fun upload(
                    userId: String,
                    entry: com.sanket_satpute_20.ironmind.domain.model.OutboxEntry
                ): com.sanket_satpute_20.ironmind.domain.common.Result<com.sanket_satpute_20.ironmind.domain.repository.SyncUploadResult, Exception> =
                    com.sanket_satpute_20.ironmind.domain.common.Result.Failure(
                        IllegalStateException("Local Firestore emulator unavailable. Production fallback is strictly blocked.")
                    )

                override suspend fun pull(
                    userId: String,
                    entityType: String,
                    sinceTimestamp: Long,
                    limit: Int
                ): com.sanket_satpute_20.ironmind.domain.common.Result<com.sanket_satpute_20.ironmind.domain.repository.SyncPullResult, Exception> =
                    com.sanket_satpute_20.ironmind.domain.common.Result.Failure(
                        IllegalStateException("Local Firestore emulator unavailable. Production fallback is strictly blocked.")
                    )
            }
        }
    }

    override val syncConflictRepository: com.sanket_satpute_20.ironmind.domain.repository.SyncConflictRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.SyncConflictRepositoryImpl(
            database.syncConflictDao(),
            database.outboxDao(),
            database,
            database.ironMindDao()
        )
    }

    override val syncCheckpointStore: SyncCheckpointStore by lazy {
        RoomSyncCheckpointStore(database.syncCheckpointDao())
    }

    override val syncOrchestrator: SyncOrchestrator by lazy {
        SyncOrchestrator(
            outboxRepository = outboxRepository,
            syncRepository = syncRepository,
            syncConflictDao = database.syncConflictDao(),
            ironMindDao = database.ironMindDao(),
            outboxDao = database.outboxDao(),
            database = database,
            checkpointStore = syncCheckpointStore,
            userSessionProvider = userSessionProvider,
            syncWorkerLeaseDao = database.syncWorkerLeaseDao(),
            appSessionStateDao = database.appSessionStateDao()
        )
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
            buildFactualContextSnapshotUseCase = buildFactualContextSnapshotUseCase,
            reflectionRepository = reflectionRepository,
            protectionRepository = protectionRepository,
            goalRepository = goalRepository,
            patternRepository = patternRepository,
            memoryRepository = memoryRepository,
            planRepository = planRepository,
            taskRepository = taskRepository,
            outcomeRepository = outcomeRepository,
            barrierRepository = barrierRepository,
            appUsageObservationProvider = appUsageObservationProvider,
            appUsageSettingsRepository = appUsageObservationSettingsRepository,
            observationRepository = observationRepository,
            checkpointStore = appUsageCheckpointStore,
            getDueTasksUseCase = getDueTasksUseCase,
            getGoalProgressUseCase = getGoalProgressUseCase,
            getPlanProgressUseCase = getPlanProgressUseCase,
            zoneId = java.time.ZoneId.systemDefault()
        )
    }

    override val synthesizePersonalContextUseCase: SynthesizePersonalContextUseCase by lazy {
        SynthesizePersonalContextUseCase(
            contextEngine = contextEngine,
            ironMindAI = ironMindAI
        )
    }

    override val patternRepository: PatternRepository by lazy {
        PatternRepositoryImpl(
            dao = database.patternDao(),
            database = database,
            eventDao = database.ironMindDao()
        )
    }

    override val patternEngine: PatternEngine by lazy {
        PatternEngineImpl(
            clock = clock,
            patternRepository = patternRepository,
            evidenceSufficiencyEvaluator = evidenceSufficiencyEvaluator
        )
    }

    override val evidenceDiscovery: com.sanket_satpute_20.ironmind.domain.engine.EvidenceDiscovery by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.EvidenceDiscovery(eventRepository, observationRepository, reflectionRepository, logger)
    }

    override val evidenceResolver: com.sanket_satpute_20.ironmind.domain.engine.EvidenceResolver by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.EvidenceResolver(observationRepository, eventRepository, reflectionRepository)
    }

    override val evidenceValidator: com.sanket_satpute_20.ironmind.domain.engine.EvidenceValidator by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.EvidenceValidator()
    }

    override val evidenceSufficiencyEvaluator: com.sanket_satpute_20.ironmind.domain.engine.EvidenceSufficiencyEvaluator by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.EvidenceSufficiencyEvaluator()
    }

    override val patternAcceptanceEvaluator: com.sanket_satpute_20.ironmind.domain.engine.PatternAcceptanceEvaluator by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.PatternAcceptanceEvaluator(
            evidenceSufficiencyEvaluator = evidenceSufficiencyEvaluator
        )
    }

    override val patternAcceptanceUseCase: com.sanket_satpute_20.ironmind.domain.usecase.pattern.PatternAcceptanceUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.pattern.PatternAcceptanceUseCase(
            evaluator = patternAcceptanceEvaluator,
            patternRepository = patternRepository,
            idGenerator = { java.util.UUID.randomUUID().toString() }
        )
    }

    override val evaluatePatternCandidateUseCase: com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidateUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidateUseCase(
            clock = clock,
            evidenceDiscovery = evidenceDiscovery,
            evidenceResolver = evidenceResolver,
            evidenceValidator = evidenceValidator,
            patternAcceptanceUseCase = patternAcceptanceUseCase,
            logger = logger
        )
    }

    override val evaluatePatternCandidatesUseCase: com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidatesUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.pattern.EvaluatePatternCandidatesUseCase(
            evaluatePatternCandidateUseCase = evaluatePatternCandidateUseCase,
            logger = logger
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
            workManager = androidx.work.WorkManager.getInstance(context),
            sessionProvider = { userSessionProvider },
            observationCoordinatorProvider = { observationSchedulingCoordinator }
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

    override val interventionRecommendationRepository: com.sanket_satpute_20.ironmind.domain.repository.InterventionRecommendationRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.InterventionRecommendationRepositoryImpl(database.interventionRecommendationDao())
    }

    override val barrierRepository: BarrierRepository by lazy {
        BarrierRepositoryImpl(
            barrierDao = database.barrierDao(),
            database = database,
            eventDao = database.ironMindDao()
        )
    }

    override val barrierUnderstandingOrchestrator: BarrierUnderstandingOrchestrator by lazy {
        BarrierUnderstandingOrchestrator(
            understandBarriersUseCase = understandBarriersUseCase,
            barrierRepository = barrierRepository,
            idGenerator = idGenerator,
            clock = clock
        )
    }

    override val barrierHypothesisDiscoveryEngine: com.sanket_satpute_20.ironmind.domain.engine.BarrierHypothesisDiscoveryEngine by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.BarrierHypothesisDiscoveryEngine(
            evidenceSufficiencyEvaluator = evidenceSufficiencyEvaluator,
            idGenerator = idGenerator
        )
    }

    override val interventionReasoningEngine: com.sanket_satpute_20.ironmind.domain.engine.InterventionReasoningEngine by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.InterventionReasoningEngine(
            equivalencePolicy = com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionEquivalencePolicy(),
            idGenerator = idGenerator
        )
    }

    override val barrierUnderstandingAndInterventionOrchestrator: com.sanket_satpute_20.ironmind.domain.engine.BarrierUnderstandingAndInterventionOrchestrator by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.BarrierUnderstandingAndInterventionOrchestrator(
            contextEngine = contextEngine,
            patternEngine = patternEngine,
            barrierHypothesisDiscoveryEngine = barrierHypothesisDiscoveryEngine,
            interventionReasoningEngine = interventionReasoningEngine,
            patternRepository = patternRepository,
            barrierRepository = barrierRepository,
            interventionRecommendationRepository = interventionRecommendationRepository,
            clock = clock
        )
    }

    override val insightRepository: InsightRepository by lazy {
        InsightRepositoryImpl(dao = database.ironMindDao())
    }

    override val personalInsightDiscoveryEngine: PersonalInsightDiscoveryEngine by lazy {
        PersonalInsightDiscoveryEngine(
            clock = clock,
            idGenerator = idGenerator
        )
    }

    override val personalIntelligenceOrchestrator: PersonalIntelligenceOrchestrator by lazy {
        PersonalIntelligenceOrchestrator(
            barrierUnderstandingOrchestrator = barrierUnderstandingAndInterventionOrchestrator,
            personalInsightDiscoveryEngine = personalInsightDiscoveryEngine,
            insightRepository = insightRepository,
            outcomeRepository = outcomeRepository,
            clock = clock,
            recommendationRepository = interventionRecommendationRepository
        )
    }

    override val getPersonalInsightsUseCase: GetPersonalInsightsUseCase by lazy {
        GetPersonalInsightsUseCase(insightRepository)
    }

    override val observePersonalInsightsUseCase: ObservePersonalInsightsUseCase by lazy {
        ObservePersonalInsightsUseCase(insightRepository)
    }

    override val reviewPersonalInsightUseCase: ReviewPersonalInsightUseCase by lazy {
        ReviewPersonalInsightUseCase(
            insightRepository = insightRepository,
            recommendationRepository = interventionRecommendationRepository,
            clock = clock
        )
    }

    override val orchestratePersonalIntelligenceUseCase: OrchestratePersonalIntelligenceUseCase by lazy {
        OrchestratePersonalIntelligenceUseCase(personalIntelligenceOrchestrator)
    }

    override val actionRepository: ActionRepository by lazy {
        ActionRepositoryImpl(database.ironMindDao())
    }

    override val actionPlanningEngine: ActionPlanningEngine by lazy {
        ActionPlanningEngine(clock, idGenerator)
    }

    override val actionLifecycleUseCase: ActionLifecycleUseCase by lazy {
        ActionLifecycleUseCase(
            actionRepository = actionRepository,
            recommendationRepository = interventionRecommendationRepository,
            outcomeRepository = outcomeRepository,
            eventRepository = eventRepository,
            taskRepository = taskRepository,
            editTaskUseCase = editTaskUseCase,
            createTaskUseCase = createTaskUseCase,
            scheduleCommitmentUseCase = scheduleCommitmentUseCase,
            createCommitmentUseCase = createCommitmentUseCase,
            updatePlanStatusUseCase = updatePlanStatusUseCase,
            clock = clock,
            idGenerator = idGenerator,
            commitmentRepository = commitmentRepository
        )
    }

    override val dailyBriefingEngine: DailyBriefingEngine by lazy {
        DailyBriefingEngine(
            actionPlanningEngine = actionPlanningEngine,
            clock = clock
        )
    }

    override val assembleDailyBriefingUseCase: AssembleDailyBriefingUseCase by lazy {
        AssembleDailyBriefingUseCase(
            actionRepository = actionRepository,
            dailyBriefingEngine = dailyBriefingEngine,
            personalIntelligenceOrchestrator = personalIntelligenceOrchestrator,
            getDueTasksUseCase = getDueTasksUseCase,
            getActiveCommitmentsUseCase = getActiveCommitmentsUseCase,
            goalRepository = goalRepository,
            planRepository = planRepository,
            insightRepository = insightRepository,
            recommendationRepository = interventionRecommendationRepository,
            clock = clock,
            actionLifecycleUseCase = actionLifecycleUseCase
        )
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

    override val goalResurfacingEngine: com.sanket_satpute_20.ironmind.domain.engine.GoalResurfacingEngine by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.GoalResurfacingEngineImpl(
            goalRepository = goalRepository,
            taskRepository = taskRepository,
            interventionExecutionPipeline = interventionExecutionPipeline,
            clock = clock,
            logger = logger
        )
    }

    override val ironMindAI: IronMindAI by lazy {
        when (aiProviderType) {
            AIProviderType.GEMINI -> {
                if (BuildConfig.DEBUG && !BuildConfig.ALLOW_REMOTE_INFERENCE) {
                    DisabledIronMindAI("Firebase AI Logic is blocked in debug validation builds.")
                } else {
                    GeminiIronMindAI()
                }
            }
            AIProviderType.CLOUDFLARE_WORKER -> CloudflareWorkerIronMindAI(
                endpointUrl = cloudflareWorkerEndpointUrl ?: if (BuildConfig.DEBUG) BuildConfig.CLOUDFLARE_DEV_ENDPOINT else CloudflareWorkerIronMindAI.DEFAULT_LOCAL_DEV_URL,
                requireAppCheck = cloudflareWorkerRequireAppCheck,
                authUserProvider = { userSessionProvider.getActiveUserId() }
            )
            AIProviderType.DISABLED -> DisabledIronMindAI()
            AIProviderType.MOCK -> DisabledIronMindAI("Mock AI Provider")
        }
    }

    override val cloudflareWorkerDiagnostic: CloudflareWorkerDiagnostic by lazy {
        CloudflareWorkerDiagnostic(
            context = context,
            healthUrl = if (BuildConfig.DEBUG) "http://127.0.0.1:8787/health" else CloudflareWorkerDiagnostic.DEFAULT_HEALTH_URL,
            authVerifyUrl = if (BuildConfig.DEBUG) "http://127.0.0.1:8787/v1/auth/verify" else CloudflareWorkerDiagnostic.DEFAULT_AUTH_VERIFY_URL,
            productionUrl = if (BuildConfig.DEBUG) "http://127.0.0.1:8787" else CloudflareWorkerDiagnostic.DEFAULT_PRODUCTION_URL
        )
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

    override val assembleRecommendationContextUseCase: AssembleRecommendationContextUseCase by lazy {
        AssembleRecommendationContextUseCase(
            goalRepository = goalRepository,
            commitmentRepository = commitmentRepository,
            observationRepository = observationRepository,
            patternRepository = patternRepository,
            barrierRepository = barrierRepository,
            reflectionRepository = reflectionRepository,
            eventRepository = eventRepository,
            timeProvider = { clock.currentTimeMillis() }
        )
    }

    override val orchestrateInterventionGenerationUseCase: OrchestrateInterventionGenerationUseCase by lazy {
        OrchestrateInterventionGenerationUseCase(
            assembleContextUseCase = assembleRecommendationContextUseCase,
            recommendInterventionUseCase = recommendInterventionUseCase
        )
    }

    override val recommendInterventionUseCase: RecommendInterventionUseCase by lazy {
        RecommendInterventionUseCase(
            ironMindAI = ironMindAI,
            clock = clock,
            idGenerator = idGenerator,
            autonomySettingsRepository = autonomySettingsRepository,
            recommendationRepository = interventionRecommendationRepository,
            targetCompletionResolver = com.sanket_satpute_20.ironmind.domain.model.intervention.TargetCompletionResolver(
                goalRepository = goalRepository,
                commitmentRepository = commitmentRepository
            ),
            equivalencePolicy = com.sanket_satpute_20.ironmind.domain.model.intervention.InterventionEquivalencePolicy()
        )
    }

    override val handleInterventionResultUseCase: HandleInterventionResultUseCase by lazy {
        HandleInterventionResultUseCase(
            eventRepository = eventRepository,
            updateInterventionRecommendationStatusUseCase = updateInterventionRecommendationStatusUseCase,
            clock = clock,
            idGenerator = idGenerator,
            backgroundExecutor = backgroundExecutor
        )
    }

    override val getIntelligenceHistoryUseCase: GetIntelligenceHistoryUseCase by lazy {
        GetIntelligenceHistoryUseCase(
            interventionRecommendationRepository = interventionRecommendationRepository,
            eventRepository = eventRepository
        )
    }

    override val getPendingInterventionRecommendationsUseCase: GetPendingInterventionRecommendationsUseCase by lazy {
        GetPendingInterventionRecommendationsUseCase(
            repository = interventionRecommendationRepository
        )
    }

    override val updateInterventionRecommendationStatusUseCase: UpdateInterventionRecommendationStatusUseCase by lazy {
        UpdateInterventionRecommendationStatusUseCase(
            repository = interventionRecommendationRepository
        )
    }

    override val evaluateCorrectionEligibilityUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.EvaluateCorrectionEligibilityUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.intervention.EvaluateCorrectionEligibilityUseCase(
            eventRepository = eventRepository,
            recommendationRepository = interventionRecommendationRepository,
            patternRepository = patternRepository,
            barrierRepository = barrierRepository
        )
    }

    override val evaluateCorrectionReviewDecisionUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.EvaluateCorrectionReviewDecisionUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.intervention.EvaluateCorrectionReviewDecisionUseCase(
            evaluateCorrectionEligibilityUseCase = evaluateCorrectionEligibilityUseCase
        )
    }

    override val getCorrectionReviewCandidatesUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.GetCorrectionReviewCandidatesUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.intervention.GetCorrectionReviewCandidatesUseCase(
            eventRepository = eventRepository,
            evaluateCorrectionReviewDecisionUseCase = evaluateCorrectionReviewDecisionUseCase
        )
    }

    override val correctionReviewRepository: com.sanket_satpute_20.ironmind.domain.repository.CorrectionReviewRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.CorrectionReviewRepositoryImpl(
            eventRepository = eventRepository,
            evaluateCorrectionReviewDecisionUseCase = evaluateCorrectionReviewDecisionUseCase,
            correctionReviewDao = database.correctionReviewDao(),
            database = database,
            patternRepository = patternRepository,
            barrierRepository = barrierRepository,
            getCorrectionReviewCandidatesUseCase = getCorrectionReviewCandidatesUseCase
        )
    }

    override val approveCorrectionReviewUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.ApproveCorrectionReviewUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.intervention.ApproveCorrectionReviewUseCase(
            correctionReviewRepository = correctionReviewRepository,
            clock = clock
        )
    }

    override val refuteCorrectionReviewUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.RefuteCorrectionReviewUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.intervention.RefuteCorrectionReviewUseCase(
            correctionReviewRepository = correctionReviewRepository,
            clock = clock
        )
    }

    override val dismissCorrectionReviewUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.DismissCorrectionReviewUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.intervention.DismissCorrectionReviewUseCase(
            correctionReviewRepository = correctionReviewRepository,
            clock = clock
        )
    }

    override val getPendingCorrectionReviewsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.intervention.GetPendingCorrectionReviewsUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.intervention.GetPendingCorrectionReviewsUseCase(
            correctionReviewRepository = correctionReviewRepository
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
        SetAppUsageObservationEnabledUseCase(appUsageObservationSettingsRepository, getAppUsageObservationSettingsUseCase, observationSchedulingCoordinator)
    }

    override val appUsageObservationCheckpointDao: com.sanket_satpute_20.ironmind.data.local.dao.AppUsageObservationCheckpointDao by lazy {
        database.appUsageObservationCheckpointDao()
    }

    override val appUsageCheckpointStore: com.sanket_satpute_20.ironmind.domain.repository.AppUsageCheckpointStore by lazy {
        com.sanket_satpute_20.ironmind.data.repository.RoomAppUsageCheckpointStore(
            database = database,
            observationDao = database.observationDao(),
            checkpointDao = appUsageObservationCheckpointDao
        )
    }

    override val collectAppUsageObservationsUseCase: CollectAppUsageObservationsUseCase by lazy {
        CollectAppUsageObservationsUseCase(
            settingsRepository = appUsageObservationSettingsRepository,
            observationRepository = observationRepository,
            appUsageObservationProvider = appUsageObservationProvider,
            idGenerator = idGenerator,
            clock = clock,
            checkpointStore = appUsageCheckpointStore
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
            patternRepository = patternRepository,
            evaluatePatternCandidatesUseCase = evaluatePatternCandidatesUseCase,
            userProfileRepository = userProfileRepository
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
        com.sanket_satpute_20.ironmind.domain.usecase.observation.SetCalendarObservationEnabledUseCase(calendarObservationSettingsRepository, getCalendarObservationSettingsUseCase, observationSchedulingCoordinator)
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
        com.sanket_satpute_20.ironmind.domain.usecase.observation.SetLocationObservationEnabledUseCase(locationObservationSettingsRepository, getLocationObservationSettingsUseCase, observationSchedulingCoordinator)
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
        com.sanket_satpute_20.ironmind.domain.usecase.observation.SetActivityObservationEnabledUseCase(activityObservationSettingsRepository, getActivityObservationSettingsUseCase, observationSchedulingCoordinator)
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

    override val observationScheduler: com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationScheduler by lazy {
        com.sanket_satpute_20.ironmind.infrastructure.worker.ObservationScheduler(
            workManager = androidx.work.WorkManager.getInstance(context),
            sessionProvider = { userSessionProvider }
        )
    }

    override val intelligenceOrchestrationScheduler: com.sanket_satpute_20.ironmind.infrastructure.worker.IntelligenceOrchestrationScheduler by lazy {
        com.sanket_satpute_20.ironmind.infrastructure.worker.IntelligenceOrchestrationScheduler(
            workManager = androidx.work.WorkManager.getInstance(context),
            sessionProvider = { userSessionProvider },
            clock = clock,
            context = context
        )
    }

    override val observationSchedulingPolicyUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingPolicyUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingPolicyUseCase(
            authRepository = authRepository,
            autonomySettingsRepository = autonomySettingsRepository,
            appUsageSettingsRepository = appUsageObservationSettingsRepository,
            activitySettingsRepository = activityObservationSettingsRepository,
            calendarSettingsRepository = calendarObservationSettingsRepository,
            locationSettingsRepository = locationObservationSettingsRepository
        )
    }

    override val activateObservationSchedulingUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.ActivateObservationSchedulingUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.ActivateObservationSchedulingUseCase(
            policyUseCase = observationSchedulingPolicyUseCase,
            observationScheduler = observationScheduler
        )
    }

    override val reconcileObservationSchedulingUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.ReconcileObservationSchedulingUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.ReconcileObservationSchedulingUseCase(

            activateUseCase = activateObservationSchedulingUseCase,
            observationScheduler = observationScheduler
        )
    }

    override val observationSchedulingCoordinator: com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingCoordinator by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.ObservationSchedulingCoordinator(
            reconcileObservationSchedulingUseCase = reconcileObservationSchedulingUseCase
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
            globalAutonomyStateDao = globalAutonomyStateDao,
            learnerEventStatusDao = learnerEventStatusDao,
            database = database,
            workManager = try { androidx.work.WorkManager.getInstance(context) } catch (e: Exception) { null },
            interventionRecommendationDao = database.interventionRecommendationDao(),
            barrierDao = database.barrierDao(),
            outboxDao = database.outboxDao(),
            appUsageObservationSettingsDao = database.appUsageObservationSettingsDao(),
            notificationObservationSettingsDao = database.notificationObservationSettingsDao(),
            calendarObservationSettingsDao = database.calendarObservationSettingsDao(),
            locationObservationSettingsDao = database.locationObservationSettingsDao(),
            activityObservationSettingsDao = database.activityObservationSettingsDao(),
            correctionReviewDao = database.correctionReviewDao(),
            syncConflictDao = database.syncConflictDao()
        )
    }

    override val dataLifecycleEngine: com.sanket_satpute_20.ironmind.domain.engine.DataLifecycleEngine by lazy {
        com.sanket_satpute_20.ironmind.domain.engine.DataLifecycleEngineImpl(
            dataManagementRepository = dataManagementRepository,
            clock = clock,
            logger = logger
        )
    }

    override val dataExportRepository: com.sanket_satpute_20.ironmind.domain.repository.DataExportRepository by lazy {
        com.sanket_satpute_20.ironmind.data.repository.DataExportRepositoryImpl(
            database = database,
            clock = clock
        )
    }

    override val exportUserDataUseCase: com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataUseCase(
            dataExportRepository = dataExportRepository,
            userSessionProvider = userSessionProvider,
            clock = clock
        )
    }

    override val userDataExportCacheManager: com.sanket_satpute_20.ironmind.domain.repository.UserDataExportCacheManager by lazy {
        val exportDir = java.io.File(context.cacheDir, "exports")
        com.sanket_satpute_20.ironmind.data.export.DefaultUserDataExportCacheManager(exportDir)
    }

    override val userDataExportFileWriter: com.sanket_satpute_20.ironmind.domain.repository.UserDataExportFileWriter by lazy {
        val exportDir = java.io.File(context.cacheDir, "exports")
        com.sanket_satpute_20.ironmind.data.export.UserDataExportFileWriter(
            exportDirectory = exportDir,
            cacheManager = userDataExportCacheManager
        )
    }

    override val exportUserDataToFileUseCase: com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataToFileUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.data.ExportUserDataToFileUseCase(
            exportUserDataUseCase = exportUserDataUseCase,
            fileWriter = userDataExportFileWriter
        )
    }

    override val userDataExportShareManager: com.sanket_satpute_20.ironmind.ui.screens.settings.UserDataExportShareManager by lazy {
        com.sanket_satpute_20.ironmind.ui.screens.settings.AndroidUserDataExportShareManager()
    }

    override val deleteUserDataUseCase: com.sanket_satpute_20.ironmind.domain.usecase.data.DeleteUserDataUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.data.DeleteUserDataUseCase(
            dataManagementRepository = dataManagementRepository,
            userSessionProvider = userSessionProvider,
            authRepository = authRepository,
            exportCacheManager = userDataExportCacheManager
        )
    }

    // V4.7 — Factual Context Assembly
    override val aggregateAppUsageObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateAppUsageObservationsUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateAppUsageObservationsUseCase(observationRepository)
    }

    override val aggregateActivityObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateActivityObservationsUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateActivityObservationsUseCase(observationRepository)
    }

    override val aggregateCalendarObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateCalendarObservationsUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateCalendarObservationsUseCase(observationRepository)
    }

    override val aggregateLocationObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateLocationObservationsUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateLocationObservationsUseCase(observationRepository)
    }

    override val aggregateNotificationObservationsUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateNotificationObservationsUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.AggregateNotificationObservationsUseCase(observationRepository)
    }

    override val buildFactualContextSnapshotUseCase: com.sanket_satpute_20.ironmind.domain.usecase.observation.BuildFactualContextSnapshotUseCase by lazy {
        com.sanket_satpute_20.ironmind.domain.usecase.observation.BuildFactualContextSnapshotUseCase(
            aggregateAppUsage = aggregateAppUsageObservationsUseCase,
            aggregateActivity = aggregateActivityObservationsUseCase,
            aggregateCalendar = aggregateCalendarObservationsUseCase,
            aggregateLocation = aggregateLocationObservationsUseCase,
            aggregateNotifications = aggregateNotificationObservationsUseCase
        )
    }
}

