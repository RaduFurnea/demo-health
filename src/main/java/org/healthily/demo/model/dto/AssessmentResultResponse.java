package org.healthily.demo.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AssessmentResultResponse {
    private String condition;
    private Map<String, String> probabilities;

    @JsonIgnore
    private String userId;
} 