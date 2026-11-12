-- =====================================================================
-- V8 오늘 카드 (AI) — docs/오늘카드_API_명세.md
--   일지를 작성 완료하면 AI가 이용자용 그림 카드(앞면)와 보호자 알림장(뒷면)을 만든다.
--   담당자가 확인·수정한 뒤 공유 링크를 만들어 보호자에게 직접 전달한다 (문자 발송 없음).
-- =====================================================================

-- 이용자: 카드에 보일 별칭, 보호자 카드 수신 동의
ALTER TABLE client ADD COLUMN card_nickname   VARCHAR(20);                          -- 비어 있으면 카드에 '이용자'로 보인다 (실명은 쓰지 않음)
ALTER TABLE client ADD COLUMN card_consent    BOOLEAN NOT NULL DEFAULT FALSE;       -- 동의가 없으면 공유 링크를 만들 수 없다
ALTER TABLE client ADD COLUMN card_consent_at TIMESTAMPTZ;                          -- 동의를 등록한 시각 (동의를 끄면 비움)

-- 참여 활동: 기본 그림. AI가 목록 밖의 그림을 고르면 이 값을 쓴다
ALTER TABLE activity_type ADD COLUMN pictogram VARCHAR(20) NOT NULL DEFAULT 'favorite'
    CHECK (pictogram IN ('music','meal','art','sports','cooking','walk','outing','daily','favorite','rest'));
UPDATE activity_type SET pictogram = CASE code
    WHEN 'daily-living' THEN 'daily'
    WHEN 'art'          THEN 'art'
    WHEN 'sports'       THEN 'sports'
    WHEN 'community'    THEN 'outing'
    WHEN 'cooking'      THEN 'cooking'
    WHEN 'field-trip'   THEN 'outing'
    ELSE 'favorite' END;

-- 카드 (일지 1건에 1장)
CREATE TABLE today_card (
    card_id             BIGSERIAL   PRIMARY KEY,
    log_id              BIGINT      NOT NULL UNIQUE REFERENCES daily_log(log_id) ON DELETE CASCADE,
    status              VARCHAR(12) NOT NULL
                        CHECK (status IN ('generating','ready','failed','outdated','sent')),
    summary             TEXT,                                  -- 마지막 장 '오늘의 한마디'
    needs_call          BOOLEAN     NOT NULL DEFAULT FALSE,    -- 담당자가 '다쳤거나 위험했던 일이 있어요'를 체크
    fail_reason         TEXT,                                  -- 로그용. 응답에는 내보내지 않는다
    token_hash          CHAR(64)    UNIQUE,                    -- 공유 링크 토큰의 SHA-256. 보호자 요청은 이 값으로 찾는다
    token_enc           TEXT,                                  -- 같은 링크를 다시 복사할 수 있게 서버 비밀키로 암호화한 토큰
    sent_at             TIMESTAMPTZ,                           -- 공유 링크를 만든 시각
    expires_on          DATE,                                  -- 링크가 열리는 마지막 날
    verify_fail_count   INT         NOT NULL DEFAULT 0,        -- 보호자 확인(생일 4자리) 연속 실패 횟수
    verify_locked_until TIMESTAMPTZ,                           -- 5번 틀리면 30분 잠금
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),    -- 동시 수정 비교, generating 멈춤 판단에 씀
    CHECK ((status = 'sent') = (token_hash IS NOT NULL))       -- 보낸 카드만 링크가 있다
);

-- 카드 항목 (시간이 있는 활동마다 1개)
CREATE TABLE today_card_item (
    item_id        BIGSERIAL    PRIMARY KEY,
    card_id        BIGINT       NOT NULL REFERENCES today_card(card_id) ON DELETE CASCADE,
    record_id      BIGINT       REFERENCES activity_record(record_id) ON DELETE SET NULL,   -- 일지를 고치면 활동이 새로 만들어져 비게 된다
    sort_order     INT          NOT NULL,
    start_time     TIME         NOT NULL,
    activity_label VARCHAR(50)  NOT NULL,
    word           VARCHAR(10)  NOT NULL,
    pictogram      VARCHAR(20)  NOT NULL
                   CHECK (pictogram IN ('music','meal','art','sports','cooking','walk','outing','daily','favorite','rest')),
    mood           VARCHAR(10)  NOT NULL CHECK (mood IN ('excited','good','tired')),
    easy_text      VARCHAR(100) NOT NULL,
    guardian_text  VARCHAR(500) NOT NULL,
    home_tip       VARCHAR(200)
);
CREATE INDEX idx_card_item_card ON today_card_item(card_id);

-- 보호자가 고른 결과 (카드 1장에 1개, 다시 고르면 덮어씀)
CREATE TABLE today_card_pick (
    card_id    BIGINT      PRIMARY KEY REFERENCES today_card(card_id) ON DELETE CASCADE,
    item_id    BIGINT      REFERENCES today_card_item(item_id) ON DELETE SET NULL,
    how        VARCHAR(10) CHECK (how IN ('pointed','observed')),
    skipped    BOOLEAN     NOT NULL,
    picked_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
