package kr.seoil.welfare.category;

import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import kr.seoil.welfare.category.CategoriesResponse.BehaviorType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 선택 항목 목록 테이블(weather, activity_type ...)을 읽고, 참여 활동(activity_type)을 추가·수정하는 SQL 모음.
 *
 * 일지 저장용 Catalog는 목록을 메모리에 기억해 두지만, 여기서는 매번 DB에서 바로 읽는다.
 * 관리 화면에서 바꾼 내용이 바로 보여야 하고, 자주 부르는 API가 아니기 때문이다.
 */
@Repository
public class CategoryRepository {

    /** 조회 결과 한 줄 → CategoryItem */
    private static final RowMapper<CategoryItem> ITEM = (rs, rowNum) -> new CategoryItem(
            rs.getString("code"),
            rs.getString("name"),
            rs.getInt("sort_order"),
            rs.getBoolean("is_active"),
            rs.getBoolean("is_etc"));

    private final NamedParameterJdbcTemplate jdbc;

    public CategoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 목록 테이블 하나를 화면 순서대로 읽기.
     * table은 CategoryService가 정해진 이름만 넘긴다 (사용자 입력이 SQL에 들어가지 않음).
     * hasEtc: weather 테이블만 is_etc 칸이 없어서 FALSE로 채운다.
     */
    public List<CategoryItem> list(String table, boolean hasEtc) {
        String etc = hasEtc ? "is_etc" : "FALSE AS is_etc";
        return jdbc.query("SELECT code, name, sort_order, is_active, " + etc
                + " FROM " + table + " ORDER BY sort_order, code", ITEM);
    }

    /** 도전적 행동 영역 (타해·자해·파괴행동·기타) */
    public List<BehaviorType> behaviorTypes() {
        return jdbc.query("SELECT code, name, sort_order FROM behavior_area ORDER BY sort_order",
                (rs, rowNum) -> new BehaviorType(rs.getString("code"), rs.getString("name"), rs.getInt("sort_order")));
    }

    /** 도전적 행동 세부 항목을 영역별로 묶기 { otherHarm: [...], selfHarm: [...], ... } (영역 순서 유지) */
    public Map<String, List<CategoryItem>> behaviorDetails() {
        Map<String, List<CategoryItem>> result = new LinkedHashMap<>();   // 넣은 순서대로 꺼내지는 Map
        jdbc.query("SELECT a.code AS area_code, i.code, i.name, i.sort_order, i.is_active, i.is_etc"
                        + " FROM behavior_item i JOIN behavior_area a ON a.area_id = i.area_id"
                        + " ORDER BY a.sort_order, i.sort_order",
                rs -> {
                    String area = rs.getString("area_code");
                    // 그 영역 칸이 없으면 빈 목록을 만들고, 항목 1개를 추가
                    result.computeIfAbsent(area, k -> new ArrayList<>()).add(ITEM.mapRow(rs, 0));
                });
        return result;
    }

    /** 참여 활동 1개를 코드로 찾기 (없으면 빈 Optional) */
    public Optional<CategoryItem> findParticipation(String code) {
        return jdbc.query("SELECT code, name, sort_order, is_active, is_etc FROM activity_type WHERE code = :code",
                        new MapSqlParameterSource("code", code), ITEM)
                .stream().findFirst();
    }

    /**
     * 참여 활동 추가. 만들어진 코드를 돌려준다 (예: custom-9).
     *
     * SQL 한 번에 세 가지를 한다.
     *  1) etc_shift : '기타'를 한 칸 뒤로 민다 (기타는 항상 맨 끝)
     *  2) new_id    : 새 번호를 미리 받는다 → 코드를 'custom-번호'로 만들기 위해
     *  3) INSERT    : 기타를 뺀 마지막 순서 + 1 자리에 넣는다
     * 한 문장이라 중간에 실패하면 셋 다 취소된다.
     */
    public String insertParticipation(String name) {
        return jdbc.queryForObject("""
                WITH etc_shift AS (
                    UPDATE activity_type SET sort_order = sort_order + 1 WHERE is_etc
                ), new_id AS (
                    SELECT nextval(pg_get_serial_sequence('activity_type', 'activity_type_id'))::smallint AS id
                )
                INSERT INTO activity_type (activity_type_id, code, name, is_etc, sort_order)
                SELECT id, 'custom-' || id, :name, FALSE,
                       (SELECT coalesce(max(sort_order), 0) + 1 FROM activity_type WHERE NOT is_etc)
                FROM new_id
                RETURNING code
                """, new MapSqlParameterSource("name", name), String.class);
    }

    /**
     * 참여 활동 이름·사용 여부 바꾸기. null로 넘긴 칸은 그대로 둔다 (coalesce = 앞의 값이 null이면 뒤의 값).
     * null일 때도 PostgreSQL이 칸 종류를 알 수 있게 Types를 함께 넘긴다.
     */
    public void updateParticipation(String code, String name, Boolean active) {
        jdbc.update("""
                UPDATE activity_type
                SET name = coalesce(:name, name),
                    is_active = coalesce(:active, is_active)
                WHERE code = :code
                """, new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("name", name, Types.VARCHAR)
                .addValue("active", active, Types.BOOLEAN));
    }
}
