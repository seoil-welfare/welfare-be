# 오늘 카드(AI) 백엔드 API 명세

프론트(`welfare-fe`, 브랜치 `develop`)는 이 문서의 첫 버전대로 연동해 두었다. 백엔드는 `feature/today-card`에 구현했다.

> **2026-11-12 결정 반영 (백엔드 구현 기준)**
> - 문자 발송은 하지 않는다. '보내기'는 **공유 링크를 만드는 것**이고, 담당자가 링크를 복사해 보호자에게 직접 보낸다 (5-4)
> - 같은 링크를 **언제든 다시 복사**할 수 있다. 토큰은 해시와 함께 서버 비밀키로 암호화해 저장한다 (7장)
> - 보호자 휴대폰 칸은 없앴다. 보낼 때는 이용자의 **카드 수신 동의**만 확인한다 (5-9)
> - `generating`이 5분 넘게 이어지면(생성 중 서버 재시작 등) 조회할 때 `failed`로 바꾼다 (5-2)
> - 프론트가 바꿔야 할 것은 12장에 모았다

- 프론트 계약 코드: `src/api/types.ts`의 `TodayCardApi`, `GuardianCardApi`
- 카드 데이터 타입: `src/types/todayCard.ts`
- 호출 경로: `src/api/http/httpApi.ts`

---

## 1. 기능 한 줄 요약

담당자가 일지를 **작성 완료**하면, 서버가 AI로 이용자용 그림 카드(앞면)와 보호자 알림장(뒷면)을 만든다. 담당자는 확인하고 고친 뒤 **공유 링크를 만들어** 카카오톡 등으로 보호자에게 직접 보낸다. 보호자는 로그인 없이 링크로 열고, 이용자가 오늘 제일 좋았던 활동을 고르면 담당자 화면에 전해진다.

```
[담당자] 일지 작성 완료
      │
      ▼
[서버] 카드 생성 (status: generating → ready / failed)
      │
[담당자] 오늘 카드 확인 → 고치기(PUT) → 미리보기 → 공유 링크 만들기(send)
      │                                         │
      │                                         ▼
      │                         [서버] 링크 토큰 발급 (7일 뒤 만료) → 응답 shareUrl
      │                         [담당자] 링크 복사 → 보호자에게 직접 전달 (다시 복사 가능)
      ▼                                         │
 일지를 다시 고치면 status: outdated            ▼
 → 다시 만들기(regenerate)          [보호자] 링크 열기 → 생일 4자리 확인 → 카드 보기 → 제일 좋았던 것 고르기(pick)
                                                │
                                                ▼
                                [담당자] 확인 화면에 고른 결과 표시
```

---

## 2. 공통 약속

| 항목 | 값 |
|---|---|
| 기본 경로 | `/api` |
| 형식 | JSON, UTF-8 |
| 날짜 | `YYYY-MM-DD` (KST 업무일) |
| 시각 | 오프셋이 붙은 ISO 8601 (`2026-11-10T17:00:00+09:00`) |
| 활동 시간 | `HH:mm` |
| 오류 본문 | `{ "message": "사용자에게 보여 줄 한국어 문장", "code": "오류 코드" }` (기존 API와 같음) |
| id | 문자열로 내려준다 (기존 API와 같음) |

> ⚠️ **보호자용 API(`/api/cards/**`)는 401을 쓰면 안 된다.** 프론트는 401을 받으면 "직원 로그인 만료"로 보고 로그인 화면으로 보낸다. 보호자 확인이 필요할 때는 **403 `CARD_VERIFY_REQUIRED`**를 쓴다.

---

## 3. 정해진 값 (enum)

| 이름 | 값 | 뜻 |
|---|---|---|
| `status` | `generating` | AI가 만드는 중 |
| | `ready` | 만들어짐, 담당자 확인 대기 |
| | `failed` | 만들지 못함 |
| | `outdated` | 만든 뒤 일지가 바뀜 (다시 만들기 권장) |
| | `sent` | 보호자에게 보냄 |
| `pictogram` | `music` `meal` `art` `sports` `cooking` `walk` `outing` `daily` `favorite` `rest` | 그림 카드. **AI가 이 목록 밖의 값을 내면 안 된다** |
| `mood` | `excited` `good` `tired` | 앞면 표정. 앞면에는 좋았던 순간만 담으므로 부정적인 값은 없다 |
| `how` | `pointed` | 이용자가 직접 가리킴 |
| | `observed` | 보호자가 보기에 좋아함 |

### status 바뀌는 규칙

```
(일지 완료) ─► generating ─┬─► ready ──┬─► sent        (보내기)
                           │           └─► outdated    (완료 일지를 수정)
                           └─► failed
failed / outdated ─(regenerate)─► generating
sent 상태는 일지를 고쳐도 바뀌지 않는다 (이미 보낸 내용은 그대로)
```

---

## 4. 데이터 모양

### 4-1. 담당자용 카드 `TodayCard`

```json
{
  "diaryId": "393",
  "status": "ready",
  "nickname": "하늘",
  "date": "2026-11-10",
  "program": "주간활동",
  "summary": "오전에는 기분 좋게 활동했어요. 오후에 조금 피곤해서 일찍 쉬었어요.",
  "needsCall": false,
  "updatedAt": "2026-11-10T17:00:00+09:00",
  "sentAt": "2026-11-10T17:10:00+09:00",
  "expiresOn": "2026-11-17",
  "pick": {
    "itemId": "a1",
    "how": "pointed",
    "skipped": false,
    "pickedAt": "2026-11-10T19:00:00+09:00"
  },
  "items": [
    {
      "id": "a1",
      "time": "10:00",
      "activity": "음악활동",
      "word": "북",
      "pictogram": "music",
      "mood": "excited",
      "easyText": "북을 쳤어요. 신났어요.",
      "guardianText": "선생님 리듬을 따라 북을 20분 동안 쳤어요. 소리가 커질 때 귀를 막았는데, 헤드폰을 드리니 금방 편안해져서 끝까지 함께했어요.",
      "homeTip": "큰 소리가 나는 곳에서는 헤드폰을 먼저 건네 보세요."
    }
  ]
}
```

| 필드 | 필수 | 설명 |
|---|---|---|
| `diaryId` | O | 일지 id |
| `status` | O | 3장 참고 |
| `nickname` | O | 카드에 보이는 이름. **실명이 아니라** 이용자에 저장한 카드 별칭 |
| `date` | O | 일지 날짜 |
| `program` | O | 사업 이름 (지금은 `"주간활동"` 고정) |
| `summary` | O | 하루 요약 한두 문장 (마지막 장 "오늘의 한마디") |
| `needsCall` | O | 담당자가 "다쳤거나 위험했던 일이 있어요"를 체크했는지. true면 프론트가 뒷면에 "선생님이 전화로 따로 연락드릴게요"를 붙인다 |
| `updatedAt` | O | 수정할 때 그대로 돌려받는다 (동시 수정 확인용) |
| `sentAt` | sent일 때 | 보낸 시각 |
| `expiresOn` | sent일 때 | 링크가 열리는 마지막 날 (보낸 날 + 7일) |
| `shareUrl` | sent일 때 | 보호자에게 보낼 링크 `https://welfare-fe.vercel.app/card/{token}`. 다시 불러와도 같은 값 |
| `pick` | 고른 뒤 | 보호자 쪽에서 고른 결과 |
| `items[].id` | O | 카드 항목 id (pick의 `itemId`가 이 값을 가리킨다) |
| `items[].time` | O | 활동 시작 시각. 시간이 없는 활동은 카드에서 뺀다 |
| `items[].activity` | O | 참여 활동 이름 (일지의 대표 참여 활동, '기타'면 직접 입력한 글) |
| `items[].word` | O | 앞면 큰 제목 한 단어 (예: 북, 밥, 그림). 2~4글자 권장 |
| `items[].pictogram` | O | 3장 목록 중 하나 |
| `items[].mood` | O | 3장 목록 중 하나 |
| `items[].easyText` | O | 앞면 문장. 이용자에게 읽어 주는 쉬운 말 1~2문장 |
| `items[].guardianText` | O | 뒷면 보호자 메모. 있었던 일을 사실대로 |
| `items[].homeTip` | X | 집에서 해 보면 좋은 것. 없으면 필드를 뺀다 |

### 4-2. 보호자용 카드 `GuardianCard`

4-1에서 **`diaryId`, `status`, `updatedAt`, `sentAt`, `shareUrl`을 뺀** 모양이다. `expiresOn`은 항상 있다.
이용자 실명, 생년월일, 직원 이름은 **절대 넣지 않는다.**

---

## 5. API 목록

| 순위 | 메서드 | 경로 | 로그인 | 용도 |
|---|---|---|---|---|
| 1 | (서버 동작) | 일지 작성 완료 시 | - | 카드 자동 생성 |
| 1 | GET | `/api/diaries/{diaryId}/card` | 직원 | 카드 조회 |
| 1 | PUT | `/api/diaries/{diaryId}/card` | 직원 | 문장 고치기 |
| 1 | POST | `/api/diaries/{diaryId}/card/send` | 직원 | 공유 링크 만들기 |
| 2 | POST | `/api/diaries/{diaryId}/card/regenerate` | 직원 | 다시 만들기 |
| 2 | GET | `/api/cards/{token}` | 없음 | 보호자 카드 조회 |
| 2 | POST | `/api/cards/{token}/verify` | 없음 | 보호자 확인 |
| 2 | POST | `/api/cards/{token}/pick` | 없음 | 제일 좋았던 것 고르기 |
| 3 | (기존 API 확장) | `/api/service-users`, `/api/categories` | 직원 | 별칭·카드 수신 동의, 기본 그림 |

---

### 5-1. [1순위] 카드 자동 생성 (일지 작성 완료 시)

**언제**
- 일지 저장(`POST /api/diaries`, `PUT /api/diaries/{id}`)에서 `status`가 **`completed`로 처음 바뀔 때**
- 이미 `completed`인 일지를 다시 저장할 때 → 카드가 `ready`면 **`outdated`로만** 바꾼다 (자동으로 다시 만들지 않는다. 담당자가 고친 문장이 날아가지 않게)
- 카드가 `sent`면 아무것도 바꾸지 않는다
- 바뀐 내용 없이 다시 저장한 경우는 아무것도 하지 않는다 (일지 규칙 5와 같음)
- 카드가 아예 없는 완료 일지(이 기능 전에 쓴 일지)를 고치면 그때 카드를 만든다

**어떻게**
1. 일지 저장 트랜잭션 안에서 카드 행을 `generating`으로 만든다.
2. **트랜잭션 커밋 뒤** 비동기로 AI를 부른다 (`@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` 등). 일지 저장 응답을 AI 때문에 늦추지 않는다.
3. AI 결과를 검증하고 `ready`로 저장한다. 실패하면 `failed`.

**AI에 보내는 것 (개인정보 최소화)**
- 이용자 이름 대신 **카드 별칭**, 생년월일·주소·연락처는 보내지 않는다
- 직원 이름은 보내지 않는다
- 보내는 것: 날짜, 활동별(시작 시각, 참여 활동, 활동 상세 내용, 사전 지원, 이용자 반응, 긍정행동지원, 도전적 행동 유형·세부), 특이사항, 컨디션
- **입력을 학습에 쓰지 않는 AI API**를 쓴다

**AI에 지시할 규칙 (프롬프트)**
- 앞면(`easyText`, `word`, `mood`): 쉬운 말, 짧은 문장, 좋았던 순간만. **도전적 행동은 앞면에 절대 넣지 않는다**
- 뒷면(`guardianText`): 있었던 일을 사실대로, 쉬운 말로. 도전적 행동이 있었으면 사실만 부드럽게 적는다. 진단·평가·추측은 하지 않는다
- `pictogram`, `mood`는 3장 목록에서만 고른다
- 출력은 정해진 JSON 모양으로만

**결과 검증 (서버에서 꼭 할 것)**
- JSON 파싱 실패 → `failed`
- `pictogram` / `mood`가 목록 밖 → 그 항목은 참여 활동의 기본 그림(5-8) / `good`으로 바꾼다
- 일지 활동 수와 카드 항목 수가 다르거나 시간이 안 맞음 → `failed`
- 빈 문장 → `failed`
- AI 호출 시간 초과(예: 30초) → 한 번 다시 시도, 그래도 실패면 `failed`

**만들지 않는 경우**
- 시간이 적힌 활동이 하나도 없는 일지 → 카드를 만들지 않는다 (조회 시 404 `CARD_NOT_FOUND`)

---

### 5-2. [1순위] `GET /api/diaries/{diaryId}/card`

담당자 확인 화면과 미리보기가 쓴다. `status`가 `generating`이면 프론트가 **3초마다 다시 부른다.**

| 응답 | 경우 |
|---|---|
| `200` + `TodayCard` (4-1) | 카드가 있음 (만드는 중·실패 포함) |
| `404 CARD_NOT_FOUND` | 일지가 아직 작성 중이거나, 카드를 만들지 않는 일지 |
| `404` | 일지가 없음 |
| `401` | 로그인 안 함 |

- `generating`, `failed`일 때는 `items`를 빈 배열로 내린다.
- `generating`이 **5분 넘게** 이어지면(생성 중 서버 재시작 등) 이 조회에서 `failed`로 바꿔 돌려준다. 담당자는 다시 만들기로 회복한다.

---

### 5-3. [1순위] `PUT /api/diaries/{diaryId}/card`

담당자가 문장을 고치거나 "다쳤거나 위험했던 일" 체크를 바꿀 때 부른다.

**요청**
```json
{
  "updatedAt": "2026-11-10T17:00:00+09:00",
  "needsCall": true,
  "items": [
    { "id": "a1", "easyText": "…", "guardianText": "…", "homeTip": "…" },
    { "id": "a2", "easyText": "…", "guardianText": "…" }
  ]
}
```

- **카드만 바뀐다.** `today_card`, `today_card_item`만 고치고 일지(`daily_log`, `activity_record`, 선택 항목 테이블)는 절대 건드리지 않는다. 카드 문장은 보호자용으로 바꿔 쓴 글이라 기록 원문과 다르기 때문이다
- 프론트는 **항목 전체**를 보낸다 (고친 항목만이 아님)
- 바꿀 수 있는 것은 `easyText`, `guardianText`, `homeTip`, `needsCall`뿐이다. `word`, `pictogram`, `mood`, `time`, `activity`는 무시한다
- `homeTip`이 없거나 빈 문자열이면 지운다

| 응답 | 경우 |
|---|---|
| `200` + 고친 `TodayCard` (새 `updatedAt`) | 성공 |
| `409 CARD_UPDATED_BY_OTHER` | 요청의 `updatedAt`이 DB와 다름 (그사이 다른 사람이 고침). 일지 동시 수정(`DIARY_UPDATED_BY_OTHER`)과 같은 방식 |
| `400 CARD_ALREADY_SENT` | 이미 보낸 카드 |
| `400 CARD_NOT_READY` | `generating` 또는 `failed` |
| `400 VALIDATION_ERROR` | 빈 문장, 없는 항목 id, 너무 긴 문장 (앞면 100자, 뒷면 500자, homeTip 200자 정도) |

- 고치는 것은 `ready`, `outdated` 상태에서만 된다

---

### 5-4. [1순위] `POST /api/diaries/{diaryId}/card/send`

요청 본문 없음.

**하는 일** (문자는 보내지 않는다)
1. `status`가 `ready`인지 확인한다
2. 이용자에 카드 수신 동의가 있는지 확인한다
3. 링크 토큰을 만든다 → 7장 참고
4. `sentAt = 지금`, `expiresOn = 오늘 + 7일`, `status = sent`
5. 응답의 `shareUrl`(`https://welfare-fe.vercel.app/card/{token}`)을 담당자가 복사해 보호자에게 직접 보낸다
   - 링크를 잃어버리면 카드를 다시 불러오면 된다. `GET` 응답에 같은 `shareUrl`이 있다
   - 프론트는 휴대폰에서 `navigator.share`(공유창)를, 안 되면 클립보드 복사를 쓰면 된다

| 응답 | 경우 |
|---|---|
| `200` + `status: "sent"`, `shareUrl`이 있는 `TodayCard` | 성공 |
| `400 CARD_NOT_READY` | `ready`가 아님 (`outdated`도 보낼 수 없다. 다시 만들거나 확인 후 보내도록) |
| `400 CARD_ALREADY_SENT` | 이미 보냄 → 링크는 `GET`의 `shareUrl`로 다시 복사 |
| `400 GUARDIAN_NOT_READY` | 카드 수신 동의가 없음. 메시지: "이용자 정보에서 보호자의 카드 수신 동의를 먼저 등록해 주세요." |

> `outdated` 카드 보내기 정책은 협의 필요. 지금 프론트는 `ready`일 때만 보내기 버튼을 켠다.

---

### 5-5. [2순위] `POST /api/diaries/{diaryId}/card/regenerate`

요청 본문 없음. `failed` 또는 `outdated` 카드를 다시 만든다.

- 담당자가 고친 문장은 **새 AI 결과로 덮어쓴다** (프론트에서 확인 후 부른다)
- 응답: `status: "generating"`인 `TodayCard` → 프론트가 3초마다 GET으로 결과를 확인한다

| 응답 | 경우 |
|---|---|
| `200` | 다시 만들기 시작 |
| `400 CARD_NOT_REGENERABLE` | `generating`, `ready`, `sent` 상태 |

---

### 5-6. [2순위] `GET /api/cards/{token}` (로그인 없음)

| 응답 | 경우 |
|---|---|
| `200` + `GuardianCard` (4-2) | 확인 쿠키가 맞음 |
| `403 CARD_VERIFY_REQUIRED` | 이 링크의 보호자 확인 쿠키가 없거나 틀림 |
| `410 CARD_EXPIRED` | `expiresOn`이 지남 |
| `404` | 없는 토큰 |

- **401 금지** (2장 경고)
- 응답에 `Cache-Control: no-store`

---

### 5-7. [2순위] `POST /api/cards/{token}/verify` (로그인 없음)

**요청**
```json
{ "birthMmdd": "0307" }
```

이용자 생일의 월·일 4자리 (생일이 3월 7일이면 `0307`).

| 응답 | 경우 |
|---|---|
| `204` + `Set-Cookie` | 맞음 |
| `400 CARD_VERIFY_FAILED` | 틀림. 메시지 예: "생일이 맞지 않아요. 다시 입력해 주세요." |
| `429 CARD_VERIFY_LOCKED` | 5번 틀림 → 30분 잠금 |
| `410` / `404` | 만료 / 없는 토큰 |

**쿠키**
- 이름 예: `card_verified`
- 값: `토큰 해시 + 만료 시각`을 서버 비밀키로 서명한 것 (HMAC). DB 조회 없이 확인 가능
- `HttpOnly; Secure; SameSite=Lax; Path=/api/cards/{token}; Max-Age`는 링크 만료까지

---

### 5-8. [2순위] `POST /api/cards/{token}/pick` (로그인 없음, 확인 쿠키 필요)

**요청**
```json
{ "itemId": "a1", "how": "pointed", "skipped": false }
```
고르지 않았으면 `{ "skipped": true }`.

**응답** `200`
```json
{ "itemId": "a1", "how": "pointed", "skipped": false, "pickedAt": "2026-11-10T19:00:00+09:00" }
```

- 다시 고르면 **덮어쓴다** (카드 1장에 고른 결과 1개)
- `itemId`가 이 카드 항목이 아니면 `400 VALIDATION_ERROR`
- 확인 쿠키 없음 → `403 CARD_VERIFY_REQUIRED`, 만료 → `410`
- 저장한 결과는 5-2 응답의 `pick`으로 담당자에게 보인다

---

### 5-9. [3순위] 기존 API 확장

**이용자 (`/api/service-users`)**: 응답과 등록·수정 요청에 칸을 추가한다

| 필드 | 설명 |
|---|---|
| `cardNickname` | 카드에 보이는 별칭 (예: 하늘, 20자까지). 없으면 카드에 이름 대신 "이용자"로 |
| `cardConsent` | 카드 수신 동의 여부 (`true`/`false`) |
| `cardConsentAt` | 동의한 시각 (응답에만, 동의 안 했으면 빠짐) |

- 등록·수정 요청에서 두 칸을 **보내지 않으면(null) 그대로 둔다.** 카드 칸이 없는 지금 화면이 저장해도 지워지지 않게 하기 위해서다. 별칭을 지우려면 빈 문자열을 보낸다
- 동의를 켜면 `cardConsentAt`이 그 시각으로, 끄면 비워진다

**참여 활동 (`/api/categories`)**: `participation` 항목에 `pictogram`(기본 그림 키)을 추가한다. AI가 고른 그림이 목록 밖이면 이 값을 쓴다. 참여 활동을 추가할 때도 받는다 (없으면 `favorite`).

> 프론트에는 아직 이 칸들의 입력 화면이 없다. API가 생기면 이용자 등록·상세와 참여 활동 관리 화면에 추가한다.

---

## 6. DB 설계 (실제 구현: `src/main/resources/db/migration/V8__today_card.sql`)

아래는 처음 설계안이다. 실제 V8은 여기서 `guardian_phone`을 빼고, `today_card`에 `token_enc`(다시 복사용 암호문)를 더했다.
기존 참여 활동의 기본 그림도 넣었다 (예술활동 art, 체육활동 sports, 요리활동 cooking, 일상생활훈련 daily, 지역참여활동·현장학습 outing, 나머지 favorite).

```sql
-- 이용자: 카드용 칸
ALTER TABLE client ADD COLUMN card_nickname   VARCHAR(20);
ALTER TABLE client ADD COLUMN card_consent    BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE client ADD COLUMN card_consent_at TIMESTAMPTZ;

-- 참여 활동: 기본 그림
ALTER TABLE activity_type ADD COLUMN pictogram VARCHAR(20) NOT NULL DEFAULT 'favorite';

-- 카드 (일지 1건에 1장)
CREATE TABLE today_card (
  card_id          BIGSERIAL PRIMARY KEY,
  log_id           BIGINT NOT NULL UNIQUE REFERENCES daily_log(log_id) ON DELETE CASCADE,
  status           VARCHAR(12) NOT NULL CHECK (status IN ('generating','ready','failed','outdated','sent')),
  summary          TEXT,
  needs_call       BOOLEAN NOT NULL DEFAULT false,
  fail_reason      TEXT,                       -- 로그용. 응답에는 내보내지 않는다
  token_hash       CHAR(64) UNIQUE,            -- 토큰 원문은 저장하지 않는다 (SHA-256)
  sent_at          TIMESTAMPTZ,
  expires_on       DATE,
  verify_fail_count INT NOT NULL DEFAULT 0,
  verify_locked_until TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 카드 항목 (활동마다 1개)
CREATE TABLE today_card_item (
  item_id        BIGSERIAL PRIMARY KEY,
  card_id        BIGINT NOT NULL REFERENCES today_card(card_id) ON DELETE CASCADE,
  record_id      BIGINT REFERENCES activity_record(record_id) ON DELETE SET NULL,
  sort_order     INT NOT NULL,
  start_time     TIME NOT NULL,
  activity_label VARCHAR(50) NOT NULL,
  word           VARCHAR(10) NOT NULL,
  pictogram      VARCHAR(20) NOT NULL,
  mood           VARCHAR(10) NOT NULL CHECK (mood IN ('excited','good','tired')),
  easy_text      VARCHAR(100) NOT NULL,
  guardian_text  VARCHAR(500) NOT NULL,
  home_tip       VARCHAR(200)
);

-- 보호자가 고른 결과 (카드 1장에 1개, 다시 고르면 덮어씀)
CREATE TABLE today_card_pick (
  card_id    BIGINT PRIMARY KEY REFERENCES today_card(card_id) ON DELETE CASCADE,
  item_id    BIGINT REFERENCES today_card_item(item_id) ON DELETE SET NULL,
  how        VARCHAR(10) CHECK (how IN ('pointed','observed')),
  skipped    BOOLEAN NOT NULL,
  picked_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

- 일지를 지우면(작성 중 일지만 삭제 가능) 카드도 함께 지워진다
- 다시 만들기는 `today_card_item`을 지우고 새로 넣는다 (`today_card_pick`도 지운다)

---

## 7. 링크 토큰과 보안

| 항목 | 구현 |
|---|---|
| 토큰 | `SecureRandom` 32바이트 → Base64URL (추측 불가) |
| 저장 | 찾기용 **SHA-256 해시**와, 같은 링크를 다시 복사하기 위한 **AES-256-GCM 암호문**을 저장한다. 원문은 저장하지 않는다. 암호화·쿠키 서명 키는 환경 변수 `CARD_SECRET`에서 만든다 (운영은 필수) |
| 만료 | `expires_on`이 지나면 410. 만료된 카드 내용은 지우지 않는다 (기록 보관) |
| 보호자 확인 | 생일 4자리 + 서명 쿠키 (5-7) |
| 무차별 대입 | 확인 5번 틀리면 30분 잠금, `/api/cards/**`에 IP별 요청 제한 (1분 30번, 넘으면 `429 RATE_LIMITED`) |
| 로그 | 토큰 원문을 로그에 남기지 않는다 (nginx 접근 로그에서 `/card/` 경로 마스킹 검토) |
| 응답 | 보호자 응답에 실명·생년월일·직원 이름 금지 |
| 캐시 | `/api/cards/**`는 `Cache-Control: no-store` |

---

## 8. 권한 (협의 필요, 제안안)

| 동작 | 제안 |
|---|---|
| 카드 조회 | 로그인한 직원 모두 (일지 조회와 같음) |
| 고치기 · 보내기 · 다시 만들기 | 일지 작성자, 이용자 담당자, 팀장·국장 |

---

## 9. 오류 코드 정리

| 코드 | HTTP | 언제 |
|---|---|---|
| `CARD_NOT_FOUND` | 404 | 작성 중 일지, 카드를 만들지 않는 일지 |
| `CARD_UPDATED_BY_OTHER` | 409 | 고치기 동시 충돌 |
| `CARD_ALREADY_SENT` | 400 | 보낸 카드를 고치거나 다시 보냄 |
| `CARD_NOT_READY` | 400 | ready가 아닌데 보내기 / generating·failed인데 고치기 |
| `CARD_NOT_REGENERABLE` | 400 | failed·outdated가 아닌데 다시 만들기 |
| `GUARDIAN_NOT_READY` | 400 | 카드 수신 동의 없음 |
| `RATE_LIMITED` | 429 | 보호자 주소에 1분 30번 넘게 요청 |
| `CARD_VERIFY_REQUIRED` | 403 | 보호자 확인 전 (**401 아님**) |
| `CARD_VERIFY_FAILED` | 400 | 생일 4자리 틀림 |
| `CARD_VERIFY_LOCKED` | 429 | 5번 틀려서 잠김 |
| `CARD_EXPIRED` | 410 | 링크 만료 |
| `VALIDATION_ERROR` | 400 | 빈 문장, 길이 초과, 없는 항목 id |

---

## 10. 제대로 동작해야 하는 것 (완료 기준)

백엔드 테스트(`./gradlew test`)와 프론트 화면에서 아래가 모두 되어야 완료다.

**생성**
- [ ] 일지를 작성 완료하면 카드가 `generating`으로 생기고, 잠시 뒤 `ready`가 된다
- [ ] 일지 저장 응답 속도가 AI 때문에 느려지지 않는다
- [ ] AI가 이상한 값을 내면 `failed`가 되고, 다시 만들기로 회복된다
- [ ] AI 요청 본문에 이용자 실명·생년월일·직원 이름이 없다 (테스트로 확인)
- [ ] 앞면 문장에 도전적 행동이 들어가지 않는다 (도전적 행동이 있는 일지로 확인)
- [ ] `pictogram`, `mood`가 항상 정해진 값 중 하나다
- [ ] 작성 중 일지는 카드가 없다 (404 `CARD_NOT_FOUND`)

**확인·수정**
- [ ] 문장을 고치면 저장되고, 미리보기와 보호자 화면에 고친 문장이 나온다
- [ ] 카드 문장을 고쳐도 일지 내용·수정 시각·수정 이력(`log_history`)·통계가 바뀌지 않는다
- [ ] 두 사람이 동시에 고치면 나중 사람이 409를 받는다
- [ ] 완료 일지를 다시 고치면 카드가 `outdated`가 되고, 담당자가 고친 문장은 그대로 남는다
- [ ] 다시 만들기를 하면 새 내용으로 바뀐다

**보내기**
- [ ] `ready`일 때만 보낼 수 있다
- [ ] 보호자 연락처나 동의가 없으면 `GUARDIAN_NOT_READY`로 막힌다
- [ ] 보내면 보호자 휴대폰에 링크 문자가 온다
- [ ] 문자 발송이 실패하면 `sent`가 되지 않는다
- [ ] 보낸 뒤에는 고치기·다시 보내기가 막힌다

**보호자 화면**
- [ ] 링크를 처음 열면 생일 4자리 확인 화면이 나온다 (403, 로그인 화면으로 가지 않음)
- [ ] 맞게 입력하면 카드가 보이고, 같은 브라우저에서 다시 열면 확인 없이 보인다
- [ ] 5번 틀리면 잠긴다
- [ ] 7일이 지나면 "링크 기간이 지났어요" 화면이 나온다 (410)
- [ ] 아무 토큰이나 넣으면 404
- [ ] 보호자 응답에 실명·생년월일·직원 이름이 없다
- [ ] 제일 좋았던 것을 고르면 담당자 확인 화면에 "제일 좋았던 것: 북 (직접 가리켰어요)"가 보인다

---

## 11. 함께 정해야 할 것

1. ~~권한~~ → 8장 제안안으로 구현
2. ~~문자 발송 수단~~ → 문자를 보내지 않고 공유 링크를 담당자가 직접 전달 (5-4)
3. ~~AI 서비스~~ → OpenAI `gpt-5.4-mini` (환경 변수 `OPENAI_API_KEY`, `OPENAI_MODEL`). 키가 없으면 정해진 문장으로 만든다. 한 달 예산은 아직 미정
4. **보호자 확인 방식**: 생일 4자리 + 링크 만료일까지 쿠키로 구현. 충분한지 복지관 확인 필요
5. **outdated 카드 보내기**: 지금은 막음. 담당자가 확인했으면 그대로 보내게 할지
6. **보관 기간**: 보낸 카드와 고른 결과를 얼마나 보관할지 (일지 보관 기간과 같게 제안)
7. **고른 결과 알림**: 보호자가 고르면 지금은 담당자가 카드 화면을 열어야 보인다. 홈 '소식'에 띄울지

---

## 12. 프론트가 바꿀 것 (백엔드 구현과 맞추기)

| 위치 | 바꿀 것 |
|---|---|
| `src/types/todayCard.ts` `TodayCard` | `shareUrl?: string` 추가 (sent일 때) |
| 확인 화면 '보호자에게 보내기' | 문구를 '공유 링크 만들기'로. 성공하면 `shareUrl`을 공유창(`navigator.share`) 또는 클립보드로 |
| 확인 화면 (sent 상태) | '링크 다시 복사' 버튼 → `card.shareUrl` 복사 |
| 오류 처리 | `SEND_FAILED` 삭제, `GUARDIAN_NOT_READY` 문구는 서버 메시지 그대로 |
| 이용자 등록·상세 | `cardNickname`(20자), `cardConsent` 입력 칸. 보내지 않으면 서버가 그대로 둔다 |
| 참여 활동 관리 | `pictogram` 선택 (10개 중 하나) |
| `TODAY_CARD_ENABLED` | 백엔드 배포 후 `true`로 (지금은 개발 서버만) |

## 13. 배포 환경 변수 (AWS 백엔드)

| 이름 | 필수 | 설명 |
|---|---|---|
| `CARD_SECRET` | O | 링크 토큰 암호화·쿠키 서명 키. 16자 이상 임의 문자열. 바꾸면 이미 보낸 링크의 '다시 복사'와 확인 쿠키가 무효가 된다 |
| `OPENAI_API_KEY` | O | 없으면 AI 대신 정해진 문장으로 만든다 |
| `OPENAI_MODEL` | X | 기본 `gpt-5.4-mini` |
| `CARD_SHARE_BASE_URL` | X | 기본 `https://welfare-fe.vercel.app` |
