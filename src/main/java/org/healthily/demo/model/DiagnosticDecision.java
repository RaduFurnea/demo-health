package org.healthily.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
public class DiagnosticDecision {
    private DiagnosticStatus status;
    private String nextQuestion;
    private Map<String, BigDecimal> currentProbabilities;
    private String diagnosis;
}
