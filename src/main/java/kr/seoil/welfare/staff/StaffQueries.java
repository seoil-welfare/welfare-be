package kr.seoil.welfare.staff;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * staff 테이블 조회 SQL 모음.
 * 로그인(지민씨)과 일지·직원 목록이 함께 쓴다.
 */
@Repository                                                     // DB 담당 클래스라는 표시
public class StaffQueries {

    // 조회 결과 한 줄(Row)을 StaffDto로 바꾸는 규칙
    private static final RowMapper<StaffDto> MAPPER = (rs, rowNum) -> new StaffDto(
            String.valueOf(rs.getLong("staff_id")),             // 숫자 → 문자열
            rs.getString("name"),
            rs.getString("department"),
            rs.getString("position"),
            rs.getString("role"),
            rs.getString("login_id"));

    private static final String COLUMNS = "staff_id, name, department, position, role, login_id";

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
}
