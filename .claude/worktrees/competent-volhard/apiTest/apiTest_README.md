# API Test Module

This module contains api tests that run against a Docker containerized version of the application. 
The tests make HTTP calls to verify the API endpoints are working correctly.

## Overview

The `apiTest` is a shell script that will:
- Build spring boot jarfile
- Build docker image
- Spin up the docker compose stack with required docker images
- Run apiTest gradle test

## Prerequisites

Before running the tests, ensure you have the following installed and configured:

### Required Software

1. **Java 25** (or higher)
   - Verify installation: `java -version`
   - Should show version 25 or higher

2. **Docker Desktop** (or Docker Engine)
   - Verify installation: `docker --version`
   - Docker must be running (check with `docker ps`)
   - Docker Compose V2 must be available: `docker compose version`

### System Requirements

- At least 4GB of free RAM (Docker containers need memory)
- Ports available: `8082` (application), `5432` (PostgreSQL), `9999` (WireMock)
- Sufficient disk space for Docker images

## Running Tests

### From the Root Directory

```bash
# Navigate to the apiTest directory
cd apiTest

# Build and run api tests
./build-and-run-apitest.sh
```

### What Happens When You Run Tests

1. **Builds the root project's bootJar** - The application JAR is built first
2. **Builds Docker images** - Creates the application Docker image
3. **Starts Docker containers**:
   - PostgreSQL database (port 5432)
   - Application server (port 8082)
   - WireMock server (port 9999)
   - Service Bus
4. **Runs tests** - Executes all test classes
5. **Stops and removes containers** - Cleanup after tests complete

## Test Reports

After running tests, you can view the results in several formats:

### HTML Test Report (Recommended)

**Location:** `apiTest/build/reports/tests/test/index.html`

The HTML report includes:
- Test summary (total, passed, failed, skipped)
- Individual test results with execution times
- Stack traces for failed tests
- Package and class-level summaries

## Troubleshooting

**Common causes:**
- Application configuration errors
- Database connection issues
- Missing environment variables
- Port conflicts

### Issue: Port already in use

**Solution:** Stop any services using the required ports:
```bash
# Check what's using port 8082
lsof -i :8082

# Check what's using port 5432
lsof -i :5432

# Stop conflicting services or change ports in docker-compose.yml
```

### Issue: Cannot connect to database

**Solution:**
- Ensure the database container is healthy: `docker ps` should show "Healthy"
- Check database logs: `docker-compose -f docker-compose.yml logs db`
- Verify connection string in `docker-compose.yml` matches database configuration


## Test Configuration

### Environment Variables

Tests use the following default configuration:
- Application base URL: `http://localhost:8082` (can be overridden with `app.baseUrl` system property)
- Database: PostgreSQL on port 5432
- WireMock: Port 9999


