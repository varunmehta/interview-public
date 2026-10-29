# CRM API Server

A simple CRM-style API server for managing contacts and files. Available in both Go and Java implementations with identical API interfaces.

## Implementations

- **Go**: Located in `go/` directory
- **Java**: Located in `java/` directory (Spring Boot)

Both implementations share the same SQLite database schema and provide identical REST API endpoints.

## Quick Start

### Go

```bash
cd go
make seed   # Seed the database with test data
make run    # Start the server
```

Or without make:

```bash
cd go
go run ./cmd/server --seed
go run ./cmd/server
```

### Java

```bash
cd java
make seed   # Seed the database with test data
make run    # Start the server
```

Or without make:

```bash
cd java
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.seed=true --app.startup-check=false"
./mvnw spring-boot:run
```

The server runs on `http://localhost:8080` by default.

## Authentication

All `/api/*` endpoints require a bearer token in the `Authorization` header:

```bash
curl -H "Authorization: Bearer user@example.com" http://localhost:8080/api/contacts
```

The token should be a valid email address. It serves as the user identifier.

## Rate Limiter

The server rate limits requests with an in-memory leaky bucket (`ratelimiter/LeakyBucketRateLimiter`). Each request adds one token, and the bucket drains steadily at `capacity / period`. Any request that would overflow the bucket gets `429 Too Many Requests` with a `Retry-After` header.

A single global bucket is shared by all clients. Every response carries `X-RateLimit-Limit`, `X-RateLimit-Remaining` and `X-RateLimit-Reset` headers. The `/health` endpoint is exempt from the limit.

To tune the limiter, see the Configuration section below.

## Endpoints

### Health

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Health check (no auth required) |

### Contacts

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/contacts` | List contacts (paginated) |
| `GET` | `/api/contacts/:id` | Get a contact |
| `POST` | `/api/contacts` | Create a contact |
| `PUT` | `/api/contacts/:id` | Update a contact |
| `DELETE` | `/api/contacts/:id` | Delete a contact |
| `POST` | `/api/contacts/import` | Bulk import contacts (JSON array, max 10k) |
| `GET` | `/api/contacts/export` | Export all contacts as CSV |

### Files

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/files` | List files |
| `POST` | `/api/files` | Upload a file (multipart form, max 100MB) |
| `GET` | `/api/files/:id` | Download a file |

### Reports

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/reports/activity` | Activity report (last 30 days by default) |

## Pagination

The `/api/contacts` endpoint uses cursor-based pagination. The response includes a `next_page_token` field when more results are available:

```json
{
  "contacts": [...],
  "next_page_token": "eyJjcmVhdGVkX2F0Ijoi..."
}
```

Pass the token as a query parameter to fetch the next page:

```bash
curl -H "Authorization: Bearer user@example.com" \
  "http://localhost:8080/api/contacts?page_token=eyJjcmVhdGVkX2F0Ijoi..."
```

## Examples

```bash
# List contacts (first page)
curl -H "Authorization: Bearer user@example.com" \
  http://localhost:8080/api/contacts?limit=10

# Create a contact
curl -X POST -H "Authorization: Bearer user@example.com" \
  -H "Content-Type: application/json" \
  -d '{"first_name":"Jane","last_name":"Doe","email":"jane@example.com","phone":"555-1234","company":"Acme"}' \
  http://localhost:8080/api/contacts

# Upload a file
curl -X POST -H "Authorization: Bearer user@example.com" \
  -F "file=@/path/to/file.pdf" \
  http://localhost:8080/api/files

# Bulk import
curl -X POST -H "Authorization: Bearer user@example.com" \
  -H "Content-Type: application/json" \
  -d '[{"first_name":"A","last_name":"B","email":"a@b.com","phone":"555","company":"X"}]' \
  http://localhost:8080/api/contacts/import

# Export contacts
curl -H "Authorization: Bearer user@example.com" \
  http://localhost:8080/api/contacts/export > contacts.csv
```

## Seeding Options

### Go

```bash
cd go
make seed                                      # Default: 10k contacts, 20 files
go run ./cmd/server --seed --contacts=50000    # Custom contact count
go run ./cmd/server --seed --files=100         # Custom file count
```

### Java

```bash
cd java
make seed                                                                              # Default: 10k contacts, 20 files
./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.seed=true --app.startup-check=false --contacts=50000"  # Custom contact count
```

To reset the database:

```bash
cd go && make reset   # Go
cd java && make reset # Java
```

## Benchmarks (Go only)

```bash
cd go
make bench
```

## Running Tests

### Java

```bash
cd java
make test        

./mvnw test
```

## Configuration

### Go

| Flag | Default | Description |
|------|---------|-------------|
| `--addr` | `:8080` | Server listen address |
| `--data` | `data` | Data directory for database and files |
| `--seed` | `false` | Seed database with test data |
| `--contacts` | `10000` | Number of contacts to seed |
| `--files` | `20` | Number of files to seed |

### Java

Configuration is in `src/main/resources/application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | `8080` | Server listen port |
| `app.data-dir` | `data` | Data directory for database and files |
| `app.max-upload-size` | `104857600` | Max file upload size (100MB) |
| `app.rate-limit.capacity`| `10` | Max requests the bucket holds (burst size and requests per period) |
| `app.rate-limit.period` | `1m` | Time to fully drain the bucket, as a Spring `Duration` (e.g. `30s`, `1m`, `1h`) |
