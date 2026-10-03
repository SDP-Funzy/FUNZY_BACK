# SDP1617-project-BACK

## 로컬 개발 환경

### 요구 사항
- JDK 21

기본 프로필(`local`)은 H2(파일 기반, `.data/`)를 사용해서 별도 설치/실행 없이 바로 개발할 수 있고 재시작해도 데이터가 유지됩니다.

### 1. 애플리케이션 실행
```bash
./gradlew bootRun
```

### 2. 테스트
```bash
./gradlew test
```

## 프로필

| 프로필 | 용도 | DB |
|---|---|---|
| `local` (기본) | 로컬 개발 | H2 파일 기반 (postgres 호환 모드, `.data/`) |
| `prod` | CI / 운영 | PostgreSQL (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수) |

CI는 실제 PostgreSQL(service container) 위에서 `prod` 프로필로 빌드/테스트를 실행해 postgres 전용 문법·기능 차이를 PR 단계에서 잡아냅니다.

## Docker (배포용 `prod` 스택 로컬 검증)

로컬 개발(`local` 프로필, H2)에는 Docker가 필요 없습니다. `prod` 프로필로 배포하기 전에 Postgres/Redis까지 포함한 전체 스택이 실제로 뜨는지 미리 확인하고 싶을 때만 사용하세요.

### 요구 사항
- Docker + Docker Compose (Docker Desktop, 또는 Colima/OrbStack 등 대안)

### 실행
```bash
# 1. jar 빌드 (Dockerfile이 소스를 빌드하지 않고 미리 빌드된 jar를 그대로 사용함)
./gradlew bootJar

# 2. .env 준비 (.env는 git에 커밋하지 않음, 값 설명은 .env.example 주석 참고)
cp .env.example .env

# 3. app + postgres + redis 기동
#    (HTTPS 리버스 프록시 caddy는 운영 전용 prod 프로필이라 로컬에서는 뜨지 않음 — 운영 도메인 인증서 발급 시도 방지)
docker compose up -d --build

# 4. 확인 (기동 완료까지 기다렸다가 확인, 최대 60초)
for i in $(seq 1 30); do curl -sf http://localhost:8080/health && break; sleep 2; done
curl -sf http://localhost:8080/health || { echo "앱이 시간 내에 기동하지 못했습니다" >&2; exit 1; }
```

### 종료
```bash
docker compose down      # 컨테이너만 정리, 데이터(볼륨)는 유지
docker compose down -v   # 컨테이너 + 데이터까지 완전 정리
```

## Redis

refresh token/세션, 이메일 인증·비밀번호 재설정 토큰, 소셜 회원가입 임시 세션 등 인증 관련 단기 데이터를 저장하는 데 사용합니다 (TTL 기반).

애플리케이션 자체는 redis 없이도 기동은 되지만, redis에 연결이 안 되면 로그인/토큰 재발급/이메일 인증/소셜 회원가입 등 인증 관련 기능이 런타임에 전부 실패합니다. 이 기능들을 로컬에서 테스트하려면 redis가 떠 있어야 하며, 전체 `docker compose` 스택을 띄우기 부담스러우면 redis만 가볍게 띄워도 됩니다.

```bash
docker run -d -p 6379:6379 redis:7
```

접속 설정은 `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` 환경변수로 하며 (기본값 `localhost:6379`, 비밀번호 없음), `prod` 프로필 기준 설정은 `application-prod.yml`을 참고하세요.
