-- =====================================================================
-- 낮활동서비스 활동일지 시스템 - PostgreSQL 스키마 v0.3
-- 기준: 기능명세서 v0.3 (프론트 welfare-fe a87dfbb + 리뷰 반영)
--
-- 테이블 네 종류 (총 18개)
--   [사람]  staff, client
--   [목록]  weather, condition_status, activity_type, pre_support, reaction,
--           pbs_support, behavior_area, behavior_item           ← 메뉴판
--   [기록]  daily_log, activity_record, log_history              ← 주문서
--   [연결]  record_activity_type, record_pre_support, record_reaction,
--           record_pbs_support, record_behavior                 ← 주문 상세
--
-- v0.2 → v0.3 변경
--   staff        : department, role, notify_new_log, updated_at 추가
--   client       : birth6(CHAR 6) → birth_date(DATE), staff_id(담당 직원) 추가,
--                  is_active → status(active/closed) + closed_at
--   목록 테이블   : code 칸 추가 (명세서 5.1 선택 항목 코드화)
--   daily_log    : status(draft/completed) 추가
--   log_history  : 신규 (오늘의 소식 등록·수정 이력)
--
-- 실행 순서: 사람 → 목록 → 기록 → 연결 (참조 당하는 테이블을 먼저 만들어야 함)
-- 주의: 이용자 데이터는 넣지 않았음. 테스트 이용자는 반드시 가상 데이터로
-- =====================================================================


-- =====================================================================
-- [1] 사람
-- =====================================================================

-- 담당자 계정 (선생님 한 분당 하나, 명세서 SF·SE)
CREATE TABLE staff (
    staff_id       BIGSERIAL    PRIMARY KEY,                     -- 담당자 번호 (자동 증가)
    login_id       VARCHAR(50)  NOT NULL UNIQUE,                 -- 로그인 아이디, 중복이면 409 DUPLICATE_LOGIN_ID
    password_hash  VARCHAR(255) NOT NULL,                        -- 비밀번호 원문 저장 금지, BCrypt 같은 해시만 저장
    name           VARCHAR(50)  NOT NULL,                        -- 담당자 이름
    department     VARCHAR(50)  NOT NULL,                        -- 소속 팀 (프론트 Staff.department)
    position       VARCHAR(30)  NOT NULL,                        -- 직위 표기 (사회복지사, 팀장 등)
    role           VARCHAR(20)  NOT NULL DEFAULT 'staff'
                   CHECK (role IN ('staff', 'teamLead', 'director')), -- 권한 역할 (프론트 Staff.role), 권한 정책은 명세서 8.1
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,           -- 사용 중지(퇴사·직무 정지) 시 FALSE, 삭제하지 않음 (명세서 8.3)
    notify_new_log BOOLEAN      NOT NULL DEFAULT FALSE,          -- 새 일지 알림 받기 (제안, B-13)
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),          -- 계정 만든 시각
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()           -- 마지막 수정 시각
);

-- 이용자 (명세서 US·CU)
CREATE TABLE client (
    client_id  BIGSERIAL   PRIMARY KEY,                          -- 이용자 번호 (이름이 바뀌어도 그대로)
    name       VARCHAR(50) NOT NULL,                             -- 이용자 이름
    birth_date DATE        NOT NULL,                             -- 생년월일. 화면은 6자리로 받고 DB는 날짜로 저장 (명세서 6장 2번)
    staff_id   BIGINT      REFERENCES staff(staff_id),           -- 담당 직원. 비어 있으면 '담당 미지정'
    status     VARCHAR(10) NOT NULL DEFAULT 'active'
               CHECK (status IN ('active', 'closed')),           -- 이용 중 / 이용 종결 (종결은 삭제가 아님)
    closed_at  TIMESTAMPTZ,                                      -- 종결한 시각 (이용 중이면 비어 있음)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),               -- 등록 시각
    CHECK ((status = 'closed') = (closed_at IS NOT NULL))        -- 종결이면 종결 시각이 반드시 있고, 이용 중이면 없어야 함
);


-- =====================================================================
-- [2] 목록 (메뉴판) - 양식의 선택 항목마다 테이블 하나
--     공통 칸: 번호, code(저장·통계 기준), name(화면 표시), 기타 여부, 순서, 사용 여부
--     항목을 없앨 때는 지우지 않고 is_active = FALSE (과거 일지·통계 보존)
-- =====================================================================

-- 날씨 (1개 선택)
CREATE TABLE weather (
    weather_id SMALLSERIAL PRIMARY KEY,                -- 날씨 번호
    code       VARCHAR(30) NOT NULL UNIQUE,            -- 코드 (예: sunny). 이름이 바뀌어도 그대로
    name       VARCHAR(20) NOT NULL,                   -- 화면 표시 이름 (예: 맑음)
    sort_order INT         NOT NULL DEFAULT 0,         -- 표시 순서, 통계에서 건수가 같을 때 정렬 기준
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE       -- 사용 여부
);

-- 컨디션 (1개 선택)
CREATE TABLE condition_status (
    condition_id SMALLSERIAL PRIMARY KEY,
    code         VARCHAR(30) NOT NULL UNIQUE,
    name         VARCHAR(20) NOT NULL,
    is_etc       BOOLEAN     NOT NULL DEFAULT FALSE,   -- '기타'면 TRUE → 화면에 직접 입력란
    sort_order   INT         NOT NULL DEFAULT 0,
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE
);

-- 참여 활동 (복수 선택)
CREATE TABLE activity_type (
    activity_type_id SMALLSERIAL PRIMARY KEY,
    code             VARCHAR(30) NOT NULL UNIQUE,
    name             VARCHAR(30) NOT NULL,
    is_etc           BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order       INT         NOT NULL DEFAULT 0,
    is_active        BOOLEAN     NOT NULL DEFAULT TRUE
);

-- 사전 지원 (복수 선택, '없음'은 다른 항목과 함께 고를 수 없음 — 서버에서 검사)
CREATE TABLE pre_support (
    pre_support_id SMALLSERIAL PRIMARY KEY,
    code           VARCHAR(30) NOT NULL UNIQUE,
    name           VARCHAR(30) NOT NULL,
    is_etc         BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order     INT         NOT NULL DEFAULT 0,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE
);

-- 이용자 반응 (복수 선택)
CREATE TABLE reaction (
    reaction_id SMALLSERIAL PRIMARY KEY,
    code        VARCHAR(30) NOT NULL UNIQUE,
    name        VARCHAR(30) NOT NULL,
    is_etc      BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order  INT         NOT NULL DEFAULT 0,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE
);

-- 긍정행동지원 (복수 선택)
CREATE TABLE pbs_support (
    pbs_support_id SMALLSERIAL PRIMARY KEY,
    code           VARCHAR(30) NOT NULL UNIQUE,
    name           VARCHAR(30) NOT NULL,
    is_etc         BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order     INT         NOT NULL DEFAULT 0,
    is_active      BOOLEAN     NOT NULL DEFAULT TRUE
);

-- 도전적 행동 영역 (타해·자해·파괴행동·기타) — 가지가 한 번 더 갈라짐
CREATE TABLE behavior_area (
    area_id    SMALLSERIAL PRIMARY KEY,
    code       VARCHAR(30) NOT NULL UNIQUE,            -- 프론트 BehaviorType과 같음 (otherHarm 등)
    name       VARCHAR(20) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0
);

-- 도전적 행동 세부 항목
CREATE TABLE behavior_item (
    item_id    SMALLSERIAL  PRIMARY KEY,
    area_id    SMALLINT     NOT NULL REFERENCES behavior_area(area_id), -- 어느 영역의 가지인지
    code       VARCHAR(30)  NOT NULL UNIQUE,           -- 프론트 detailIds와 같음 (oh-1, sh-other 등)
    name       VARCHAR(100) NOT NULL,                  -- '꼬집기'는 타해·자해 둘 다 있으므로 화면·통계에서는 영역과 함께 표시
    is_etc     BOOLEAN      NOT NULL DEFAULT FALSE,
    sort_order INT          NOT NULL DEFAULT 0,
    is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (area_id, name)                             -- 같은 영역 안에서만 이름 중복 금지
);


-- =====================================================================
-- [3] 기록 (주문서)
-- =====================================================================

-- 일지: 이용자 한 명의 하루 한 장 (명세서 JW·JD·JT)
CREATE TABLE daily_log (
    log_id        BIGSERIAL   PRIMARY KEY,
    client_id     BIGINT      NOT NULL REFERENCES client(client_id),      -- 누구의 일지인지. 저장 후에는 바꾸지 않음 (B-8 제안)
    log_date      DATE        NOT NULL,                                   -- 작성일 (한국 시간 업무일)
    status        VARCHAR(10) NOT NULL DEFAULT 'draft'
                  CHECK (status IN ('draft', 'completed')),               -- 작성 중 / 작성 완료. completed → draft 되돌리기는 서버에서 거부 (명세서 4.5)
    weather_id    SMALLINT    REFERENCES weather(weather_id),             -- 날씨 (1개 선택이라 연결 테이블 없이 바로 저장)
    condition_id  SMALLINT    REFERENCES condition_status(condition_id),  -- 컨디션 (1개 선택)
    condition_etc VARCHAR(200),                                           -- 컨디션 '기타'일 때 직접 쓴 내용
    remark        TEXT,                                                   -- 특이사항
    created_by    BIGINT      NOT NULL REFERENCES staff(staff_id),        -- 처음 쓴 사람 (바뀌지 않음)
    updated_by    BIGINT      REFERENCES staff(staff_id),                 -- 작성 완료 후 마지막으로 고친 사람
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),                     -- 동시 수정 비교에 씀 (명세서 4.6)
    UNIQUE (client_id, log_date)                                          -- 이용자·날짜당 1건. 위반하면 409 DIARY_ALREADY_EXISTS
);
CREATE INDEX idx_log_date ON daily_log(log_date);                          -- 달력·기간 조회 (GET /diaries?from&to)
CREATE INDEX idx_log_client_status_date ON daily_log(client_id, status, log_date); -- 통계 조회 (이용자 + 작성 완료 + 기간)

-- 활동 기록: 일지 안의 시간대별 한 줄
--   일지를 저장할 때마다 그 일지의 활동을 전부 지우고 새로 넣는다 (프론트가 활동 목록을 통째로 보냄)
CREATE TABLE activity_record (
    record_id    BIGSERIAL PRIMARY KEY,
    log_id       BIGINT    NOT NULL REFERENCES daily_log(log_id) ON DELETE CASCADE, -- 일지가 지워지면 함께 삭제
    start_time   TIME      NOT NULL,                   -- 시작 시각
    end_time     TIME      NOT NULL,                   -- 종료 시각. 30분 간격 검사는 하지 않음 (명세서 8.5)
    content      TEXT,                                 -- 활동 상세 내용 (유일한 서술 칸)
    has_behavior BOOLEAN   NOT NULL DEFAULT FALSE,     -- 도전적 행동 여부
    CHECK (end_time > start_time)                      -- 종료가 시작보다 늦어야 저장됨
);
CREATE INDEX idx_record_log ON activity_record(log_id);

-- 등록·수정 이력: 오늘의 소식 (명세서 HM-03, NW, 4.5)
--   작성 중(draft) 저장은 이력을 남기지 않는다
CREATE TABLE log_history (
    history_id  BIGSERIAL   PRIMARY KEY,
    log_id      BIGINT      NOT NULL REFERENCES daily_log(log_id) ON DELETE CASCADE, -- 어느 일지의 이력인지
    kind        VARCHAR(10) NOT NULL CHECK (kind IN ('created', 'updated')),        -- 등록 / 수정
    actor_id    BIGINT      NOT NULL REFERENCES staff(staff_id),                    -- 누가 했는지
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()                                  -- 언제 했는지
);
CREATE INDEX idx_history_occurred ON log_history(occurred_at DESC);                  -- 최신순 조회 (GET /news?since)


-- =====================================================================
-- [4] 연결 (주문 상세) - 복수 선택 항목마다 테이블 하나
--     한 시간대에 여러 개를 고르면 고른 개수만큼 줄이 생긴다
-- =====================================================================

CREATE TABLE record_activity_type (
    record_id        BIGINT   NOT NULL REFERENCES activity_record(record_id) ON DELETE CASCADE,
    activity_type_id SMALLINT NOT NULL REFERENCES activity_type(activity_type_id),
    etc_text         VARCHAR(200),                     -- '기타'일 때 직접 쓴 내용
    PRIMARY KEY (record_id, activity_type_id)          -- 같은 줄에 같은 항목 두 번 금지
);

CREATE TABLE record_pre_support (
    record_id      BIGINT   NOT NULL REFERENCES activity_record(record_id) ON DELETE CASCADE,
    pre_support_id SMALLINT NOT NULL REFERENCES pre_support(pre_support_id),
    etc_text       VARCHAR(200),
    PRIMARY KEY (record_id, pre_support_id)
);

CREATE TABLE record_reaction (
    record_id   BIGINT   NOT NULL REFERENCES activity_record(record_id) ON DELETE CASCADE,
    reaction_id SMALLINT NOT NULL REFERENCES reaction(reaction_id),
    etc_text    VARCHAR(200),
    PRIMARY KEY (record_id, reaction_id)
);

CREATE TABLE record_pbs_support (
    record_id      BIGINT   NOT NULL REFERENCES activity_record(record_id) ON DELETE CASCADE,
    pbs_support_id SMALLINT NOT NULL REFERENCES pbs_support(pbs_support_id),
    etc_text       VARCHAR(200),
    PRIMARY KEY (record_id, pbs_support_id)
);

-- 도전적 행동: 세부 항목에 연결하면 영역은 behavior_item을 타고 알 수 있다
CREATE TABLE record_behavior (
    record_id BIGINT   NOT NULL REFERENCES activity_record(record_id) ON DELETE CASCADE,
    item_id   SMALLINT NOT NULL REFERENCES behavior_item(item_id),
    etc_text  VARCHAR(200),                            -- '기타' 서술
    PRIMARY KEY (record_id, item_id)                   -- 같은 활동의 같은 행동은 1건 (통계 규칙과 같음)
);


-- =====================================================================
-- [5] 목록 기초 데이터 (복지관 양식 + 명세서 5.1 코드 규칙)
-- =====================================================================

INSERT INTO weather (code, name, sort_order) VALUES
 ('sunny','맑음',1), ('partly-cloudy','구름조금',2), ('cloudy','흐림',3), ('rain','비',4), ('snow','눈',5);

INSERT INTO condition_status (code, name, is_etc, sort_order) VALUES
 ('good','양호',FALSE,1), ('normal','보통',FALSE,2), ('low','저조',FALSE,3),
 ('observe','관찰요망',FALSE,4), ('other','기타',TRUE,5);

INSERT INTO activity_type (code, name, is_etc, sort_order) VALUES
 ('daily-living','일상생활훈련',FALSE,1), ('art','예술활동',FALSE,2), ('sports','체육활동',FALSE,3),
 ('preferred','선호활동',FALSE,4), ('community','지역참여활동',FALSE,5), ('cooking','요리활동',FALSE,6),
 ('field-trip','현장학습',FALSE,7), ('other','기타',TRUE,8);

INSERT INTO pre_support (code, name, is_etc, sort_order) VALUES
 ('reinforcer','강화물',FALSE,1), ('preparation','활동준비',FALSE,2), ('guidance','활동안내',FALSE,3),
 ('choice','선택지 제공',FALSE,4), ('modeling','모델링',FALSE,5), ('safety','안전교육',FALSE,6),
 ('rest','휴식',FALSE,7), ('none','없음',FALSE,8), ('other','기타',TRUE,9);

-- '도전적 행동' 항목 추가 여부는 회의 확인 (명세서 6장 4번)
INSERT INTO reaction (code, name, is_etc, sort_order) VALUES
 ('active','적극적 참여',FALSE,1), ('stable','안정적 참여',FALSE,2), ('partial','부분적 참여',FALSE,3),
 ('not-joined','참여하지 않음',FALSE,4), ('other','기타',TRUE,5);

INSERT INTO pbs_support (code, name, is_etc, sort_order) VALUES
 ('reinforcer','강화물',FALSE,1), ('encouragement','언어 촉진 및 격려',FALSE,2), ('choice','선택지 제공',FALSE,3),
 ('rest','휴식',FALSE,4), ('alternative','대체행동 지원',FALSE,5), ('other','기타',TRUE,6);

INSERT INTO behavior_area (code, name, sort_order) VALUES
 ('otherHarm','타해',1), ('selfHarm','자해',2), ('destruction','파괴행동',3), ('other','기타',4);

-- 타해 ('타인' 표현 제외, 복지관 답변 2)
INSERT INTO behavior_item (area_id, code, name, is_etc, sort_order)
SELECT a.area_id, v.code, v.name, v.is_etc, v.ord
FROM behavior_area a,
     (VALUES ('oh-1','때리기',FALSE,1), ('oh-2','발로 차기',FALSE,2), ('oh-3','물기',FALSE,3),
             ('oh-4','꼬집기',FALSE,4), ('oh-5','할퀴기',FALSE,5), ('oh-6','밀치기',FALSE,6),
             ('oh-7','침뱉기',FALSE,7), ('oh-8','머리카락 잡아당기기',FALSE,8),
             ('oh-9','물건을 던져 위협 또는 상해를 가하는 행동',FALSE,9), ('oh-other','기타',TRUE,10)
     ) AS v(code, name, is_etc, ord)
WHERE a.code = 'otherHarm';

-- 자해
INSERT INTO behavior_item (area_id, code, name, is_etc, sort_order)
SELECT a.area_id, v.code, v.name, v.is_etc, v.ord
FROM behavior_area a,
     (VALUES ('sh-1','머리 때리기',FALSE,1), ('sh-2','얼굴 때리기',FALSE,2), ('sh-3','손 물기',FALSE,3),
             ('sh-4','신체 부위 깨물기',FALSE,4), ('sh-5','머리 쥐어뜯기',FALSE,5), ('sh-6','머리 박기',FALSE,6),
             ('sh-7','피부 뜯기 및 상처 파기',FALSE,7), ('sh-8','신체를 벽이나 바닥 등에 부딪히기',FALSE,8),
             ('sh-9','꼬집기',FALSE,9), ('sh-10','귀·눈·코·입 등 신체 부위 파기',FALSE,10), ('sh-other','기타',TRUE,11)
     ) AS v(code, name, is_etc, ord)
WHERE a.code = 'selfHarm';

-- 파괴행동
INSERT INTO behavior_item (area_id, code, name, is_etc, sort_order)
SELECT a.area_id, v.code, v.name, v.is_etc, v.ord
FROM behavior_area a,
     (VALUES ('ds-1','물건 던지기',FALSE,1), ('ds-2','물건 내리치기',FALSE,2), ('ds-3','물건 밟거나 짓누르기',FALSE,3),
             ('ds-4','물건 찢기',FALSE,4), ('ds-5','물건 부수기',FALSE,5), ('ds-6','가구·시설물 손상시키기',FALSE,6),
             ('ds-7','물건을 잡아당기거나 뜯어내기',FALSE,7), ('ds-8','물건을 뒤엎거나 쏟기',FALSE,8), ('ds-other','기타',TRUE,9)
     ) AS v(code, name, is_etc, ord)
WHERE a.code = 'destruction';

-- 기타 영역 (서술만)
INSERT INTO behavior_item (area_id, code, name, is_etc, sort_order)
SELECT area_id, 'other', '기타', TRUE, 1 FROM behavior_area WHERE code = 'other';

