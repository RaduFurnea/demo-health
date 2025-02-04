package org.healthily.demo.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentResponse {
    @JsonProperty("assessment_id")
    private String assessmentId;
    @JsonProperty("next_question_id")
    private String nextQuestionId;

    @JsonIgnore
    private String userId;
}