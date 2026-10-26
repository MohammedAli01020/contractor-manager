# Observability & Production Monitoring

**Branch:** `feature/platform-owner-activation`  
**Status:** Planned; this document records agreed scope and is not evidence of implementation.  
**Roadmap:** [Product Roadmap](PRODUCT_ROADMAP.md), Phase 10.

## Goal

Measure and diagnose production behavior: request volume, response latency, errors, runtime and database health, and availability. Keep infrastructure monitoring distinct from business analytics.

## Proposed stack

- Spring Boot Actuator + Micrometer for application health and metrics.
- Prometheus for time-series metrics.
- Grafana for dashboards and alerts.
- Grafana Loki for centralized logs when useful.
- OpenTelemetry tracing when service boundaries or diagnosis justify it.
- Java Flight Recorder (JFR) for targeted JVM profiling.
- Keep the initial deployment compatible with the existing Docker Compose model. Do not add Kubernetes solely for monitoring.
- Verify tool/dependency compatibility with Spring Boot 2.7.18 and Java 11 before implementation.

## Metrics

### HTTP/API
- Request rate and counts over time.
- Response status classes and 4xx/5xx error rates.
- Latency distributions, especially p50, p95, and p99.
- Slow endpoints and timeout counts.
- Use bounded-cardinality route templates. Do not use raw URLs, arbitrary query parameters, tokens, user IDs, or tenant IDs as metric labels.

### Runtime and infrastructure
- JVM heap/non-heap, garbage collection, threads, and process CPU.
- Container CPU/memory and restart counts where available.
- Disk capacity, application availability, and health-check status.

### MySQL
- Database reachability and connection-pool active/idle/pending connections.
- Connection acquisition timeouts and failures.
- Safe slow-query investigation without exposing sensitive parameter values.

### Business activity
- Track aggregate operational events such as shifts created, completed, or cancelled separately from HTTP request counts; one business action can cause multiple HTTP requests.
- Enforce tenant privacy and role permissions for detailed breakdowns.

## Dashboards

1. System overview: availability, request rate, p95 latency, error rate, alerts.
2. API performance: endpoint traffic, latency, errors, and timeouts.
3. JVM/containers: memory, CPU, GC, threads, restarts, disk pressure.
4. Database: connection-pool pressure and connection failures.
5. Business activity: aggregate operational counts only where useful.

A Platform Owner summary may show safe platform-wide aggregate counts and system health. It must not automatically grant access to individual tenant logs, personal information, or financial data.

## Logs, tracing, and profiling

- Use structured logs with timestamp, severity, service/environment, and a generated or validated request/correlation ID.
- Carry the correlation ID through relevant application layers; use trace IDs if tracing is added.
- Centralize logs with Loki when cross-container search is needed.
- Never log passwords, access/refresh tokens, TOTP secrets, recovery codes, or unnecessary personal/financial data.
- Use OpenTelemetry tracing when needed to locate latency across services or dependencies.
- Use JFR or an approved profiler to investigate reproducible CPU, allocation, lock, or latency problems. Measure overhead and avoid collecting sensitive data.

## Alerts and operations

Add actionable, environment-appropriate alerts for sustained 5xx/error spikes, high p95 latency/timeouts, app or database unavailability, connection-pool exhaustion, low disk space, sustained resource pressure, restart loops, and failed health checks. Establish thresholds from a baseline rather than treating arbitrary values as universal. Test alert delivery and document who responds and the first diagnostic steps.

## Security and retention

- Keep Actuator management endpoints, Prometheus, Grafana, Loki, and tracing systems private or strongly authenticated.
- Expose only deliberately selected health information publicly.
- Separate dashboard/log access by role and operational need.
- Define retention, storage limits, and access roles before production; monitor the monitoring stack's own resource use.
- Do not put secrets or high-cardinality identifiers into metric labels.

## Delivery sequence

1. Inspect Docker Compose, Spring configuration, dependencies, security filters, and deployment topology.
2. Add compatible Actuator/Micrometer instrumentation and explicitly configure safe management exposure.
3. Add Prometheus and a minimal Grafana dashboard to development/test Compose.
4. Verify request, error, latency, JVM, and connection-pool metrics against controlled requests.
5. Add structured logs and request correlation; add Loki if justified.
6. Add actionable alerts and verify delivery.
7. Add tracing and targeted profiling where needed.
8. Load-test representative endpoints and measure instrumentation overhead before production acceptance.

## Acceptance checklist

- [ ] Dashboard displays real metrics from the running app, not sample values.
- [ ] Request rate, status/error rate, and p50/p95/p99 latency are visible.
- [ ] JVM, container/host, and MySQL connection-pool indicators are available.
- [ ] A controlled error can be found in logs and correlated with its request ID.
- [ ] Health checks detect controlled app/database failure scenarios.
- [ ] Alerts are tested and actionable.
- [ ] Monitoring UIs and management endpoints are not publicly exposed without suitable access controls.
- [ ] Secrets and sensitive tenant data do not appear in logs, labels, or dashboards.
- [ ] Retention, storage caps, access roles, and operational ownership are documented.
- [ ] Load testing confirms acceptable monitoring overhead.
- [ ] A runbook explains investigation of slow requests, elevated errors, database pressure, and resource exhaustion.

**Important:** No component is considered implemented until the current branch is inspected, changes are committed, and relevant tests/deployment checks are run and recorded.
