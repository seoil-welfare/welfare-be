package kr.seoil.welfare.staff;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * staff 테이블 조회 SQL 모음. ==직원 테이블에 SQL을 보내는 파일, 판단은 하지 않으며 SQL만 실행
 * 로그인과 일지·직원 목록이 함께 쓴다.
 */
@Repository                                                     // DB 담당 클래스라는 표시
public class StaffQueries {                                     //Spring이 만들어서 AuthService 등에 넣어줌

    // 조회 결과 한 줄(Row)을 StaffDto로 바꾸는 규칙
    private static final RowMapper<StaffDto> MAPPER = (rs, rowNum) -> new StaffDto(
            String.valueOf(rs.getLong("staff_id")),             // 숫자 → 문자열
            rs.getString("name"),
            rs.getString("department"),
            rs.getString("position"),
            rs.getString("role"),
            rs.getString("login_id"),
            rs.getBoolean("is_active"),   // 사용중인지를 꺼내어 StaffDto의 active에 담는다
            rs.getString("birth_date"));  // 생년월일을 꺼내어 StaffDto의 birthDate에 담는다 (DATE → "1995-03-14", 없으면 null)

    private static final String COLUMNS = "staff_id, name, department, position, role, login_id, is_active, birth_date";
    //COLUMNS는 SELECT할 칸 목록
    private final JdbcTemplate jdbc;                            // SQL 실행 도구

    public StaffQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 전체 직원 (사용 중지 직원도 포함 — 과거 일지에 작성자 이름을 보여 줘야 하므로) */
    public List<StaffDto> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM staff ORDER BY staff_id", MAPPER);
    }

    /** 직원 번호로 1명 찾기 (없으면 빈 Optional) */
    public Optional<StaffDto> findById(long staffId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM staff WHERE staff_id = ?", MAPPER, staffId)
                .stream().findFirst();
    }

    /**
     * 직원 1명을 찾으면서 그 줄을 트랜잭션이 끝날 때까지 잠근다 (SELECT ... FOR UPDATE).
     * 관리 권한 이양을 동시에 두 번 눌렀을 때 두 번째 요청이 기다렸다가 바뀐 역할을 보게 한다 (AuthService.transferDirector)
     */
    public Optional<StaffDto> findByIdForUpdate(long staffId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM staff WHERE staff_id = ? FOR UPDATE", MAPPER, staffId)
                .stream().findFirst();
    }

    /**
     * 계정 사용 중지(false) / 다시 사용(true). 바뀐 줄 수를 돌려준다.
     * 줄을 지우지 않는다 — 그 직원이 쓴 과거 일지에 작성자 이름이 남아야 하기 때문 (명세서 8.3)
     */
    public int updateActive(long staffId, boolean active) {
        return jdbc.update("UPDATE staff SET is_active = ?, updated_at = now() WHERE staff_id = ?",
                active, staffId);
    }

    /** 역할과 직위 표기를 함께 바꾼다. 바뀐 줄 수를 돌려준다 (AuthService.changeRole에서 호출) */
    public int updateRole(long staffId, String role, String position) {
        return jdbc.update("UPDATE staff SET role = ?, position = ?, updated_at = now() WHERE staff_id = ?",
                role, position, staffId);
    }
}