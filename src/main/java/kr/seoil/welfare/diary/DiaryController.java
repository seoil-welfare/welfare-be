package kr.seoil.welfare.diary;

import java.util.List;

import kr.seoil.welfare.auth.SessionStaff;
import kr.seoil.welfare.diary.dto.DiaryResponse;
import kr.seoil.welfare.diary.dto.SaveDiaryRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * 일지 API — 주소와 요청을 받아 DiaryService에 넘기는 '창구'.
 * 규칙은 DiaryService에, SQL은 DiaryRepository에 있다.
 *
 *  GET  /api/diaries?userId=&from=&to=&status=   목록 (홈·달력·통계)
 *  GET  /api/diaries/{id}                        1건 (일지 상세)
 *  POST /api/diaries                             새 일지 저장
 *  PUT  /api/diaries/{id}                        일지 수정
 */
@RestController
@RequestMapping("/api/diaries")
public class DiaryController {

    private final DiaryService diaryService;

    public DiaryController(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    @GetMapping
    public List<DiaryResponse> list(
            @SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,   // 로그인한 직원 번호
            @RequestParam(required = false) String userId,       // 이용자 번호
            @RequestParam(required = false) String from,         // 시작 날짜 (포함)
            @RequestParam(required = false) String to,           // 끝 날짜 (포함)
            @RequestParam(required = false) String status) {     // draft | completed
        SessionStaff.require(staffId);                           // 로그인 안 했으면 401
        return diaryService.list(userId, from, to, status);
    }

    @GetMapping("/{id}")
    public DiaryResponse get(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                             @PathVariable String id) {
        SessionStaff.require(staffId);
        return diaryService.get(id);
    }

    @PostMapping
    public ResponseEntity<DiaryResponse> create(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                                @RequestBody SaveDiaryRequest body) {
        long actor = SessionStaff.require(staffId);              // 작성자 = 로그인한 직원
        return ResponseEntity.status(HttpStatus.CREATED).body(diaryService.create(body, actor));   // 201
    }

    @PutMapping("/{id}")
    public DiaryResponse update(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                @PathVariable String id,
                                @RequestBody SaveDiaryRequest body) {
        long actor = SessionStaff.require(staffId);
        return diaryService.update(id, body, actor);
    }
}
