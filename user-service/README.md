# 👤 User Service

The User Service is responsible for user authentication, registration, and profile management in VellumHub. It provides JWT-based authentication with support for traditional email/password login and Google OAuth.

## 🎯 Overview

This service manages user accounts, authentication tokens, and user preferences. It serves as the identity provider for the entire VellumHub platform.

**Port:** `8084`  
**Database:** `user_db` (PostgreSQL)

---

## 📋 Features

### 1. **User Registration** 📝
- Email/password registration with validation
- Automatic user preference creation
- Password strength validation
- Email uniqueness enforcement
- Support for USER and ADMIN roles

### 2. **Authentication** 🔐
- **Email/Password Login** - Traditional authentication
- **Google OAuth** - Social login integration
- **JWT Token Generation** - Stateless authentication
- Token-based session management
- Role-based access control

### 3. **User Management** 👥
- Complete CRUD operations for users
- Profile retrieval
- User listing with pagination
- Soft delete support
- Self-service profile access (`/users/me`)

### 4. **User Preferences** ⭐
- Genre preference tracking
- Preference event publishing to Kafka
- Integration with recommendation engine

---

## 🏗️ Architecture

### Modules

The service is organized into three main modules:

#### **auth** Module
- **Controller:** `AuthController`
- **Handlers:** RegisterUser, LoginUser, LoginExternal
- **Ports:** 
  - `AuthenticatorPort` - Authentication abstraction
  - `ExternalVerification` - OAuth verification
  - `TokenProvider` - JWT token management
- **Features:**
  - Password validation via `PasswordValidatorAdapter`
  - Spring Security integration
  - Google OAuth verification

#### **user** Module
- **Controller:** `UserController`
- **Handlers:** CreateUser, GetUser, GetAllUser, UpdateUser, DeleteUser
- **Features:**
  - User CRUD operations
  - Email uniqueness validation
  - Kafka event publishing for preferences
  - Pagination support

#### **user_preference** Module
- **Handler:** `CreateUserPreferenceHandler`
- **Events:** `CreateUserPreferenceEvent`
- **Features:**
  - Genre preference management
  - Event-driven preference updates

### Architecture Patterns

- **Modular Monolith:** Clear module boundaries
- **Hexagonal Architecture:** Ports and adapters for external dependencies
- **Event-Driven:** Kafka events for preference updates
- **Token-Based Authentication:** Stateless JWT approach

---

## 🔌 API Endpoints

### Authentication

```http
POST   /auth/register    # Register new user
POST   /auth/login       # Login with email/password
POST   /auth/google      # Login with Google OAuth
```

#### Register Request
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "SecurePass123!"
}
```

#### Login Request
```json
{
  "email": "john@example.com",
  "password": "SecurePass123!"
}
```

#### Login Response
```json
{
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 86400000
}
```

#### Google OAuth Request
```json
{
  "idToken": "google-id-token-here"
}
```

### User Management

```http
POST   /users          # Create user (ADMIN only)
GET    /users          # List all users (paginated)
GET    /users/{id}     # Get user by ID
GET    /users/me       # Get authenticated user's profile
PUT    /users/{id}     # Update user (ADMIN only)
DELETE /users/{id}     # Delete user (ADMIN only)
```

**Query Parameters for GET /users:**
- `pageNumber` - Page number (default: 0)
- `pageSize` - Page size (default: 10)

---

## 📊 Database Schema

### Main Tables

#### tb_users
- `id` (UUID, PK) - Unique user identifier
- `name` (String, required) - User's full name
- `email` (String, unique, required) - User's email
- `password` (String, required) - Hashed password (BCrypt)
- `role` (RoleUser enum) - USER or ADMIN
- `active` (boolean) - Account status
- `version` (Long) - Optimistic locking
- `created_at`, `updated_at`, `deleted_at` - Audit timestamps

#### user_preferences
- Links users to their preferred book genres
- Published as Kafka events for the recommendation service

### Enums

#### RoleUser
- `USER` - Standard user with read/write permissions
- `ADMIN` - Administrator with full system access

---

## 🔄 Event Publishing

The service publishes events to Kafka for inter-service communication:

### Produced Events

**Topic:** `user-preference-events`
- `CreateUserPreferenceEvent` - Published when user preferences are created/updated

These events are consumed by the **Recommendation Service** to initialize and update user profile vectors.

---

## 🛠️ Technology Stack

- **Java 21**
- **Spring Boot 3.4.2**
- **Spring Security** - Authentication/authorization
- **Spring Data JPA** - Database access
- **PostgreSQL** - Relational database
- **Apache Kafka** - Event streaming
- **JWT** - JSON Web Tokens for authentication
- **Google OAuth Client** - Google authentication integration
- **BCrypt** - Password hashing
- **Maven** - Build tool

---

## 🚀 Running the Service

### Standalone (Development)

```bash
cd user-service
./mvnw spring-boot:run
```

The service will start on port `8084`.

### With Docker Compose

From the project root:

```bash
docker-compose up user-service
```

### Environment Variables

Required configuration (typically in `.env` or `application.yml`):

```properties
# Database
POSTGRES_USER=admin
POSTGRES_PASSWORD=your-password

# Service Port
SERVER_PORT=8084

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# JWT Configuration
JWT_KEY=your-256-bit-secret-key-here
JWT_EXPIRATION=86400000  # 24 hours in milliseconds

# Google OAuth (optional)
GOOGLE_CLIENT_ID=your-google-client-id
```

---

## 🔐 Security

### Password Requirements

Passwords must meet the following criteria:
- Minimum length (typically 8 characters)
- Validation enforced by `PasswordValidatorAdapter`

### JWT Token

- **Algorithm:** HS256 (HMAC with SHA-256)
- **Expiration:** Configurable (default: 24 hours)
- **Claims:** Contains user ID, email, and role
- **Header:** `Authorization: Bearer <token>`

### Role-Based Access Control

Protected endpoints use `@PreAuthorize` annotations:

```java
@PreAuthorize("hasRole('ADMIN')")  // Admin only
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")  // Any authenticated user
```

### Google OAuth Flow

1. Client obtains Google ID token
2. Service verifies token with Google
3. Service creates/retrieves user account
4. Service issues JWT token

---

## 🧪 Testing

Run the test suite:

```bash
./mvnw test
```

The service includes unit tests using:
- JUnit 5
- Mockito
- Spring Boot Test
- Spring Security Test

---

## 📈 Monitoring

### Health Check

```bash
curl http://localhost:8084/actuator/health
```

### Metrics

```bash
curl http://localhost:8084/actuator/metrics
```

---

## 🔍 Common Use Cases

### 1. New User Registration

```bash
curl -X POST http://localhost:8084/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Alice Smith",
    "email": "alice@example.com",
    "password": "SecurePassword123!"
  }'
```

### 2. User Login

```bash
curl -X POST http://localhost:8084/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "SecurePassword123!"
  }'
```

### 3. Get Own Profile

```bash
curl -X GET http://localhost:8084/users/me \
  -H "Authorization: Bearer <your-jwt-token>"
```

### 4. Create Admin User (via SQL)

```sql
INSERT INTO tb_users (id, name, email, password, role, active, created_at, updated_at)
VALUES (
  gen_random_uuid(),
  'Admin',
  'admin@vellumhub.com',
  '$2a$10$...',  -- BCrypt hash of password
  'ADMIN',
  true,
  NOW(),
  NOW()
);
```

---

## 🤝 Contributing

This service follows established patterns:

1. **Controllers** handle HTTP requests and validation
2. **Handlers** contain authentication and user management logic
3. **Services** orchestrate operations
4. **Ports** define interfaces for external dependencies
5. **Adapters** implement port interfaces (Spring Security, JWT, Google OAuth)

When adding features:
- Follow the existing module structure
- Use DTOs for API contracts
- Implement proper validation
- Add unit tests
- Update this README

---

## 📚 Related Services

- **Catalog Service** - Uses user authentication for book operations
- **Engagement Service** - Validates user tokens for ratings
- **Recommendation Service** - Consumes user preference events

---

## 🐛 Troubleshooting

### "Email already exists" Error

Each email must be unique in the system. Use a different email or update the existing user.

### JWT Token Expired

Tokens expire after the configured duration. Obtain a new token by logging in again.

### Google OAuth Fails

Ensure:
- Valid Google Client ID is configured
- ID token is recent (tokens expire quickly)
- Google account has verified email

### Cannot Access Admin Endpoints

Verify:
- User has `ADMIN` role in the database
- JWT token contains correct role claim
- Token is not expired

---

**User Service** - Your gateway to VellumHub 🔐
