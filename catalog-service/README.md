# 📚 Catalog Service

The Catalog Service is the core microservice of VellumHub responsible for managing the book catalog, user reading lists, book collections, and the book approval workflow.

## 🎯 Overview

This service handles all book-related operations including CRUD operations, cover management, user-created book collections, reading progress tracking, and the community-driven book submission process.

**Port:** `8081`  
**Database:** `catalog_db` (PostgreSQL)

---

## 📋 Features

### 1. **Book Management** 📖
- Complete CRUD operations for books
- Rich metadata support (title, author, publisher, ISBN, genres, page count, release year)
- Book versioning with optimistic locking
- Soft delete support
- Bulk operations for efficient data retrieval

### 2. **Book Cover Management** 🖼️
- Upload and store book cover images
- Retrieve individual covers
- Bulk cover retrieval (Base64 encoded) to solve N+1 query problems
- Cover storage abstraction via ports/adapters pattern

### 3. **Book Lists & Collections** 📋
- Create custom book collections
- Public and private lists
- Collaborative features with role-based memberships (ADMIN/MEMBER)
- Like/unlike functionality
- Advanced filtering (by title, description, genres, owner, type)

### 4. **Book Submission & Approval** ✋
- Users can submit new books for approval
- Admins review and approve/reject submissions
- Duplicate detection
- Approved books are automatically added to the catalog

### 5. **Reading Progress Tracking** 📈
- Track reading status: `TO_READ`, `READING`, `COMPLETED`
- Update current page progress
- View personal reading list
- Remove books from reading list

---

## 🏗️ Architecture

### Modules

The service is organized into four main modules following Domain-Driven Design principles:

#### **book** Module
- **Controllers:** `BookController`, `BookCoverFileController`
- **Handlers:** 15 specialized handlers for various operations
- **Domain Logic:** Book entity with genres, versioning, audit fields
- **Ports:** `BookRepository`, `BookEventProducer`, `BookCoverStorage`

#### **book_list** Module
- **Controller:** `BookListController`
- **Use Cases:** CreateBookList, GetAllBookList, GetBookListById, UpdateBookList, DeleteBookList
- **Domain Logic:** BookList entity with memberships, likes, and role-based access control
- **Entities:** `BookList`, `BookListMembership`, `BookListLike`

#### **book_request** Module
- **Controller:** `BookRequestController`
- **Use Cases:** CreateBookRequest, ApproveBookRequest, DeleteBookRequest, GetAllBookRequest
- **Domain Logic:** Request validation and approval orchestration
- **Entity:** `BookRequest` with similar structure to Book

#### **book_progress** Module
- **Controller:** `BookProgressController`
- **Handlers:** DefineBookStatus, UpdateBookProgress, DeleteBookProgress, GetReadingList
- **Domain Logic:** Status transitions and progress validation
- **Entity:** `BookProgress` linking users to books with status and page count

### Architecture Patterns

- **Hexagonal Architecture:** Clear separation between domain logic and infrastructure
- **Layered Architecture:** Controller → Service → Handler → Use Case → Repository
- **Event-Driven:** Publishes book lifecycle events to Kafka
- **DDD Patterns:** Aggregates, value objects, domain events

---

## 🔌 API Endpoints

### Books

```http
GET    /books                  # List all books (paginated)
GET    /books/{id}             # Get book by ID
POST   /books                  # Create book (ADMIN only)
PUT    /books/{id}             # Update book (ADMIN only)
DELETE /books/{id}             # Delete book (ADMIN only)
POST   /books/bulk             # Get multiple books by IDs (internal)
POST   /books/{id}/cover       # Upload book cover (ADMIN only)
GET    /books/{id}/cover       # Get book cover image
POST   /books/covers/bulk      # Get multiple covers (Base64, bulk)
```

### Book Lists

```http
POST   /book/list                 # Create new book list
GET    /book/list                 # Get all lists with filters (paginated)
GET    /book/list/{bookListId}    # Get specific list by ID
PUT    /book/list/{bookListId}    # Update list (owner/admin only)
DELETE /book/list/{bookListId}    # Delete list (owner only)
```

**Query Parameters for GET /book/list:**
- `title` - Filter by title (case-insensitive)
- `description` - Filter by description
- `genres` - Filter by genres (comma-separated)
- `owner` - Filter by owner user ID
- `type` - Filter by type (PUBLIC/PRIVATE)
- `pageNumber` - Page number (default: 0)
- `pageSize` - Page size (default: 10)

### Book Requests

```http
POST   /book-requests                    # Submit book for approval
GET    /book-requests                    # List all requests (ADMIN, paginated)
POST   /book-requests/{id}/approve       # Approve request (ADMIN only)
```

### Book Progress

```http
POST   /book-progress/{bookId}/status    # Set reading status
PUT    /book-progress/{bookId}/progress  # Update current page
DELETE /book-progress/{bookId}           # Remove from reading list
GET    /book-progress/reading-list       # Get user's reading list
```

**Reading Status Values:**
- `TO_READ` - Book is on the reading list
- `READING` - Currently reading
- `COMPLETED` - Finished reading

---

## 📊 Database Schema

### Main Tables

#### books
- `id` (UUID, PK) - Unique book identifier
- `title`, `description`, `author`, `publisher` - Metadata
- `release_year`, `page_count`, `isbn` - Book details
- `cover_url` - Cover image reference
- `version` - Optimistic locking
- `created_at`, `updated_at`, `deleted_at` - Audit timestamps

#### tb_book_genre
- Element collection for book genres
- Links books to multiple genres

#### book_lists
- `id` (UUID, PK)
- `title`, `description` - List metadata
- `type` (PUBLIC/PRIVATE)
- `user_owner` (UUID) - List creator
- `created_at`, `updated_at`

#### book_lists_books
- Many-to-many relationship between lists and books

#### book_list_memberships
- Links users to book lists with roles (ADMIN/MEMBER)

#### book_list_likes
- Tracks user likes on book lists

#### book_progress
- `id` (Long, PK)
- `user_id` (UUID) - User reference
- `book_id` (UUID) - Book reference
- `reading_status` (ReadingStatus enum)
- `current_page` (int)

#### book_requests
- Similar structure to books table
- Stores pending book submissions

---

## 🔄 Event Publishing

The service publishes events to Kafka for inter-service communication:

### Produced Events

**Topic:** `book-events`
- `CreateBookEvent` - Published when a new book is created
- `UpdateBookEvent` - Published when a book is updated
- `DeleteBookEvent` - Published when a book is deleted

These events are consumed by the **Recommendation Service** to maintain synchronized book features and embeddings.

---

## 🛠️ Technology Stack

- **Java 21**
- **Spring Boot 3.4.2**
- **Spring Data JPA** - Database access
- **Spring Security** - Authentication/authorization
- **PostgreSQL** - Relational database
- **Apache Kafka** - Event streaming
- **OpenAPI 3.0 (Swagger)** - API documentation
- **Maven** - Build tool

---

## 🚀 Running the Service

### Standalone (Development)

```bash
cd catalog-service
./mvnw spring-boot:run
```

The service will start on port `8081`.

### With Docker Compose

From the project root:

```bash
docker-compose up catalog-service
```

### Environment Variables

Required configuration (typically in `.env` or `application.yml`):

```properties
# Database
POSTGRES_USER=admin
POSTGRES_PASSWORD=your-password

# Service Port
SERVER_PORT=8081

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Security (JWT validation)
JWT_KEY=your-secret-key
```

---

## 📝 API Documentation

When the service is running, access the Swagger UI at:

```
http://localhost:8081/swagger-ui.html
```

---

## 🔐 Security

### Authentication

All endpoints (except health checks) require JWT authentication.

### Authorization

- **ADMIN role required:**
  - Create, update, delete books
  - Upload book covers
  - Approve book requests
  - View all book requests

- **USER role allowed:**
  - View books and lists
  - Create personal book lists
  - Submit book requests
  - Manage own reading progress

### Access Control for Book Lists

- **Owner:** Full control over their lists (update, delete)
- **ADMIN members:** Can manage list content
- **MEMBER:** Can view and interact with the list
- **PUBLIC lists:** Viewable by all users
- **PRIVATE lists:** Only visible to members

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

---

## 📈 Monitoring

### Health Check

```bash
curl http://localhost:8081/actuator/health
```

### Metrics

```bash
curl http://localhost:8081/actuator/metrics
```

---

## 🤝 Contributing

This service follows the established patterns:

1. **Controllers** handle HTTP requests and validation
2. **Services** orchestrate use cases
3. **Handlers** contain domain logic
4. **Use Cases** represent business operations
5. **Repositories** abstract data access
6. **Ports** define interfaces for external dependencies
7. **Adapters** implement port interfaces

When adding features:
- Follow the existing module structure
- Use DTOs for API contracts
- Implement proper error handling
- Add unit tests
- Update this README

---

## 📚 Related Services

- **User Service** - Provides user authentication and profiles
- **Engagement Service** - Receives book ratings
- **Recommendation Service** - Consumes book events for recommendations

---

**Catalog Service** - The heart of VellumHub's book ecosystem 📚
