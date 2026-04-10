package com.learnmart.app.domain.repository

import com.learnmart.app.domain.model.*
import java.time.Instant

interface AssessmentRepository {
    // QuestionBank
    suspend fun createQuestionBank(bank: QuestionBank): QuestionBank
    suspend fun getQuestionBankById(id: String): QuestionBank?
    suspend fun getQuestionBanksByClassOffering(classOfferingId: String): List<QuestionBank>

    // KnowledgeTag
    suspend fun createTag(tag: KnowledgeTag): KnowledgeTag
    suspend fun getAllTags(): List<KnowledgeTag>

    // Question
    suspend fun createQuestion(question: Question): Question
    suspend fun updateQuestion(question: Question): Boolean
    suspend fun getQuestionById(id: String): Question?
    suspend fun getQuestionsByBankId(bankId: String): List<Question>
    suspend fun getQuestionsByIds(ids: List<String>): List<Question>

    // QuestionChoice
    suspend fun createChoices(choices: List<QuestionChoice>)
    suspend fun getChoicesForQuestion(questionId: String): List<QuestionChoice>

    // Assignment
    suspend fun createAssignment(assignment: Assignment): Assignment
    suspend fun updateAssignment(assignment: Assignment): Boolean
    suspend fun getAssignmentById(id: String): Assignment?
    suspend fun getAssignmentsByClassOffering(classOfferingId: String): List<Assignment>
    suspend fun getActiveAssignments(now: Long): List<Assignment>

    // ReleaseWindow
    suspend fun createReleaseWindow(window: AssessmentReleaseWindow)
    suspend fun getReleaseWindowsForAssessment(assessmentId: String): List<AssessmentReleaseWindow>

    // Submission
    suspend fun createSubmission(submission: Submission): Submission
    suspend fun updateSubmission(submission: Submission): Boolean
    suspend fun getSubmissionById(id: String): Submission?
    suspend fun getSubmissionByAssessmentAndLearner(assessmentId: String, learnerId: String): Submission?
    suspend fun getSubmissionsByAssessment(assessmentId: String, limit: Int, offset: Int): List<Submission>
    suspend fun getSubmissionsByLearner(learnerId: String): List<Submission>
    suspend fun updateSubmissionStatus(id: String, status: SubmissionStatus, currentVersion: Int): Boolean

    // SubmissionAnswer
    suspend fun createAnswers(answers: List<SubmissionAnswer>)
    suspend fun getAnswersForSubmission(submissionId: String): List<SubmissionAnswer>
    suspend fun getAnswerBySubmissionAndQuestion(submissionId: String, questionId: String): SubmissionAnswer?

    // Grading
    suspend fun createObjectiveGradeResults(results: List<ObjectiveGradeResult>)
    suspend fun getObjectiveGradeResult(submissionAnswerId: String): ObjectiveGradeResult?
    suspend fun createGradeQueueItem(item: SubjectiveGradeQueueItem)
    suspend fun updateGradeQueueItem(item: SubjectiveGradeQueueItem)
    suspend fun getGradeQueueItemById(id: String): SubjectiveGradeQueueItem?
    suspend fun getPendingQueueByClassOffering(classOfferingId: String): List<SubjectiveGradeQueueItem>
    suspend fun getPendingQueueByGrader(graderId: String): List<SubjectiveGradeQueueItem>
    suspend fun getPendingQueueByRole(roleType: RoleType): List<SubjectiveGradeQueueItem>
    suspend fun getQueueItemsForSubmission(submissionId: String): List<SubjectiveGradeQueueItem>
    suspend fun countPendingQueue(): Int
    suspend fun createGradeDecision(decision: GradeDecision)
    suspend fun getGradeDecision(submissionAnswerId: String): GradeDecision?

    // Explanations
    suspend fun createExplanationLink(link: WrongAnswerExplanationLink)
    suspend fun getExplanationsForQuestion(questionId: String): List<WrongAnswerExplanationLink>

    // Similarity
    suspend fun createSimilarityFingerprint(fingerprint: SimilarityFingerprint)
    suspend fun getSimilarityFingerprintsByAssessment(assessmentId: String): List<SimilarityFingerprint>
    suspend fun getSimilarityFingerprintForSubmission(submissionId: String): SimilarityFingerprint?
    suspend fun createSimilarityMatchResult(result: SimilarityMatchResult)
    suspend fun getSimilarityMatchesByAssessment(assessmentId: String): List<SimilarityMatchResult>
    suspend fun getFlaggedMatchesByAssessment(assessmentId: String): List<SimilarityMatchResult>
}
