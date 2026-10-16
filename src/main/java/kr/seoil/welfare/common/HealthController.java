package kr.seoil.welfare.common;                                // 공통 기능을 모아 두는 패키지

import java.util.LinkedHashMap;                                 // 넣은 순서대로 JSON에 나오게 하는 Map
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;              // SQL을 직접 실행하는 도구
import org.springframework.web.bind.annotation.GetMapping;      // GET 요청을 받는 표시
import org.springframework.web.bind.annotation.RequestMapping;  // 공통 주소 앞부분 지정
import org.springframework.web.bind.annotation.RestController;  // 결과를 JSON으로 돌려주는 컨트롤러 표시

/**
 * 서버와 DB가 잘 붙었는지 확인하는 주소.
 * 브라우저에서 http://localhost:8080/api/health 를 열면 결과가 보인다.
 */
@RestController                                                 // 이 클래스의 메서드 결과 = JSON 응답
@RequestMapping("/api")                                         // 이 클래스의 모든 주소 앞에 /api 를 붙임 (프론트가 /api로 부름)
public class HealthController {

    private final JdbcTemplate jdbc;                            // DB에 SQL을 보내는 도구 (스프링이 넣어 줌)

    public HealthController(JdbcTemplate jdbc) {                // 생성자 주입: 스프링이 JdbcTemplate을 만들어서 전달
        this.jdbc = jdbc;
    }

    @GetMapping("/health")                                      // GET /api/health 요청이 오면 아래 메서드 실행
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();     // 응답으로 보낼 JSON 내용

        result.put("status", "ok");                             // 여기까지 왔으면 서버는 살아 있음

        // DB 버전 조회 — 실패하면 DB 연결이 안 된 것
        result.put("db", jdbc.queryForObject("SELECT version()", String.class));

        // public 스키마의 테이블 수 — V1(18개) + Flyway 기록표(1개) = 19 가 나오면 정상
        result.put("tables", jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'",
                Integer.class));

        // 도전적 행동 세부 항목 수 — 31 이 나오면 목록 데이터까지 잘 들어간 것
        result.put("behaviorItems", jdbc.queryForObject("SELECT count(*) FROM behavior_item", Integer.class));

        // 가상 직원 수 — local 프로필이면 5
        result.put("staff", jdbc.queryForObject("SELECT count(*) FROM staff", Integer.class));

        return result;                                          // Map → JSON 으로 자동 변환되어 응답
    }
}
