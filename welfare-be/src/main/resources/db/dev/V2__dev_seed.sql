-- =====================================================================
-- 개발용 가상 데이터 (local 프로필에서만 들어감)
-- 프론트 mock(src/data/fixtures.ts)과 같은 가상 직원·이용자
-- 실제 이용자 이름·생년월일은 절대 넣지 않는다
-- 비밀번호는 모두 demo1234 (BCrypt 해시)
-- =====================================================================

INSERT INTO staff (login_id, password_hash, name, department, position, role) VALUES
  ('demo.staff1',   '$2a$10$CS5blBJo5wROAHWXkE7zXumKFskFpKe8B8BRZI/iseMfS0EBEhRHm', '가 담당', '주간활동지원팀', '사회복지사', 'staff'),
  ('demo.staff2',   '$2a$10$CS5blBJo5wROAHWXkE7zXumKFskFpKe8B8BRZI/iseMfS0EBEhRHm', '나 담당', '주간활동지원팀', '사회복지사', 'staff'),
  ('demo.staff3',   '$2a$10$CS5blBJo5wROAHWXkE7zXumKFskFpKe8B8BRZI/iseMfS0EBEhRHm', '다 담당', '주간활동지원팀', '사회복지사', 'staff'),
  ('demo.lead',     '$2a$10$CS5blBJo5wROAHWXkE7zXumKFskFpKe8B8BRZI/iseMfS0EBEhRHm', '라 팀장', '주간활동지원팀', '팀장',       'teamLead'),
  ('demo.director', '$2a$10$CS5blBJo5wROAHWXkE7zXumKFskFpKe8B8BRZI/iseMfS0EBEhRHm', '마 국장', '사무국',         '국장',       'director');

-- 가상 이용자 4명 (마지막 1명은 종결 상태)
INSERT INTO client (name, birth_date, staff_id, status, closed_at)
SELECT v.name, v.birth::date, s.staff_id, v.status, v.closed_at
FROM (VALUES
  (1, '김가상', '1999-12-07', 'demo.staff1', 'active', NULL::timestamptz),
  (2, '이예시', '2001-04-18', 'demo.staff2', 'active', NULL),
  (3, '박모의', '1997-08-30', 'demo.staff3', 'active', NULL),
  (4, '최종결', '1995-02-11', 'demo.staff1', 'closed', now())
) AS v(ord, name, birth, login_id, status, closed_at)
JOIN staff s ON s.login_id = v.login_id
ORDER BY v.ord;                                   -- 위에 적은 순서대로 번호(client_id)가 매겨지게
