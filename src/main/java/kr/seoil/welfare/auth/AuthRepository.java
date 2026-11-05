package kr.seoil.welfare.auth;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * staff 테이블에서 '로그인에만 필요한' 칸을 다루는 SQL 모음.
 *
 * 화면에 보여 줄 직원 정보(이름·부서·직위)는 StaffQueries가 담당한다. 여기서는
 * password_hash 같은 민감한 칸을 다루므로 일부러 분리했다 — 해시가 StaffDto에 섞여
 * 실수로 JSON으로 나가는 일을 막기 위한 것이다.
 */
@Repository
public class AuthRepository {

    /** 로그인 검사에 필요한 최소 정보. 비밀번호 해시가 들어 있으므로 절대 그대로 응답하지 않는다 */
    public record Account(long staffId, String passwordHash, boolean active) {
    }

    private static final RowMapper<Account> ACCOUNT_MAPPER = (rs, rowNum) -> new Account(
            rs.getLong("staff_id"),
            rs.getString("password_hash"),
            rs.getBoolean("is_active"));

    private static final String ACCOUNT_COLUMNS = "staff_id, password_hash, is_active";

    private final JdbcTemplate jdbc;

    public AuthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 아이디로 계정 찾기 (없으면 빈 Optional) */
    public Optional<Account> findByLoginId(String loginId) {
        return jdbc.query("SELECT " + ACCOUNT_COLUMNS + " FROM staff WHERE login_id = ?",
                        ACCOUNT_MAPPER, loginId)
                .stream().findFirst();
    }

    /** 직원 번호로 계정 찾기 (세션이 가리키는 계정이 아직 쓸 수 있는지 확인할 때) */
    public Optional<Account> findById(long staffId) {
        return jdbc.query("SELECT " + ACCOUNT_COLUMNS + " FROM staff WHERE staff_id = ?",
                        ACCOUNT_MAPPER, staffId)
                .stream().findFirst();
    }

    /** 아이디가 이미 쓰이고 있는지 (회원가입 전 미리 확인) */
    public boolean existsLoginId(String loginId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM staff WHERE login_id = ?", Integer.class, loginId);
        return count != null && count > 0;
    }

    /**
     * 새 계정 한 줄 추가. 만들어진 직원 번호를 돌려준다.
     * RETURNING은 PostgreSQL 기능으로, INSERT와 번호 조회를 한 번에 한다.
     * birthDate는 비어 있을 수 있다(null → DB에도 NULL).
     */
    public long insert(String loginId, String passwordHash, String name,
                       String department, String position, String role, LocalDate birthDate) {
        Long staffId = jdbc.queryForObject("""
                INSERT INTO staff (login_id, password_hash, name, department, position, role, birth_date)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING staff_id
                """, Long.class, loginId, passwordHash, name, department, position, role, birthDate);
        if (staffId == null) {                       // 정상 동작에서는 일어나지 않는다
            throw new IllegalStateException("계정을 추가했지만 staff_id를 받지 못했습니다.");
        }
        return staffId;
    }

    /** 비밀번호 해시 바꾸기 (SE-02) */
    public void updatePasswordHash(long staffId, String passwordHash) {
        jdbc.update("UPDATE staff SET password_hash = ?, updated_at = now() WHERE staff_id = ?",
                passwordHash, staffId);
    }
}