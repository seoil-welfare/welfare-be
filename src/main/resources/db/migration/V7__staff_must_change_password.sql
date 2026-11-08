-- 임시 비밀번호로 로그인한 직원에게 비밀번호 변경을 요구한다 (첫 로그인 비밀번호 변경)
-- 계정 추가·임시 비밀번호 발급 때 TRUE, 본인이 비밀번호를 바꾸면 FALSE (AuthService)
-- 이미 있는 계정은 FALSE로 둔다
ALTER TABLE staff ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
