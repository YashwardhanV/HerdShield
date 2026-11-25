# Verification Status

This document separates checks actually executed during repository construction from checks configured for the target development or CI environment.

## Executed successfully

- ESLint completed with zero errors and zero warnings.
- TypeScript project compilation completed successfully.
- Vite produced a production frontend bundle.
- `frontend/package-lock.json` is committed, and local, container, and CI installs use `npm ci`.
- `git diff --check` reported no whitespace errors.
- The benchmark shell script passed `bash -n` syntax validation.
- Repository JSON files parsed successfully.
- `backend/pom.xml` parsed successfully as XML.

## Configured but not executed in the construction environment

The construction environment did not expose Maven, a working Java 21 runtime, Docker, or PostgreSQL. Consequently, it could not honestly execute:

- `mvn verify`, including the JUnit, Mockito, and Testcontainers suites;
- `docker compose config` or image builds;
- live REST experiments and benchmark runs.

GitHub Actions performs the backend test suite, frontend checks, Compose validation, and container builds on every push and pull request. On a workstation with Docker, run the complete local path with:

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/actuator/health
./scripts/run-benchmarks.sh
```

Do not fill benchmark or resume metrics until those commands produce actual raw results.
