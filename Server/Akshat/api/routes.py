"""
Centralized Flask-RESTful route registration.

Moving the route table into a dedicated module keeps `create_app`
compact, eliminates accidental duplicate imports, and gives us a single
place to audit URL definitions.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional, Tuple, Type

from flask_restful import Api, Resource

from app.api.admin_ai_workflows import (
    AdminAIWorkflowLogDetail,
    AdminAIWorkflowLogs,
    AdminAIWorkflowsList,
    AdminAIWorkflowStats,
    AdminExperiments,
    AdminPromptManagement,
)

# Admin APIs
from app.api.admin_auth import (
    AdminLogin,
    AdminLogout,
    AdminMe,
    AdminUserRevoke,
    AdminVerify,
)
from app.api.admin_cache_api import (
    AdminCacheClearResource,
    AdminCacheResource,
    AdminCacheStatsResource,
)

# Core platform APIs
from app.api.app_settings_api import AppSettingsAPI, AppSettingsResetAPI

# Auth endpoints
from app.api.auth import (
    AuthLogin,
    AuthProvider,
    AuthSignup,
    AuthVerifyEmail,
    AuthVerifyToken,
)
from app.api.documents_api import (
    DocumentChangesAPI,
    DocumentDetailAPI,
    DocumentPageDetailAPI,
    DocumentPagesAPI,
    DocumentPagesReorderAPI,
    DocumentScanAPI,
    DocumentSearchAPI,
    DocumentsListAPI,
    DocumentSyncAPI,
)
from app.api.fcm_api import FCMTestAPI, FCMTokenAPI
from app.api.optimized_upload_api import (
    OptimizedUploadCompleteAPI,
    OptimizedUploadSignedUrlAPI,
    OptimizedUploadStatusAPI,
)
from app.api.referrals_api import (
    ReferralLimitsAPI,
    ReferralMyCodeAPI,
    ReferralMyReferralsAPI,
    ReferralStatsAPI,
    ReferralUsageAPI,
    ReferralValidateAPI,
)
from app.api.subscription_api import (
    CurrentSubscriptionAPI,
    SubscriptionPlansAPI,
    SubscriptionUpgradeAPI,
)
from app.api.courses_api import (
    CourseConceptsAPI,
    CourseDetailAPI,
    CourseMaterialsAPI,
    CourseModulesAPI,
    CourseSearchAPI,
    CoursesAPI,
    CourseStructureGenerateAPI,
    MaterialDetailAPI,
    MaterialReingestAPI,
    MaterialStatusAPI,
    SemesterDetailAPI,
    SemestersAPI,
)
from app.api.student_profile_api import StudentProfileAPI
from app.api.tutor_api import TutorMessagesAPI, TutorSessionDetailAPI, TutorSessionsAPI
from app.api.work_api import WorkSubmissionDetailAPI, WorkSubmissionStreamAPI, WorkSubmissionsAPI
from app.api.admin_university_api import (
    AdminAICostsAPI,
    AdminCatalogAPI,
    AdminModerationAPI,
    AdminModerationResolveAPI,
    AdminOverviewAPI,
    AdminStudentDetailAPI,
    AdminStudentsAPI,
)
from app.api.research_api import (
    CompareAPI,
    ExportAPI,
    MaterialSummaryAPI,
    MilestoneDetailAPI,
    PaperAnalyzeAPI,
    PaperDetailAPI,
    PaperDownloadAPI,
    PapersAPI,
    ProjectMilestonesAPI,
    ProjectMilestonesSuggestAPI,
    ResearchProjectDetailAPI,
    ResearchProjectsAPI,
    WritingFeedbackAPI,
)
from app.api.study_groups_api import (
    GroupDetailAPI,
    GroupDiscoverAPI,
    GroupInviteCodeAPI,
    GroupJoinAPI,
    GroupJoinByCodeAPI,
    GroupLeaveAPI,
    GroupMemberAPI,
    GroupNoteDetailAPI,
    GroupNoteDownloadAPI,
    GroupNotesAPI,
    GroupsAPI,
    ReportsAPI,
)
from app.api.student_progress_api import (
    GradeEntryAPI,
    GradeTrackerAPI,
    ProgressCourseDetailAPI,
    ProgressCoursesAPI,
    TodayAPI,
)
from app.api.deadlines_api import (
    DeadlineDetailAPI,
    DeadlinesAPI,
    DeadlinesBulkAPI,
    DeadlinesImportICalAPI,
    StudyPlanDetailAPI,
    StudyPlanGenerateAPI,
    StudyPlansAPI,
    StudyPlanTaskAPI,
    SyllabusExtractAPI,
)
from app.api.practice_api import (
    PracticeAttemptDetailAPI,
    PracticeAttemptRegradeAPI,
    PracticeAttemptsAPI,
    PracticeAttemptStartAPI,
    PracticeAttemptSubmitAPI,
    PracticeGenerateAPI,
    PracticeSetDetailAPI,
    PracticeSetsAPI,
)
from app.api.flashcards_api import (
    CourseFlashcardsAPI,
    FlashcardDetailAPI,
    FlashcardGenerateAPI,
    FlashcardReviewAPI,
    FlashcardsAPI,
    FlashcardSummaryAPI,
)

Route = Tuple[Type[Resource], Tuple[str, ...], Optional[str]]


@dataclass(frozen=True)
class ApiRoute:
    resource: Type[Resource]
    urls: Tuple[str, ...]
    endpoint: Optional[str] = None

    def as_tuple(self) -> Route:
        return (self.resource, self.urls, self.endpoint)


ROUTES: Tuple[ApiRoute, ...] = (
    # Auth
    ApiRoute(AuthSignup, ("/api/auth/signup",)),
    ApiRoute(AuthLogin, ("/api/auth/login",)),
    ApiRoute(AuthProvider, ("/api/auth/provider",)),
    ApiRoute(AuthVerifyEmail, ("/api/auth/verify-email",)),
    ApiRoute(AuthVerifyToken, ("/api/auth/verify-token/<string:token>",)),
    ApiRoute(AdminLogin, ("/api/admin/auth/login",)),
    ApiRoute(AdminLogout, ("/api/admin/auth/logout",)),
    ApiRoute(AdminMe, ("/api/admin/auth/me",)),
    ApiRoute(AdminVerify, ("/api/admin/auth/verify",)),
    ApiRoute(AdminUserRevoke, ("/api/admin/users/<string:user_id>/revoke",)),
    # Student profile
    ApiRoute(StudentProfileAPI, ("/api/student/profile",)),
    # University courses (migration Phase 2)
    ApiRoute(SemestersAPI, ("/api/semesters",)),
    ApiRoute(SemesterDetailAPI, ("/api/semesters/<string:semester_id>",)),
    ApiRoute(CoursesAPI, ("/api/courses",)),
    ApiRoute(CourseDetailAPI, ("/api/courses/<string:course_id>",)),
    ApiRoute(CourseMaterialsAPI, ("/api/courses/<string:course_id>/materials",)),
    ApiRoute(CourseStructureGenerateAPI, ("/api/courses/<string:course_id>/structure/generate",)),
    ApiRoute(CourseModulesAPI, ("/api/courses/<string:course_id>/modules",)),
    ApiRoute(CourseConceptsAPI, ("/api/courses/<string:course_id>/concepts",)),
    ApiRoute(CourseSearchAPI, ("/api/courses/<string:course_id>/search",)),
    ApiRoute(MaterialDetailAPI, ("/api/materials/<string:material_id>",)),
    ApiRoute(MaterialStatusAPI, ("/api/materials/<string:material_id>/status",)),
    ApiRoute(MaterialReingestAPI, ("/api/materials/<string:material_id>/reingest",)),
    # Tutor chat (migration Phase 3)
    ApiRoute(TutorSessionsAPI, ("/api/courses/<string:course_id>/tutor/sessions",)),
    ApiRoute(TutorSessionDetailAPI, ("/api/tutor/sessions/<string:session_id>",)),
    ApiRoute(TutorMessagesAPI, ("/api/tutor/sessions/<string:session_id>/messages",)),
    # Check my work (migration Phase 4)
    ApiRoute(WorkSubmissionsAPI, ("/api/work/submissions",)),
    ApiRoute(WorkSubmissionDetailAPI, ("/api/work/submissions/<string:submission_id>",)),
    ApiRoute(WorkSubmissionStreamAPI, ("/api/work/submissions/<string:submission_id>/stream",)),
    # Flashcards and spaced repetition (migration Phase 5)
    ApiRoute(FlashcardGenerateAPI, ("/api/courses/<string:course_id>/flashcards/generate",)),
    ApiRoute(CourseFlashcardsAPI, ("/api/courses/<string:course_id>/flashcards",)),
    ApiRoute(FlashcardsAPI, ("/api/flashcards",)),
    ApiRoute(FlashcardSummaryAPI, ("/api/flashcards/summary",)),
    ApiRoute(FlashcardDetailAPI, ("/api/flashcards/<string:card_id>",)),
    ApiRoute(FlashcardReviewAPI, ("/api/flashcards/<string:card_id>/review",)),
    # Practice sets and mock exams (migration Phase 6)
    ApiRoute(PracticeGenerateAPI, ("/api/courses/<string:course_id>/practice-sets/generate",)),
    ApiRoute(PracticeSetsAPI, ("/api/practice-sets",)),
    ApiRoute(PracticeSetDetailAPI, ("/api/practice-sets/<string:set_id>",)),
    ApiRoute(PracticeAttemptStartAPI, ("/api/practice-sets/<string:set_id>/attempts",)),
    ApiRoute(PracticeAttemptsAPI, ("/api/practice-attempts",)),
    ApiRoute(PracticeAttemptDetailAPI, ("/api/practice-attempts/<string:attempt_id>",)),
    ApiRoute(PracticeAttemptSubmitAPI, ("/api/practice-attempts/<string:attempt_id>/submit",)),
    ApiRoute(PracticeAttemptRegradeAPI, ("/api/practice-attempts/<string:attempt_id>/regrade",)),
    # Deadlines, syllabus, iCal and exam study plans (migration Phase 7)
    ApiRoute(DeadlinesAPI, ("/api/deadlines",)),
    ApiRoute(DeadlinesBulkAPI, ("/api/deadlines/bulk",)),
    ApiRoute(DeadlinesImportICalAPI, ("/api/deadlines/import-ical",)),
    ApiRoute(DeadlineDetailAPI, ("/api/deadlines/<string:deadline_id>",)),
    ApiRoute(SyllabusExtractAPI, ("/api/courses/<string:course_id>/syllabus/extract",)),
    ApiRoute(StudyPlanGenerateAPI, ("/api/study-plans/generate",)),
    ApiRoute(StudyPlansAPI, ("/api/study-plans",)),
    ApiRoute(StudyPlanDetailAPI, ("/api/study-plans/<string:plan_id>",)),
    ApiRoute(StudyPlanTaskAPI, ("/api/study-plans/<string:plan_id>/tasks/<string:task_id>",)),
    # Today, course progress and grade tracker (migration Phase 8)
    ApiRoute(TodayAPI, ("/api/today",)),
    ApiRoute(ProgressCoursesAPI, ("/api/progress/courses",)),
    ApiRoute(ProgressCourseDetailAPI, ("/api/progress/courses/<string:course_id>",)),
    ApiRoute(GradeTrackerAPI, ("/api/grades-tracker",)),
    ApiRoute(GradeEntryAPI, ("/api/grades-tracker/<string:entry_id>",)),
    # Study groups, shared notes and reports (migration Phase 9)
    ApiRoute(GroupsAPI, ("/api/groups",)),
    ApiRoute(GroupDiscoverAPI, ("/api/groups/discover",)),
    ApiRoute(GroupJoinByCodeAPI, ("/api/groups/join",)),
    ApiRoute(GroupDetailAPI, ("/api/groups/<string:group_id>",)),
    ApiRoute(GroupJoinAPI, ("/api/groups/<string:group_id>/join",)),
    ApiRoute(GroupLeaveAPI, ("/api/groups/<string:group_id>/leave",)),
    ApiRoute(GroupInviteCodeAPI, ("/api/groups/<string:group_id>/invite-code",)),
    ApiRoute(GroupMemberAPI, ("/api/groups/<string:group_id>/members/<string:member_id>",)),
    ApiRoute(GroupNotesAPI, ("/api/groups/<string:group_id>/notes",)),
    ApiRoute(GroupNoteDetailAPI, ("/api/groups/<string:group_id>/notes/<string:note_id>",)),
    ApiRoute(GroupNoteDownloadAPI, ("/api/groups/<string:group_id>/notes/<string:note_id>/download",)),
    ApiRoute(ReportsAPI, ("/api/reports",)),
    # Research tools and lecture summaries (migration Phase 10)
    ApiRoute(ResearchProjectsAPI, ("/api/research/projects",)),
    ApiRoute(ResearchProjectDetailAPI, ("/api/research/projects/<string:project_id>",)),
    ApiRoute(ProjectMilestonesAPI, ("/api/research/projects/<string:project_id>/milestones",)),
    ApiRoute(ProjectMilestonesSuggestAPI, ("/api/research/projects/<string:project_id>/milestones/suggest",)),
    ApiRoute(MilestoneDetailAPI, ("/api/research/milestones/<string:milestone_id>",)),
    ApiRoute(PapersAPI, ("/api/research/papers",)),
    ApiRoute(PaperDetailAPI, ("/api/research/papers/<string:paper_id>",)),
    ApiRoute(PaperAnalyzeAPI, ("/api/research/papers/<string:paper_id>/analyze",)),
    ApiRoute(PaperDownloadAPI, ("/api/research/papers/<string:paper_id>/download",)),
    ApiRoute(CompareAPI, ("/api/research/compare",)),
    ApiRoute(ExportAPI, ("/api/research/export",)),
    ApiRoute(WritingFeedbackAPI, ("/api/research/writing-feedback",)),
    ApiRoute(MaterialSummaryAPI, ("/api/materials/<string:material_id>/summary",)),
    # Cache
    ApiRoute(AdminCacheResource, ("/api/admin/cache",)),
    ApiRoute(AdminCacheStatsResource, ("/api/admin/cache/stats",)),
    ApiRoute(AdminCacheClearResource, ("/api/admin/cache/clear",)),
    # AI Workflows
    ApiRoute(AdminAIWorkflowsList, ("/api/admin/ai-workflows",)),
    ApiRoute(AdminAIWorkflowLogs, ("/api/admin/ai-workflows/logs",)),
    ApiRoute(
        AdminAIWorkflowLogDetail,
        ("/api/admin/ai-workflows/logs/<string:log_id>",),
    ),
    ApiRoute(AdminAIWorkflowStats, ("/api/admin/ai-workflows/stats",)),
    ApiRoute(AdminPromptManagement, ("/api/admin/prompts",)),
    ApiRoute(AdminExperiments, ("/api/admin/experiments",)),
    # University admin: overview, students, catalog, moderation, AI costs (migration Phase 11)
    ApiRoute(AdminOverviewAPI, ("/api/admin/overview",)),
    ApiRoute(AdminStudentsAPI, ("/api/admin/students",)),
    ApiRoute(AdminStudentDetailAPI, ("/api/admin/students/<string:user_id>",)),
    ApiRoute(AdminCatalogAPI, ("/api/admin/catalog",)),
    ApiRoute(AdminModerationAPI, ("/api/admin/moderation",)),
    ApiRoute(AdminModerationResolveAPI, ("/api/admin/moderation/<string:report_id>/resolve",)),
    ApiRoute(AdminAICostsAPI, ("/api/admin/ai-costs",)),
    # FCM
    ApiRoute(FCMTokenAPI, ("/api/fcm/token",)),
    ApiRoute(FCMTestAPI, ("/api/fcm/test",)),
    # Documents
    ApiRoute(DocumentScanAPI, ("/api/documents/scan",)),
    ApiRoute(DocumentsListAPI, ("/api/documents",)),
    ApiRoute(DocumentDetailAPI, ("/api/documents/<string:doc_id>",)),
    ApiRoute(DocumentPagesAPI, ("/api/documents/<string:doc_id>/pages",)),
    ApiRoute(
        DocumentPageDetailAPI,
        ("/api/documents/<string:doc_id>/pages/<string:page_id>",),
    ),
    ApiRoute(
        DocumentPagesReorderAPI,
        ("/api/documents/<string:doc_id>/pages/reorder",),
    ),
    ApiRoute(DocumentSearchAPI, ("/api/documents/search",)),
    ApiRoute(DocumentSyncAPI, ("/api/documents/sync",)),
    ApiRoute(DocumentChangesAPI, ("/api/documents/changes",)),
    # App settings
    ApiRoute(AppSettingsAPI, ("/api/v1/settings",)),
    ApiRoute(AppSettingsResetAPI, ("/api/v1/settings/reset",)),
    # Optimized uploads (direct Firebase Storage)
    ApiRoute(OptimizedUploadSignedUrlAPI, ("/api/uploads/signed-url",)),
    ApiRoute(OptimizedUploadCompleteAPI, ("/api/uploads/complete",)),
    ApiRoute(OptimizedUploadStatusAPI, ("/api/uploads/<string:upload_id>/status",)),
    # Referral system
    ApiRoute(ReferralValidateAPI, ("/api/referrals/validate",)),
    ApiRoute(ReferralMyCodeAPI, ("/api/referrals/my-code",)),
    ApiRoute(ReferralMyReferralsAPI, ("/api/referrals/my-referrals",)),
    ApiRoute(ReferralStatsAPI, ("/api/referrals/stats",)),
    ApiRoute(ReferralLimitsAPI, ("/api/referrals/my-limits",)),
    ApiRoute(ReferralUsageAPI, ("/api/referrals/usage",)),
    # Subscription system
    ApiRoute(SubscriptionPlansAPI, ("/api/subscriptions/plans",)),
    ApiRoute(CurrentSubscriptionAPI, ("/api/subscriptions/current",)),
    ApiRoute(SubscriptionUpgradeAPI, ("/api/subscriptions/upgrade",)),
)


def register_routes(api: Api) -> None:
    """Attach all REST resources to the given API instance."""
    for resource, urls, endpoint in (route.as_tuple() for route in ROUTES):
        if endpoint:
            api.add_resource(resource, *urls, endpoint=endpoint)
        else:
            api.add_resource(resource, *urls)
