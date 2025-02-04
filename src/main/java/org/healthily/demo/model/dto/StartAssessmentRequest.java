package org.healthily.demo.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartAssessmentRequest {
    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @NotEmpty(message = "At least one initial symptom is required")
    @JsonProperty("initial_symptoms")
    private List<String> initialSymptoms;
} 