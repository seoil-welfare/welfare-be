package kr.seoil.welfare.client;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

import kr.seoil.welfare.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용자 등록·수정·종결 (명세서 US·CU, FR-91)
 *
 *  - 권한: 모든 직원 가능 (명세서 8.1)
 *  - 종결은 삭제가 아니다. status = closed, closed_at = 지금. 과거 일지는 그대로 남는다
 *  - 담당 직원은 비워 둘 수 있다 ('담당 미지정'). 고를 때는 사용 중인 직원만
 */
@Service
public class ServiceUserService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int NAME_MAX = 50;                     // DB VARCHAR(50)

    private static final String COLUMNS = "client_id, name, birth_date, staff_id, status";

    // 조회 결과 한 줄 → ServiceUserDto
    private static final RowMapper<ServiceUserDto> MAPPER = (rs, rowNum) -> {
        long assigned = rs.getLong("staff_id");                 // NULL이면 0이 나옴
        boolean noStaff = rs.wasNull();                         // 방금 읽은 값이 NULL이었는지
        return new ServiceUserDto(
                String.valueOf(rs.getLong("client_id")),
                rs.getString("name"),
                rs.getDate("birth_date").toLocalDate().toString(),   // 2001-04-18 형식
                noStaff ? "" : String.valueOf(assigned),
                rs.getString("status"));
    };

    private final JdbcTemplate jdbc;

    public ServiceUserService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 전체 이용자. 종결된 이용자도 포함 (과거 일지에 이름을 보여 줘야 하므로) */
    @Transactional(readOnly = true)
    public List<ServiceUserDto> list() {
        return jdbc.query("SELECT " + COLUMNS + " FROM client ORDER BY client_id", MAPPER);
    }

    /** 새 이용자 등록 → 이용 중(active) */
    @Transactional
    public ServiceUserDto create(ServiceUserInput input) {
        Checked c = check(input);
        Long id = jdbc.queryForObject(
                "INSERT INTO client (name, birth_date, staff_id) VALUES (?, ?, ?) RETURNING client_id",
                Long.class, c.name, Date.valueOf(c.birthDate), c.staffId);
        return find(id);
    }

    /** 이름·생년월일·담당 직원 수정 (종결된 이용자도 정보 수정은 가능) */
    @Transactional
    public ServiceUserDto update(String userId, ServiceUserInput input) {
        long id = parsePathId(userId);
        Checked c = check(input);
        int updated = jdbc.update("UPDATE client SET name = ?, birth_date = ?, staff_id = ? WHERE client_id = ?",
                c.name, Date.valueOf(c.birthDate), c.staffId, id);
        if (updated == 0) {
            throw ApiException.notFound("이용자");
        }
        return find(id);
    }

    /** 이용 종결. 이미 종결된 이용자면 그대로 돌려준다 (종결 시각을 덮어쓰지 않게) */
    @Transactional
    public ServiceUserDto close(String userId) {
        long id = parsePathId(userId);
        jdbc.update("UPDATE client SET status = 'closed', closed_at = now() WHERE client_id = ? AND status = 'active'", id);
        return find(id);
    }

    // ------------------------------------------------------------------
    // 도우미
    // ------------------------------------------------------------------

    /** 검사를 통과한 저장용 값 */
    private record Checked(String name, LocalDate birthDate, Long staffId) {
    }

    private Checked check(ServiceUserInput input) {
        if (input == null) {
            throw ApiException.badRequest("이용자 정보를 입력해 주세요.");
        }
        String name = input.name() == null ? "" : input.name().trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("이름을 입력해 주세요.");
        }
        if (name.length() > NAME_MAX) {
            throw ApiException.badRequest("이름이 너무 길어요.");
        }

        LocalDate birthDate;
        try {
            birthDate = LocalDate.parse(input.birthDate() == null ? "" : input.birthDate().trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("생년월일을 YYYY-MM-DD 형식으로 입력해 주세요.");
        }
        if (birthDate.isAfter(LocalDate.now(KST))) {
            throw ApiException.badRequest("생년월일이 오늘보다 뒤예요.");
        }

        Long staffId = null;                                    // 비어 있으면 '담당 미지정'
        String assigned = input.assignedStaffId();
        if (assigned != null && !assigned.isBlank()) {
            try {
                staffId = Long.parseLong(assigned.trim());
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("담당 직원 번호가 올바르지 않아요.");
            }
            Integer active = jdbc.queryForObject(
                    "SELECT count(*) FROM staff WHERE staff_id = ? AND is_active", Integer.class, staffId);
            if (active == null || active == 0) {
                throw ApiException.badRequest("담당 직원으로 고를 수 없는 직원이에요.");
            }
        }
        return new Checked(name, birthDate, staffId);
    }

    private ServiceUserDto find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM client WHERE client_id = ?", MAPPER, id)
                .stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("이용자"));
    }

    /** 주소의 id (/api/service-users/abc) → 숫자. 잘못되면 그런 이용자는 없는 것이므로 404 */
    private static long parsePathId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            throw ApiException.notFound("이용자");
        }
    }
}
