-- =====================================================================
-- V3: 활동 시작·종료 시간을 비워 둘 수 있게 변경
-- 이유: 프론트는 시간을 고르기 전에도 '나중에 이어서'(작성 중)로 저장할 수 있다
-- 종료 > 시작 CHECK는 그대로 유지 (둘 다 있을 때만 검사됨)
-- =====================================================================
ALTER TABLE activity_record ALTER COLUMN start_time DROP NOT NULL;
ALTER TABLE activity_record ALTER COLUMN end_time   DROP NOT NULL;
