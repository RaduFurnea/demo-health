package org.healthily.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.healthily.demo.exception.BadRequestException;
import org.healthily.demo.model.DiagnosticDecision;
import org.healthily.demo.model.DiagnosticStatus;
import org.healthily.demo.model.dto.AnswerQuestionRequest;
import org.healthily.demo.model.dto.AssessmentResponse;
import org.healthily.demo.model.dto.AssessmentResultResponse;
import org.healthily.demo.model.dto.StartAssessmentRequest;
import org.healthily.demo.repository.AssessmentRepository;
import org.healthily.demo.repository.HealthDataRepository;
import org.healthily.demo.utils.DiagnosticUtils;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssessmentService {

    private final HealthDataRepository healthDataRepository;
    private final AssessmentRepository assessmentRepository;

    @PreAuthorize("#request.userId == authentication.principal.username")
    public AssessmentResponse startAssessment(StartAssessmentRequest request) {
        List<Map<String, AttributeValue>> conditions = healthDataRepository.getAllConditions();
        List<Map<String, AttributeValue>> allSymptoms = healthDataRepository.getAllSymptoms();

        List<Map<String, AttributeValue>> initialSymptomData = allSymptoms.stream()
                .filter(symptom -> {
                    String symptomId = symptom.get("id").s().replace("SYMPTOM#", "");
                    return request.getInitialSymptoms().contains(symptomId);
                })
                .toList();

        Map<String, BigDecimal> conditionProbabilities = DiagnosticUtils.calculateInitialProbabilities(
                conditions,
                initialSymptomData
        );

        log.info("Initial probabilities: {}", conditionProbabilities);

        DiagnosticDecision decision = DiagnosticUtils.evaluateNextStep(
                conditionProbabilities,
                request.getInitialSymptoms(),
                List.of(),
                allSymptoms
        );

        String assessmentId = UUID.randomUUID().toString();
        Map<String, AttributeValue> item = createAssessmentItem(request, decision);
        assessmentRepository.saveAssessment(assessmentId, item);

        return createAssessmentResponse(assessmentId, decision, request.getUserId());
    }

    @PostAuthorize("returnObject.userId == authentication.principal.username")
    public AssessmentResponse answerQuestion(String assessmentId, AnswerQuestionRequest request) {
        Map<String, AttributeValue> assessment = assessmentRepository.getAssessment(assessmentId);
        if (assessment == null || assessment.isEmpty()) {
            throw new BadRequestException("Assessment not found");
        }

        // Check next question id matches
        if (!request.getQuestionId().equals(assessment.get("next_question_id").s())) {
            throw new BadRequestException("Invalid next question id: %s, should be: %s".formatted(request.getQuestionId(), assessment.get("next_question_id").s()));
        }

        Map<String, AttributeValue> symptom = healthDataRepository.getSymptom(request.getQuestionId());
        if (symptom == null || symptom.isEmpty()) {
            throw new BadRequestException("Symptom not found");
        }

        Map<String, BigDecimal> currentProbabilities = new HashMap<>();
        assessment.get("probabilities").m().forEach((key, value) ->
                currentProbabilities.put(key, new BigDecimal(value.n()))
        );

        Map<String, BigDecimal> updatedProbabilities = DiagnosticUtils.updateProbabilities(
                currentProbabilities,
                symptom.get("conditions").m(),
                request.getResponse().toBooleanValue()
        );

        log.info("Updated probabilities: {}", updatedProbabilities);

        Map<String, AttributeValue> askedQuestions = new HashMap<>();
        if (assessment.containsKey("asked_questions")) {
            askedQuestions.putAll(assessment.get("asked_questions").m());
        }
        askedQuestions.put(request.getQuestionId(),
                AttributeValue.builder().s(request.getResponse().toString().toLowerCase()).build());

        List<String> allQuestions = Stream.concat(
                assessment.get("initial_symptoms").ss().stream(),
                askedQuestions.keySet().stream()
        ).toList();

        DiagnosticDecision decision = DiagnosticUtils.evaluateNextStep(
                updatedProbabilities,
                allQuestions,
                askedQuestions.keySet().stream().toList(),
                healthDataRepository.getAllSymptoms()
        );

        Map<String, AttributeValue> updateItem = createUpdatedAssessmentItem(assessment, decision, askedQuestions);
        assessmentRepository.updateAssessment(updateItem);

        return createAssessmentResponse(assessmentId, decision, assessment.get("user_id").s());
    }

    @PostAuthorize("returnObject.userId == authentication.principal.username")
    public AssessmentResultResponse getAssessmentResult(String assessmentId) {
        Map<String, AttributeValue> assessment = assessmentRepository.getAssessment(assessmentId);
        if (assessment == null) {
            throw new BadRequestException("Assessment not found");
        }

        if ("ongoing".equals(assessment.get("status").s())) {
            throw new BadRequestException("Assessment %s is still ongoing, please answer the remaining questions first".formatted(assessmentId));
        }

        Map<String, BigDecimal> rawProbabilities = new HashMap<>();
        assessment.get("probabilities").m().forEach((key, value) ->
                rawProbabilities.put(key, new BigDecimal(value.n()).multiply(BigDecimal.valueOf(100L))));

        Map<String, String> formattedProbabilities = rawProbabilities.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> String.format("%.1f%%", e.getValue())
                ));

        return AssessmentResultResponse.builder()
                .condition(assessment.get("diagnosis").s())
                .probabilities(formattedProbabilities)
                .userId(assessment.get("user_id").s())
                .build();
    }

    private Map<String, AttributeValue> createAssessmentItem(StartAssessmentRequest request, DiagnosticDecision decision) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("user_id", AttributeValue.builder().s(request.getUserId()).build());
        item.put("initial_symptoms", AttributeValue.builder().ss(request.getInitialSymptoms()).build());
        item.put("status", AttributeValue.builder().s(
                decision.getStatus() == DiagnosticStatus.DIAGNOSIS_READY ? "completed" : "ongoing"
        ).build());

        Map<String, AttributeValue> probabilitiesMap = new HashMap<>();
        decision.getCurrentProbabilities().forEach((k, v) ->
                probabilitiesMap.put(k, AttributeValue.builder().n(String.valueOf(v)).build())
        );
        item.put("probabilities", AttributeValue.builder().m(probabilitiesMap).build());

        if (decision.getStatus() == DiagnosticStatus.NEEDS_MORE_INFO) {
            item.put("next_question_id", AttributeValue.builder().s(decision.getNextQuestion()).build());
        } else {
            item.put("diagnosis", AttributeValue.builder().s(decision.getDiagnosis()).build());
        }

        return item;
    }

    private Map<String, AttributeValue> createUpdatedAssessmentItem(
            Map<String, AttributeValue> assessment,
            DiagnosticDecision decision,
            Map<String, AttributeValue> askedQuestions) {
        Map<String, AttributeValue> updateItem = new HashMap<>(assessment);

        Map<String, AttributeValue> probabilitiesMap = new HashMap<>();
        decision.getCurrentProbabilities().forEach((k, v) ->
                probabilitiesMap.put(k, AttributeValue.builder().n(String.valueOf(v)).build())
        );
        updateItem.put("probabilities", AttributeValue.builder().m(probabilitiesMap).build());
        updateItem.put("asked_questions", AttributeValue.builder().m(askedQuestions).build());

        if (decision.getStatus() == DiagnosticStatus.DIAGNOSIS_READY) {
            updateItem.put("status", AttributeValue.builder().s("completed").build());
            updateItem.put("diagnosis", AttributeValue.builder().s(decision.getDiagnosis()).build());
            updateItem.remove("next_question_id");
        } else {
            updateItem.put("next_question_id", AttributeValue.builder().s(decision.getNextQuestion()).build());
        }

        return updateItem;
    }

    private AssessmentResponse createAssessmentResponse(String assessmentId, DiagnosticDecision decision, String userId) {
        return AssessmentResponse.builder()
                .assessmentId(assessmentId)
                .nextQuestionId(decision.getNextQuestion())
                .userId(userId)
                .build();
    }
} 