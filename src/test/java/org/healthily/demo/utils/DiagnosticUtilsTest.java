package org.healthily.demo.utils;

import org.healthily.demo.model.DiagnosticDecision;
import org.healthily.demo.model.DiagnosticStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DiagnosticUtilsTest {

    @Test
    void calculateInitialProbabilities_WithOneSymptom_whenPrevalenceOutweighsProbability() {
        List<Map<String, AttributeValue>> conditions = Arrays.asList(
                createCondition("cold", "0.5"),
                createCondition("flu", "0.05")
        );
        List<Map<String, AttributeValue>> symptoms = Collections.singletonList(
                createSymptom("fever", Map.of("cold", "0.3", "flu", "0.8"))
        );

        Map<String, BigDecimal> probabilities = DiagnosticUtils.calculateInitialProbabilities(conditions, symptoms);

        assertEquals(2, probabilities.size());
        assertTrue(probabilities.get("flu").compareTo(probabilities.get("cold")) < 0);
    }

    @Test
    void updateProbabilities_WithPositiveResponse() {
        Map<String, BigDecimal> priorProbabilities = new HashMap<>();
        priorProbabilities.put("cold", BigDecimal.valueOf(0.5));
        priorProbabilities.put("flu", BigDecimal.valueOf(0.5));

        Map<String, AttributeValue> symptomConditions = new HashMap<>();
        symptomConditions.put("cold", AttributeValue.builder().n("0.3").build());
        symptomConditions.put("flu", AttributeValue.builder().n("0.8").build());

        Map<String, BigDecimal> updatedProbabilities = DiagnosticUtils.updateProbabilities(
                priorProbabilities, symptomConditions, true);

        assertTrue(updatedProbabilities.get("flu").compareTo(updatedProbabilities.get("cold")) > 0);
        assertEquals(1.0, updatedProbabilities.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue(), 0.0001);
    }

    @Test
    void evaluateNextStep_ShouldMakeDiagnosis_WhenConfidentEnough_NoQuestionsAsked() {
        Map<String, BigDecimal> probabilities = new HashMap<>();
        probabilities.put("cold", BigDecimal.valueOf(0.2));
        probabilities.put("flu", BigDecimal.valueOf(0.901));

        List<String> allSymptoms = Arrays.asList("fever", "cough");
        List<Map<String, AttributeValue>> availableSymptoms = createAvailableSymptoms();

        DiagnosticDecision decision = DiagnosticUtils.evaluateNextStep(
                probabilities, allSymptoms, List.of(), availableSymptoms);

        assertEquals(DiagnosticStatus.NEEDS_MORE_INFO, decision.getStatus());
        assertNull(decision.getDiagnosis());
    }

    @Test
    void evaluateNextStep_ShouldMakeDiagnosis_WhenConfidentEnough_OneQuestionAsked() {
        Map<String, BigDecimal> probabilities = new HashMap<>();
        probabilities.put("cold", BigDecimal.valueOf(0.2));
        probabilities.put("flu", BigDecimal.valueOf(0.901));

        List<String> allSymptoms = List.of("fever", "cough", "runny nose");
        List<String> askedSymptom = List.of("runny nose");

        List<Map<String, AttributeValue>> availableSymptoms = createAvailableSymptoms();

        DiagnosticDecision decision = DiagnosticUtils.evaluateNextStep(
                probabilities, allSymptoms, askedSymptom, availableSymptoms);

        assertEquals(DiagnosticStatus.DIAGNOSIS_READY, decision.getStatus());
        assertEquals("flu", decision.getDiagnosis());
    }

    @Test
    void evaluateNextStep_ShouldAskMoreQuestions_WhenUncertain() {
        Map<String, BigDecimal> probabilities = new HashMap<>();
        probabilities.put("cold", BigDecimal.valueOf(0.4));
        probabilities.put("flu", BigDecimal.valueOf(0.6));

        List<String> askedQuestions = Collections.singletonList("fever");
        List<Map<String, AttributeValue>> availableSymptoms = createAvailableSymptoms();

        DiagnosticDecision decision = DiagnosticUtils.evaluateNextStep(
                probabilities, askedQuestions, List.of(), availableSymptoms);

        assertEquals(DiagnosticStatus.NEEDS_MORE_INFO, decision.getStatus());
        assertNotNull(decision.getNextQuestion());
    }

    @Test
    void calculateInitialProbabilities_ShouldConsiderPrevalence() {
        List<Map<String, AttributeValue>> conditions = Arrays.asList(
                createCondition("cold", "0.1"),
                createCondition("flu", "0.05")
        );

        List<Map<String, AttributeValue>> symptoms = Collections.singletonList(
                createSymptom("fever", Map.of("cold", "0.3", "flu", "0.8"))
        );

        Map<String, BigDecimal> probabilities = DiagnosticUtils.calculateInitialProbabilities(conditions, symptoms);

        assertEquals(2, probabilities.size());
        BigDecimal coldProbability = probabilities.get("cold");
        BigDecimal fluProbability = probabilities.get("flu");

        assertEquals(1, coldProbability.add(fluProbability).doubleValue(), 0.0001);
        assertTrue(fluProbability.compareTo(coldProbability) > 0);
    }

    private Map<String, AttributeValue> createSymptom(String id, Map<String, String> conditionProbabilities) {
        Map<String, AttributeValue> symptom = new HashMap<>();
        symptom.put("id", AttributeValue.builder().s("SYMPTOM#" + id).build());

        Map<String, AttributeValue> conditions = new HashMap<>();
        conditionProbabilities.forEach((k, v) ->
                conditions.put(k, AttributeValue.builder().n(v).build())
        );

        symptom.put("conditions", AttributeValue.builder().m(conditions).build());
        return symptom;
    }

    private List<Map<String, AttributeValue>> createAvailableSymptoms() {
        return Arrays.asList(
                createSymptom("fever", Map.of("cold", "0.3", "flu", "0.8")),
                createSymptom("cough", Map.of("cold", "0.6", "flu", "0.5")),
                createSymptom("headache", Map.of("cold", "0.4", "flu", "0.7"))
        );
    }

    private Map<String, AttributeValue> createCondition(String id, String prevalence) {
        Map<String, AttributeValue> condition = new HashMap<>();
        condition.put("id", AttributeValue.builder().s("CONDITION#" + id).build());
        condition.put("prevalence", AttributeValue.builder().n(prevalence).build());
        return condition;
    }
}