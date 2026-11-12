package kr.seoil.welfare.category;

import java.util.List;
import java.util.Set;

import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.card.CardRules;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.diary.Catalog;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 선택 항목 조회와 참여 활동 관리 규칙 (명세서 5.1).
 *
 *  - 조회: 로그인한 직원 누구나
 *  - 참여 활동 추가·이름 변경·숨기기: 팀장·국장만
 *  - 지우지 않는다. 숨기면(active = false) 새 일지에서만 안 보이고 과거 일지·통계는 그대로
 *  - 이름을 바꿔도 코드·번호가 같아서 과거 통계가 이어진다
 */
@Service
public class CategoryService {

    /** 참여 활동을 바꿀 수 있는 역할 (계정 추가와 같은 기준) */
    private static final Set<String> MANAGERS = Set.of("teamLead", "director");

    /** activity_type.name 칸 길이 (VARCHAR(30)) */
    private static final int MAX_LABEL = 30;

    private final CategoryRepository repo;
    private final AuthService authService;
    private final Catalog catalog;          // 일지 저장용 메뉴판. 바꾼 뒤 다시 읽게 한다

    public CategoryService(CategoryRepository repo, AuthService authService, Catalog catalog) {
        this.repo = repo;
        this.authService = authService;
        this.catalog = catalog;
    }

    // ------------------------------------------------------------------
    // 조회
    // ------------------------------------------------------------------

    /** 선택 항목 전부 (테이블 이름은 여기서 정한 값만 쓴다) */
    @Transactional(readOnly = true)
    public CategoriesResponse all() {
        return new CategoriesResponse(
                repo.list("weather", false),            // weather만 is_etc 칸이 없음
                repo.list("condition_status", true),
                repo.list("activity_type", true),
                repo.list("pre_support", true),
                repo.list("reaction", true),
                repo.list("pbs_support", true),
                repo.behaviorTypes(),
                repo.behaviorDetails());
    }

    // ------------------------------------------------------------------
    // 참여 활동 추가·수정 (팀장·국장)
    // ------------------------------------------------------------------

    /**
     * 참여 활동 추가 → 만들어진 항목. '기타' 바로 앞에 들어간다.
     * SQL이 한 문장이라 @Transactional 없이도 전부 되거나 전부 취소된다.
     */
    public CategoryItem addParticipation(long actorId, ParticipationRequest body) {
        checkManager(actorId);
        String label = checkLabel(body == null ? null : body.label(), null);
        if (label == null) {
            throw ApiException.badRequest("추가할 참여 활동 이름을 입력해 주세요.");
        }
        String pictogram = checkPictogram(body.pictogram());
        String code = repo.insertParticipation(label, pictogram == null ? "favorite" : pictogram);
        catalog.reload();                                   // 일지 저장에서도 새 항목을 쓸 수 있게
        return repo.findParticipation(code).orElseThrow();
    }

    /** 참여 활동 이름 바꾸기·숨기기·다시 보이기 → 바뀐 항목 */
    public CategoryItem updateParticipation(long actorId, String code, ParticipationRequest body) {
        checkManager(actorId);
        CategoryItem current = repo.findParticipation(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "찾을 수 없는 참여 활동이에요."));
        if (body == null || (body.label() == null && body.active() == null && body.pictogram() == null)) {
            throw ApiException.badRequest("바꿀 이름, 사용 여부, 기본 그림 중 하나를 보내 주세요.");
        }
        String label = checkLabel(body.label(), current.code());
        if (Boolean.FALSE.equals(body.active()) && current.etc()) {
            // 기타를 숨기면 목록에 없는 활동을 적을 곳이 사라진다
            throw ApiException.badRequest("'기타'는 숨길 수 없어요.");
        }
        repo.updateParticipation(current.code(), label, body.active(), checkPictogram(body.pictogram()));
        catalog.reload();                                   // 일지 저장·응답에 바뀐 이름이 쓰이게
        return repo.findParticipation(current.code()).orElseThrow();
    }

    // ------------------------------------------------------------------
    // 도우미
    // ------------------------------------------------------------------

    /** 오늘 카드 기본 그림 검사 (보내지 않았으면 null) */
    private static String checkPictogram(String raw) {
        if (raw == null) {
            return null;
        }
        String pictogram = raw.trim();
        if (!CardRules.PICTOGRAMS.contains(pictogram)) {
            throw ApiException.badRequest("기본 그림은 " + String.join(", ", CardRules.PICTOGRAMS) + " 중에서 골라 주세요.");
        }
        return pictogram;
    }

    /** 팀장·국장이 아니면 403 (사용 중지된 계정이면 me()에서 403) */
    private void checkManager(long actorId) {
        String role = authService.me(actorId).role();
        if (!MANAGERS.contains(role)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "참여 활동은 팀장·국장만 바꿀 수 있어요.");
        }
    }

    /**
     * 이름 검사 → 앞뒤 공백을 뺀 이름 (보내지 않았으면 null).
     * 다른 참여 활동과 이름이 같으면 409. 일지 저장이 이름으로 항목을 찾으므로(Catalog.byLabel)
     * 띄어쓰기만 다른 이름도 같은 이름으로 본다 ("예술 활동" = "예술활동").
     * selfCode: 수정할 때 자기 자신은 비교에서 뺀다.
     */
    private String checkLabel(String raw, String selfCode) {
        if (raw == null) {
            return null;
        }
        String label = raw.trim();
        if (label.isEmpty()) {
            throw ApiException.badRequest("참여 활동 이름을 입력해 주세요.");
        }
        if (label.length() > MAX_LABEL) {
            throw ApiException.badRequest("참여 활동 이름은 " + MAX_LABEL + "자까지 쓸 수 있어요.");
        }
        String key = label.replace(" ", "");
        List<CategoryItem> all = repo.list("activity_type", true);
        for (CategoryItem item : all) {
            if (!item.code().equals(selfCode) && item.label().replace(" ", "").equals(key)) {
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CATEGORY",
                        "이미 있는 참여 활동 이름이에요: " + item.label());
            }
        }
        return label;
    }
}
