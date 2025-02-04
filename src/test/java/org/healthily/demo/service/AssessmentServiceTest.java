package org.healthily.demo.service;

import org.healthily.demo.exception.BadRequestException;
import org.healthily.demo.model.ResponseType;
import org.healthily.demo.model.dto.AnswerQuestionRequest;
import org.healthily.demo.model.dto.AssessmentResponse;
import org.healthily.demo.model.dto.AssessmentResultResponse;
import org.healthily.demo.model.dto.StartAssessmentRequest;
import org.healthily.demo.repository.AssessmentRepository;
import org.healthily.demo.repository.HealthDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.test.context.support.WithMockUser;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssessmentServiceTest {

    @Mock
    private HealthDataRepository healthDataRepository;

    @Mock
    private AssessmentRepository assessmentRepository;

    @InjectMocks
    private AssessmentService assessmentService;

    private List<Map<String, AttributeValue>> mockConditions;
    private List<Map<String, AttributeValue>> mockSymptoms;

    @BeforeEach
    void setUp() {
        mockConditions = Arrays.asList(
                createCondition("cold", "0.1"),
                createCondition("flu", "0.05")
        );

        mockSymptoms = Arrays.asList(
                createSymptom("fever", Map.of("cold", "0.3", "flu", "0.8")),
                createSymptom("cough", Map.of("cold", "0.6", "flu", "0.5"))
        );
    }

    @Test
    @WithMockUser(username = "user123")
    void startAssessment_Success() {
        StartAssessmentRequest request = new StartAssessmentRequest();
        request.setUserId("user123");
        request.setInitialSymptoms(Collections.singletonList("fever"));

        when(healthDataRepository.getAllConditions()).thenReturn(mockConditions);
        when(healthDataRepository.getAllSymptoms()).thenReturn(mockSymptoms);

        AssessmentResponse response = assessmentService.startAssessment(request);

        assertNotNull(response);
        assertNotNull(response.getAssessmentId());
        assertEquals("user123", response.getUserId());
        assertNotNull(response.getNextQuestionId());
        verify(healthDataRepository).getAllConditions();
        verify(healthDataRepository).getAllSymptoms();
        verify(assessmentRepository).saveAssessment(anyString(), any());
    }

    @Test
    @WithMockUser(username = "user123")
    void answerQuestion_Success() {
        String assessmentId = "test-id";
        AnswerQuestionRequest request = new AnswerQuestionRequest();
        request.setQuestionId("fever");
        request.setResponse(ResponseType.YES);

        Map<String, AttributeValue> assessment = createMockAssessment("user123", "fever");
        when(assessmentRepository.getAssessment(assessmentId)).thenReturn(assessment);
        when(healthDataRepository.getSymptom("fever")).thenReturn(mockSymptoms.get(0));
        when(healthDataRepository.getAllSymptoms()).thenReturn(mockSymptoms);

        AssessmentResponse response = assessmentService.answerQuestion(assessmentId, request);

        assertNotNull(response);
        assertEquals(assessmentId, response.getAssessmentId());
        assertEquals("user123", response.getUserId());
        verify(assessmentRepository).getAssessment(assessmentId);
        verify(healthDataRepository).getSymptom("fever");
        verify(assessmentRepository).updateAssessment(any());
    }

    @Test
    @WithMockUser(username = "user123")
    void answerQuestion_ThrowsException_WhenNoAssessment() {
        String assessmentId = "test-id";
        AnswerQuestionRequest request = new AnswerQuestionRequest();

        when(assessmentRepository.getAssessment(assessmentId)).thenReturn(null);

        assertThrows(BadRequestException.class, () ->
                assessmentService.answerQuestion(assessmentId, request));

        verify(assessmentRepository).getAssessment(assessmentId);
        verifyNoInteractions(healthDataRepository);
        verifyNoMoreInteractions(assessmentRepository);
    }

    @Test
    @WithMockUser(username = "user123")
    void answerQuestion_ThrowsException_WhenQuestionIdDoesNotMatch() {
        String assessmentId = "test-id";
        AnswerQuestionRequest request = new AnswerQuestionRequest();
        request.setQuestionId("fever");
        request.setResponse(ResponseType.YES);

        Map<String, AttributeValue> assessment = createMockAssessment("user123", "no fever");
        when(assessmentRepository.getAssessment(assessmentId)).thenReturn(assessment);

        assertThrows(BadRequestException.class, () ->
                assessmentService.answerQuestion(assessmentId, request));

        verify(assessmentRepository).getAssessment(assessmentId);
        verifyNoInteractions(healthDataRepository);
        verifyNoMoreInteractions(assessmentRepository);
    }

    @Test
    @WithMockUser(username = "user123")
    void answerQuestion_ThrowsException_WhenSymptomDoesNotExist() {
        String assessmentId = "test-id";
        AnswerQuestionRequest request = new AnswerQuestionRequest();
        request.setQuestionId("fever");
        request.setResponse(ResponseType.YES);

        Map<String, AttributeValue> assessment = createMockAssessment("user123", "fever");
        when(assessmentRepository.getAssessment(assessmentId)).thenReturn(assessment);
        when(healthDataRepository.getSymptom("fever")).thenReturn(null);

        assertThrows(BadRequestException.class, () ->
                assessmentService.answerQuestion(assessmentId, request));

        verify(assessmentRepository).getAssessment(assessmentId);
        verify(healthDataRepository).getSymptom("fever");
        verifyNoMoreInteractions(assessmentRepository);
    }


    @Test
    @WithMockUser(username = "user123")
    void getAssessmentResult_Success() {
        String assessmentId = "test-id";
        Map<String, AttributeValue> assessment = createCompletedAssessment("user123", "flu");
        when(assessmentRepository.getAssessment(assessmentId)).thenReturn(assessment);

        AssessmentResultResponse response = assessmentService.getAssessmentResult(assessmentId);

        assertNotNull(response);
        assertEquals("flu", response.getCondition());
        assertEquals("user123", response.getUserId());
        assertNotNull(response.getProbabilities());
        verify(assessmentRepository).getAssessment(assessmentId);
    }

    @Test
    @WithMockUser(username = "user123")
    void getAssessmentResult_ThrowsException_WhenNoAssessment() {
        String assessmentId = "test-id";
        when(assessmentRepository.getAssessment(assessmentId)).thenReturn(null);

        assertThrows(BadRequestException.class, () ->
                assessmentService.getAssessmentResult(assessmentId));
    }

    @Test
    @WithMockUser(username = "user123")
    void getAssessmentResult_ThrowsException_WhenAssessmentOngoing() {
        String assessmentId = "test-id";
        Map<String, AttributeValue> assessment = createMockAssessment("user123", "fever");
        when(assessmentRepository.getAssessment(assessmentId)).thenReturn(assessment);

        assertThrows(BadRequestException.class, () ->
                assessmentService.getAssessmentResult(assessmentId));
    }

    private Map<String, AttributeValue> createMockAssessment(String userId, String nextQuestionId) {
        return Map.of(
                "user_id", AttributeValue.builder().s(userId).build(),
                "status", AttributeValue.builder().s("ongoing").build(),
                "next_question_id", AttributeValue.builder().s(nextQuestionId).build(),
                "probabilities", AttributeValue.builder().m(Map.of(
                        "cold", AttributeValue.builder().n("0.6").build(),
                        "flu", AttributeValue.builder().n("0.4").build()
                )).build(),
                "initial_symptoms", AttributeValue.builder().ss(List.of("headache")).build()
        );
    }

    private Map<String, AttributeValue> createCompletedAssessment(String userId, String diagnosis) {
        Map<String, AttributeValue> probabilities = Map.of(
                "cold", AttributeValue.builder().n("0.2").build(),
                "flu", AttributeValue.builder().n("0.8").build()
        );

        return Map.of(
                "user_id", AttributeValue.builder().s(userId).build(),
                "status", AttributeValue.builder().s("completed").build(),
                "diagnosis", AttributeValue.builder().s(diagnosis).build(),
                "probabilities", AttributeValue.builder().m(probabilities).build()
        );
    }

    private Map<String, AttributeValue> createCondition(String id, String prevalence) {
        return Map.of(
                "id", AttributeValue.builder().s("CONDITION#" + id).build(),
                "prevalence", AttributeValue.builder().n(prevalence).build()
        );
    }

    private Map<String, AttributeValue> createSymptom(String id, Map<String, String> conditionProbabilities) {
        Map<String, AttributeValue> conditions = conditionProbabilities.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> AttributeValue.builder().n(entry.getValue()).build()
                ));

        return Map.of(
                "id", AttributeValue.builder().s("SYMPTOM#" + id).build(),
                "conditions", AttributeValue.builder().m(conditions).build()
        );
    }
} 