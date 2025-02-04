package org.healthily.demo.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;

import java.util.List;
import java.util.Map;

@Repository
@Slf4j
@RequiredArgsConstructor
public class HealthDataRepository {
    private final DynamoDbClient dynamoDB;

    @Value("${dynamodb.table.health_data}")
    private String tableName;

    @Cacheable(value = "conditions")
    public List<Map<String, AttributeValue>> getAllConditions() {
        log.info("Fetching all conditions from DynamoDB");
        ScanRequest scanRequest = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("entityType = :entityType")
                .expressionAttributeValues(
                        Map.of(":entityType", AttributeValue.builder().s("CONDITION").build()))
                .build();

        return dynamoDB.scan(scanRequest).items();
    }

    @Cacheable(value = "symptoms")
    public List<Map<String, AttributeValue>> getAllSymptoms() {
        log.info("Fetching all symptoms from DynamoDB");
        ScanRequest scanRequest = ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("entityType = :entityType")
                .expressionAttributeValues(
                        Map.of(":entityType", AttributeValue.builder().s("SYMPTOM").build()))
                .build();

        return dynamoDB.scan(scanRequest).items();
    }

    @Cacheable(value = "symptom", key = "#symptomId")
    public Map<String, AttributeValue> getSymptom(String symptomId) {
        log.info("Fetching symptom {} from DynamoDB", symptomId);
        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key((Map.of("id", AttributeValue.builder().s("SYMPTOM#" + symptomId).build())))
                .build();

        return dynamoDB.getItem(request).item();
    }
} 