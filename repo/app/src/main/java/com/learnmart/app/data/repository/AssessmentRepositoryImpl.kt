package com.learnmart.app.data.repository

import com.learnmart.app.data.local.dao.AssessmentDao
import com.learnmart.app.data.local.entity.AssessmentReleaseWindowEntity
import com.learnmart.app.data.local.entity.AssignmentEntity
import com.learnmart.app.data.local.entity.GradeDecisionEntity
import com.learnmart.app.data.local.entity.KnowledgeTagEntity
import com.learnmart.app.data.local.entity.ObjectiveGradeResultEntity
import com.learnmart.app.data.local.entity.QuestionBankEntity
import com.learnmart.app.data.local.entity.QuestionChoiceEntity
import com.learnmart.app.data.local.entity.QuestionEntity
import com.learnmart.app.data.local.entity.SimilarityFingerprintEntity
import com.learnmart.app.data.local.entity.SimilarityMatchResultEntity
import com.learnmart.app.data.local.entity.SubjectiveGradeQueueItemEntity
import com.learnmart.app.data.local.entity.SubmissionAnswerEntity
import com.learnmart.app.data.local.entity.SubmissionEntity
import com.learnmart.app.data.local.entity.WrongAnswerExplanationLinkEntity
import com.learnmart.app.domain.model.AssessmentReleaseWindow
import com.learnmart.app.domain.model.AssessmentType
import com.learnmart.app.domain.model.Assignment
import com.learnmart.app.domain.model.DifficultyLevel
import com.learnmart.app.domain.model.GradeDecision
import com.learnmart.app.domain.model.GradeQueueStatus
import com.learnmart.app.domain.model.KnowledgeTag
import com.learnmart.app.domain.model.ObjectiveGradeResult
import com.learnmart.app.domain.model.Question
import com.learnmart.app.domain.model.QuestionBank
import com.learnmart.app.domain.model.QuestionChoice
import com.learnmart.app.domain.model.QuestionType
import com.learnmart.app.domain.model.RoleType
import com.learnmart.app.domain.model.SimilarityFingerprint
import com.learnmart.app.domain.model.SimilarityFlag
import com.learnmart.app.domain.model.SimilarityMatchResult
import com.learnmart.app.domain.model.Submission
import com.learnmart.app.domain.model.SubmissionAnswer
import com.learnmart.app.domain.model.SubmissionStatus
import com.learnmart.app.domain.model.SubjectiveGradeQueueItem
import com.learnmart.app.domain.model.WrongAnswerExplanationLink
import com.learnmart.app.domain.repository.AssessmentRepository
import com.learnmart.app.util.TimeUtils
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssessmentRepositoryImpl @Inject constructor(
    private val assessmentDao: AssessmentDao
) : AssessmentRepository {

    // ==================== QuestionBank ====================

    override suspend fun createQuestionBank(bank: QuestionBank): QuestionBank {
        val entity = bank.toEntity()
        assessmentDao.insertQuestionBank(entity)
        return entity.toDomain()
    }

    override suspend fun getQuestionBankById(id: String): QuestionBank? =
        assessmentDao.getQuestionBankById(id)?.toDomain()

    override suspend fun getQuestionBanksByClassOffering(classOfferingId: String): List<QuestionBank> =
        assessmentDao.getQuestionBanksByClassOfferingId(classOfferingId).map { it.toDomain() }

    // ==================== KnowledgeTag ====================

    override suspend fun createTag(tag: KnowledgeTag): KnowledgeTag {
        val entity = tag.toEntity()
        assessmentDao.insertKnowledgeTag(entity)
        return entity.toDomain()
    }

    override suspend fun getAllTags(): List<KnowledgeTag> =
        assessmentDao.getAllKnowledgeTags().map { it.toDomain() }

    // ==================== Question ====================

    override suspend fun createQuestion(question: Question): Question {
        val entity = question.toEntity()
        assessmentDao.insertQuestion(entity)
        return entity.toDomain()
    }

    override suspend fun updateQuestion(question: Question): Boolean {
        val existing = assessmentDao.getQuestionById(question.id) ?: return false
        val updated = existing.copy(
            questionType = question.questionType.name,
            difficulty = question.difficulty.name,
            questionText = question.questionText,
            explanation = question.explanation,
            points = question.points,
            tagIds = question.tagIds.toCommaSeparated(),
            updatedAt = TimeUtils.nowUtc().toEpochMilli(),
            version = existing.version + 1
        )
        assessmentDao.updateQuestion(updated)
        return true
    }

    override suspend fun getQuestionById(id: String): Question? =
        assessmentDao.getQuestionById(id)?.toDomain()

    override suspend fun getQuestionsByBankId(bankId: String): List<Question> =
        assessmentDao.getQuestionsByQuestionBankId(bankId).map { it.toDomain() }

    override suspend fun getQuestionsByIds(ids: List<String>): List<Question> =
        assessmentDao.getQuestionsByIds(ids).map { it.toDomain() }

    // ==================== QuestionChoice ====================

    override suspend fun createChoices(choices: List<QuestionChoice>) {
        assessmentDao.insertAllQuestionChoices(choices.map { it.toEntity() })
    }

    override suspend fun getChoicesForQuestion(questionId: String): List<QuestionChoice> =
        assessmentDao.getQuestionChoicesByQuestionId(questionId).map { it.toDomain() }

    // ==================== Assignment ====================

    override suspend fun createAssignment(assignment: Assignment): Assignment {
        val entity = assignment.toEntity()
        assessmentDao.insertAssignment(entity)
        return entity.toDomain()
    }

    override suspend fun updateAssignment(assignment: Assignment): Boolean {
        val existing = assessmentDao.getAssignmentById(assignment.id) ?: return false
        val updated = existing.copy(
            title = assignment.title,
            description = assignment.description,
            assessmentType = assignment.assessmentType.name,
            questionBankId = assignment.questionBankId,
            questionIds = assignment.questionIds.toCommaSeparated(),
            totalPoints = assignment.totalPoints,
            releaseStart = assignment.releaseStart.toEpochMilli(),
            releaseEnd = assignment.releaseEnd.toEpochMilli(),
            timeLimitMinutes = assignment.timeLimitMinutes,
            allowLateSubmission = assignment.allowLateSubmission,
            allowResubmission = assignment.allowResubmission,
            updatedAt = TimeUtils.nowUtc().toEpochMilli(),
            version = existing.version + 1
        )
        assessmentDao.updateAssignment(updated)
        return true
    }

    override suspend fun getAssignmentById(id: String): Assignment? =
        assessmentDao.getAssignmentById(id)?.toDomain()

    override suspend fun getAssignmentsByClassOffering(classOfferingId: String): List<Assignment> =
        assessmentDao.getAssignmentsByClassOfferingId(classOfferingId).map { it.toDomain() }

    override suspend fun getActiveAssignments(now: Long): List<Assignment> =
        assessmentDao.getActiveAssignments(now).map { it.toDomain() }

    // ==================== ReleaseWindow ====================

    override suspend fun createReleaseWindow(window: AssessmentReleaseWindow) {
        assessmentDao.insertAssessmentReleaseWindow(window.toEntity())
    }

    override suspend fun getReleaseWindowsForAssessment(assessmentId: String): List<AssessmentReleaseWindow> =
        assessmentDao.getAssessmentReleaseWindowsByAssessmentId(assessmentId).map { it.toDomain() }

    // ==================== Submission ====================

    override suspend fun createSubmission(submission: Submission): Submission {
        val entity = submission.toEntity()
        assessmentDao.insertSubmission(entity)
        return entity.toDomain()
    }

    override suspend fun updateSubmission(submission: Submission): Boolean {
        val existing = assessmentDao.getSubmissionById(submission.id) ?: return false
        val updated = existing.copy(
            status = submission.status.name,
            startedAt = submission.startedAt?.toEpochMilli(),
            submittedAt = submission.submittedAt?.toEpochMilli(),
            totalScore = submission.totalScore,
            maxScore = submission.maxScore,
            gradePercentage = submission.gradePercentage,
            gradedBy = submission.gradedBy,
            gradedAt = submission.gradedAt?.toEpochMilli(),
            finalizedAt = submission.finalizedAt?.toEpochMilli(),
            versionNumber = submission.versionNumber,
            updatedAt = TimeUtils.nowUtc().toEpochMilli(),
            version = existing.version + 1
        )
        assessmentDao.updateSubmission(updated)
        return true
    }

    override suspend fun getSubmissionById(id: String): Submission? =
        assessmentDao.getSubmissionById(id)?.toDomain()

    override suspend fun getSubmissionByAssessmentAndLearner(
        assessmentId: String,
        learnerId: String
    ): Submission? {
        val submissions = assessmentDao.getSubmissionsByAssessmentAndLearner(assessmentId, learnerId)
        return submissions.firstOrNull()?.toDomain()
    }

    override suspend fun getSubmissionsByAssessment(
        assessmentId: String,
        limit: Int,
        offset: Int
    ): List<Submission> =
        assessmentDao.getSubmissionsByAssessmentId(assessmentId, limit, offset).map { it.toDomain() }

    override suspend fun getSubmissionsByLearner(learnerId: String): List<Submission> =
        assessmentDao.getSubmissionsByLearnerId(learnerId).map { it.toDomain() }

    override suspend fun updateSubmissionStatus(
        id: String,
        status: SubmissionStatus,
        currentVersion: Int
    ): Boolean {
        val rows = assessmentDao.updateSubmissionStatus(
            id = id,
            status = status.name,
            updatedAt = TimeUtils.nowUtc().toEpochMilli(),
            currentVersion = currentVersion
        )
        return rows > 0
    }

    // ==================== SubmissionAnswer ====================

    override suspend fun createAnswers(answers: List<SubmissionAnswer>) {
        assessmentDao.insertAllSubmissionAnswers(answers.map { it.toEntity() })
    }

    override suspend fun getAnswersForSubmission(submissionId: String): List<SubmissionAnswer> =
        assessmentDao.getSubmissionAnswersBySubmissionId(submissionId).map { it.toDomain() }

    override suspend fun getAnswerBySubmissionAndQuestion(
        submissionId: String,
        questionId: String
    ): SubmissionAnswer? =
        assessmentDao.getSubmissionAnswerBySubmissionAndQuestion(submissionId, questionId)?.toDomain()

    // ==================== Grading ====================

    override suspend fun createObjectiveGradeResults(results: List<ObjectiveGradeResult>) {
        assessmentDao.insertAllObjectiveGradeResults(results.map { it.toEntity() })
    }

    override suspend fun getObjectiveGradeResult(submissionAnswerId: String): ObjectiveGradeResult? =
        assessmentDao.getObjectiveGradeResultsBySubmissionAnswerId(submissionAnswerId)
            .firstOrNull()?.toDomain()

    override suspend fun createGradeQueueItem(item: SubjectiveGradeQueueItem) {
        assessmentDao.insertSubjectiveGradeQueueItem(item.toEntity())
    }

    override suspend fun updateGradeQueueItem(item: SubjectiveGradeQueueItem) {
        assessmentDao.updateSubjectiveGradeQueueItem(item.toEntity())
    }

    override suspend fun getGradeQueueItemById(id: String): SubjectiveGradeQueueItem? =
        assessmentDao.getSubjectiveGradeQueueItemById(id)?.toDomain()

    override suspend fun getPendingQueueByClassOffering(classOfferingId: String): List<SubjectiveGradeQueueItem> =
        assessmentDao.getPendingSubjectiveGradesByClassOffering(classOfferingId).map { it.toDomain() }

    override suspend fun getQueueItemsForSubmission(submissionId: String): List<SubjectiveGradeQueueItem> =
        assessmentDao.getSubjectiveGradeQueueItemsBySubmission(submissionId).map { it.toDomain() }

    override suspend fun getPendingQueueByGrader(graderId: String): List<SubjectiveGradeQueueItem> =
        assessmentDao.getPendingSubjectiveGradesByGrader(graderId).map { it.toDomain() }

    override suspend fun getPendingQueueByRole(roleType: RoleType): List<SubjectiveGradeQueueItem> =
        assessmentDao.getPendingSubjectiveGradesByRole(roleType.name).map { it.toDomain() }

    override suspend fun countPendingQueue(): Int =
        assessmentDao.countPendingSubjectiveGrades()

    override suspend fun createGradeDecision(decision: GradeDecision) {
        assessmentDao.insertGradeDecision(decision.toEntity())
    }

    override suspend fun getGradeDecision(submissionAnswerId: String): GradeDecision? =
        assessmentDao.getGradeDecisionsBySubmissionAnswerId(submissionAnswerId)
            .firstOrNull()?.toDomain()

    // ==================== Explanations ====================

    override suspend fun createExplanationLink(link: WrongAnswerExplanationLink) {
        assessmentDao.insertWrongAnswerExplanationLink(link.toEntity())
    }

    override suspend fun getExplanationsForQuestion(questionId: String): List<WrongAnswerExplanationLink> =
        assessmentDao.getWrongAnswerExplanationsByQuestionId(questionId).map { it.toDomain() }

    // ==================== Similarity ====================

    override suspend fun createSimilarityFingerprint(fingerprint: SimilarityFingerprint) {
        assessmentDao.insertSimilarityFingerprint(fingerprint.toEntity())
    }

    override suspend fun getSimilarityFingerprintsByAssessment(assessmentId: String): List<SimilarityFingerprint> =
        assessmentDao.getSimilarityFingerprintsByAssessmentId(assessmentId).map { it.toDomain() }

    override suspend fun getSimilarityFingerprintForSubmission(submissionId: String): SimilarityFingerprint? =
        assessmentDao.getSimilarityFingerprintsBySubmissionId(submissionId).firstOrNull()?.toDomain()

    override suspend fun createSimilarityMatchResult(result: SimilarityMatchResult) {
        assessmentDao.insertSimilarityMatchResult(result.toEntity())
    }

    override suspend fun getSimilarityMatchesByAssessment(assessmentId: String): List<SimilarityMatchResult> =
        assessmentDao.getSimilarityMatchResultsByAssessmentId(assessmentId).map { it.toDomain() }

    override suspend fun getFlaggedMatchesByAssessment(assessmentId: String): List<SimilarityMatchResult> =
        assessmentDao.getFlaggedSimilarityMatchResultsByAssessmentId(assessmentId).map { it.toDomain() }

    // ==================== Entity <-> Domain Mapping ====================

    // --- List<String> <-> comma-separated String helpers ---

    private fun List<String>.toCommaSeparated(): String =
        joinToString(",")

    private fun String.fromCommaSeparated(): List<String> =
        if (isBlank()) emptyList() else split(",")

    // --- QuestionBank ---

    private fun QuestionBankEntity.toDomain() = QuestionBank(
        id = id,
        classOfferingId = classOfferingId,
        name = name,
        description = description,
        createdBy = createdBy,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    private fun QuestionBank.toEntity() = QuestionBankEntity(
        id = id,
        classOfferingId = classOfferingId,
        name = name,
        description = description,
        createdBy = createdBy,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )

    // --- KnowledgeTag ---

    private fun KnowledgeTagEntity.toDomain() = KnowledgeTag(
        id = id,
        name = name,
        description = description
    )

    private fun KnowledgeTag.toEntity() = KnowledgeTagEntity(
        id = id,
        name = name,
        description = description
    )

    // --- Question ---

    private fun QuestionEntity.toDomain() = Question(
        id = id,
        questionBankId = questionBankId,
        questionType = QuestionType.valueOf(questionType),
        difficulty = DifficultyLevel.valueOf(difficulty),
        questionText = questionText,
        explanation = explanation,
        points = points,
        tagIds = tagIds.fromCommaSeparated(),
        createdBy = createdBy,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        version = version
    )

    private fun Question.toEntity() = QuestionEntity(
        id = id,
        questionBankId = questionBankId,
        questionType = questionType.name,
        difficulty = difficulty.name,
        questionText = questionText,
        explanation = explanation,
        points = points,
        tagIds = tagIds.toCommaSeparated(),
        createdBy = createdBy,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        version = version
    )

    // --- QuestionChoice ---

    private fun QuestionChoiceEntity.toDomain() = QuestionChoice(
        id = id,
        questionId = questionId,
        choiceText = choiceText,
        isCorrect = isCorrect,
        displayOrder = displayOrder
    )

    private fun QuestionChoice.toEntity() = QuestionChoiceEntity(
        id = id,
        questionId = questionId,
        choiceText = choiceText,
        isCorrect = isCorrect,
        displayOrder = displayOrder
    )

    // --- Assignment ---

    private fun AssignmentEntity.toDomain() = Assignment(
        id = id,
        classOfferingId = classOfferingId,
        title = title,
        description = description,
        assessmentType = AssessmentType.valueOf(assessmentType),
        questionBankId = questionBankId,
        questionIds = questionIds.fromCommaSeparated(),
        totalPoints = totalPoints,
        releaseStart = Instant.ofEpochMilli(releaseStart),
        releaseEnd = Instant.ofEpochMilli(releaseEnd),
        timeLimitMinutes = timeLimitMinutes,
        allowLateSubmission = allowLateSubmission,
        allowResubmission = allowResubmission,
        createdBy = createdBy,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        version = version
    )

    private fun Assignment.toEntity() = AssignmentEntity(
        id = id,
        classOfferingId = classOfferingId,
        title = title,
        description = description,
        assessmentType = assessmentType.name,
        questionBankId = questionBankId,
        questionIds = questionIds.toCommaSeparated(),
        totalPoints = totalPoints,
        releaseStart = releaseStart.toEpochMilli(),
        releaseEnd = releaseEnd.toEpochMilli(),
        timeLimitMinutes = timeLimitMinutes,
        allowLateSubmission = allowLateSubmission,
        allowResubmission = allowResubmission,
        createdBy = createdBy,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        version = version
    )

    // --- AssessmentReleaseWindow ---

    private fun AssessmentReleaseWindowEntity.toDomain() = AssessmentReleaseWindow(
        id = id,
        assessmentId = assessmentId,
        releaseStart = Instant.ofEpochMilli(releaseStart),
        releaseEnd = Instant.ofEpochMilli(releaseEnd),
        isActive = isActive
    )

    private fun AssessmentReleaseWindow.toEntity() = AssessmentReleaseWindowEntity(
        id = id,
        assessmentId = assessmentId,
        releaseStart = releaseStart.toEpochMilli(),
        releaseEnd = releaseEnd.toEpochMilli(),
        isActive = isActive
    )

    // --- Submission ---

    private fun SubmissionEntity.toDomain() = Submission(
        id = id,
        assessmentId = assessmentId,
        learnerId = learnerId,
        status = SubmissionStatus.valueOf(status),
        startedAt = startedAt?.let { Instant.ofEpochMilli(it) },
        submittedAt = submittedAt?.let { Instant.ofEpochMilli(it) },
        totalScore = totalScore,
        maxScore = maxScore,
        gradePercentage = gradePercentage,
        gradedBy = gradedBy,
        gradedAt = gradedAt?.let { Instant.ofEpochMilli(it) },
        finalizedAt = finalizedAt?.let { Instant.ofEpochMilli(it) },
        versionNumber = versionNumber,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        version = version
    )

    private fun Submission.toEntity() = SubmissionEntity(
        id = id,
        assessmentId = assessmentId,
        learnerId = learnerId,
        status = status.name,
        startedAt = startedAt?.toEpochMilli(),
        submittedAt = submittedAt?.toEpochMilli(),
        totalScore = totalScore,
        maxScore = maxScore,
        gradePercentage = gradePercentage,
        gradedBy = gradedBy,
        gradedAt = gradedAt?.toEpochMilli(),
        finalizedAt = finalizedAt?.toEpochMilli(),
        versionNumber = versionNumber,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        version = version
    )

    // --- SubmissionAnswer ---

    private fun SubmissionAnswerEntity.toDomain() = SubmissionAnswer(
        id = id,
        submissionId = submissionId,
        questionId = questionId,
        answerText = answerText,
        selectedChoiceIds = selectedChoiceIds.fromCommaSeparated(),
        score = score,
        maxScore = maxScore,
        isAutoGraded = isAutoGraded,
        feedback = feedback,
        submittedAt = Instant.ofEpochMilli(submittedAt)
    )

    private fun SubmissionAnswer.toEntity() = SubmissionAnswerEntity(
        id = id,
        submissionId = submissionId,
        questionId = questionId,
        answerText = answerText,
        selectedChoiceIds = selectedChoiceIds.toCommaSeparated(),
        score = score,
        maxScore = maxScore,
        isAutoGraded = isAutoGraded,
        feedback = feedback,
        submittedAt = submittedAt.toEpochMilli()
    )

    // --- ObjectiveGradeResult ---

    private fun ObjectiveGradeResultEntity.toDomain() = ObjectiveGradeResult(
        id = id,
        submissionAnswerId = submissionAnswerId,
        questionId = questionId,
        isCorrect = isCorrect,
        score = score,
        maxScore = maxScore,
        correctChoiceIds = correctChoiceIds.fromCommaSeparated(),
        selectedChoiceIds = selectedChoiceIds.fromCommaSeparated(),
        gradedAt = Instant.ofEpochMilli(gradedAt)
    )

    private fun ObjectiveGradeResult.toEntity() = ObjectiveGradeResultEntity(
        id = id,
        submissionAnswerId = submissionAnswerId,
        questionId = questionId,
        isCorrect = isCorrect,
        score = score,
        maxScore = maxScore,
        correctChoiceIds = correctChoiceIds.toCommaSeparated(),
        selectedChoiceIds = selectedChoiceIds.toCommaSeparated(),
        gradedAt = gradedAt.toEpochMilli()
    )

    // --- SubjectiveGradeQueueItem ---

    private fun SubjectiveGradeQueueItemEntity.toDomain() = SubjectiveGradeQueueItem(
        id = id,
        submissionId = submissionId,
        submissionAnswerId = submissionAnswerId,
        questionId = questionId,
        assessmentId = assessmentId,
        classOfferingId = classOfferingId,
        assignedGraderId = assignedGraderId,
        assignedRoleType = assignedRoleType?.let { RoleType.valueOf(it) },
        status = GradeQueueStatus.valueOf(status),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt)
    )

    private fun SubjectiveGradeQueueItem.toEntity() = SubjectiveGradeQueueItemEntity(
        id = id,
        submissionId = submissionId,
        submissionAnswerId = submissionAnswerId,
        questionId = questionId,
        assessmentId = assessmentId,
        classOfferingId = classOfferingId,
        assignedGraderId = assignedGraderId,
        assignedRoleType = assignedRoleType?.name,
        status = status.name,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli()
    )

    // --- GradeDecision ---

    private fun GradeDecisionEntity.toDomain() = GradeDecision(
        id = id,
        submissionAnswerId = submissionAnswerId,
        gradedBy = gradedBy,
        score = score,
        maxScore = maxScore,
        feedback = feedback,
        gradedAt = Instant.ofEpochMilli(gradedAt)
    )

    private fun GradeDecision.toEntity() = GradeDecisionEntity(
        id = id,
        submissionAnswerId = submissionAnswerId,
        gradedBy = gradedBy,
        score = score,
        maxScore = maxScore,
        feedback = feedback,
        gradedAt = gradedAt.toEpochMilli()
    )

    // --- WrongAnswerExplanationLink ---

    private fun WrongAnswerExplanationLinkEntity.toDomain() = WrongAnswerExplanationLink(
        id = id,
        questionId = questionId,
        explanationText = explanationText,
        referenceUrl = referenceUrl
    )

    private fun WrongAnswerExplanationLink.toEntity() = WrongAnswerExplanationLinkEntity(
        id = id,
        questionId = questionId,
        explanationText = explanationText,
        referenceUrl = referenceUrl
    )

    // --- SimilarityFingerprint ---

    private fun SimilarityFingerprintEntity.toDomain() = SimilarityFingerprint(
        id = id,
        submissionId = submissionId,
        assessmentId = assessmentId,
        fingerprintData = fingerprintData,
        generatedAt = Instant.ofEpochMilli(generatedAt)
    )

    private fun SimilarityFingerprint.toEntity() = SimilarityFingerprintEntity(
        id = id,
        submissionId = submissionId,
        assessmentId = assessmentId,
        fingerprintData = fingerprintData,
        generatedAt = generatedAt.toEpochMilli()
    )

    // --- SimilarityMatchResult ---

    private fun SimilarityMatchResultEntity.toDomain() = SimilarityMatchResult(
        id = id,
        submissionId1 = submissionId1,
        submissionId2 = submissionId2,
        assessmentId = assessmentId,
        similarityScore = similarityScore,
        flag = SimilarityFlag.valueOf(flag),
        reviewedBy = reviewedBy,
        reviewedAt = reviewedAt?.let { Instant.ofEpochMilli(it) },
        reviewNotes = reviewNotes,
        detectedAt = Instant.ofEpochMilli(detectedAt)
    )

    private fun SimilarityMatchResult.toEntity() = SimilarityMatchResultEntity(
        id = id,
        submissionId1 = submissionId1,
        submissionId2 = submissionId2,
        assessmentId = assessmentId,
        similarityScore = similarityScore,
        flag = flag.name,
        reviewedBy = reviewedBy,
        reviewedAt = reviewedAt?.toEpochMilli(),
        reviewNotes = reviewNotes,
        detectedAt = detectedAt.toEpochMilli()
    )
}
