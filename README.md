# AlimentadorAutomático (Automatic Pet Feeder API)

A production-ready Spring Boot backend for IoT-connected automatic pet feeders. The API provides user account management via Google OAuth 2.0, feeder hardware device pairing, highly configurable scheduling rules (interval-based or daily time-based), and an authenticated polling interface for embedded microcontrollers.

---

## Table of Contents

- [Overview & Architecture](#overview--architecture)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Environment Variables](#environment-variables)
- [API Reference](#api-reference)
  - [Authentication](#authentication)
  - [Token Lifecycle (Refresh Token Flow)](#token-lifecycle-refresh-token-flow)
  - [Feeders Management](#feeders-management)
  - [Feeding Schedules (Configs)](#feeding-schedules-configs)
  - [Hardware Device Interface](#hardware-device-interface)
  - [Development Endpoints](#development-endpoints)
- [Error Handling & RFC 9457](#error-handling--rfc-9457)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Running in Development](#running-in-development)
  - [Running in Production with Docker](#running-in-production-with-docker)
  - [Running Tests](#running-tests)
- [Database Migrations](#database-migrations)
- [License](#license)

---

## Overview & Architecture

The application is structured following clean domain-driven architecture and 12-factor application principles:

- **Strict Tenant Isolation**: Endpoints never allow the caller to pass user IDs in query parameters, URL paths, or request payloads. The user identity is extracted securely from the verified application JWT claims (`sub` UUID). Any operation targeting a feeder or configuration belonging to another user returns an identical `404 Not Found` response to prevent ID enumeration.
- **Stateless Authentication with Refresh Tokens**: Frontend clients authenticate via Google Sign-In. The backend validates the Google ID token against Google's public JWKS keys (validating audience, issuer, and email verification) and exchanges it for a short-lived application JWT (access token) paired with a long-lived opaque refresh token stored server-side. Tokens rotate on every refresh (RFC 6819) and re-use of a revoked token invalidates all sessions for that user immediately.
- **Hardware-First Schedule Engine**: Scheduling rules support continuous frequency intervals (`INTERVAL`) or wall-clock schedules (`DAILY_TIME`) across specific days of the week, tied to the feeder's specific IANA timezone (defaulting to `America/Bahia`).
- **Database Migrations as Single Source of Truth**: Flyway handles database versioning. Hibernate operates with `ddl-auto=validate` and `open-in-view=false` to ensure data integrity and avoid unintended N+1 queries outside transactions.

---

## Key Features

- **Google OAuth 2.0 Sign-In with Refresh Tokens**: Validates Google ID tokens and issues a short-lived HS256 JWT (access token, 1 h) plus a long-lived opaque refresh token (30 d, server-side). Tokens rotate on every use; replayed revoked tokens trigger full-session revocation.
- **Feeder Management**: Pair physical hardware UUIDs, assign human-readable labels, adjust timezones, and toggle operational states.
- **Flexible Feeding Scheduling**:
  - `INTERVAL`: Dispenses food every $N$ minutes ($1 \le N \le 10080$ minutes / 7 days).
  - `DAILY_TIME`: Dispenses food at specific wall-clock times (`HH:mm:ss`) on designated days of the week (`MON`, `TUE`, etc.).
  - `dose`: Calibrated motor activations per dispense ($1 \le \text{dose} \le 100$).
- **IoT Hardware Polling Endpoint**: Embedded hardware queries its current active configuration and server timestamp via `GET /devices/{deviceId}/config` without requiring user-level tokens.
- **OpenAPI 3 / Swagger UI**: Available exclusively under the `dev` profile for interactive testing with Bearer authorization.
- **Hardened Security & Logging**: Log injection prevention regex (`[\p{Cc}\p{Zl}\p{Zp}]`), CORS configuration, and non-root Docker containerization.

---

## Technology Stack

- **Runtime & Language**: Java 25
- **Framework**: Spring Boot 4.1.1
- **Persistence**: Spring Data JPA, Hibernate 6+
- **Database**: PostgreSQL 17 (Production) / In-Memory H2 (Development)
- **Migrations**: Flyway (`flyway-database-postgresql`)
- **Security**: Spring Security 6+, OAuth2 Resource Server, Nimbus JOSE JWT
- **Documentation**: Springdoc OpenAPI 3.0 / Swagger UI (v2.8.5)
- **Containerization**: Docker (multi-stage build), Docker Compose
- **Build Tool**: Apache Maven Wrapper (`mvnw`)

---

## Project Structure

```text
AlimentadorAutomatico/
├── .mvn/wrapper/                 # Maven wrapper binaries
├── src/
│   ├── main/
│   │   ├── java/com/threedimensional/feeder/
│   │   │   ├── config/          # Security, JWT, OpenAPI, Auth properties
│   │   │   ├── controller/      # REST API Controllers & Exception Handlers
│   │   │   ├── dto/             # Request & Response record contracts
│   │   │   ├── exception/       # Domain-specific business exceptions
│   │   │   ├── model/           # JPA entities and enums (User, Feeder, FeederConfig)
│   │   │   ├── repository/      # Spring Data JPA repositories
│   │   │   └── service/         # Business services and token verification
│   │   └── resources/
│   │       ├── db/migration/    # Flyway SQL migration scripts (V1–V4)
│   │       ├── application.properties      # Shared baseline configuration
│   │       ├── application-dev.properties  # Development profile configuration
│   │       └── application-prod.properties # Production profile configuration
│   └── test/java/com/threedimensional/feeder/ # Comprehensive integration & unit tests
├── .env.example                 # Template for environment variables
├── .gitignore                   # Git ignore rules
├── Dockerfile                   # Multi-stage container build
├── docker-compose.dev.yml       # Docker Compose for local dev + PostgreSQL
├── docker-compose.prod.yml      # Docker Compose for production deployment
├── docker-compose.yml           # Default Compose linking to development
└── pom.xml                      # Maven dependencies and build definition
```

---

## Environment Variables

The application strictly separates configuration by profiles. Profile-specific settings are loaded from environment variables:

| Variable | Description | Profile | Default / Example |
| :--- | :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | All | `dev` (fallback: `dev`) |
| `PORT` | Web server HTTP port | All / Prod | `8080` |
| `POSTGRES_DB` | PostgreSQL database name | Prod / Compose | `feeder` |
| `POSTGRES_USER` | PostgreSQL username | Prod / Compose | `feeder` |
| `POSTGRES_PASSWORD` | PostgreSQL password | Prod / Compose | *(Must be provided)* |
| `SPRING_DATASOURCE_URL` | Full JDBC database URL | Prod | `jdbc:postgresql://db:5432/feeder` |
| `SPRING_DATASOURCE_USERNAME` | JDBC connection username | Prod | `feeder` |
| `SPRING_DATASOURCE_PASSWORD` | JDBC connection password | Prod | *(Must be provided)* |
| `GOOGLE_CLIENT_ID` | Google OAuth Web Client ID | Prod | `*.apps.googleusercontent.com` |
| `JWT_SECRET` | HMAC-SHA256 signing secret ($\ge 32$ chars) | Prod | *(Must be provided in production)* |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed frontend origins | Prod | `https://your-frontend.com` |
| `DB_POOL_MAX_SIZE` | HikariCP maximum pool size | Prod | `10` |
| `DB_POOL_MIN_IDLE` | HikariCP minimum idle connections | Prod | `2` |

> [!IMPORTANT]
> In production (`SPRING_PROFILES_ACTIVE=prod`), `GOOGLE_CLIENT_ID`, `JWT_SECRET` ($\ge 32$ characters), and `CORS_ALLOWED_ORIGINS` are validated at application startup by `@Validated` on `AuthProperties`. If missing or invalid, the container fails fast and shuts down immediately to prevent insecure executions.

---

## API Reference

All requests sending payloads must include `Content-Type: application/json`.  
Endpoints requiring authentication must include the HTTP header:
```http
Authorization: Bearer <accessToken>
```

---

### Authentication

> [!NOTE]
> The auth flow issues a **short-lived access token** (JWT, default 1 h) for API calls and a **long-lived refresh token** (opaque, default 30 d) stored server-side. Refresh tokens rotate on every use: each call to `/auth/refresh` invalidates the old token and returns a new pair. If a revoked token is replayed, every active session for that user is immediately revoked as a replay-attack countermeasure (RFC 6819 §5.2.1).

#### Token Lifecycle (Refresh Token Flow)

```
Client                           API
  │── POST /auth/google ────────► │  verifies Google ID token
  │◄─ { accessToken, refreshToken } ─│  issues JWT + opaque refresh token
  │                                │
  │  (access token expires)        │
  │── POST /auth/refresh ─────────► │  validates refresh token
  │◄─ { accessToken, refreshToken } ─│  rotates: old revoked, new pair returned
  │                                │
  │  (user logs out)               │
  │── POST /auth/revoke ──────────► │  revokes refresh token (single session)
  │◄─ 204 No Content ─────────────│
```

#### 1. Exchange Google ID Token for Application Tokens
- **Route**: `POST /auth/google`
- **Access**: Public
- **Request Body**:
```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6..."
}
```
- **Responses**:
  - `200 OK`:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "refreshToken": "a3f8c2e1b4d7...",
  "refreshExpiresIn": 2592000,
  "user": {
    "id": "c3e808fb-8fa4-46c5-9b21-4f18df2b9dc3",
    "email": "user@example.com",
    "name": "Jane Doe"
  }
}
```
  - `401 Unauthorized`: Invalid or expired Google ID token.

> [!TIP]
> `expiresIn` and `refreshExpiresIn` are in seconds. Use them to schedule silent refreshes without decoding either token. Store the `refreshToken` securely (e.g. `HttpOnly` cookie or secure storage on mobile).

#### 2. Rotate Refresh Token (Silent Refresh)
- **Route**: `POST /auth/refresh`
- **Access**: Public
- **Description**: Exchanges a valid refresh token for a fresh access token and a new refresh token. The submitted token is immediately revoked; the client must replace it with the one returned.
- **Request Body**:
```json
{
  "refreshToken": "a3f8c2e1b4d7..."
}
```
- **Responses**:
  - `200 OK`: Same schema as `POST /auth/google`, with a new `accessToken` and a new `refreshToken`.
  - `400 Bad Request`: Missing or blank `refreshToken` field.
  - `401 Unauthorized`: Token not found, already revoked, or expired.

> [!CAUTION]
> Each refresh token is single-use. If the API detects a revoked token being replayed, **all sessions for that user are invalidated immediately**. The client must always store and use the latest `refreshToken` from the response.

#### 3. Revoke Refresh Token (Logout)
- **Route**: `POST /auth/revoke`
- **Access**: Public
- **Description**: Revokes the given refresh token, ending the current session. Unknown or already-revoked tokens are silently ignored — the response is always `204 No Content`.
- **Request Body**:
```json
{
  "refreshToken": "a3f8c2e1b4d7..."
}
```
- **Responses**:
  - `204 No Content`: Token revoked (or was already revoked / unknown — idempotent).
  - `400 Bad Request`: Missing or blank `refreshToken` field.

---

### Feeders Management

#### 4. Register a New Feeder
- **Route**: `POST /feeders`
- **Access**: Authenticated
- **Request Body**:
```json
{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Living Room Feeder",
  "description": "Feeder near the balcony",
  "timezone": "America/Bahia"
}
```
- **Responses**:
  - `201 Created` with header `Location: /feeders/{id}`:
```json
{
  "id": "7fa8b9c0-1234-4567-89ab-cdef01234567",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "name": "Living Room Feeder",
  "description": "Feeder near the balcony",
  "timezone": "America/Bahia",
  "active": true,
  "createdAt": "2026-11-15T20:00:00Z",
  "updatedAt": null
}
```
  - `400 Bad Request`: Validation failure (empty name, unknown IANA timezone).
  - `409 Conflict`: Hardware `deviceId` is already paired with another feeder.

#### 3. List All Owned Feeders
- **Route**: `GET /feeders`
- **Access**: Authenticated
- **Response**: `200 OK`
```json
[
  {
    "id": "7fa8b9c0-1234-4567-89ab-cdef01234567",
    "deviceId": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Living Room Feeder",
    "description": "Feeder near the balcony",
    "timezone": "America/Bahia",
    "active": true,
    "createdAt": "2026-11-15T20:00:00Z",
    "updatedAt": null
  }
]
```

#### 4. Get Feeder Details by ID
- **Route**: `GET /feeders/{id}`
- **Access**: Authenticated
- **Response**: `200 OK` (returns single `FeederResponse`) or `404 Not Found`.

#### 5. Update Feeder Labels
- **Route**: `PUT /feeders/{id}`
- **Access**: Authenticated
- **Request Body**:
```json
{
  "name": "Kitchen Feeder",
  "description": "Moved to the kitchen area"
}
```
- **Response**: `200 OK` (returns updated `FeederResponse`).

#### 6. Update Feeder Timezone
- **Route**: `PUT /feeders/{id}/timezone`
- **Access**: Authenticated
- **Request Body**:
```json
{
  "timezone": "America/Sao_Paulo"
}
```
- **Response**: `200 OK` (returns updated `FeederResponse`) or `400 Bad Request` if invalid zone.

#### 7. Activate / Deactivate Feeder
- **Route**: `POST /feeders/{id}/activate` and `POST /feeders/{id}/deactivate`
- **Access**: Authenticated (Idempotent)
- **Response**: `200 OK` (returns updated `FeederResponse` with `active: true` or `active: false`).

#### 8. Delete Feeder
- **Route**: `DELETE /feeders/{id}`
- **Access**: Authenticated
- **Response**: `204 No Content`
  - Deleting a feeder automatically cascades and deletes all attached feeding schedules via database `ON DELETE CASCADE`.

---

### Feeding Schedules (Configs)

A feeder configuration is classified as either:
- `INTERVAL`: Runs periodically every `intervalMinutes`. `timeOfDay` must be `null`.
- `DAILY_TIME`: Runs at a wall-clock `timeOfDay` on specified `weekdays`. `intervalMinutes` must be `null`.

#### 9. Create Feeding Schedule
- **Route**: `POST /feeders/{feederId}/configs`
- **Access**: Authenticated

*Example 1: INTERVAL rule (every 6 hours / 360 minutes, 2 doses)*
```json
{
  "type": "INTERVAL",
  "intervalMinutes": 360,
  "timeOfDay": null,
  "weekdays": null,
  "dose": 2
}
```

*Example 2: DAILY_TIME rule (07:30 AM on weekdays, 1 dose)*
```json
{
  "type": "DAILY_TIME",
  "intervalMinutes": null,
  "timeOfDay": "07:30:00",
  "weekdays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
  "dose": 1
}
```

- **Responses**:
  - `201 Created` with header `Location: /feeders/{feederId}/configs/{id}`:
```json
{
  "id": "e8f9a0b1-2345-6789-01ab-cdef01234567",
  "type": "DAILY_TIME",
  "intervalMinutes": null,
  "timeOfDay": "07:30:00",
  "weekdays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
  "dose": 1,
  "lastRunAt": null,
  "active": true,
  "createdAt": "2026-11-15T20:05:00Z",
  "updatedAt": null
}
```
  - `400 Bad Request`: Interval and timeOfDay both set or both null, dose $< 1$ or $> 100$, or interval $< 1$ or $> 10080$.
  - `409 Conflict`: Schedule with exact same interval or timeOfDay already exists for this feeder.

#### 10. List Schedules of a Feeder
- **Route**: `GET /feeders/{feederId}/configs`
- **Access**: Authenticated
- **Response**: `200 OK` (returns list sorted chronologically: `INTERVAL` rules first, followed by `DAILY_TIME` rules ordered by time).

#### 11. Get Single Schedule
- **Route**: `GET /feeders/{feederId}/configs/{id}`
- **Access**: Authenticated
- **Response**: `200 OK` or `404 Not Found`.

#### 12. Update Schedule
- **Route**: `PUT /feeders/{feederId}/configs/{id}`
- **Access**: Authenticated
- **Request Body**: Same schema as `CreateFeederConfigRequest`.
- **Response**: `200 OK`.

#### 13. Activate / Deactivate Schedule
- **Route**: `POST /feeders/{feederId}/configs/{id}/activate` and `POST /feeders/{feederId}/configs/{id}/deactivate`
- **Access**: Authenticated (Idempotent)
- **Response**: `200 OK`.

#### 14. Delete Schedule
- **Route**: `DELETE /feeders/{feederId}/configs/{id}`
- **Access**: Authenticated
- **Response**: `204 No Content`.

---

### Hardware Device Interface

#### 15. Hardware Device Configuration Polling
- **Route**: `GET /devices/{deviceId}/config`
- **Access**: Public (Hardware authenticated by physical `deviceId` pairing)
- **Description**: Consumed by the microcontroller (e.g. ESP32, Arduino) inside the feeder to synchronize active schedules, local timezone, and current UTC server clock.
- **Responses**:
  - `200 OK`:
```json
{
  "active": true,
  "timezone": "America/Bahia",
  "serverTime": "2026-11-15T20:10:00.123456Z",
  "schedules": [
    {
      "id": "e8f9a0b1-2345-6789-01ab-cdef01234567",
      "type": "DAILY_TIME",
      "intervalMinutes": null,
      "timeOfDay": "07:30:00",
      "weekdays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
      "dose": 1
    }
  ]
}
```
  - `404 Not Found`: Device UUID is not paired with any registered feeder.

---

### Development Endpoints

*(Available exclusively when `SPRING_PROFILES_ACTIVE=dev`)*

- **Swagger UI Interactive Documentation**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI 3 JSON Specification**: `http://localhost:8080/v3/api-docs`
- **In-Memory H2 Database Console**: `http://localhost:8080/h2-console`
  - JDBC URL: `jdbc:h2:mem:feederdb`
  - Username: `feeder`
  - Password: *(blank)*

---

## Error Handling & RFC 9457

All API errors return RFC 9457 compliant `ProblemDetail` JSON representations:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "invalid request content",
  "instance": "/feeders"
}
```

Standard HTTP status mappings:
- `400 Bad Request`: Payload validation errors, invalid IANA timezone, malformed JSON.
- `401 Unauthorized`: Missing, invalid, or expired Bearer JWT / Google token / refresh token.
- `404 Not Found`: Feeder, schedule, or device not found (also returned when accessing foreign resources).
- `409 Conflict`: Device already registered, duplicate schedule parameter, or attempting to delete user with active feeders.

---

## Getting Started

### Prerequisites

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/)
- Alternatively, for local bare-metal compilation: Java 25 JDK and Git.

---

### Running in Development

#### Option A: Bare-metal with Maven (Default H2 Database)
```bash
# Clone the repository
git clone git@github.com:TridimensionalJR/AlimentadorAutomatico.git
cd AlimentadorAutomatico

# Run with dev profile (default)
./mvnw spring-boot:run
```
The application will boot on port `8080` with in-memory H2, throwaway OAuth test credentials, and Swagger UI available at `http://localhost:8080/swagger-ui.html`.

#### Option B: With Docker Compose (Development)
Spins up the application container and an optional local PostgreSQL database:
```bash
docker compose -f docker-compose.dev.yml up -d --build
# or simply:
docker compose up -d --build
```
To view logs:
```bash
docker compose logs -f app
```

---

### Running in Production with Docker

1. **Create and configure the production environment file**:
```bash
cp .env.example .env
```
Edit `.env` with your real production secrets:
```dotenv
SPRING_PROFILES_ACTIVE=prod
PORT=8080

POSTGRES_DB=feeder
POSTGRES_USER=feeder
POSTGRES_PASSWORD=YourStrongDatabasePassword123!

SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/feeder
SPRING_DATASOURCE_USERNAME=feeder
SPRING_DATASOURCE_PASSWORD=YourStrongDatabasePassword123!

GOOGLE_CLIENT_ID=123456789-abcdef.apps.googleusercontent.com
JWT_SECRET=production-secret-key-that-must-be-at-least-32-chars-long!
CORS_ALLOWED_ORIGINS=https://app.yourdomain.com
```

2. **Launch the production stack**:
```bash
docker compose -f docker-compose.prod.yml up -d --build
```

3. **Verify running containers**:
```bash
docker compose -f docker-compose.prod.yml ps
```

The database container includes an automated healthcheck. The application container waits for PostgreSQL to become fully ready before running Flyway migrations and starting the HTTP listener.

---

### Running Tests

Execute the comprehensive unit and integration test suite:
```bash
./mvnw clean test
```

---

## Database Migrations

Database evolution is version-controlled via Flyway scripts located in `src/main/resources/db/migration/`:

- `V1__create_users_table.sql`: Creates `users` table with unique Google ID and email constraints.
- `V2__create_feeders_table.sql`: Creates `feeders` table with unique hardware `device_id` and foreign key constraint (`ON DELETE RESTRICT`) referencing users.
- `V3__create_feeder_configs_table.sql`: Adds `timezone` to feeders and creates `feeder_configs` table with scheduling mutual exclusivity check constraints (`INTERVAL` vs `DAILY_TIME`), interval bounds ($1 \dots 10080$), dose limits ($1 \dots 100$), and cascade deletion (`ON DELETE CASCADE`).
- `V4__create_refresh_tokens_table.sql`: Creates `refresh_tokens` table storing opaque server-side refresh tokens. Includes a unique index on the 64-character hex token value, a foreign key to `users` with `ON DELETE CASCADE`, and a check constraint enforcing the 64-character token length.

---

## License

Copyright © 2026 **Tridimensional - Liga De Prototipagem E Desenvolvimento De Produto** (and its respective members). All Rights Reserved.

This software, source code, and associated files are **proprietary and confidential**. Any unauthorized copying, reproduction, modification, reverse engineering, distribution, sublicensing, or use in whole or in part without prior express written authorization from the copyright holders is strictly prohibited. See the [LICENSE](LICENSE) file for complete terms.
