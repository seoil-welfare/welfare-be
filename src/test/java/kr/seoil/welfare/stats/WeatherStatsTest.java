package kr.seoil.welfare.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import kr.seoil.welfare.auth.AuthRepository;
import kr.seoil.welfare.auth.AuthService;
import kr.seoil.welfare.common.ApiException;
import kr.seoil.welfare.diary.Catalog;
import kr.seoil.welfare.diary.DiaryRepository;
import kr.seoil.welfare.diary.DiaryService;
import kr.seoil.welfare.diary.dto.ActivityDto;
import kr.seoil.welfare.diary.dto.BehaviorDto;
import kr.seoil.welfare.diary.dto.DiaryResponse;
import kr.seoil.welfare.diary.dto.SaveDiaryRequest;
import kr.seoil.welfare.staff.StaffQueries;
import kr.seoil.welfare.stats.WeatherStatsResponse.TemperatureCount;
import kr.seoil.welfare.stats.WeatherStatsResponse.WeatherCount;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 일지 온도 구간 저장과 날씨별·온도 구간별 통계 (GET /api/stats/weather).
 * 실제 PostgreSQL과 운영 Flyway 마이그레이션(V10 온도 칸 포함)으로 검사한다. 데이터는 모두 가상이다.
 */
class WeatherStatsTest {
    static EmbeddedPostgres pg;
    static JdbcTemplate jdbc;
    static TransactionTemplate tx;
    static NamedParameterJdbcTemplate named;
    DiaryService diaries;
    StatsService stats;

    @BeforeAll static void database() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        var source = pg.getPostgresDatabase();
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source);
        named = new NamedParameterJdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.update("INSERT INTO staff (staff_id, login_id, password_hash, name, department, position, role) VALUES "
                + "(1,'qa.staff','unused','직원','팀','담당자','staff')");
    }

    @AfterAll static void stop() throws Exception {
        if (pg != null) pg.close();
    }

    @BeforeEach void services() {
        jdbc.execute("TRUNCATE daily_log, client RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO client (client_id, name, birth_date, staff_id) VALUES (1,'가상 이용자','2000-01-01',1)");
        AuthService auth = new AuthService(new AuthRepository(jdbc), new StaffQueries(jdbc), new BCryptPasswordEncoder());
        Catalog catalog = new Catalog(jdbc);
        diaries = new DiaryService(new DiaryRepository(named), catalog, auth,
                Clock.fixed(Instant.parse("2026-11-07T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        stats = new StatsService(new StatsRepository(named), catalog);
    }

    /** 활동 1개짜리 일지. behaviors개만큼 자해 세부 항목을 골라 도전적 행동 건수를 정한다 */
    static SaveDiaryRequest diary(String date, String status, String weather, String temperature, int behaviors) {
        List<String> items = List.of("sh-1", "sh-2", "sh-3").subList(0, behaviors);
        ActivityDto activity = new ActivityDto(null, "10:00", "11:00", List.of("예술활동"), null, "활동",
                List.of("선택지 제공"), null, List.of("적극적 참여"), null, List.of(), null, behaviors > 0,
                behaviors > 0 ? List.of(new BehaviorDto("selfHarm", items, null)) : List.of());
        return new SaveDiaryRequest("1", date, status, weather, temperature, "양호", null, List.of(activity), "", null);
    }

    DiaryResponse save(SaveDiaryRequest request) {
        return tx.execute(s -> diaries.create(request, 1));
    }

    @Test @DisplayName("온도 구간을 저장하고 다시 읽으면 같은 코드, 비우면 응답에서 빠진다")
    void temperatureRoundTrip() {
        DiaryResponse saved = save(diary("2026-11-02", "completed", "맑음", "WARM", 0));
        assertThat(diaries.get(saved.id()).temperature()).isEqualTo("WARM");

        SaveDiaryRequest cleared = new SaveDiaryRequest("1", "2026-11-02", "completed", "맑음", " ", "양호", null,
                diary("2026-11-02", "completed", "맑음", null, 0).activities(), "", saved.updatedAt());
        tx.execute(s -> diaries.update(saved.id(), cleared, 1));
        assertThat(diaries.get(saved.id()).temperature()).isNull();
    }

    @Test @DisplayName("모르는 온도 값은 400으로 거절하고 저장하지 않는다")
    void unknownTemperature() {
        assertThatThrownBy(() -> save(diary("2026-11-02", "completed", "맑음", "WARMISH", 0)))
                .isInstanceOf(ApiException.class)
                .hasMessage("온도는 COLD, COOL, WARM, HOT 중 하나여야 해요.");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_log", Integer.class)).isZero();
    }

    @Test @DisplayName("날씨별·온도별로 행동 건수와 날 수를 세고, 온도 없는 일지는 따로 알려 준다")
    void weatherAndTemperature() {
        save(diary("2026-11-01", "completed", "맑음", "WARM", 2));
        save(diary("2026-11-02", "completed", "맑음", "WARM", 1));
        save(diary("2026-11-03", "completed", "비", "COLD", 0));
        save(diary("2026-11-04", "completed", "비", null, 3));            // 온도 없는 일지 (기존 기록과 같음)
        save(diary("2026-11-05", "draft", "맑음", "HOT", 3));             // 작성 중은 세지 않는다
        save(diary("2026-10-31", "completed", "맑음", "WARM", 3));        // 기간 밖

        WeatherStatsResponse r = stats.weather(1, stats.toRange("2026-11-01", "2026-11-30"));

        assertThat(r.completedDiaries()).isEqualTo(4);
        assertThat(r.weather()).extracting(WeatherCount::label)
                .containsExactly("맑음", "구름조금", "흐림", "비", "눈");      // 0건도 목록 순서대로 모두
        assertThat(weather(r, "맑음")).isEqualTo(new WeatherCount("sunny", "맑음", 3, 2));
        assertThat(weather(r, "비")).isEqualTo(new WeatherCount("rain", "비", 3, 2));
        assertThat(weather(r, "눈").days()).isZero();

        assertThat(r.temperature()).extracting(TemperatureCount::code).containsExactly("COLD", "COOL", "WARM", "HOT");
        assertThat(temperature(r, "WARM")).isEqualTo(new TemperatureCount("WARM", "따뜻함", "20~27℃", 3, 2));
        assertThat(temperature(r, "COLD")).isEqualTo(new TemperatureCount("COLD", "추움", "10℃ 이하", 0, 1));
        assertThat(temperature(r, "HOT").days()).isZero();
        assertThat(r.temperatureExcluded()).isEqualTo(new WeatherStatsResponse.Excluded(1, 3));
    }

    @Test @DisplayName("기간이 비었거나 거꾸로면 400")
    void badRange() {
        assertThatThrownBy(() -> stats.toRange(null, "2026-11-30")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> stats.toRange("2026-11-30", "2026-11-01")).hasMessage("시작일이 종료일보다 늦어요.");
        assertThatThrownBy(() -> stats.toRange("2026/11/01", "2026-11-30")).hasMessage("날짜는 YYYY-MM-DD 형식이어야 해요.");
    }

    static WeatherCount weather(WeatherStatsResponse r, String label) {
        return r.weather().stream().filter(w -> w.label().equals(label)).findFirst().orElseThrow();
    }

    static TemperatureCount temperature(WeatherStatsResponse r, String code) {
        return r.temperature().stream().filter(t -> t.code().equals(code)).findFirst().orElseThrow();
    }
}
