# 운영 서버 배포 (EC2 + Docker)

```
브라우저 ──► Vercel (프론트, /api/* 를 그대로 넘김) ──► EC2: nginx(HTTPS) ──► app(Spring Boot) ──► RDS PostgreSQL
```

- 프론트는 Vercel이 `/api/*`를 이 서버로 넘겨 줍니다(`welfare-fe/vercel.json`). 브라우저는 Vercel 주소만 보므로 CORS 설정이 필요 없습니다.
- 이 서버의 Docker 이미지는 **항상 `prod` 프로필**로 뜹니다(`Dockerfile`). 개발용 가상 직원·일지(`db/dev`)는 들어가지 않습니다.
- DB는 Docker에 넣지 않고 RDS를 씁니다. 백업·복구를 AWS가 맡게 하려는 것입니다.

## 파일

| 파일 | 내용 |
|---|---|
| `Dockerfile` | jar를 빌드해 Java 21에서 실행하는 이미지 |
| `docker-compose.yml` | `app`(Spring Boot) + `nginx`(HTTPS) + `certbot`(인증서, 필요할 때만) |
| `src/main/resources/application-prod.yml` | 운영 설정: 가상 데이터 제외, 로그인 쿠키 `Secure`·`HttpOnly`·`SameSite=Lax` |
| `deploy/nginx/default.conf.template` | 80은 HTTPS로 보내고, 443은 `/api/`만 Spring Boot로 넘김 |
| `deploy/deploy.sh` | main을 받아 app만 다시 빌드·실행하고 `/api/health`를 확인. 자동 배포가 SSM으로 부른다 |
| `.github/workflows/deploy.yml` | main에 merge되면 테스트 후 배포 (`ci.yml`은 PR마다 테스트) |
| `deploy/.env.prod.example` | RDS 접속 정보와 백엔드 도메인. EC2에서 `deploy/.env.prod`로 복사해 채움 (git에 안 올라감) |

## 처음 한 번 준비

### 1. AWS

1. **RDS PostgreSQL** (서울 리전, `db.t4g.micro` 이상)
   - 퍼블릭 액세스 **끔**, 저장소 암호화 **켬**, 자동 백업 **켬**
   - DB 이름 `welfare`, 사용자 `welfare`
2. **EC2** (서울 리전, `t3.small` 이상, Ubuntu 24.04)
   - 보안 그룹: 22(내 IP만), 80, 443 열기
   - 탄력적 IP를 붙여 주소가 바뀌지 않게 하기
3. **RDS 보안 그룹**: 5432는 **EC2의 보안 그룹에서 오는 것만** 허용
4. **도메인**: 백엔드용 도메인(예: `api.example.com`)의 A 레코드를 EC2 탄력적 IP로

### 2. EC2에 Docker 설치하고 코드 받기

```bash
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
sudo usermod -aG docker $USER && newgrp docker
git clone https://github.com/seoil-welfare/welfare-be.git && cd welfare-be
cp deploy/.env.prod.example deploy/.env.prod
nano deploy/.env.prod        # RDS 엔드포인트·비밀번호, API_DOMAIN 채우기
chmod 600 deploy/.env.prod
```

### 3. HTTPS 인증서 받기 (처음 한 번)

nginx가 켜지기 전에 인증서가 있어야 하므로, 처음 한 번은 certbot이 80번 포트를 직접 씁니다.

```bash
source deploy/.env.prod
docker compose run --rm -p 80:80 certbot certonly --standalone \
  -d "$API_DOMAIN" --agree-tos -m <연락받을 이메일> --no-eff-email
```

### 4. 실행

```bash
docker compose up -d --build
curl https://$API_DOMAIN/api/health      # {"status":"ok", ... "staff":0} 이면 성공
```

처음 켜질 때 Flyway가 `db/migration`으로 테이블을 만듭니다. `staff`가 0이면 가상 데이터가 안 들어간 것입니다.

### 5. 첫 국장 계정 넣기

회원가입이 없으므로 첫 계정 하나는 DB에 직접 넣습니다. 이 계정으로 로그인해 나머지 직원은 `계정 추가`로 만듭니다.

```bash
# 비밀번호 해시 만들기 (원문은 기록에 남지 않게 따옴표 안에만 쓰고, 로그인 후 바로 바꾸기)
docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" '<임시 비밀번호>' | tr -d ':\n'; echo
```

RDS는 외부에서 접속할 수 없으니 EC2에서 접속합니다.

```bash
docker run --rm -it postgres:18-alpine psql "host=<RDS 엔드포인트> user=welfare dbname=welfare sslmode=require"
```

```sql
INSERT INTO staff (login_id, password_hash, name, department, position, role)
VALUES ('<아이디, 영문 소문자>', '<위에서 만든 해시>', '<이름>', '주간활동지원팀', '국장', 'director');
```

로그인한 뒤 **마이페이지 → 비밀번호 변경**으로 바로 바꾸세요.

## 새 버전 배포

**main에 merge하면 자동으로 배포됩니다** (아래 "자동 배포"를 한 번 설정한 뒤).
GitHub의 **Actions → Deploy**에서 진행 상황과 서버 출력을 볼 수 있습니다. 테스트가 실패하면 배포하지 않습니다.

손으로 배포할 때는 EC2에서:

```bash
cd ~/welfare-be && bash deploy/deploy.sh
```

- 서버가 다시 켜지는 동안(수십 초) 요청이 실패합니다.
- 로그인 세션이 서버 메모리에 있어서 **배포하면 모두 다시 로그인해야 합니다.**
- 서버에서 직접 고친 파일이 있으면 `deploy.sh`가 덮어쓰지 않고 멈춥니다. 서버에서는 코드를 고치지 마세요.

## 자동 배포 (GitHub Actions + SSM, 처음 한 번 설정)

```
main에 merge → Actions: 테스트 → AWS 임시 권한(OIDC) → SSM으로 EC2에 "deploy.sh 실행" → /api/health 확인
```

- SSH 포트를 새로 열지 않고, GitHub에 AWS 키나 서버 키를 저장하지 않습니다.
- PR마다 테스트가 돌아갑니다 (`.github/workflows/ci.yml`).

### 1. EC2가 SSM 명령을 받게 하기

1. IAM → 역할 → **역할 생성** → 신뢰할 엔터티: **AWS 서비스 / EC2** → 권한: **`AmazonSSMManagedInstanceCore`** → 이름 `welfare-ec2-ssm`
2. EC2 → 인스턴스 `welfare-be` 선택 → **작업 → 보안 → IAM 역할 수정** → `welfare-ec2-ssm` → 업데이트
3. EC2에서 SSM 에이전트를 다시 시작 (Ubuntu AMI에는 미리 깔려 있음)
   ```bash
   sudo snap restart amazon-ssm-agent
   ```
4. Systems Manager → **Fleet Manager**에 `welfare-be`가 **온라인**으로 보이면 됩니다 (1~2분).

### 2. GitHub이 AWS에 로그인할 수 있게 하기 (OIDC)

1. IAM → **자격 증명 공급자** → 공급자 추가 → **OpenID Connect**
   - 공급자 URL: `https://token.actions.githubusercontent.com`
   - 대상: `sts.amazonaws.com`
2. IAM → 역할 → **역할 생성** → 신뢰할 엔터티: **웹 자격 증명**
   - 자격 증명 공급자: `token.actions.githubusercontent.com`, 대상: `sts.amazonaws.com`
   - GitHub 조직 `seoil-welfare`, 저장소 `welfare-be`, 브랜치 `main` (이 저장소의 main에서만 쓸 수 있게)
   - 권한은 건너뛰고 이름 `welfare-github-deploy`로 만듭니다.
   - **만든 뒤 신뢰 관계를 꼭 고칩니다.** GitHub은 이제 토큰의 `sub`에 조직·저장소 ID를 붙여 보냅니다
     (`repo:seoil-welfare@<조직 ID>/welfare-be@<저장소 ID>:ref:refs/heads/main`). 콘솔은 옛 형식
     (`repo:seoil-welfare/welfare-be:ref:...`)으로 조건을 만들어서 그대로 두면
     `Not authorized to perform sts:AssumeRoleWithWebIdentity`로 실패합니다. 신뢰 정책 편집에서 조건을 이렇게 바꿉니다:
     ```json
     "Condition": {
       "StringEquals": {
         "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
         "token.actions.githubusercontent.com:sub": "repo:seoil-welfare@339118369/welfare-be@1408694806:ref:refs/heads/main"
       }
     }
     ```
     ID를 모르면 워크플로에서 토큰을 찍어 확인합니다 (이 저장소의 `fix/deploy-oidc` 커밋 참고).
   - **인라인 정책**을 추가합니다 (JSON):
     ```json
     {
       "Version": "2012-10-17",
       "Statement": [
         {
           "Effect": "Allow",
           "Action": "ssm:SendCommand",
           "Resource": [
             "arn:aws:ec2:ap-northeast-2:<계정 ID>:instance/<인스턴스 ID>",
             "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript"
           ]
         },
         { "Effect": "Allow", "Action": "ssm:GetCommandInvocation", "Resource": "*" }
       ]
     }
     ```
     이 서버 한 대에 명령을 보내는 권한만 줍니다.

### 3. GitHub 저장소에 값 넣기

`welfare-be` → Settings → **Secrets and variables → Actions → Variables** 탭에 추가 (비밀 값이 아니라 Variables):

| 이름 | 값 |
|---|---|
| `AWS_ROLE_ARN` | 2번에서 만든 역할의 ARN (`arn:aws:iam::<계정 ID>:role/welfare-github-deploy`) |
| `EC2_INSTANCE_ID` | `i-`로 시작하는 인스턴스 ID |

### 4. 확인

GitHub → **Actions → Deploy → Run workflow**로 한 번 손으로 돌려 봅니다. 마지막에 `결과: Success`와 서버의 `배포 완료: {"status":"ok", ...}`가 보이면 끝입니다.

## 인증서 갱신 (90일마다)

EC2에서 `crontab -e`로 아래 한 줄을 넣어 두면 매주 확인하고, 만료가 가까울 때만 갱신합니다.

```
0 4 * * 1 cd /home/ubuntu/welfare-be && docker compose run --rm certbot renew --webroot -w /var/www/certbot -q && docker compose exec nginx nginx -s reload
```

## 확인할 것

- [ ] `https://<API_DOMAIN>/api/health`가 `staff: 0`으로 시작했는지 (가상 데이터 없음)
- [ ] `http://`로 열면 `https://`로 넘어가는지
- [ ] RDS에 EC2 말고는 접속할 수 없는지
- [ ] 로그인 응답의 `Set-Cookie`에 `Secure; HttpOnly; SameSite=Lax`가 있는지
- [ ] Vercel 주소에서 로그인하고 새로고침해도 로그인이 유지되는지 (아이폰 Safari 포함)
