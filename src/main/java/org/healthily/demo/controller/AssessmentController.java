package org.healthily.demo.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.healthily.demo.model.dto.AnswerQuestionRequest;
import org.healthily.demo.model.dto.AssessmentResponse;
import org.healthily.demo.model.dto.AssessmentResultResponse;
import org.healthily.demo.model.dto.StartAssessmentRequest;
import org.healthily.demo.service.AssessmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

@RestController
@RequestMapping("/assessment")
@Slf4j
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    @PostMapping("/start")
    public ResponseEntity<AssessmentResponse> startAssessment(@Valid @RequestBody StartAssessmentRequest request) {
        AssessmentResponse response = assessmentService.startAssessment(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{assessment_id}/answer")
    public ResponseEntity<AssessmentResponse> answerQuestion(
            @PathVariable("assessment_id") String assessmentId,
            @Valid @RequestBody AnswerQuestionRequest request) {
        try {
            AssessmentResponse response = assessmentService.answerQuestion(assessmentId, request);
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found ", e);
            throw e;
        }
    }

    @GetMapping("/{assessment_id}/result")
    public ResponseEntity<AssessmentResultResponse> getAssessmentResult(
            @PathVariable("assessment_id") String assessmentId) {
        try {
            AssessmentResultResponse response = assessmentService.getAssessmentResult(assessmentId);
            return ResponseEntity.ok(response);
        } catch (ResourceNotFoundException e) {
            log.error("Resource not found ", e);
            throw e;
        }
    }

}
