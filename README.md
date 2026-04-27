# S3-Like Object Storage

A distributed S3-compatible object storage system in Java 21 + Spring Boot 3, with sharded metadata across 4 Postgres instances, 3-way replicated data nodes with quorum (W=2 of N=3) writes, JWT authentication, multipart uploads, versioning + delete markers, garbage collection, an Nginx load balancer, full Prometheus + Grafana observability, and a Java load test client. Everything runs via `docker compose`.

## Architecture

See `infra/diagrams/components.drawio` (open in [app.diagrams.net](https://app.diagrams.net)).

```
Client ──▶ nginx-lb ──▶ api-1, api-2 ──┬─▶ iam-1, iam-2 ──▶ postgres-iam
                                        │
                                        ├─▶ metadata-1, metadata-2 ──▶ postgres-meta-global
                                        │                              postgres-meta-{0..3}  (sharded)
                                        │
                                        ├─▶ placement-1, placement-2 ──▶ postgres-placement
                                        │
                                        └─▶ data-routing-1, data-routing-2
                                                          │  (W=2 of N=3 quorum)
                                                          ▼
                                            data-node-1 (Rack A)
                                            data-node-2 (Rack B)
                                            data-node-3 (Rack C)

gc ──(scheduled)──▶ placement, data-routing, data-nodes
prometheus ──(scrape)──▶ all services       grafana ──▶ prometheus
```

| Layer            | Service          | Replicas | Backing store                     |
|------------------|------------------|---------:|-----------------------------------|
| Load balancer    | nginx-lb         |        1 | —                                 |
| Public REST API  | api-1, api-2     |        2 | —                                 |
| Auth + ACL       | iam-1, iam-2     |        2 | postgres-iam                      |
| Metadata         | metadata-1, -2   |        2 | postgres-meta-global + 4 shards   |
| Placement        | placement-1, -2  |        2 | postgres-placement                |
| Data routing     | data-routing-1, -2 |      2 | —                                 |
| Data node        | data-node-1, -2, -3 |     3 | local volume (append-only chunks) |
| Garbage collector| gc               |        1 | —                                 |

## Quick Start

Prerequisites: Docker (with Compose v2), JDK 21, and `openssl` (or any way to generate a strong secret).

The Java jars are built on the host first, then each Docker image just copies the jar and runs it on a JRE base image. This avoids running 15 concurrent Gradle JVMs inside Docker (which OOMs on most laptops).

```bash
export JWT_SECRET=$(openssl rand -base64 48)
./infra/build.sh                 # gradle assemble + docker compose build
cd infra && docker compose up -d
```

Wait ~60–90 seconds for everything to become healthy:

```bash
docker compose ps
```

All services should be `healthy` (or `running` for nginx-lb / gc / prometheus / grafana).

### Smoke test

```bash
# 1. Login as the seeded loadtest user
TOKEN=$(curl -s -X POST http://localhost:8081/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"principalId":"loadtest","apiKey":"loadtest-secret-key"}' | jq -r .accessToken)

# Note: IAM is not exposed via nginx; from the host, talk to iam-1 directly.
# Inside the docker network, services use the IAM_BASE_URLS list.
docker compose exec api-1 wget -qO- http://iam-1:8081/auth/principal/loadtest

# 2. Create a bucket
curl -X PUT http://localhost:8080/photos -H "Authorization: Bearer $TOKEN"

# 3. Upload an object
curl -X PUT http://localhost:8080/photos/cat.jpg \
  -H "Authorization: Bearer $TOKEN" \
  --data-binary @/etc/hosts

# 4. Download the object
curl -s http://localhost:8080/photos/cat.jpg -H "Authorization: Bearer $TOKEN"

# 5. List objects
curl -s 'http://localhost:8080/photos?list-type=2' -H "Authorization: Bearer $TOKEN" | jq
```

> The IAM service is on port 8081 in the docker network. To talk to it from your host, either expose its port in `docker-compose.yml` or use `docker compose exec` as shown above. The seeded credentials live in `infra/sql/01-iam.sql`: `loadtest` / `loadtest-secret-key`.

### Run the load test

```bash
docker compose --profile test up load-client
```

This runs a 24-thread mixed workload (50% PUT, 30% GET, 5% multipart, 15% DELETE) for 120 seconds (configurable via env). On shutdown, the client prints a summary with throughput and mean/max latencies. Final metrics scrape into Prometheus before the container exits.

Adjust via env:

```bash
LOAD_DURATION_SEC=300 LOAD_THREADS=64 docker compose --profile test up load-client
```

## Observability

- Prometheus: <http://localhost:9090>
- Grafana:    <http://localhost:3000> (anonymous Admin login)
  - Open the **"S3-Like Object Storage — Overview"** dashboard.

Key metrics:

| Metric                                         | Meaning                              |
|------------------------------------------------|--------------------------------------|
| `api_objects_total{op}`                         | Per-op request counter               |
| `api_objects_duration_seconds`                  | Per-op latency histogram             |
| `api_bytes_in` / `api_bytes_out`                | Bytes ingested / served              |
| `api_auth_failures_total`                       | 401s on the API                      |
| `routing_write_quorum_total{result}`            | Quorum write OK vs fail              |
| `routing_write_duration_seconds`                | Quorum write latency histogram       |
| `routing_read_crc_mismatch_total`               | End-to-end CRC failures              |
| `datanode_chunks_writes_total`                  | Per-node write counter               |
| `datanode_chunks_bytes_written` (gauge)         | Cumulative bytes written per node    |
| `datanode_chunks_crc_mismatch_total`            | CRC failures detected at storage     |
| `gc_tombstone_reclaimed_total`                  | Objects reclaimed                    |
| `gc_compaction_chunks_total`                    | Chunk files compacted                |
| `gc_rereplication_repaired_total`               | Replicas restored                    |

## API Reference

All endpoints (except IAM `/auth/login`) require `Authorization: Bearer <jwt>`.

### Authentication (on `iam:8081`)

| Method | Path | Body / Notes |
|---|---|---|
| `POST` | `/auth/login` | `{principalId, apiKey}` → `{accessToken, expiresInSeconds, principalId}` |

### Buckets (on `nginx-lb:80`)

| Method | Path | Notes |
|---|---|---|
| `PUT`    | `/{bucket}`               | Create bucket; OWNER + READ + WRITE granted to caller |
| `PUT`    | `/{bucket}?versioning`    | Enable versioning |
| `GET`    | `/{bucket}?versioning`    | `{status: Enabled \| Suspended}` |
| `GET`    | `/{bucket}`               | Bucket info |
| `GET`    | `/{bucket}?list-type=2&prefix=&continuation-token=&max-keys=` | List current versions of objects |
| `DELETE` | `/{bucket}`               | Delete (must be empty) |
| `GET`    | `/`                       | List user's buckets |

### Objects

| Method | Path | Notes |
|---|---|---|
| `PUT`    | `/{bucket}/{key}`        | Upload object; supports `x-amz-meta-*` headers |
| `GET`    | `/{bucket}/{key}[?versionId=]` | Download |
| `HEAD`   | `/{bucket}/{key}[?versionId=]` | Headers only |
| `DELETE` | `/{bucket}/{key}`              | Insert delete marker (versioned) or hard-delete (unversioned) |
| `DELETE` | `/{bucket}/{key}?versionId=X`  | Hard delete a specific version |

### Multipart upload

| Method | Path | Notes |
|---|---|---|
| `POST`   | `/{bucket}/{key}?uploads`                         | Initiate; returns `UploadId` |
| `PUT`    | `/{bucket}/{key}?uploadId=X&partNumber=N`         | Upload part `N` |
| `POST`   | `/{bucket}/{key}?uploadId=X`                      | Complete; body `{Parts:[{PartNumber:1},{PartNumber:2}, …]}` |
| `DELETE` | `/{bucket}/{key}?uploadId=X`                      | Abort |

## Configuration

| Env var | Default | Where |
|---|---|---|
| `JWT_SECRET` | (required, ≥ 32 chars) | iam, api |
| `LOAD_DURATION_SEC` | 120 | load-client |
| `LOAD_THREADS` | 32 | load-client |
| `JWT_TTL_MINUTES` | 10 | iam |
| `WRITE_QUORUM` | 2 | data-routing |
| `REPLICATION_FACTOR` | 3 | placement |
| `CHUNK_SIZE_BYTES` | 1 GiB | data-node |
| `COMPACTION_DELETED_RATIO` | 0.4 | data-node |
| `TOMBSTONE_INTERVAL_MS` | 600000 | gc |
| `COMPACTION_INTERVAL_MS` | 3600000 | gc |
| `REREPLICATION_INTERVAL_MS` | 3600000 | gc |
| `TOMBSTONE_AGE_SECONDS` | 60 | gc |

## Module Map

```
common-api/             — DTOs and request/response records (no Spring deps)
common-core/            — Sharding hash, ULID, CRC32C, JWT, RoundRobinClient, ClusterMapLoader, Metrics auto-config
service-iam/            — JWT issuer + ACL store
service-metadata/       — Sharded object_versions + bucket + multipart
service-placement/      — Rendezvous placement with rack diversity
service-data-node/      — Append-only chunk storage with side-car index, CRC, compaction
service-data-routing/   — Quorum (W=2) coordinator + multipart assembly
service-api/            — Public S3-like REST API
service-gc/             — Scheduled tombstone reclamation, compaction trigger, re-replication
client-loadtest/        — Java CLI load test runner
infra/                  — Docker compose, Nginx, Prometheus, Grafana, SQL init, drawio diagram
```

## Development

```bash
./gradlew assemble        # build everything
./gradlew test            # run all unit tests
./gradlew :service-api:bootJar
```

Run a single service locally for debugging (point at a running compose stack's Postgres):

```bash
JWT_SECRET=$(openssl rand -base64 48) IAM_DB_HOST=localhost \
  ./gradlew :service-iam:bootRun
```

## Limitations / Out of scope

- Static cluster map; no dynamic data-node add/remove.
- Pure 3x replication; no erasure coding.
- JWT TTL = 10 minutes; no live revocation (out by expiry).
- Versioning toggles `Enabled`/`Suspended` (no purge-disable).
- Single Nginx instance (it's the load balancer).
- Postgres has no replication / failover; it's a single instance per role.

## License

Internal demo project. Not for production use as-is.
