package kr.seoil.welfare.auth;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import kr.seoil.welfare.staff.StaffDto;
import kr.seoil.welfare.staff.StaffQueries;

/**
 * 테스트용 메모리 staff 테이블. DB 없이 AuthService 규칙을 검사할 때 쓴다.
 * queries()는 StaffQueries, authRepository()는 AuthRepository 대신 AuthService에 넣는다.
 */
class FakeStaffStore {

    final Map<Long, StaffDto> rows = new LinkedHashMap<>();   // staff_id → 한 줄
    final List<String> log = new ArrayList<>();                // 바꾼 순서 기록 ("role:3:teamLead", "active:1:false")

    /** 직원 한 명 추가 (position은 role에서 정함) */
    FakeStaffStore put(long id, String role, boolean active) {
        rows.put(id, new StaffDto(String.valueOf(id), "직원" + id, "주간활동지원팀",
                AuthService.positionOf(role), role, "user" + id, active, null));
        return this;
    }

    StaffDto get(long id) {
        return rows.get(id);
    }

    long countRole(String role) {
        return rows.values().stream().filter(s -> role.equals(s.role())).count();
    }

    StaffQueries queries() {
        return new StaffQueries(null) {
            @Override
            public List<StaffDto> findAll() {
                return List.copyOf(rows.values());
            }

            @Override
            public Optional<StaffDto> findById(long staffId) {
                return Optional.ofNullable(rows.get(staffId));
            }

            @Override
            public Optional<StaffDto> findByIdForUpdate(long staffId) {
                log.add("lock:" + staffId);
                return Optional.ofNullable(rows.get(staffId));
            }

            @Override
            public int updateRole(long staffId, String role, String position) {
                StaffDto s = rows.get(staffId);
                if (s == null) {
                    return 0;
                }
                log.add("role:" + staffId + ":" + role);
                rows.put(staffId, new StaffDto(s.id(), s.name(), s.department(), position, role,
                        s.loginId(), s.active(), s.birthDate()));
                return 1;
            }

            @Override
            public int updateActive(long staffId, boolean active) {
                StaffDto s = rows.get(staffId);
                if (s == null) {
                    return 0;
                }
                log.add("active:" + staffId + ":" + active);
                rows.put(staffId, new StaffDto(s.id(), s.name(), s.department(), s.position(), s.role(),
                        s.loginId(), active, s.birthDate()));
                return 1;
            }
        };
    }

    AuthRepository authRepository() {
        return new AuthRepository(null) {
            @Override
            public Optional<Account> findById(long staffId) {
                StaffDto s = rows.get(staffId);
                return s == null ? Optional.empty() : Optional.of(new Account(staffId, "hash", s.active()));
            }

            @Override
            public boolean existsLoginId(String loginId) {
                return rows.values().stream().anyMatch(s -> loginId.equals(s.loginId()));
            }

            @Override
            public long insert(String loginId, String passwordHash, String name, String department,
                               String position, String role, LocalDate birthDate) {
                long id = rows.keySet().stream().mapToLong(Long::longValue).max().orElse(0) + 1;
                rows.put(id, new StaffDto(String.valueOf(id), name, department, position, role, loginId, true,
                        birthDate == null ? null : birthDate.toString()));
                return id;
            }
        };
    }
}
