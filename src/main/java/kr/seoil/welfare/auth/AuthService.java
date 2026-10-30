package kr.seoil.welfare.auth;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import kr.seoil.welfare.auth.dto.ChangePasswordRequest;
import kr.seoil.welfare.auth.dto.LoginRequest;
import kr.seoil.welfare.auth.dto.SignupRequest;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.staff.CreateStaffRequest;
import kr.seoil.welfare.staff.CreatedStaffResponse;
import kr.seoil.welfare.staff.StaffDto;
import kr.seoil.welfare.staff.StaffQueries;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인·회원가입·비밀번호 변경의 규칙을 모아 둔 곳 (명세서 3.1 LG, 3.18 SE).
 *
 * 세션(쿠키)을 만들고 지우는 일은 AuthController가 한다. 여기서는 "이 사람이 맞는가"만 판단한다.
 * 이렇게 나누면 서비스 코드는 HttpSession 없이도 테스트할 수 있다.
 */
@Service
public class AuthService {

    /** 아이디에 쓸 수 있는 글자: 영문 소문자·숫자·점·밑줄·붙임표, 4~50자 (DB VARCHAR(50)) */
    private static final Pattern LOGIN_ID = Pattern.compile("[a-z0-9._-]{4,50}");

    private static final String DEFAULT_DEPARTMENT = "주간활동지원팀";   // 명세서 SF-02 기본값

    private static final Set<String> ROLES = Set.of("staff", "teamLead", "director");
    /** 계정 추가를 할 수 있는 역할 (지금 프론트가 팀장·국장에게만 메뉴를 보여 주는 것과 같게) */
    private static final Set<String> STAFF_MANAGERS = Set.of("teamLead", "director");

    /** 임시 비밀번호 글자: 헷갈리는 글자(0 O 1 l I)는 뺐다 */
    private static final String TEMP_PASSWORD_CHARS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TEMP_PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 아이디가 없을 때도 비밀번호 비교를 한 번 해 주기 위한 가짜 해시.
     * 이게 없으면 아이디가 없는 경우는 바로 끝나고, 아이디는 있는데 비밀번호만 틀린 경우는
     * BCrypt 계산 시간만큼 늦게 끝난다. 응답 시간 차이로 가입된 아이디를 알아낼 수 있어서 맞춰 준다.
     */
    private static final String DUMMY_HASH =
            "$2a$10$CS5blBJo5wROAHWXkE7zXumKFskFpKe8B8BRZI/iseMfS0EBEhRHm";

    private final AuthRepository authRepository;
    private final StaffQueries staffQueries;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthRepository authRepository, StaffQueries staffQueries,
                       PasswordEncoder passwordEncoder) {
        this.authRepository = authRepository;
        this.staffQueries = staffQueries;
        this.passwordEncoder = passwordEncoder;
    }

    // ------------------------------------------------------------------
    // 회원가입
    // ------------------------------------------------------------------

    /**
     * 새 계정을 만들고 직원 번호를 돌려준다.
     *
     * ★ role(권한)과 position(직위)은 요청으로 받지 않고 항상 staff / 사회복지사로 넣는다.
     *   - 명세서 SF-02: 직위는 역할에서 정해진다
     *   - 가입 화면은 로그인 전이라 누구나 부를 수 있다. 여기서 role을 받으면 아무나
     *     teamLead·director로 가입할 수 있게 되고, 이 시스템은 이용자 개인정보를 다룬다.
     *   팀장·국장 승격은 담당자 관리 기능(SF-02, POST /api/staff)이 생길 때 로그인한
     *   팀장·국장만 할 수 있게 만든다. 그때까지는 DB에서 직접 바꾼다 (README 참고).
     */
    @Transactional
    public long signup(SignupRequest body) {
        if (body == null) {
            throw ApiException.badRequest("가입 정보를 입력해 주세요.");
        }
        NewAccount account = checkNewAccount(body.loginId(), body.name(), body.department());
        PasswordPolicy.check(body.password());
        return insertAccount(account, body.password(), "staff");
    }

    // ------------------------------------------------------------------
    // 계정 추가 (팀장·국장)
    // ------------------------------------------------------------------

    /**
     * 팀장·국장이 직원 계정을 추가한다 (SF-02, POST /api/staff).
     * 화면에 비밀번호 입력란이 없으므로 서버가 임시 비밀번호를 만들어 응답에 한 번만 담는다.
     * 국장 계정은 국장만 만들 수 있다 (팀장이 자기보다 높은 권한을 만들지 못하게).
     */
    @Transactional
    public CreatedStaffResponse createStaff(long actorId, CreateStaffRequest body) {
        String actorRole = me(actorId).role();                         // 사용 중지된 계정이면 여기서 403
        if (!STAFF_MANAGERS.contains(actorRole)) {
            throw forbidden("계정 추가는 팀장·국장만 할 수 있어요.");
        }
        if (body == null) {
            throw ApiException.badRequest("계정 정보를 입력해 주세요.");
        }
        String role = body.role() == null || body.role().isBlank() ? "staff" : body.role().trim();
        if (!ROLES.contains(role)) {
            throw ApiException.badRequest("역할은 staff, teamLead, director 중 하나여야 해요.");
        }
        if ("director".equals(role) && !"director".equals(actorRole)) {
            throw forbidden("국장 계정은 국장만 추가할 수 있어요.");
        }
        NewAccount account = checkNewAccount(body.loginId(), body.name(), body.department());

        String temporaryPassword = temporaryPassword();
        long staffId = insertAccount(account, temporaryPassword, role);
        StaffDto created = staffQueries.findById(staffId)
                .orElseThrow(() -> new IllegalStateException("방금 만든 계정을 찾지 못했습니다: " + staffId));
        return CreatedStaffResponse.of(created, temporaryPassword);
    }

    /** 검사를 통과한 새 계정 정보 */
    private record NewAccount(String loginId, String name, String department) {
    }

    /** 회원가입·계정 추가 공통 입력 검사 */
    private NewAccount checkNewAccount(String rawLoginId, String rawName, String rawDepartment) {
        String loginId = trimToEmpty(rawLoginId).toLowerCase();         // 대소문자로 아이디가 갈리지 않게
        String name = trimToEmpty(rawName);
        String department = trimToEmpty(rawDepartment);

        if (loginId.isEmpty() || name.isEmpty()) {
            throw ApiException.badRequest("아이디와 이름을 입력해 주세요.");
        }
        if (!LOGIN_ID.matcher(loginId).matches()) {
            throw ApiException.badRequest("아이디는 영문 소문자·숫자와 . _ - 를 써서 4~50자로 정해 주세요.");
        }
        if (name.length() > 50) {
            throw ApiException.badRequest("이름이 너무 길어요.");
        }
        if (department.isEmpty()) {
            department = DEFAULT_DEPARTMENT;
        } else if (department.length() > 50) {
            throw ApiException.badRequest("소속 팀 이름이 너무 길어요.");
        }
        // 미리 한 번 확인해서 보통의 중복은 친절한 문구로 돌려준다
        if (authRepository.existsLoginId(loginId)) {
            throw duplicateLoginId();
        }
        return new NewAccount(loginId, name, department);
    }

    /** 비밀번호를 해시해서 계정 한 줄 추가 → 직원 번호 */
    private long insertAccount(NewAccount account, String password, String role) {
        String hash = passwordEncoder.encode(password);                // 원문은 저장하지 않는다
        try {
            return authRepository.insert(account.loginId(), hash, account.name(), account.department(),
                    positionOf(role), role);
        } catch (DuplicateKeyException e) {
            // 두 사람이 같은 아이디로 동시에 가입을 눌러 위 확인을 둘 다 통과한 경우.
            // DB의 UNIQUE 제약이 막아 주는데, 공통 처리기는 이 예외를 일지 중복으로 보기 때문에
            // 여기서 아이디 중복으로 바꿔 던진다.
            throw duplicateLoginId();
        }
    }

    /** 임시 비밀번호 12자 (비밀번호 규칙 8자 이상을 만족) */
    private static String temporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 로그인
    // ------------------------------------------------------------------

    /**
     * 아이디·비밀번호를 확인하고 직원 번호를 돌려준다 (LG-01).
     * 실패하면 401 INVALID_CREDENTIALS, 사용 중지 계정이면 403 ACCOUNT_DISABLED.
     */
    public long authenticate(LoginRequest body) {
        if (body == null || trimToEmpty(body.loginId()).isEmpty()
                || body.password() == null || body.password().isEmpty()) {
            // 빈칸은 인증 실패와 구분해서 알려 준다 (명세서 LG-01)
            throw ApiException.badRequest("아이디와 비밀번호를 입력해 주세요.");
        }
        String loginId = trimToEmpty(body.loginId()).toLowerCase();

        Optional<AuthRepository.Account> found = authRepository.findByLoginId(loginId);
        if (found.isEmpty()) {
            passwordEncoder.matches(body.password(), DUMMY_HASH);     // 응답 시간을 맞추기 위한 헛계산
            throw invalidCredentials();
        }

        AuthRepository.Account account = found.get();
        if (!passwordEncoder.matches(body.password(), account.passwordHash())) {
            throw invalidCredentials();
        }
        // 비밀번호가 맞아도 사용 중지된 계정은 들여보내지 않는다 (명세서 8.3)
        if (!account.active()) {
            throw accountDisabled();
        }
        return account.staffId();
    }

    // ------------------------------------------------------------------
    // 내 정보
    // ------------------------------------------------------------------

    /**
     * 세션에 들어 있는 직원 번호로 내 정보를 읽는다 (LG-04, MY-01, SE-01).
     *
     * 세션이 만들어진 뒤에 계정이 사용 중지될 수 있다. 그래서 매번 is_active를 다시 확인한다
     * (명세서 8.3 — 이미 로그인된 세션도 끊는다). 세션을 끊는 일은 컨트롤러가 한다.
     */
    public StaffDto me(long staffId) {
        AuthRepository.Account account = authRepository.findById(staffId)
                .orElseThrow(ApiException::unauthenticated);          // 계정이 사라졌으면 로그인 상태가 아닌 것
        if (!account.active()) {
            throw accountDisabled();
        }
        return staffQueries.findById(staffId).orElseThrow(ApiException::unauthenticated);
    }

    // ------------------------------------------------------------------
    // 비밀번호 변경
    // ------------------------------------------------------------------

    /** 현재 비밀번호를 확인한 뒤 새 비밀번호로 바꾼다 (SE-02) */
    @Transactional
    public void changePassword(long staffId, ChangePasswordRequest body) {
        if (body == null || body.currentPassword() == null || body.currentPassword().isEmpty()
                || body.newPassword() == null || body.newPassword().isEmpty()) {
            throw ApiException.badRequest("현재 비밀번호와 새 비밀번호를 입력해 주세요.");
        }
        AuthRepository.Account account = authRepository.findById(staffId)
                .orElseThrow(ApiException::unauthenticated);
        if (!account.active()) {
            throw accountDisabled();
        }
        if (!passwordEncoder.matches(body.currentPassword(), account.passwordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_MISMATCH",
                    "현재 비밀번호가 일치하지 않아요.");
        }
        PasswordPolicy.check(body.newPassword());
        if (body.newPassword().equals(body.currentPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY",
                    "지금 쓰는 비밀번호와 다르게 정해 주세요.");
        }
        authRepository.updatePasswordHash(staffId, passwordEncoder.encode(body.newPassword()));
    }

    // ------------------------------------------------------------------
    // 도우미
    // ------------------------------------------------------------------

    /** 권한(role)에 따라 정해지는 직위 표기 (명세서 SF-02) */
    static String positionOf(String role) {
        return switch (role) {
            case "teamLead" -> "팀장";
            case "director" -> "국장";
            default -> "사회복지사";
        };
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "아이디 또는 비밀번호가 일치하지 않아요.");
    }

    private static ApiException accountDisabled() {
        return new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                "사용이 중지된 계정이에요. 관리자에게 문의해 주세요.");
    }

    private static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    private static ApiException duplicateLoginId() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID",
                "이미 사용 중인 아이디예요.");
    }
}
