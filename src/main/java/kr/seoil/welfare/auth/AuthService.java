package kr.seoil.welfare.auth;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import kr.seoil.welfare.auth.dto.ChangePasswordRequest;
import kr.seoil.welfare.auth.dto.LoginRequest;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.staff.ChangeRoleRequest;
import kr.seoil.welfare.staff.CreateStaffRequest;
import kr.seoil.welfare.staff.CreatedStaffResponse;
import kr.seoil.welfare.staff.StaffDto;
import kr.seoil.welfare.staff.StaffQueries;
import kr.seoil.welfare.staff.TransferDirectorRequest;
import kr.seoil.welfare.staff.TransferDirectorResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인·계정 추가·비밀번호 변경의 규칙을 모아 둔 곳 (명세서 3.1 LG, 3.18 SE).
 *
 * 세션(쿠키)을 만들고 지우는 일은 AuthController가 한다. 여기서는 "이 사람이 맞는가"만 판단한다.
 * 이렇게 나누면 서비스 코드는 HttpSession 없이도 테스트할 수 있다.
 */
@Service
public class AuthService {

    /** 아이디에 쓸 수 있는 글자: 영문 소문자·숫자·점·밑줄·붙임표, 4~50자 (DB VARCHAR(50)) */
    private static final Pattern LOGIN_ID = Pattern.compile("[a-z0-9._-]{4,50}");

    private static final String DEFAULT_DEPARTMENT = "주간활동지원팀";   // 명세서 SF-02 기본값

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");         // '오늘'은 한국 날짜 기준
    private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);   // 잘못 친 연도(0199 등) 막기

    /** 계정 추가를 할 수 있는 역할 (지금 프론트가 팀장·국장에게만 메뉴를 보여 주는 것과 같게) */
    private static final Set<String> STAFF_MANAGERS = Set.of("teamLead", "director");
    /** 역할 변경으로 줄 수 있는 역할 (국장은 이양으로만) */
    private static final Set<String> CHANGEABLE_ROLES = Set.of("staff", "teamLead");

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
    // 계정 추가 (팀장·국장)
    // ------------------------------------------------------------------

    /**
     * 팀장·국장이 직원 계정을 추가한다 (SF-02, POST /api/staff).
     * 화면에 비밀번호 입력란이 없으므로 서버가 임시 비밀번호를 만들어 응답에 한 번만 담는다.
     * 담당자(staff)로만 만든다. 팀장은 역할 변경(changeRole), 국장은 관리 권한 이양(transferDirector)으로 바꾼다.
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
        String role = trimToEmpty(body.role());
        if (!role.isEmpty() && !"staff".equals(role)) {                // 비었거나 staff만 받는다
            throw ApiException.badRequest("계정 추가는 담당자로만 할 수 있어요. 팀장은 역할 변경으로 바꿔 주세요.");
        }
        NewAccount account = checkNewAccount(body.loginId(), body.name(), body.department(), body.birthDate());

        String temporaryPassword = temporaryPassword();
        long staffId = insertAccount(account, temporaryPassword, "staff");
        StaffDto created = staffQueries.findById(staffId)
                .orElseThrow(() -> new IllegalStateException("방금 만든 계정을 찾지 못했습니다: " + staffId));
        return CreatedStaffResponse.of(created, temporaryPassword);
    }

    // ------------------------------------------------------------------
    // 계정 사용 중지·다시 사용 (팀장·국장)
    // ------------------------------------------------------------------

    /**
     * 직원 계정을 사용 중지(active = false)하거나 다시 사용(active = true)하게 한다
     * (명세서 8.3, POST /api/staff/{id}/deactivate · /activate).
     *
     * 규칙은 계정 추가(createStaff)와 맞춘다.
     *  - 팀장·국장만 할 수 있다
     *  - 국장 계정은 국장만 바꿀 수 있다 (팀장이 윗사람 계정을 막지 못하게)
     *  - 내 계정은 사용 중지할 수 없다 (실수로 관리할 사람이 없어지는 일 방지)
     *  - 이미 그 상태여도 오류 없이 지금 정보를 돌려준다 (두 번 눌러도 안전)
     * 사용 중지된 직원은 로그인(authenticate)과 내 정보(me)에서 403 ACCOUNT_DISABLED로 막힌다.
     */
    @Transactional
    public StaffDto setStaffActive(long actorId, String targetId, boolean active) {
        String actorRole = me(actorId).role();                         // 요청한 사람이 사용 중지 상태면 여기서 403
        if (!STAFF_MANAGERS.contains(actorRole)) {
            throw forbidden("계정 사용 중지는 팀장·국장만 할 수 있어요.");
        }
        long staffId = parseStaffId(targetId);                         // 숫자가 아니면 그런 직원은 없음 → 404
        StaffDto target = staffQueries.findById(staffId).orElseThrow(AuthService::staffNotFound);
        if ("director".equals(target.role()) && !"director".equals(actorRole)) {
            throw forbidden("국장 계정은 국장만 바꿀 수 있어요.");
        }
        if (!active && staffId == actorId) {
            throw ApiException.badRequest("내 계정은 사용 중지할 수 없어요.");
        }
        staffQueries.updateActive(staffId, active);
        return staffQueries.findById(staffId).orElseThrow(AuthService::staffNotFound);   // 바뀐 정보로 응답
    }

    // ------------------------------------------------------------------
    // 임시 비밀번호 다시 발급 (팀장·국장)
    // ------------------------------------------------------------------

    /**
     * 비밀번호를 잊은 직원에게 새 임시 비밀번호를 만들어 준다 (POST /api/staff/{id}/reset-password).
     * 계정 추가와 같은 방식으로 만들어 응답에 한 번만 담는다 (DB에는 해시만 저장). 원래 비밀번호는 더 이상 쓸 수 없다.
     *  - 팀장·국장만 할 수 있다. 국장 계정은 국장만 (setStaffActive와 같은 규칙)
     *  - 내 계정은 안 된다. 마이페이지의 비밀번호 변경을 쓴다
     *  - 사용 중지된 계정은 안 된다. 먼저 다시 사용하게 한다
     */
    @Transactional
    public CreatedStaffResponse resetPassword(long actorId, String targetId) {
        String actorRole = me(actorId).role();                         // 요청한 사람이 사용 중지 상태면 여기서 403
        if (!STAFF_MANAGERS.contains(actorRole)) {
            throw forbidden("임시 비밀번호 발급은 팀장·국장만 할 수 있어요.");
        }
        long staffId = parseStaffId(targetId);
        StaffDto target = staffQueries.findById(staffId).orElseThrow(AuthService::staffNotFound);
        if (staffId == actorId) {
            throw ApiException.badRequest("내 비밀번호는 마이페이지의 비밀번호 변경에서 바꿔 주세요.");
        }
        if ("director".equals(target.role()) && !"director".equals(actorRole)) {
            throw forbidden("국장 계정은 국장만 바꿀 수 있어요.");
        }
        if (!target.active()) {
            throw ApiException.badRequest("사용이 중지된 계정은 임시 비밀번호를 발급할 수 없어요.");
        }
        String temporaryPassword = temporaryPassword();
        authRepository.updatePasswordHash(staffId, passwordEncoder.encode(temporaryPassword), true);   // 다음 로그인 때 변경 요구
        return CreatedStaffResponse.of(target, temporaryPassword);
    }

    // ------------------------------------------------------------------
    // 역할 변경 (팀장·국장)
    // ------------------------------------------------------------------

    /**
     * 담당자를 팀장으로 올리거나 팀장을 담당자로 내린다 (SF-04, POST /api/staff/{id}/role).
     * StaffController.changeRole에서 호출, StaffQueries.updateRole로 role과 position을 함께 바꾼다.
     *  - 팀장·국장만 할 수 있다
     *  - 내 계정, 국장 계정, 사용 중지된 계정은 대상이 될 수 없다
     *  - director는 줄 수 없다. 국장은 관리 권한 이양으로만 바뀐다
     *  - 이미 그 역할이면 바꾸지 않고 지금 정보를 돌려준다 (두 번 눌러도 안전)
     */
    @Transactional
    public StaffDto changeRole(long actorId, String targetId, ChangeRoleRequest body) {
        String actorRole = me(actorId).role();                         // 요청한 사람이 사용 중지 상태면 여기서 403
        if (!STAFF_MANAGERS.contains(actorRole)) {
            throw forbidden("역할 변경은 팀장·국장만 할 수 있어요.");
        }
        String role = body == null ? "" : trimToEmpty(body.role());
        if (!CHANGEABLE_ROLES.contains(role)) {
            throw ApiException.badRequest("역할은 담당자 또는 팀장만 고를 수 있어요.");
        }
        long staffId = parseStaffId(targetId);
        StaffDto target = staffQueries.findById(staffId).orElseThrow(AuthService::staffNotFound);
        if (staffId == actorId) {                                      // 국장 본인도 403이 아닌 400으로
            throw ApiException.badRequest("내 계정의 역할은 바꿀 수 없어요.");
        }
        if ("director".equals(target.role())) {
            throw forbidden("국장 계정의 역할은 바꿀 수 없어요.");
        }
        if (!target.active()) {
            throw ApiException.badRequest("사용이 중지된 계정은 역할을 바꿀 수 없어요.");
        }
        if (role.equals(target.role())) {
            return target;
        }
        staffQueries.updateRole(staffId, role, positionOf(role));
        return staffQueries.findById(staffId).orElseThrow(AuthService::staffNotFound);
    }

    // ------------------------------------------------------------------
    // 관리 권한 이양 (국장)
    // ------------------------------------------------------------------

    /**
     * 국장이 다른 직원에게 국장 역할을 넘긴다 (SF-05, POST /api/staff/{id}/transfer-director).
     * StaffController.transferDirector에서 호출. 국장은 항상 1명.
     *  - 요청한 국장 줄을 잠근 뒤 국장인지 다시 확인한다 (동시에 두 번 눌러도 한 번만 넘어감)
     *  - 원래 국장을 먼저 팀장으로 내리고(deactivate면 사용 중지까지) 그다음 대상을 국장으로 올린다.
     *    순서가 반대면 국장 유일 인덱스(V6)에 걸린다
     *  - 세션을 끊는 일은 컨트롤러가 previous.active를 보고 한다
     */
    @Transactional
    public TransferDirectorResponse transferDirector(long actorId, String targetId, TransferDirectorRequest body) {
        StaffDto actor = staffQueries.findByIdForUpdate(actorId).orElseThrow(ApiException::unauthenticated);
        if (!actor.active()) {
            throw accountDisabled();
        }
        if (!"director".equals(actor.role())) {
            throw forbidden("관리 권한 이양은 국장만 할 수 있어요.");
        }
        String previousRole = body == null ? "" : trimToEmpty(body.previousRole());
        if (previousRole.isEmpty()) {
            previousRole = "teamLead";                                 // 생략하면 팀장으로 남기
        }
        if (!"teamLead".equals(previousRole) && !"deactivate".equals(previousRole)) {
            throw ApiException.badRequest("이양 뒤 내 계정 처리는 팀장으로 남기 또는 계정 사용 중지 중에서 골라 주세요.");
        }
        long staffId = parseStaffId(targetId);
        StaffDto target = staffQueries.findById(staffId).orElseThrow(AuthService::staffNotFound);
        if (staffId == actorId) {
            throw ApiException.badRequest("내 계정에는 관리 권한을 넘길 수 없어요.");
        }
        if (!target.active()) {
            throw ApiException.badRequest("사용이 중지된 계정에는 관리 권한을 넘길 수 없어요.");
        }

        staffQueries.updateRole(actorId, "teamLead", positionOf("teamLead"));   // 원래 국장 먼저
        if ("deactivate".equals(previousRole)) {
            staffQueries.updateActive(actorId, false);
        }
        staffQueries.updateRole(staffId, "director", positionOf("director"));   // 그다음 새 국장
        return new TransferDirectorResponse(
                staffQueries.findById(staffId).orElseThrow(AuthService::staffNotFound),
                staffQueries.findById(actorId).orElseThrow(AuthService::staffNotFound));
    }

    /** 주소의 직원 번호 → 숫자. 숫자가 아니면 그런 직원은 없는 것이므로 404 */
    private static long parseStaffId(String id) {
        try {
            return Long.parseLong(id == null ? "" : id.trim());
        } catch (NumberFormatException e) {
            throw staffNotFound();
        }
    }

    /** 검사를 통과한 새 계정 정보 */
    private record NewAccount(String loginId, String name, String department, LocalDate birthDate) {
    }

    /** 계정 추가 입력 검사 */
    private NewAccount checkNewAccount(String rawLoginId, String rawName, String rawDepartment,
                                       String rawBirthDate) {
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
        LocalDate birthDate = parseBirthDate(rawBirthDate);              // 비어 있으면 null
        // 미리 한 번 확인해서 보통의 중복은 친절한 문구로 돌려준다
        if (authRepository.existsLoginId(loginId)) {
            throw duplicateLoginId();
        }
        return new NewAccount(loginId, name, department, birthDate);
    }

    /**
     * 생년월일 검사 (선택 입력). 이용자 등록(ServiceUserService)과 같은 형식·문구를 쓴다.
     *  - 비어 있으면 null (저장하지 않음)
     *  - YYYY-MM-DD로 읽을 수 없거나 없는 날짜(2000-13-40)면 400
     *  - 한국 날짜 기준 오늘보다 뒤이거나 1900년 이전이면 400
     */
    private static LocalDate parseBirthDate(String raw) {
        String text = trimToEmpty(raw);
        if (text.isEmpty()) {
            return null;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(text);                                // YYYY-MM-DD만 읽음
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("생년월일을 YYYY-MM-DD 형식으로 입력해 주세요.");
        }
        if (date.isAfter(LocalDate.now(KST))) {
            throw ApiException.badRequest("생년월일이 오늘보다 뒤예요.");
        }
        if (date.isBefore(MIN_BIRTH_DATE)) {
            throw ApiException.badRequest("생년월일을 다시 확인해 주세요.");
        }
        return date;
    }

    /** 비밀번호를 해시해서 계정 한 줄 추가 → 직원 번호 */
    private long insertAccount(NewAccount account, String password, String role) {
        String hash = passwordEncoder.encode(password);                // 원문은 저장하지 않는다
        try {
            return authRepository.insert(account.loginId(), hash, account.name(), account.department(),
                    positionOf(role), role, account.birthDate());
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
        authRepository.updatePasswordHash(staffId, passwordEncoder.encode(body.newPassword()), false);  // 임시 비밀번호 상태 해제
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

    private static ApiException staffNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "찾을 수 없는 직원이에요.");
    }

    private static ApiException duplicateLoginId() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID",
                "이미 사용 중인 아이디예요.");
    }
}