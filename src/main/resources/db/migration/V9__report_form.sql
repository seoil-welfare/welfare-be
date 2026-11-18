-- =====================================================================
-- 출력 양식 (기관이 올린 일별·월별활동일지 docx)
-- - 기본 양식은 프론트 앱 안에 있어 여기 없다
-- - 지우지 않는다. 숨기면(active = false) 출력할 때 고르는 목록에서만 빠진다 (지난 기록을 이전 양식으로 다시 출력할 수 있게)
-- - 파일은 5MB 이하라 DB에 그대로 저장한다 (RDS 백업에 함께 들어간다)
-- =====================================================================
CREATE TABLE report_form (
    form_id     BIGSERIAL    PRIMARY KEY,
    kind        VARCHAR(10)  NOT NULL CHECK (kind IN ('daily', 'monthly')),
    name        VARCHAR(30)  NOT NULL,                 -- 담당자가 정한 이름 (출력할 때 이 이름으로 고른다)
    file_name   VARCHAR(200) NOT NULL,                 -- 올린 파일 이름 (받을 때 그대로 쓴다)
    content     BYTEA        NOT NULL,
    file_size   INT          NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    uploaded_by BIGINT       NOT NULL REFERENCES staff(staff_id),
    uploaded_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_report_form_kind ON report_form (kind, uploaded_at DESC);
