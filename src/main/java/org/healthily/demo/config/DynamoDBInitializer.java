package org.healthily.demo.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Initializes DynamoDB tables and loads demo data on application startup.
 * The table creation would be done automatically by the IaC components, this is just for demo purposes.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DynamoDBInitializer implements CommandLineRunner {

    @Value("${dynamodb.table.health_data}")
    private String healthDataTable;

    @Value("${dynamodb.table.users}")
    private String usersTable;

    @Value("${dynamodb.table.assessments}")
    private String assessmentsTable;

    private final DynamoDbClient dynamoDB;

    @Override
    public void run(String... args) {
        createTablesIfNotExist();
        initializeHealthData();
        log.info("Data population completed.");
    }

    private void createTablesIfNotExist() {
        createSingleIndexTable(healthDataTable, "id", ScalarAttributeType.S);
        createSingleIndexTable(assessmentsTable, "id", ScalarAttributeType.S);
        createUsersTable();
    }

    private void createUsersTable() {
        try {
            DescribeTableResponse describeTableResult = dynamoDB.describeTable(DescribeTableRequest.builder()
                    .tableName(usersTable).build());
            log.info("Table {} already exists. Status: {}",
                    usersTable,
                    describeTableResult.table().tableStatus());
        } catch (ResourceNotFoundException e) {
            GlobalSecondaryIndex emailIndex = GlobalSecondaryIndex.builder()
                    .indexName("email-index")
                    .keySchema(List.of(
                            KeySchemaElement.builder().keyType(KeyType.HASH).attributeName("email").build()
                    ))
                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                    .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(1L).writeCapacityUnits(1L).build())
                    .build();

            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName(usersTable)
                    .keySchema(List.of(
                            KeySchemaElement.builder().keyType(KeyType.HASH).attributeName("id").build()
                    ))
                    .attributeDefinitions(Arrays.asList(
                            AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("email").attributeType(ScalarAttributeType.S).build()
                    ))
                    .globalSecondaryIndexes(emailIndex)
                    .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                    .build();
            try {
                dynamoDB.createTable(createTableRequest);
                log.info("Table {} created successfully.", usersTable);
            } catch (AwsServiceException ase) {
                log.error("Failed to create table {}: {}", usersTable, ase.getMessage());
                throw ase;
            }

        }
    }

    private void createSingleIndexTable(String tableName, String keyName, ScalarAttributeType keyType) {
        try {
            DescribeTableResponse describeTableResult = dynamoDB.describeTable((DescribeTableRequest.builder()
                    .tableName(tableName).build()));
            log.info("Table {} already exists. Status: {}",
                    tableName,
                    describeTableResult.table().tableStatus());
        } catch (ResourceNotFoundException e) {
            log.info("Table {} does not exist. Creating table...", tableName);

            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName(tableName)
                    .attributeDefinitions(AttributeDefinition.builder().attributeName(keyName).attributeType(keyType).build())
                    .keySchema(KeySchemaElement.builder().attributeName(keyName).keyType(KeyType.HASH).build())
                    .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                    .build();

            try {
                dynamoDB.createTable(createTableRequest);
                log.info("Table {} created successfully.", tableName);
            } catch (AwsServiceException ase) {
                log.error("Failed to create table {}: {}", tableName, ase.getMessage());
                throw ase;
            }
        }
    }

    private void initializeHealthData() {
        initializeConditions();
        initializeSymptoms();
    }

    private void initializeConditions() {
        putConditionItem("Common Cold", "0.5");
        putConditionItem("Hayfever", "0.3");
        putConditionItem("COVID-19", "0.2");
    }

    private void initializeSymptoms() {
        putSymptomItem("Sneezing", Map.of("Hayfever", "0.9", "COVID-19", "0.1", "Common Cold", "0.7"));
        putSymptomItem("Runny nose", Map.of("Hayfever", "0.85", "COVID-19", "0.2", "Common Cold", "0.8"));
        putSymptomItem("Nasal congestion", Map.of("Hayfever", "0.75", "COVID-19", "0.4", "Common Cold", "0.85"));
        putSymptomItem("Cough", Map.of("Hayfever", "0.1", "COVID-19", "0.7", "Common Cold", "0.6"));
        putSymptomItem("Fever", Map.of("Hayfever", "0.0", "COVID-19", "0.85", "Common Cold", "0.1"));
        putSymptomItem("Sore throat", Map.of("Hayfever", "0.05", "COVID-19", "0.65", "Common Cold", "0.75"));
        putSymptomItem("Loss of smell or taste", Map.of("Hayfever", "0.05", "COVID-19", "0.8", "Common Cold", "0.05"));
        putSymptomItem("Headache", Map.of("Hayfever", "0.3", "COVID-19", "0.6", "Common Cold", "0.4"));
        putSymptomItem("Fatigue", Map.of("Hayfever", "0.2", "COVID-19", "0.75", "Common Cold", "0.3"));
        putSymptomItem("Watery or itchy eyes", Map.of("Hayfever", "0.95", "COVID-19", "0.05", "Common Cold", "0.1"));
        putSymptomItem("Shortness of breath", Map.of("Hayfever", "0.05", "COVID-19", "0.5", "Common Cold", "0.05"));

    }

    private void putConditionItem(String conditionName, String prevalence) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s("CONDITION#" + conditionName).build());
        item.put("entityType", AttributeValue.builder().s("CONDITION").build());
        item.put("prevalence", AttributeValue.builder().n(String.valueOf(prevalence)).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(healthDataTable)
                .item(item)
                .build();

        try {
            dynamoDB.putItem(putItemRequest);
            log.info("Inserted condition: {}", conditionName);
        } catch (AwsServiceException e) {
            log.error("Error inserting condition {}: {}", conditionName, e.getMessage());
        }
    }

    private void putSymptomItem(String symptomName, Map<String, String> conditionsMap) {
        Map<String, AttributeValue> item = new HashMap<>();

        item.put("id", AttributeValue.builder().s("SYMPTOM#" + symptomName).build());
        item.put("entityType", AttributeValue.builder().s("SYMPTOM").build());

        Map<String, AttributeValue> conditionsAttributeMap = new HashMap<>();
        for (Map.Entry<String, String> entry : conditionsMap.entrySet()) {
            conditionsAttributeMap.put(entry.getKey(), AttributeValue.builder().n(entry.getValue()).build());
        }
        item.put("conditions", AttributeValue.builder().m(conditionsAttributeMap).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(healthDataTable)
                .item(item)
                .build();

        try {
            dynamoDB.putItem(putItemRequest);
            log.info("Inserted symptom: {}", symptomName);
        } catch (AwsServiceException e) {
            log.error("Error inserting symptom {}: {}", symptomName, e.getMessage());
        }
    }
}
