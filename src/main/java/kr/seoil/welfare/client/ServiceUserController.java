package kr.seoil.welfare.client;

import java.util.List;

import kr.seoil.welfare.auth.SessionStaff;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * GET /api/service-users — 이용자 목록 (일지 작성 화면의 '대상 이용자' 선택에 필요)
 * 등록·수정·종결(POST/PUT)은 아직 없음. 담당자가 정해지면 여기에 추가한다.
 */
@RestController
@RequestMapping("/api/service-users")
public class ServiceUserController {

    private final JdbcTemplate jdbc;

    public ServiceUserController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<ServiceUserDto> list(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId) {
        SessionStaff.require(staffId);                          // 로그인 안 했으면 401
        // 종결된 이용자도 포함 (과거 일지에 이름을 보여 줘야 하므로)
        return jdbc.query(
                "SELECT client_id, name, birth_date, staff_id, status FROM client ORDER BY client_id",
                (rs, rowNum) -> {
                    long assigned = rs.getLong("staff_id");     // NULL이면 0이 나옴
                    boolean noStaff = rs.wasNull();             // 방금 읽은 값이 NULL이었는지
                    return new ServiceUserDto(
                            String.valueOf(rs.getLong("client_id")),
                            rs.getString("name"),
                            rs.getDate("birth_date").toLocalDate().toString(),   // 2001-04-18 형식
                            noStaff ? "" : String.valueOf(assigned),
                            rs.getString("status"));
                });
    }
}
