package kr.seoil.welfare.client;

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.auth.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용자 등록·수정·종결 (명세서 US·CU, FR-91)
 *
 *  - 조회·등록·수정은 모든 직원, 종결·재개는 팀장·국장만 가능
 *  - 종결은 삭제가 아니다. status = closed, closed_at = 지금. 과거 일지는 그대로 남는다
 *  - 담당 직원은 비워 둘 수 있다 ('담당 미지정'). 고를 때는 사용 중인 직원만
 *  - 오늘 카드 칸(별칭·수신 동의)은 보내지 않으면 그대로 둔다. 카드 칸이 없는 이전 화면이 저장해도 지워지지 않게
 *    동의를 켜면 동의 시각을 지금으로, 끄면 비운다
 */
@Service
public class ServiceUserService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int NAME_MAX = 50;                     // DB VARCHAR(50)
    private static final int NICKNAME_MAX = 20;                 // DB VARCHAR(20)

    private static final String COLUMNS = "client_id, name, birth_date, staff_id, status, card_nickname, card_consent, card_consent_at";

    // 조회 결과 한 줄 → ServiceUserDto
    private static final RowMapper<ServiceUserDto> MAPPER = (rs, rowNum) -> {
        long assigned = rs.getLong("staff_id");                 // NULL이면 0이 나옴
        boolean noStaff = rs.wasNull();                         // 방금 읽은 값이 NULL이었는지
        return new ServiceUserDto(
                String.valueOf(rs.getLong("client_id")),
                rs.getString("name"),
                rs.getDate("birth_date").toLocalDate().toString(),   // 2001-04-18 형식
                noStaff ? "" : String.valueOf(assigned),
                rs.getString("status"),
                rs.getString("card_nickname"),
                rs.getBoolean("card_consent"),
                consentAt(rs.getObject("card_consent_at", OffsetDateTime.class)));
    };

    private static String consentAt(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(KST).toOffsetDateTime().toString();
    }

    private final JdbcTemplate jdbc;
    private final AuthService authService;

    public ServiceUserService(JdbcTemplate jdbc, AuthService authService) {
        this.jdbc = jdbc;
        this.authService = authService;
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
                "INSERT INTO client (name, birth_date, staff_id, card_nickname, card_consent, card_consent_at)"
                        + " VALUES (?, ?, ?, ?, ?, CASE WHEN ? THEN now() END) RETURNING client_id",
                Long.class, c.name, Date.valueOf(c.birthDate), c.staffId, blankToNull(c.nickname),
                Boolean.TRUE.equals(c.consent), Boolean.TRUE.equals(c.consent));
        return find(id);
    }

    /** 이름·생년월일·담당 직원 수정 (종결된 이용자도 정보 수정은 가능) */
    @Transactional
    public ServiceUserDto update(String userId, ServiceUserInput input) {
        long id = parsePathId(userId);
        Checked c = check(input);
        int updated = jdbc.update("UPDATE client SET name = ?, birth_date = ?, staff_id = ? WHERE client_id = ?",
                c.name, Date.valueOf(c.birthDate), c.staffId, id);
        if (updated > 0 && c.nickname != null) {                // null이면 그대로, 빈 문자열이면 지움
            jdbc.update("UPDATE client SET card_nickname = ? WHERE client_id = ?", blankToNull(c.nickname), id);
        }
        if (updated > 0 && c.consent != null) {
            // 켤 때: 이미 켜져 있으면 동의 시각을 그대로 둔다 / 끌 때: 시각을 비운다
            jdbc.update("UPDATE client SET card_consent_at = CASE WHEN ? THEN coalesce(CASE WHEN card_consent THEN card_consent_at END, now()) END,"
                    + " card_consent = ? WHERE client_id = ?", c.consent, c.consent, id);
        }
        if (updated == 0) {
            throw ApiException.notFound("이용자");
        }
        return find(id);
    }

    /** 이용 종결. 이미 종결된 이용자면 그대로 돌려준다 (종결 시각을 덮어쓰지 않게) */
    @Transactional
    public ServiceUserDto close(String userId, long actorId) {
        requireManager(actorId);
        long id = parsePathId(userId);
        jdbc.update("UPDATE client SET status = 'closed', closed_at = now() WHERE client_id = ? AND status = 'active'", id);
        return find(id);
    }

    /** 종결 취소. 과거 일지를 그대로 두고 같은 이용자를 다시 활성화한다. */
    @Transactional
    public ServiceUserDto reopen(String userId, long actorId) {
        requireManager(actorId);
        long id = parsePathId(userId);
        jdbc.update("UPDATE client SET status = 'active', closed_at = NULL WHERE client_id = ? AND status = 'closed'", id);
        return find(id);
    }

    private void requireManager(long actorId) {
        String role = authService.me(actorId).role();
        if (!"teamLead".equals(role) && !"director".equals(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "이용 종결·재개는 팀장·국장만 할 수 있어요.");
        }
    }

    // ------------------------------------------------------------------
    // 도우미
    // ------------------------------------------------------------------

    /** 검사를 통과한 저장용 값 */
    private record Checked(String name, LocalDate birthDate, Long staffId, String nickname, Boolean consent) {
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
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
        String nickname = input.cardNickname() == null ? null : input.cardNickname().trim();
        if (nickname != null && nickname.length() > NICKNAME_MAX) {
            throw ApiException.badRequest("카드 별칭은 " + NICKNAME_MAX + "자까지 쓸 수 있어요.");
        }
        return new Checked(name, birthDate, staffId, nickname, input.cardConsent());
    }

    private ServiceUserDto find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM client WHERE client_id = ?", MAPPER, id)
                .stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("이용자"));
    }

    /** 주소의 id (/api/service-users/abc) → 숫자. 잘못되면 그런 이용자는 없는 것이므로 404 */
    private static long parsePathId(String id) {
        try {
            long parsed = Long.parseLong(id == null ? "" : id.trim());
            if (parsed <= 0) throw ApiException.notFound("이용자");
            return parsed;
        } catch (NumberFormatException e) {
            throw ApiException.notFound("이용자");
        }
    }
}
