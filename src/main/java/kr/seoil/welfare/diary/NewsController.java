package kr.seoil.welfare.diary;

import java.util.List;

import kr.seoil.welfare.auth.SessionStaff;
import kr.seoil.welfare.diary.dto.NewsEntryDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * GET /api/news?since=YYYY-MM-DD — 오늘의 소식 (일지 등록·수정 이력, 최신순)
 * 홈 화면이 처음 열릴 때 부른다.
 */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final DiaryService diaryService;

    public NewsController(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    @GetMapping
    public List<NewsEntryDto> list(@SessionAttribute(name = SessionStaff.KEY, required = false) Long staffId,
                                   @RequestParam(required = false) String since) {
        SessionStaff.require(staffId);
        return diaryService.news(since);
    }
}
