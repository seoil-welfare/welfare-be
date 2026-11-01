package kr.seoil.welfare.category;

import kr.seoil.welfare.auth.SessionStaff;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 *  GET  /api/categories                          선택 항목 전부 (로그인한 직원 누구나)
 *  POST /api/categories/participation            참여 활동 추가 (팀장·국장만)
 *  PUT  /api/categories/participation/{code}     참여 활동 이름 변경·숨기기·다시 보이기 (팀장·국장만)
 * 규칙은 CategoryService에 있다.
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    // GET 요청이 오면 실행: 일지 화면의 선택 항목 전부
    @GetMapping
    public CategoriesResponse list(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId) {
        SessionStaff.require(staffId);                          // 로그인 안 했으면 401
        return categoryService.all();
    }

    // POST 요청이 오면 실행: 참여 활동 추가 → 201
    @PostMapping("/participation")
    public ResponseEntity<CategoryItem> addParticipation(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
            @RequestBody ParticipationRequest body) {           // { "label": "원예활동" }
        long actor = SessionStaff.require(staffId);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.addParticipation(actor, body));
    }

    // PUT 요청이 오면 실행: 참여 활동 이름 바꾸기·숨기기
    @PutMapping("/participation/{code}")
    public CategoryItem updateParticipation(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
            @PathVariable String code,                          // 바꿀 항목 코드 (예: art, custom-9)
            @RequestBody ParticipationRequest body) {           // { "label": "..." } 또는 { "active": false }
        long actor = SessionStaff.require(staffId);
        return categoryService.updateParticipation(actor, code, body);
    }
}
