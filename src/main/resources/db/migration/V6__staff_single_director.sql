-- 국장은 한 명만 (관리 권한 이양 기능, AuthService.transferDirector)
-- 이양은 원래 국장을 먼저 내리고 대상을 올리므로 이 인덱스에 걸리지 않는다
CREATE UNIQUE INDEX staff_single_director ON staff (role) WHERE role = 'director';
