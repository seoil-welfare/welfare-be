package kr.seoil.welfare.diary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kr.seoil.welfare.common.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 선택 항목 목록(메뉴판 테이블)을 메모리에 올려 두고, 이름 ↔ 번호를 바꿔 주는 클래스.
 *
 * 왜 필요한가?
 *   프론트는 "맑음", "일상생활훈련" 같은 '이름'을 보내지만, DB에는 weather_id 같은 '번호'로 저장한다.
 *   저장할 때: 이름 → 번호 / 응답할 때: 번호 → 이름
 * 목록은 거의 안 바뀌므로 처음 한 번만 읽어서 기억해 둔다.
 */
@Component
public class Catalog {

    /** 선택 항목 종류: 테이블 이름, 번호 칸 이름, 연결(다중 선택) 테이블 이름 */
    public enum Kind {
        WEATHER("weather", "weather_id", null),                               // 날씨 (1개 선택)
        CONDITION("condition_status", "condition_id", null),                  // 컨디션 (1개 선택)
        PARTICIPATION("activity_type", "activity_type_id", "record_activity_type"),
        PRE_SUPPORT("pre_support", "pre_support_id", "record_pre_support"),
        REACTION("reaction", "reaction_id", "record_reaction"),
        POSITIVE_SUPPORT("pbs_support", "pbs_support_id", "record_pbs_support");

        final String table;        // 목록 테이블
        final String idColumn;     // 목록 번호 칸
        final String junction;     // 활동과 연결하는 테이블 (1개 선택 항목은 null)

        Kind(String table, String idColumn, String junction) {
            this.table = table;
            this.idColumn = idColumn;
            this.junction = junction;
        }

        public String junction() { return junction; }
        public String idColumn() { return idColumn; }
    }

    /** 목록 항목 1개 */
    public record Item(short id, String code, String name, boolean etc, int sortOrder) {
    }

    /** 도전적 행동 세부 항목 1개 (어느 영역 소속인지 포함) */
    public record BehaviorItem(short id, String code, String areaCode, boolean etc, int sortOrder) {
    }

    /**
     * 프론트 화면 글자와 DB 이름이 다른 항목 (명세서 6장 5번 '선택지 제공' 띄어쓰기)
     * 응답할 때 프론트 글자로 바꿔 줘야 화면의 칩이 선택된 상태로 보인다.
     * 프론트가 코드 방식(명세서 5.1)으로 바뀌면 지운다.
     */
    private static final Map<String, String> FRONT_LABEL = Map.of("PRE_SUPPORT:choice", "선택지제공");

    private final JdbcTemplate jdbc;
    private volatile Data data;                                 // 처음 쓸 때 채움

    /** 메모리에 올려 둔 목록 묶음 */
    private record Data(Map<Kind, List<Item>> items, List<BehaviorItem> behaviors) {
    }

    public Catalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------
    // 찾기
    // ------------------------------------------------------------------

    /** 이름으로 찾기. 없는 이름이면 400. 띄어쓰기는 무시하고 비교한다 */
    public Item byLabel(Kind kind, String label) {
        String key = normalize(label);
        for (Item item : data().items().get(kind)) {
            if (normalize(item.name()).equals(key)) {
                return item;
            }
        }
        throw ApiException.badRequest("'" + label + "'은(는) 선택할 수 없는 값이에요.");
    }

    /** 번호로 찾기 (DB에서 읽은 값이라 반드시 있음) */
    public Item byId(Kind kind, short id) {
        for (Item item : data().items().get(kind)) {
            if (item.id() == id) {
                return item;
            }
        }
        throw new IllegalStateException(kind + " 목록에 없는 번호: " + id);
    }

    /** 한 종류의 항목 전체 (목록 순서) */
    public List<Item> items(Kind kind) {
        return data().items().get(kind);
    }

    /** 목록을 다시 읽게 한다 (참여 활동을 추가·수정한 뒤 CategoryService가 부름) */
    public void reload() {
        data = null;                                            // 다음에 data()를 부를 때 DB에서 새로 읽음
    }

    /** 응답에 넣을 화면 글자 */
    public String label(Kind kind, Item item) {
        return FRONT_LABEL.getOrDefault(kind.name() + ":" + item.code(), item.name());
    }

    /** 도전적 행동 세부 항목을 코드(oh-1 등)로 찾기. 없으면 null */
    public BehaviorItem behaviorByCode(String code) {
        for (BehaviorItem item : data().behaviors()) {
            if (item.code().equals(code)) {
                return item;
            }
        }
        return null;
    }

    /** 도전적 행동 세부 항목을 번호로 찾기 */
    public BehaviorItem behaviorById(short id) {
        for (BehaviorItem item : data().behaviors()) {
            if (item.id() == id) {
                return item;
            }
        }
        throw new IllegalStateException("behavior_item에 없는 번호: " + id);
    }

    // ------------------------------------------------------------------
    // 처음 한 번 DB에서 읽기
    // ------------------------------------------------------------------

    private Data data() {
        Data current = data;
        if (current == null) {
            synchronized (this) {                               // 여러 요청이 동시에 와도 한 번만 읽게
                if (data == null) {
                    data = load();
                }
                current = data;
            }
        }
        return current;
    }

    private Data load() {
        Map<Kind, List<Item>> items = new HashMap<>();
        for (Kind kind : Kind.values()) {
            // weather 테이블만 is_etc 칸이 없어서 FALSE로 채운다
            String etc = kind == Kind.WEATHER ? "FALSE" : "is_etc";
            String sql = "SELECT " + kind.idColumn + " AS id, code, name, " + etc + " AS etc, sort_order"
                    + " FROM " + kind.table + " ORDER BY sort_order";
            items.put(kind, jdbc.query(sql, (rs, n) -> new Item(
                    rs.getShort("id"), rs.getString("code"), rs.getString("name"),
                    rs.getBoolean("etc"), rs.getInt("sort_order"))));
        }
        List<BehaviorItem> behaviors = new ArrayList<>(jdbc.query(
                "SELECT i.item_id, i.code, a.code AS area_code, i.is_etc, i.sort_order"
                        + " FROM behavior_item i JOIN behavior_area a ON a.area_id = i.area_id"
                        + " ORDER BY a.sort_order, i.sort_order",
                (rs, n) -> new BehaviorItem(rs.getShort("item_id"), rs.getString("code"),
                        rs.getString("area_code"), rs.getBoolean("is_etc"), rs.getInt("sort_order"))));
        return new Data(items, behaviors);
    }

    /** "선택지 제공" 과 "선택지제공" 을 같게 보기 위해 공백을 지운다 */
    private static String normalize(String s) {
        return s == null ? "" : s.replace(" ", "").trim();
    }
}