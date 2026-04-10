package com.learnmart.app.domain.usecase.assessment

import com.learnmart.app.domain.model.*
import com.learnmart.app.domain.repository.AssessmentRepository
import com.learnmart.app.domain.repository.PolicyRepository
import com.learnmart.app.util.IdGenerator
import com.learnmart.app.util.TimeUtils
import javax.inject.Inject

/**
 * Local similarity/plagiarism detection using n-gram fingerprinting.
 * Generates fingerprints for submissions and compares them pairwise.
 * Thresholds: >= 0.85 HIGH_SIMILARITY, 0.70-0.8499 REVIEW_NEEDED, < 0.70 CLEAR
 */
class SimilarityEngine @Inject constructor(
    private val assessmentRepository: AssessmentRepository,
    private val policyRepository: PolicyRepository
) {
    companion object {
        private const val NGRAM_SIZE = 5
        private const val SHINGLE_SIZE = 3
    }

    /**
     * Generate fingerprint for a finalized submission.
     */
    suspend fun generateFingerprint(submissionId: String, assessmentId: String): SimilarityFingerprint {
        val answers = assessmentRepository.getAnswersForSubmission(submissionId)

        // Concatenate all text answers
        val fullText = answers
            .filter { !it.answerText.isNullOrBlank() }
            .sortedBy { it.questionId }
            .joinToString(" ") { it.answerText!! }

        // Generate n-gram hashes
        val ngrams = generateNgrams(fullText, NGRAM_SIZE)
        val hashes = ngrams.map { it.hashCode() }.sorted()

        // Use MinHash-style fingerprint: keep signature of hash values
        val signature = if (hashes.size > 100) {
            // Sample evenly spaced hashes for compact fingerprint
            val step = hashes.size / 100
            hashes.filterIndexed { index, _ -> index % step == 0 }.take(100)
        } else {
            hashes
        }

        val fingerprintData = signature.joinToString(",")

        val fingerprint = SimilarityFingerprint(
            id = IdGenerator.newId(),
            submissionId = submissionId,
            assessmentId = assessmentId,
            fingerprintData = fingerprintData,
            generatedAt = TimeUtils.nowUtc()
        )

        assessmentRepository.createSimilarityFingerprint(fingerprint)
        return fingerprint
    }

    /**
     * Compare a submission's fingerprint against all other submissions for the same assessment.
     */
    suspend fun compareAgainstPeers(
        submissionId: String,
        assessmentId: String
    ): List<SimilarityMatchResult> {
        val targetFp = assessmentRepository.getSimilarityFingerprintForSubmission(submissionId)
            ?: return emptyList()

        val allFps = assessmentRepository.getSimilarityFingerprintsByAssessment(assessmentId)
            .filter { it.submissionId != submissionId }

        val highThreshold = policyRepository.getPolicyValue(
            PolicyType.RISK, "plagiarism_high_threshold", PolicyDefaults.PLAGIARISM_HIGH_THRESHOLD
        ).toDouble()
        val reviewThreshold = policyRepository.getPolicyValue(
            PolicyType.RISK, "plagiarism_review_threshold", PolicyDefaults.PLAGIARISM_REVIEW_THRESHOLD
        ).toDouble()

        val targetHashes = parseFingerprint(targetFp.fingerprintData)
        val results = mutableListOf<SimilarityMatchResult>()
        val now = TimeUtils.nowUtc()

        for (peerFp in allFps) {
            val peerHashes = parseFingerprint(peerFp.fingerprintData)
            val score = calculateJaccardSimilarity(targetHashes, peerHashes)

            val flag = when {
                score >= highThreshold -> SimilarityFlag.HIGH_SIMILARITY
                score >= reviewThreshold -> SimilarityFlag.REVIEW_NEEDED
                else -> SimilarityFlag.CLEAR
            }

            val result = SimilarityMatchResult(
                id = IdGenerator.newId(),
                submissionId1 = submissionId,
                submissionId2 = peerFp.submissionId,
                assessmentId = assessmentId,
                similarityScore = score,
                flag = flag,
                reviewedBy = null,
                reviewedAt = null,
                reviewNotes = null,
                detectedAt = now
            )

            assessmentRepository.createSimilarityMatchResult(result)
            results.add(result)
        }

        return results
    }

    private fun generateNgrams(text: String, n: Int): List<String> {
        val normalized = text.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim()
        val words = normalized.split(Regex("\\s+"))
        if (words.size < n) return listOf(normalized)

        return (0..words.size - n).map { i ->
            words.subList(i, i + n).joinToString(" ")
        }
    }

    private fun parseFingerprint(data: String): Set<Int> {
        if (data.isBlank()) return emptySet()
        return data.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    private fun calculateJaccardSimilarity(set1: Set<Int>, set2: Set<Int>): Double {
        if (set1.isEmpty() && set2.isEmpty()) return 0.0
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size
        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }
}
