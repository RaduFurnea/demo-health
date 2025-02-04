package org.healthily.demo.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.healthily.demo.model.dto.RegisterRequest;
import org.healthily.demo.security.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserRepository {
    private final DynamoDbClient dynamoDB;
    private final PasswordEncoder passwordEncoder;

    @Value("${dynamodb.table.users}")
    private String usersTable;

    public void createUser(RegisterRequest registerRequest) {
        String userId = UUID.randomUUID().toString();

        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.builder().s(userId).build(),
                "email", AttributeValue.builder().s(registerRequest.getEmail()).build(),
                "password", AttributeValue.builder().s(passwordEncoder.encode(registerRequest.getPassword())).build(),
                "age", AttributeValue.builder().n(String.valueOf(registerRequest.getAge())).build(),
                "gender", AttributeValue.builder().s(registerRequest.getGender()).build(),
                "entityType", AttributeValue.builder().s("USER").build()
        );

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(usersTable)
                .item(item)
                .build();

        try {
            dynamoDB.putItem(putItemRequest);
        } catch (ConditionalCheckFailedException e) {
            throw new IllegalStateException("Email already exists");
        }
    }

    public User findByEmail(String email) {
        Map<String, AttributeValue> expressionAttributeValues = Map.of(
                ":email", AttributeValue.builder().s(email).build(),
                ":entityType", AttributeValue.builder().s("USER").build()
        );

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(usersTable)
                .indexName("email-index")
                .keyConditionExpression("email = :email")
                .filterExpression("entityType = :entityType")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        QueryResponse response = dynamoDB.query(queryRequest);

        if (response.items().isEmpty()) {
            return null;
        }

        Map<String, AttributeValue> item = response.items().get(0);

        return User.builder()
                .id(item.get("id").s().replace("USER#", ""))
                .email(item.get("email").s())
                .password(item.get("password").s())
                .age(Integer.parseInt(item.get("age").n()))
                .gender(item.get("gender").s())
                .entityType(item.get("entityType").s())
                .build();
    }
} 