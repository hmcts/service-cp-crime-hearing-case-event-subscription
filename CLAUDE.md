# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

We are building the API described in @AGENTS.md with Azure Service Bus notes in @README-servicebus.md. Read those files for architecture, tech stack, patterns, and database structure.

Keep replies extremely concise. No unnecessary filler, no long code snippets.

## Commands

```bash
./gradlew clean build
./gradlew test                                                                           # all tests; Testcontainers starts PostgreSQL automatically
./gradlew test --tests "uk.gov.hmcts.cp.subscription.services.NotificationServiceTest"  # single test class
./gradlew dockerTest          # integration tests against full docker-compose stack (Service Bus emulator + SQL Edge)
./gradlew bootRun             # requires PostgreSQL on localhost:5432 — see README.md for docker run command
./gradlew pmdMain             # PMD static analysis
./gradlew jacocoTestReport    # coverage report → build/reports/jacoco/
```