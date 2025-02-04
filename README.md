# Demo project - condition predictor

The project is centered around helping the clients identifying their condition based on a set of symptoms.

To run the project locally:

1. Clone the repository
2. cd into the base dir
3. run `./gradlew clean build`
4. Run `docker-compose up --build`

The service will be available at `0.0.0.0:8080` as requested - use the provided Postman collection to run requests

A GUI for DynamoDB local will be available at `localhost:8000` to check the DB state after tests.

## Security

The service is secured with JWT Bearer token. All endpoints under `/assessment` need the `Authorization: Bearer xxx`
header.

Additionally, some security measures were taken to ensure users don't have access to other user's assessments.

## Weaknesses

Due to this being a demo, on very limited time, there are some weaknesses which will need to be addressed in future
iterations:

- The DynamoDB tables are created at runtime. This would need to sit in a IaC component in the future (Terraform, CDK
  etc)
- The health data is populated at runtime. This would need an admin API where owners would be able to alter this data
  independent of application deployments.
- The test coverage is not adequate. While there are unit tests for vital parts of the application as requested,
  more tests would be needed for a production ready application. (more unit tests, web layer tests, integration tests)
- Considerations around health data table performance. The in memory cache covers for frequent scans but more measures
  would be needed for
  very large amounts of data. (parallel scans, centralized cache)
- A design choice was made by scanning all health data at once (with in memory cache). Since querying on non PK fields
  in Dynamo still gets all the data and filters after fetch,
  having it all available in code was the preferred solution.
- For symptoms, we always assume the client will provide exact matches. This might be the case if we limit them to a
  dropdown in the UI, but we might
  look at ElasticSearch for fuzzy matching for free text.