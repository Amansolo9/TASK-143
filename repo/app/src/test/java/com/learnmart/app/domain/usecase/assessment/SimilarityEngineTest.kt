package com.learnmart.app.domain.usecase.assessment

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AssessmentRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SimilarityEngineTest {
    private lateinit var assessmentRepository: AssessmentRepository
    private lateinit var policyRepository: PolicyRepository
    private lateinit var engine: SimilarityEngine

    @Before
    fun setUp() {
        assessmentRepository = mockk(relaxed = true)
        policyRepository = mockk(relaxed = true)
        coEvery { policyRepository.getPolicyValue(any(), any(), any()) } returns "0.85"
        engine = SimilarityEngine(assessmentRepository, policyRepository)
    }

    @Test
    fun `generateFingerprint with text answers creates fingerprint`() = runTest {
        coEvery { assessmentRepository.getAnswersForSubmission("sub-1") } returns listOf(
            SubmissionAnswer("a1", "sub-1", "q1", "The quick brown fox jumps over the lazy dog", emptyList(), null, 10, false, null, Instant.now())
        )
        coEvery { assessmentRepository.createSimilarityFingerprint(any()) } just runs
        val fp = engine.generateFingerprint("sub-1", "assess-1")
        assertThat(fp.submissionId).isEqualTo("sub-1")
        assertThat(fp.assessmentId).isEqualTo("assess-1")
        assertThat(fp.fingerprintData).isNotEmpty()
    }

    @Test
    fun `generateFingerprint with empty answers produces empty fingerprint`() = runTest {
        coEvery { assessmentRepository.getAnswersForSubmission("sub-1") } returns emptyList()
        coEvery { assessmentRepository.createSimilarityFingerprint(any()) } just runs
        val fp = engine.generateFingerprint("sub-1", "assess-1")
        assertThat(fp.fingerprintData).isNotNull()
    }

    @Test
    fun `compareAgainstPeers with no peers produces no matches`() = runTest {
        coEvery { assessmentRepository.getSimilarityFingerprintsByAssessment("assess-1") } returns emptyList()
        coEvery { assessmentRepository.getSimilarityFingerprintForSubmission("sub-1") } returns SimilarityFingerprint(
            "fp-1", "sub-1", "assess-1", """{"ngrams":[]}""", Instant.now()
        )
        val matches = engine.compareAgainstPeers("sub-1", "assess-1")
        assertThat(matches).isEmpty()
    }
}
