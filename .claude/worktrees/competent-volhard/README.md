# Service CP Crime Hearing Case Event Subscription

The Court Hearing Cases Event Subscription API publishes events relating to criminal court cases and manages client subscriptions for notifications.

## Software required (macOS)

- **Java 25** – required to build and run the service.  
  Check with `java -version`. Install via [SDKMAN](https://sdkman.io/), [Homebrew](https://brew.sh/) (`brew install openjdk@25`), or from [Adoptium](https://adoptium.net/).

- **Docker** – required to run PostgreSQL locally.  
  Install from [Docker Desktop for Mac](https://docs.docker.com/desktop/install/mac-install/). Check with `docker --version`.

- **PostgreSQL 15** – the service uses it for subscriptions and migrations.  
  Run it via Docker (see below).

- **direnv** (optional but recommended) – loads environment variables from `.envrc` when you `cd` into the project.  
  Install with `brew install direnv` and [hook it into your shell](https://direnv.net/docs/hook.html).

- **Gradle** – not required on your machine; the project uses the Gradle wrapper (`./gradlew`).

## Running the service on a local machine

### 1. Start PostgreSQL

The app expects PostgreSQL on `localhost:5432` with database `appdb` and user `postgres` (password `postgres`) unless you override with environment variables.

```bash
docker run -d --name postgres \
  -p 5432:5432 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=appdb \
  postgres:15
```

To stop and start the container later:

```bash
docker stop postgres
docker start postgres
```

### 2. Build and run the service

From the project root:

```bash
./gradlew clean build
./gradlew bootRun
```

The service starts on **http://localhost:4550** (override with `SERVER_PORT` if needed).

### 3. Override configuration with .envrc

The project includes an `.envrc.example` file with the environment variables the service uses. To override configuration for local development:

1. Copy the example to `.envrc` (do not commit `.envrc`; it is gitignored):
   ```bash
   cp .envrc.example .envrc
   ```
2. Edit `.envrc` with your overrides (e.g. database URL, password).
3. If you use **direnv**, run `direnv allow` in the project root. The variables will load when you `cd` into the project or run `direnv reload`.

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `4550` | HTTP port |
| `DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/appdb` | PostgreSQL JDBC URL |
| `DATASOURCE_USERNAME` | `postgres` | Database user |
| `DATASOURCE_PASSWORD` | `postgres` | Database password |
| `MATERIAL_CLIENT_URL` | `http://localhost:8081` | Material service base URL |
| `DOCUMENT_SERVICE_URL` | `http://localhost:8082` | Document service base URL |

If you don’t use direnv, you can still export these variables in your shell before running `./gradlew bootRun`.

### 4. Check the service is running

- Health: `curl http://localhost:4550/actuator/health`
- Build/version info: `curl http://localhost:4550/actuator/info`

---

## Contribute to this repository

Contributions are welcome. See [CONTRIBUTING.md](.github/CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the [MIT License](LICENSE).
