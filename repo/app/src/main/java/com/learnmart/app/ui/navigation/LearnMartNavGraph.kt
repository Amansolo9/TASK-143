package com.learnmart.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.learnmart.app.ui.screens.admin.policies.PolicyListScreen
import com.learnmart.app.ui.screens.admin.policies.PolicyEditScreen
import com.learnmart.app.ui.screens.admin.users.CreateUserScreen
import com.learnmart.app.ui.screens.admin.users.UserDetailScreen
import com.learnmart.app.ui.screens.admin.users.UserListScreen
import com.learnmart.app.ui.screens.audit.AuditLogScreen
import com.learnmart.app.ui.screens.catalog.CatalogScreen
import com.learnmart.app.ui.screens.catalog.CourseDetailScreen
import com.learnmart.app.ui.screens.catalog.CreateCourseScreen
import com.learnmart.app.ui.screens.catalog.CreateClassScreen
import com.learnmart.app.ui.screens.dashboard.DashboardScreen
import com.learnmart.app.domain.model.Permission
import com.learnmart.app.ui.screens.operations.OperationsScreen
import com.learnmart.app.ui.screens.operations.ImportScreen
import com.learnmart.app.ui.screens.operations.ReconciliationScreen
import com.learnmart.app.ui.screens.operations.BackupRestoreScreen
import com.learnmart.app.ui.screens.assessment.AssessmentListScreen
import com.learnmart.app.ui.screens.assessment.TakeAssessmentScreen
import com.learnmart.app.ui.screens.assessment.GradingScreen
import com.learnmart.app.ui.screens.assessment.SimilarityReviewScreen
import com.learnmart.app.ui.screens.commerce.CartScreen
import com.learnmart.app.ui.screens.commerce.OrderListScreen
import com.learnmart.app.ui.screens.commerce.OrderDetailScreen
import com.learnmart.app.ui.screens.commerce.RecordPaymentScreen
import com.learnmart.app.ui.screens.commerce.RefundScreen
import com.learnmart.app.ui.screens.enrollment.EnrollmentListScreen
import com.learnmart.app.ui.screens.enrollment.EnrollmentRequestScreen
import com.learnmart.app.ui.screens.enrollment.ApprovalTaskScreen
import com.learnmart.app.ui.screens.login.LoginScreen

@Composable
fun LearnMartNavGraph(
    navController: NavHostController,
    startDestination: String = NavRoutes.LOGIN
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(NavRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.DASHBOARD) {
            DashboardScreen(
                onNavigateToUsers = { navController.navigate(NavRoutes.ADMIN_USERS) },
                onNavigateToPolicies = { navController.navigate(NavRoutes.ADMIN_POLICIES) },
                onNavigateToAuditLog = { navController.navigate(NavRoutes.AUDIT_LOG) },
                onNavigateToCatalog = { navController.navigate(NavRoutes.CATALOG) },
                onNavigateToEnrollments = { navController.navigate(NavRoutes.ENROLLMENTS) },
                onNavigateToOrders = { navController.navigate(NavRoutes.ORDERS) },
                onNavigateToCart = { navController.navigate(NavRoutes.CART) },
                onNavigateToAssessments = { navController.navigate(NavRoutes.ASSESSMENTS) },
                onNavigateToOperations = { navController.navigate(NavRoutes.OPERATIONS) },
                onLogout = {
                    navController.navigate(NavRoutes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Admin routes: guarded by USER_MANAGE permission ──
        composable(NavRoutes.ADMIN_USERS) {
            RequirePermission(
                requiredPermissions = arrayOf(Permission.USER_MANAGE),
                onNavigateBack = { navController.popBackStack() }
            ) {
                UserListScreen(
                    onNavigateToCreateUser = { navController.navigate(NavRoutes.ADMIN_CREATE_USER) },
                    onNavigateToUserDetail = { userId -> navController.navigate(NavRoutes.userDetail(userId)) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(NavRoutes.ADMIN_CREATE_USER) {
            RequirePermission(
                requiredPermissions = arrayOf(Permission.USER_MANAGE),
                onNavigateBack = { navController.popBackStack() }
            ) {
                CreateUserScreen(
                    onUserCreated = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = NavRoutes.ADMIN_USER_DETAIL,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            RequirePermission(
                requiredPermissions = arrayOf(Permission.USER_MANAGE),
                onNavigateBack = { navController.popBackStack() }
            ) {
                UserDetailScreen(
                    userId = userId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // ── Policy routes: guarded by POLICY_MANAGE permission ──
        composable(NavRoutes.ADMIN_POLICIES) {
            RequirePermission(
                requiredPermissions = arrayOf(Permission.POLICY_MANAGE),
                onNavigateBack = { navController.popBackStack() }
            ) {
                PolicyListScreen(
                    onNavigateToPolicyEdit = { policyId -> navController.navigate(NavRoutes.policyEdit(policyId)) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = NavRoutes.ADMIN_POLICY_EDIT,
            arguments = listOf(navArgument("policyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val policyId = backStackEntry.arguments?.getString("policyId") ?: return@composable
            RequirePermission(
                requiredPermissions = arrayOf(Permission.POLICY_MANAGE),
                onNavigateBack = { navController.popBackStack() }
            ) {
                PolicyEditScreen(
                    policyId = policyId,
                    onSaved = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // ── Audit route: guarded by AUDIT_VIEW permission ──
        composable(NavRoutes.AUDIT_LOG) {
            RequirePermission(
                requiredPermissions = arrayOf(Permission.AUDIT_VIEW),
                onNavigateBack = { navController.popBackStack() }
            ) {
                AuditLogScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Phase 2: Catalog
        composable(NavRoutes.CATALOG) {
            CatalogScreen(
                onNavigateToCreateCourse = { navController.navigate(NavRoutes.CATALOG_CREATE_COURSE) },
                onNavigateToCourseDetail = { courseId -> navController.navigate(NavRoutes.courseDetail(courseId)) },
                onNavigateToCreateClass = { courseId -> navController.navigate(NavRoutes.createClass(courseId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.CATALOG_CREATE_COURSE) {
            CreateCourseScreen(
                onCourseCreated = { navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.CATALOG_COURSE_DETAIL,
            arguments = listOf(navArgument("courseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId") ?: return@composable
            CourseDetailScreen(
                courseId = courseId,
                onNavigateToCreateClass = { cId -> navController.navigate(NavRoutes.createClass(cId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.CATALOG_CREATE_CLASS,
            arguments = listOf(navArgument("courseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId") ?: return@composable
            CreateClassScreen(
                courseId = courseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Phase 2: Enrollment
        composable(NavRoutes.ENROLLMENTS) {
            EnrollmentListScreen(
                onNavigateToEnrollmentDetail = { },
                onNavigateToApprovalTask = { taskId -> navController.navigate(NavRoutes.approvalTask(taskId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.ENROLLMENT_REQUEST,
            arguments = listOf(navArgument("classOfferingId") { type = NavType.StringType })
        ) {
            EnrollmentRequestScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── Enrollment approval: guarded by ENROLLMENT_REVIEW ──
        composable(
            route = NavRoutes.ENROLLMENT_APPROVAL_TASK,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) {
            RequirePermission(
                requiredPermissions = arrayOf(Permission.ENROLLMENT_REVIEW),
                onNavigateBack = { navController.popBackStack() }
            ) {
                ApprovalTaskScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Phase 4: Assessments
        composable(NavRoutes.ASSESSMENTS) {
            AssessmentListScreen(
                onNavigateToAssessmentDetail = { },
                onNavigateToSubmission = { assessmentId -> navController.navigate(NavRoutes.takeAssessment(assessmentId)) },
                onNavigateToGradeItem = { queueItemId -> navController.navigate(NavRoutes.gradeItem(queueItemId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.TAKE_ASSESSMENT,
            arguments = listOf(navArgument("assessmentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val assessmentId = backStackEntry.arguments?.getString("assessmentId") ?: return@composable
            TakeAssessmentScreen(
                assessmentId = assessmentId,
                onComplete = { navController.popBackStack() },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.GRADE_ITEM,
            arguments = listOf(navArgument("queueItemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val queueItemId = backStackEntry.arguments?.getString("queueItemId") ?: return@composable
            RequirePermission(
                requiredPermissions = arrayOf(Permission.ASSESSMENT_GRADE),
                onNavigateBack = { navController.popBackStack() }
            ) {
                GradingScreen(
                    queueItemId = queueItemId,
                    onComplete = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = NavRoutes.SIMILARITY_REVIEW,
            arguments = listOf(navArgument("assessmentId") { type = NavType.StringType })
        ) { backStackEntry ->
            val assessmentId = backStackEntry.arguments?.getString("assessmentId") ?: return@composable
            SimilarityReviewScreen(
                assessmentId = assessmentId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Phase 3: Commerce
        composable(NavRoutes.CART) {
            CartScreen(
                onNavigateToOrderDetail = { orderId -> navController.navigate(NavRoutes.orderDetail(orderId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.ORDERS) {
            OrderListScreen(
                onNavigateToOrderDetail = { orderId -> navController.navigate(NavRoutes.orderDetail(orderId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.ORDER_DETAIL,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: return@composable
            OrderDetailScreen(
                orderId = orderId,
                onNavigateToRecordPayment = { oId -> navController.navigate(NavRoutes.recordPayment(oId)) },
                onNavigateToIssueRefund = { oId, pId -> navController.navigate(NavRoutes.issueRefund(oId, pId)) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = NavRoutes.RECORD_PAYMENT,
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: return@composable
            RequirePermission(
                requiredPermissions = arrayOf(Permission.PAYMENT_RECORD),
                onNavigateBack = { navController.popBackStack() }
            ) {
                RecordPaymentScreen(
                    orderId = orderId,
                    onComplete = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = NavRoutes.ISSUE_REFUND,
            arguments = listOf(
                navArgument("orderId") { type = NavType.StringType },
                navArgument("paymentId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId") ?: return@composable
            val paymentId = backStackEntry.arguments?.getString("paymentId") ?: return@composable
            RequirePermission(
                requiredPermissions = arrayOf(Permission.REFUND_ISSUE),
                onNavigateBack = { navController.popBackStack() }
            ) {
                RefundScreen(
                    orderId = orderId,
                    paymentId = paymentId,
                    onComplete = { navController.popBackStack() },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Phase 5: Operations (Admin + Finance only)
        val operationsPermissions = arrayOf(
            Permission.IMPORT_MANAGE, Permission.EXPORT_MANAGE,
            Permission.PAYMENT_RECONCILE, Permission.BACKUP_RUN, Permission.AUDIT_VIEW
        )

        composable(NavRoutes.OPERATIONS) {
            RequireOperationsAccess(
                requiredPermissions = operationsPermissions,
                onNavigateBack = { navController.popBackStack() }
            ) {
                OperationsScreen(
                    onNavigateToImport = { navController.navigate(NavRoutes.IMPORT_SETTLEMENT) },
                    onNavigateToReconciliation = { batchId -> navController.navigate(NavRoutes.reconciliation(batchId)) },
                    onNavigateToBackupDetail = { _ -> navController.navigate(NavRoutes.BACKUP_RESTORE) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(NavRoutes.IMPORT_SETTLEMENT) {
            RequireOperationsAccess(
                requiredPermissions = arrayOf(Permission.IMPORT_MANAGE),
                onNavigateBack = { navController.popBackStack() }
            ) {
                ImportScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(
            route = NavRoutes.RECONCILIATION,
            arguments = listOf(navArgument("batchId") { type = NavType.StringType })
        ) {
            RequireOperationsAccess(
                requiredPermissions = arrayOf(Permission.PAYMENT_RECONCILE),
                onNavigateBack = { navController.popBackStack() }
            ) {
                ReconciliationScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        composable(NavRoutes.BACKUP_RESTORE) {
            RequireOperationsAccess(
                requiredPermissions = arrayOf(Permission.BACKUP_RUN, Permission.RESTORE_RUN),
                onNavigateBack = { navController.popBackStack() }
            ) {
                BackupRestoreScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
