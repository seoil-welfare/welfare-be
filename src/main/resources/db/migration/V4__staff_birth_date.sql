-- 직원 생년월일 칸 추가 (노션 할 일: 직원 생년월일 추가)
--
-- NULL 허용: 이미 있는 직원 줄(가상 데이터 포함)에는 값이 없고,
-- 프론트 회원가입·계정 추가 화면에 아직 입력란이 없어서 비어 있어도 저장되게 한다.
-- 형식(YYYY-MM-DD)과 '오늘보다 뒤가 아닌지' 검사는 서버(AuthService)에서 한다.
-- (오늘 날짜를 쓰는 검사는 DB CHECK 제약으로 걸 수 없다)
ALTER TABLE staff ADD COLUMN birth_date DATE;