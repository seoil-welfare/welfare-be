#!/usr/bin/env bash
# main을 받아 app 컨테이너만 다시 빌드·실행하고, /api/health가 응답할 때까지 확인한다.
# GitHub Actions(.github/workflows/deploy.yml)가 SSM으로 부른다. 서버에서 직접 실행해도 된다: bash deploy/deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

git fetch --quiet origin main
git merge --ff-only origin/main            # 서버에서 고친 파일이 있으면 여기서 멈춘다 (덮어쓰지 않음)
echo "배포할 커밋: $(git log -1 --format='%h %s')"

docker compose up -d --build app
docker image prune -f > /dev/null          # 이전 빌드 이미지가 디스크를 채우지 않게

# 새 서버가 뜰 때까지 기다린다 (Spring Boot 기동 10~30초)
API_DOMAIN=$(grep '^API_DOMAIN=' deploy/.env.prod | cut -d= -f2-)
for i in $(seq 1 30); do
  if curl -fsS "https://$API_DOMAIN/api/health" > /dev/null 2>&1; then
    echo "배포 완료: $(curl -fsS "https://$API_DOMAIN/api/health")"
    exit 0
  fi
  sleep 5
done
echo "서버가 150초 안에 응답하지 않았습니다. 'docker compose logs app --tail 50'으로 원인을 보세요." >&2
docker compose logs app --tail 30 >&2
exit 1
