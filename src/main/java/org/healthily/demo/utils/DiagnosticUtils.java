package org.healthily.demo.utils;

import lombok.extern.slf4j.Slf4j;
import org.healthily.demo.model.DiagnosticDecision;
import org.healthily.demo.model.DiagnosticStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DiagnosticUtils {

    // I chose a confidence threshold to satisfy the part of the assessment that asked:
    // "Returns null if no further questions are required."
    private static final BigDecimal CONFIDENCE_THRESHOLD = BigDecimal.valueOf(0.90);

    // Maximum additional questions to ask as per the assessment
    private static final int MAX_QUESTIONS = 3;

    // Since the assessment specifically asked that we should ask at least one question after the initial symptoms,
    // I assumed we have to even if the confidence threshold is passed. This is used to control that behaviour.
    private static final int MIN_QUESTIONS = 1;

    public static Map<String, BigDecimal> calculateInitialProbabilities(
            List<Map<String, AttributeValue>> conditions,
            List<Map<String, AttributeValue>> initialSymptoms) {

        Map<String, BigDecimal> probabilities = new HashMap<>();
        for (Map<String, AttributeValue> condition : conditions) {
            String conditionId = condition.get("id").s().replace("CONDITION#", "");
            probabilities.put(conditionId, new BigDecimal(condition.get("prevalence").n()));
        }

        for (Map<String, AttributeValue> symptom : initialSymptoms) {
            probabilities = updateProbabilities(probabilities, symptom.get("conditions").m(), true);
        }

        return probabilities;
    }

    public static Map<String, BigDecimal> updateProbabilities(
            Map<String, BigDecimal> priorProbabilities,
            Map<String, AttributeValue> symptomConditions,
            boolean hasSymptom) {

        Map<String, BigDecimal> posteriorProbabilities = new HashMap<>();
        BigDecimal totalProbability = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> entry : priorProbabilities.entrySet()) {
            String condition = entry.getKey();
            BigDecimal priorProbability = entry.getValue();
            BigDecimal symptomLikelihood = new BigDecimal(symptomConditions.get(condition).n());

            if (!hasSymptom) {
                symptomLikelihood = BigDecimal.ONE.subtract(symptomLikelihood);
            }

            BigDecimal posterior = symptomLikelihood.multiply(priorProbability);
            posteriorProbabilities.put(condition, posterior);
            totalProbability = totalProbability.add(posterior);
        }

        if (totalProbability.compareTo(BigDecimal.ZERO) > 0) {
            for (Map.Entry<String, BigDecimal> condition : posteriorProbabilities.entrySet()) {
                posteriorProbabilities.put(condition.getKey(),
                        posteriorProbabilities.get(condition.getKey()).divide(totalProbability, 5, RoundingMode.UP));
            }
        }

        return posteriorProbabilities;
    }

    public static DiagnosticDecision evaluateNextStep(
            Map<String, BigDecimal> probabilities,
            List<String> allSymptoms,
            List<String> askedSymptoms,
            List<Map<String, AttributeValue>> availableSymptoms) {

        String mostLikelyCondition = findMostLikelyCondition(probabilities);
        BigDecimal highestProbability = probabilities.get(mostLikelyCondition);

        if (shouldMakeDiagnosis(highestProbability, askedSymptoms)) {
            return new DiagnosticDecision(
                    DiagnosticStatus.DIAGNOSIS_READY,
                    null,
                    probabilities,
                    mostLikelyCondition
            );
        }

        String nextQuestion = findNextBestQuestion(
                mostLikelyCondition,
                allSymptoms,
                availableSymptoms
        );

        return new DiagnosticDecision(
                DiagnosticStatus.NEEDS_MORE_INFO,
                nextQuestion,
                probabilities,
                null
        );
    }

    private static boolean shouldMakeDiagnosis(BigDecimal highestProbability, List<String> askedQuestions) {
        // if we are confident AND have asked at leas MIN questions
        if (highestProbability.compareTo(CONFIDENCE_THRESHOLD) > 0 && askedQuestions.size() >= MIN_QUESTIONS) {
            return true;
        }
        return askedQuestions.size() >= MAX_QUESTIONS;
    }

    private static String findMostLikelyCondition(Map<String, BigDecimal> probabilities) {
        return probabilities.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new IllegalStateException("No probabilities found"));
    }

    /*
     * Finds the most relevant question based on the most likely condition.
     * I.E. if Covid is the most likely condition, we will pick the symptom that has not been asked or reported yet that
     * has the highest probability for Covid to confirm.
     */
    private static String findNextBestQuestion(
            String mostLikelyCondition,
            List<String> allSymptoms,
            List<Map<String, AttributeValue>> availableSymptoms) {

        return availableSymptoms.stream()
                .filter(symptom -> !
                        allSymptoms.contains(
                                symptom.get("id").s().replace("SYMPTOM#", "")))
                .max((s1, s2) -> {
                    BigDecimal p1 = new BigDecimal(
                            s1.get("conditions").m().get(mostLikelyCondition).n());
                    BigDecimal p2 = new BigDecimal(
                            s2.get("conditions").m().get(mostLikelyCondition).n());
                    return p1.compareTo(p2);
                })
                .map(symptom -> symptom.get("id").s().replace("SYMPTOM#", ""))
                .orElseThrow(() -> new IllegalStateException("No more questions available"));
    }
}