# API Test Module

This module contains api tests that run against a Docker containerized version of the application. 
The tests make HTTP calls to verify the API endpoints are working correctly.

## Overview

The `apiTest` module is a standalone Gradle project that:
- Runs api tests against the application running in Docker containers
- Automatically manages Docker containers (starts before tests, stops after)
- Generates HTML and XML test reports

## Prerequisites

Before running the tests, ensure you have the following installed and configured:

### Required Software

1. **Java 21** (or higher)
   - Verify installation: `java -version`
   - Should show version 21 or higher

2. **Docker Desktop** (or Docker Engine)
   - Verify installation: `docker --version`
   - Docker must be running (check with `docker ps`)
   - Docker Compose V2 must be available: `docker compose version`

3. **Gradle** (or use the Gradle wrapper)
   - The project includes `gradlew` wrapper, so Gradle installation is optional
   - Verify wrapper: `./gradlew --version`

### System Requirements

- At least 4GB of free RAM (Docker containers need memory)
- Ports available: `8082` (application), `5432` (PostgreSQL), `9999` (WireMock)
- Sufficient disk space for Docker images

## Running Tests

### From the Root Directory

```bash
# Navigate to the apiTest directory
cd apiTest

# Run all tests
../gradlew test

# Run tests with more verbose output
../gradlew test --info

# Run tests with debug output
../gradlew test --debug
```

### From the apiTest Directory

```bash
# If you're already in the apiTest directory
../gradlew test
```

### What Happens When You Run Tests

1. **Builds the root project's bootJar** - The application JAR is built first
2. **Builds Docker images** - Creates the application Docker image
3. **Starts Docker containers**:
   - PostgreSQL database (port 5432)
   - Application server (port 8082)
   - WireMock server (port 9999)
4. **Runs tests** - Executes all test classes
5. **Stops and removes containers** - Cleanup after tests complete

### Running Specific Tests

```bash
# Run a specific test class
../gradlew test --tests "RootApiTest"

# Run a specific test method
../gradlew test --tests "RootApiTest.root_endpoint_should_be_ok"
```

## Test Reports

After running tests, you can view the results in several formats:

### HTML Test Report (Recommended)

**Location:** `apiTest/build/reports/tests/test/index.html`

The HTML report includes:
- Test summary (total, passed, failed, skipped)
- Individual test results with execution times
- Stack traces for failed tests
- Package and class-level summaries

### JUnit XML Reports

**Location:** `apiTest/build/test-results/test/`

These XML reports are useful for CI/CD integration.

## Troubleshooting

### Issue: "Could not start Gradle Test Executor 1: Failed to load JUnit Platform"

**Solution:** This should be resolved with the current configuration. If you see this error:
1. Clean the build: `../gradlew clean`
2. Rebuild: `../gradlew build`

### Issue: "no main manifest attribute, in /app/apiTest-0.0.999.jar"

**Solution:** This means the Docker build context is wrong. Ensure:
1. The `docker-compose.yml` has `context: ..` (builds from root directory)
2. The root project's `bootJar` is built before Docker build
3. Run: `../gradlew buildRootBootJar` manually if needed

### Issue: Container exits with code 1

**Check application logs:**
```bash
# View app container logs
docker logs apitest-app-1

# Or using docker-compose
docker-compose -f docker-compose.yml logs app

# View all container logs
docker-compose -f docker-compose.yml logs
```

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

### Issue: IntelliJ not recognizing Java code

Change .idea/gradle.xml to add apiTest module 

```.idea/gradle.xml
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="GradleMigrationSettings" migrationVersion="1" />
  <component name="GradleSettings">
    <option name="linkedExternalProjectsSettings">
      <GradleProjectSettings>
        <option name="externalProjectPath" value="$PROJECT_DIR$" />
        <option name="gradleJvm" value="#JAVA_HOME" />
        <option name="modules">
          <set>
            <option value="$PROJECT_DIR$" />
          </set>
        </option>
      </GradleProjectSettings>
      <GradleProjectSettings>
        <option name="externalProjectPath" value="$PROJECT_DIR$/apiTest" />
        <option name="gradleJvm" value="#JAVA_HOME" />
        <option name="modules">
          <set>
            <option value="$PROJECT_DIR$/apiTest" />
          </set>
        </option>
      </GradleProjectSettings>
    </option>
  </component>
</project>
```

**Solution:**
1. **Refresh Gradle Project:**
   - Open Gradle tool window (View → Tool Windows → Gradle)
   - Click the refresh button (circular arrow icon)
   - Or: File → Reload Gradle Project

2. **Mark Directory as Test Source:**
   - Right-click on `apiTest/src/test/java`
   - Mark Directory as → Test Sources Root

3. **Reimport Project:**
   - File → Invalidate Caches... → Invalidate and Restart

4. **Verify Module Recognition:**
   - File → Project Structure (⌘; on Mac)
   - Check that `apiTest` module appears under Modules

## Manual Container Management

If you need to manually manage containers:

```bash
# Start containers without running tests
docker-compose -f docker-compose.yml up -d

# Stop containers
docker-compose -f docker-compose.yml down

# View container logs
docker-compose -f docker-compose.yml logs -f app

# Check container status
docker-compose -f docker-compose.yml ps

# Rebuild containers
docker-compose -f docker-compose.yml build --no-cache
```

## Test Configuration

### Environment Variables

Tests use the following default configuration:
- Application base URL: `http://localhost:8082` (can be overridden with `app.baseUrl` system property)
- Database: PostgreSQL on port 5432
- WireMock: Port 9999

### Customizing Test Execution

You can override the application URL:
```bash
../gradlew test -Dapp.baseUrl=http://localhost:8080
```

