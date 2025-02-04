package org.healthily.demo.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.Map;

@Repository
@Slf4j
@RequiredArgsConstructor
public class AssessmentRepository {

    private final DynamoDbClient dynamoDB;

    @Value("${dynamodb.table.assessments}")
    private String tableName;

    public Map<String, AttributeValue> getAssessment(String assessmentId) {
        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.builder()
                        .s(assessmentId).build()))
                .build();

        return dynamoDB.getItem(request).item();
    }

    public void saveAssessment(String assessmentId, Map<String, AttributeValue> item) {
        item.put("id", AttributeValue.builder()
                .s(assessmentId)
                .build());
        dynamoDB.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }

    public void updateAssessment(Map<String, AttributeValue> item) {
        dynamoDB.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }
} 