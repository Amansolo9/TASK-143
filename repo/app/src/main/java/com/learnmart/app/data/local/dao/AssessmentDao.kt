package com.learnmart.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

@Dao
interface AssessmentDao {

    // --- QuestionBank ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuestionBank(questionBank: QuestionBankEntity)

    @Query("SELECT * FROM question_banks WHERE id = :id")
    suspend fun getQuestionBankById(id: String): QuestionBankEntity?

    @Query("SELECT * FROM question_banks WHERE class_offering_id = :classOfferingId ORDER BY name ASC")
    suspend fun getQuestionBanksByClassOfferingId(classOfferingId: String): List<QuestionBankEntity>

    // --- KnowledgeTag ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertKnowledgeTag(tag: KnowledgeTagEntity)

    @Query("SELECT * FROM knowledge_tags ORDER BY name ASC")
    suspend fun getAllKnowledgeTags(): List<KnowledgeTagEntity>

    @Query("SELECT * FROM knowledge_tags WHERE id = :id")
    suspend fun getKnowledgeTagById(id: String): KnowledgeTagEntity?

    // --- Question ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuestion(question: QuestionEntity)

    @Update
    suspend fun updateQuestion(question: QuestionEntity)

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestionById(id: String): QuestionEntity?

    @Query("SELECT * FROM questions WHERE question_bank_id = :questionBankId ORDER BY created_at ASC")
    suspend fun getQuestionsByQuestionBankId(questionBankId: String): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getQuestionsByIds(ids: List<String>): List<QuestionEntity>

    // --- QuestionChoice ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQuestionChoice(choice: QuestionChoiceEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllQuestionChoices(choices: List<QuestionChoiceEntity>)

    @Query("SELECT * FROM question_choices WHERE question_id = :questionId ORDER BY display_order ASC")
    suspend fun getQuestionChoicesByQuestionId(questionId: String): List<QuestionChoiceEntity>

    // --- Assignment ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAssignment(assignment: AssignmentEntity)

    @Update
    suspend fun updateAssignment(assignment: AssignmentEntity)

    @Query("SELECT * FROM assignments WHERE id = :id")
    suspend fun getAssignmentById(id: String): AssignmentEntity?

    @Query("SELECT * FROM assignments WHERE class_offering_id = :classOfferingId ORDER BY release_start ASC")
    suspend fun getAssignmentsByClassOfferingId(classOfferingId: String): List<AssignmentEntity>

    @Query("""
        SELECT * FROM assignments
        WHERE release_start <= :now AND release_end >= :now
        ORDER BY release_end ASC
    """)
    suspend fun getActiveAssignments(now: Long): List<AssignmentEntity>

    // --- AssessmentReleaseWindow ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAssessmentReleaseWindow(window: AssessmentReleaseWindowEntity)

    @Query("SELECT * FROM assessment_release_windows WHERE assessment_id = :assessmentId ORDER BY release_start ASC")
    suspend fun getAssessmentReleaseWindowsByAssessmentId(assessmentId: String): List<AssessmentReleaseWindowEntity>

    // --- Submission ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSubmission(submission: SubmissionEntity)

    @Update
    suspend fun updateSubmission(submission: SubmissionEntity)

    @Query("SELECT * FROM submissions WHERE id = :id")
    suspend fun getSubmissionById(id: String): SubmissionEntity?

    @Query("""
        SELECT * FROM submissions
        WHERE assessment_id = :assessmentId AND learner_id = :learnerId
        ORDER BY created_at DESC
    """)
    suspend fun getSubmissionsByAssessmentAndLearner(assessmentId: String, learnerId: String): List<SubmissionEntity>

    @Query("""
        SELECT * FROM submissions
        WHERE assessment_id = :assessmentId
        ORDER BY submitted_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getSubmissionsByAssessmentId(assessmentId: String, limit: Int, offset: Int): List<SubmissionEntity>

    @Query("SELECT * FROM submissions WHERE learner_id = :learnerId ORDER BY created_at DESC")
    suspend fun getSubmissionsByLearnerId(learnerId: String): List<SubmissionEntity>

    @Query("""
        UPDATE submissions
        SET status = :status, updated_at = :updatedAt, version = version + 1
        WHERE id = :id AND version = :currentVersion
    """)
    suspend fun updateSubmissionStatus(id: String, status: String, updatedAt: Long, currentVersion: Int): Int

    // --- SubmissionAnswer ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSubmissionAnswer(answer: SubmissionAnswerEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllSubmissionAnswers(answers: List<SubmissionAnswerEntity>)

    @Query("SELECT * FROM submission_answers WHERE submission_id = :submissionId ORDER BY submitted_at ASC")
    suspend fun getSubmissionAnswersBySubmissionId(submissionId: String): List<SubmissionAnswerEntity>

    @Query("""
        SELECT * FROM submission_answers
        WHERE submission_id = :submissionId AND question_id = :questionId
    """)
    suspend fun getSubmissionAnswerBySubmissionAndQuestion(submissionId: String, questionId: String): SubmissionAnswerEntity?

    // --- ObjectiveGradeResult ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertObjectiveGradeResult(result: ObjectiveGradeResultEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllObjectiveGradeResults(results: List<ObjectiveGradeResultEntity>)

    @Query("SELECT * FROM objective_grade_results WHERE submission_answer_id = :submissionAnswerId")
    suspend fun getObjectiveGradeResultsBySubmissionAnswerId(submissionAnswerId: String): List<ObjectiveGradeResultEntity>

    // --- SubjectiveGradeQueueItem ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSubjectiveGradeQueueItem(item: SubjectiveGradeQueueItemEntity)

    @Update
    suspend fun updateSubjectiveGradeQueueItem(item: SubjectiveGradeQueueItemEntity)

    @Query("SELECT * FROM subjective_grade_queue WHERE id = :id")
    suspend fun getSubjectiveGradeQueueItemById(id: String): SubjectiveGradeQueueItemEntity?

    @Query("""
        SELECT * FROM subjective_grade_queue
        WHERE class_offering_id = :classOfferingId AND status = 'PENDING'
        ORDER BY created_at ASC
    """)
    suspend fun getPendingSubjectiveGradesByClassOffering(classOfferingId: String): List<SubjectiveGradeQueueItemEntity>

    @Query("""
        SELECT * FROM subjective_grade_queue
        WHERE assigned_grader_id = :graderId AND status = 'PENDING'
        ORDER BY created_at ASC
    """)
    suspend fun getPendingSubjectiveGradesByGrader(graderId: String): List<SubjectiveGradeQueueItemEntity>

    @Query("""
        SELECT * FROM subjective_grade_queue
        WHERE assigned_role_type = :roleType AND status = 'PENDING'
        ORDER BY created_at ASC
    """)
    suspend fun getPendingSubjectiveGradesByRole(roleType: String): List<SubjectiveGradeQueueItemEntity>

    @Query("SELECT COUNT(*) FROM subjective_grade_queue WHERE status = 'PENDING'")
    suspend fun countPendingSubjectiveGrades(): Int

    @Query("SELECT * FROM subjective_grade_queue WHERE submission_id = :submissionId ORDER BY created_at ASC")
    suspend fun getSubjectiveGradeQueueItemsBySubmission(submissionId: String): List<SubjectiveGradeQueueItemEntity>

    // --- GradeDecision ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGradeDecision(decision: GradeDecisionEntity)

    @Query("SELECT * FROM grade_decisions WHERE submission_answer_id = :submissionAnswerId ORDER BY graded_at DESC")
    suspend fun getGradeDecisionsBySubmissionAnswerId(submissionAnswerId: String): List<GradeDecisionEntity>

    // --- WrongAnswerExplanationLink ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWrongAnswerExplanationLink(link: WrongAnswerExplanationLinkEntity)

    @Query("SELECT * FROM wrong_answer_explanations WHERE question_id = :questionId")
    suspend fun getWrongAnswerExplanationsByQuestionId(questionId: String): List<WrongAnswerExplanationLinkEntity>

    // --- SimilarityFingerprint ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSimilarityFingerprint(fingerprint: SimilarityFingerprintEntity)

    @Query("SELECT * FROM similarity_fingerprints WHERE submission_id = :submissionId")
    suspend fun getSimilarityFingerprintsBySubmissionId(submissionId: String): List<SimilarityFingerprintEntity>

    @Query("SELECT * FROM similarity_fingerprints WHERE assessment_id = :assessmentId ORDER BY generated_at DESC")
    suspend fun getSimilarityFingerprintsByAssessmentId(assessmentId: String): List<SimilarityFingerprintEntity>

    // --- SimilarityMatchResult ---

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSimilarityMatchResult(result: SimilarityMatchResultEntity)

    @Query("SELECT * FROM similarity_match_results WHERE assessment_id = :assessmentId ORDER BY similarity_score DESC")
    suspend fun getSimilarityMatchResultsByAssessmentId(assessmentId: String): List<SimilarityMatchResultEntity>

    @Query("""
        SELECT * FROM similarity_match_results
        WHERE submission_id_1 = :submissionId OR submission_id_2 = :submissionId
        ORDER BY similarity_score DESC
    """)
    suspend fun getSimilarityMatchResultsBySubmissionId(submissionId: String): List<SimilarityMatchResultEntity>

    @Query("""
        SELECT * FROM similarity_match_results
        WHERE assessment_id = :assessmentId AND flag != 'CLEAR'
        ORDER BY similarity_score DESC
    """)
    suspend fun getFlaggedSimilarityMatchResultsByAssessmentId(assessmentId: String): List<SimilarityMatchResultEntity>
}
