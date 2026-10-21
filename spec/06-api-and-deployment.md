Specification 06: API Compatibility and Deployment
Status: Proposed
Depends on: spec/01-modulith-foundation.md, spec/02-catalog-module.md, spec/03-inventory-module.md, spec/04-payments-module.md, spec/05-orders-orchestration.md
Phase: 6 — API and Deployment Migration
1. Objective

Integrate the modular monolith with existing external consumers and provide a documented, repeatable deployment process.

Preserve externally observable behavior and security while removing obsolete infrastructure dependencies only after their responsibilities have been accounted for.

2. Repository Assessment

Before changing configuration:

Inventory existing REST endpoints.
Identify the retail web application's backend URLs.
Inspect API Gateway routes.
Inspect authentication and authorization configuration.
Inspect Config Server and service-discovery dependencies.
Inspect Dockerfiles, Compose files, and deployment manifests.
Inspect health checks, metrics, logging, and tracing.
Identify external Kafka consumers and integrations.
Identify required environment variables and secrets.

Document which infrastructure components remain necessary and why.

3. REST API Compatibility

The modular monolith MUST preserve existing externally consumed APIs wherever practical.

Requirements:

Preserve HTTP methods and routes.
Preserve request and response formats.
Preserve validation rules.
Preserve status codes and error formats.
Preserve pagination and filtering.
Preserve authentication and authorization.
Preserve required CORS configuration.
Preserve API documentation where available.

Create an endpoint inventory containing:

HTTP method.
Route.
Owning business module.
Request and response contracts.
Authentication requirements.
Known consumers.
Compatibility test.

Any intentional incompatibility MUST be documented and approved.

4. Controller Placement

Controllers MUST belong to their owning business module.

Controllers MUST call the owning module's application services or public API.

The implementation MUST NOT create a second set of public endpoints merely to expose internal module APIs.

Keep transport-specific DTOs separate from internal persistence entities.

Use consistent error handling without leaking internal exceptions or sensitive information.

5. API Gateway

Assess whether the API Gateway still provides required functionality.

It MAY be removed from the deployment path when its routing responsibilities are no longer needed and its security, rate-limiting, and other responsibilities have been preserved elsewhere.

If the Gateway is retained:

Document its purpose.
Update routes to the modular monolith.
Test authentication propagation.
Test error behavior.
Remove obsolete service-discovery assumptions.

Do not remove the Gateway merely because the application now contains multiple modules in one process.

6. Config Server and Service Discovery

Assess Config Server and Eureka separately.

The monolith SHOULD support startup without Eureka or Config Server when these are not required by the chosen deployment model.

Requirements:

Preserve necessary environment-based configuration.
Keep secrets outside source control.
Avoid hard-coded deployment credentials.
Remove obsolete discovery clients only after verifying their consumers.
Document any retained centralized configuration requirement.
7. Kafka and External Integrations

Retain Kafka when required for:

External consumers.
Cross-process messaging.
Independent integration lifecycles.
Durable delivery requirements that justify a message broker.

Replace internal-only communication with module APIs or Spring application events where appropriate.

Document retained topics, producers, consumers, retry behavior, and ownership.

Do not remove a topic until its required consumers and external contracts have been verified.

8. Deployment Requirements

Provide a repeatable way to run the modular monolith locally and in the target deployment environment.

Requirements:

Document the application entry point.
Document the HTTP port and health endpoints.
Document required environment variables.
Document PostgreSQL configuration.
Configure Liquibase initialization appropriately.
Provide or update Dockerfiles and Compose configuration where applicable.
Preserve required observability.
Support clean startup and shutdown.
Document external integration dependencies.

Do not commit secrets or production credentials.

Do not remove original microservice deployment definitions before the migration is approved.

9. Observability

Preserve or configure appropriate:

Application health checks.
Readiness checks.
Structured logging.
Correlation identifiers.
Metrics.
Distributed tracing where useful.
Error reporting.
Event processing monitoring.

Ensure that operational endpoints do not expose sensitive information.

Document how to identify failed order workflows, inventory reservations, payment operations, and event processing.

10. Testing Requirements

Add or update tests for:

Existing REST API contracts.
Authentication and authorization.
Validation and error responses.
Frontend integration.
Gateway routing where retained.
Application startup without obsolete infrastructure where intended.
Database initialization.
Health and readiness endpoints.
Required external integrations.
Kafka integration where retained.

Where practical, use automated API contract tests to compare behavior with the original service.

11. Acceptance Criteria
All required external endpoints are inventoried.
API compatibility is verified or changes are documented.
The frontend can access the modular monolith.
Authentication and authorization remain correct.
Gateway responsibilities are preserved or intentionally retired.
Config Server and Eureka are retained or removed based on documented requirements.
Required Kafka integrations remain functional.
Local deployment instructions are complete.
Health checks and observability are configured.
Secrets are not committed.
Deployment and integration tests pass.
12. Exit Criteria

The application can be built, configured, started, tested, and accessed using the documented deployment process.

Required frontend and external integrations work as expected.

The original deployment configuration remains available until the final migration approval.

13. Implementation Rules

Implement only API compatibility and deployment changes required by the modular monolith.

Do not introduce unrelated infrastructure upgrades or delete original deployment assets prematurely.

Report any remaining dependencies on the original business service URLs, Eureka, Config Server, or Kafka.
