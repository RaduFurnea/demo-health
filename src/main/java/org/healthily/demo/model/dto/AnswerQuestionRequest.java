package org.healthily.demo.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.healthily.demo.model.ResponseType;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnswerQuestionRequest {
    @NotBlank(message = "Question ID is required")
    @JsonProperty("question_id")
    private String questionId;

    @NotNull(message = "Response is required")
    private ResponseType response;
}