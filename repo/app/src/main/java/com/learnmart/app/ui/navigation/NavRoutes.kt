package com.learnmart.app.ui.navigation

object NavRoutes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"

    // Admin screens
    const val ADMIN_USERS = "admin/users"
    const val ADMIN_USER_DETAIL = "admin/users/{userId}"
    const val ADMIN_CREATE_USER = "admin/users/create"
    const val ADMIN_POLICIES = "admin/policies"
    const val ADMIN_POLICY_EDIT = "admin/policies/{policyId}"

    // Audit
    const val AUDIT_LOG = "audit/log"

    // Catalog (Phase 2)
    const val CATALOG = "catalog"
    const val CATALOG_CREATE_COURSE = "catalog/create"
    const val CATALOG_COURSE_DETAIL = "catalog/{courseId}"
    const val CATALOG_CREATE_CLASS = "catalog/{courseId}/create-class"

    // Enrollment (Phase 2)
    const val ENROLLMENTS = "enrollments"
    const val ENROLLMENT_REQUEST = "enrollments/request/{classOfferingId}"
    const val ENROLLMENT_APPROVAL_TASK = "enrollments/approval/{taskId}"

    // Assessment (Phase 4)
    const val ASSESSMENTS = "assessments"
    const val TAKE_ASSESSMENT = "assessments/take/{assessmentId}"
    const val GRADE_ITEM = "assessments/grade/{queueItemId}"
    const val SIMILARITY_REVIEW = "assessments/similarity/{assessmentId}"

    // Operations (Phase 5)
    const val OPERATIONS = "operations"
    const val IMPORT_SETTLEMENT = "operations/import"
    const val RECONCILIATION = "operations/reconcile/{batchId}"
    const val BACKUP_RESTORE = "operations/backup"

    // Commerce (Phase 3)
    const val CART = "commerce/cart"
    const val ORDERS = "commerce/orders"
    const val ORDER_DETAIL = "commerce/orders/{orderId}"
    const val RECORD_PAYMENT = "commerce/orders/{orderId}/pay"
    const val ISSUE_REFUND = "commerce/orders/{orderId}/refund/{paymentId}"

    fun userDetail(userId: String) = "admin/users/$userId"
    fun policyEdit(policyId: String) = "admin/policies/$policyId"
    fun courseDetail(courseId: String) = "catalog/$courseId"
    fun createClass(courseId: String) = "catalog/$courseId/create-class"
    fun enrollmentRequest(classOfferingId: String) = "enrollments/request/$classOfferingId"
    fun approvalTask(taskId: String) = "enrollments/approval/$taskId"
    fun orderDetail(orderId: String) = "commerce/orders/$orderId"
    fun recordPayment(orderId: String) = "commerce/orders/$orderId/pay"
    fun issueRefund(orderId: String, paymentId: String) = "commerce/orders/$orderId/refund/$paymentId"
    fun takeAssessment(assessmentId: String) = "assessments/take/$assessmentId"
    fun gradeItem(queueItemId: String) = "assessments/grade/$queueItemId"
    fun similarityReview(assessmentId: String) = "assessments/similarity/$assessmentId"
    fun reconciliation(batchId: String) = "operations/reconcile/$batchId"
}
