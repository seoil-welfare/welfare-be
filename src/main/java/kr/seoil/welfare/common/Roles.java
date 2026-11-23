package kr.seoil.welfare.common;

/**
 * 직원 역할 (staff.role 값).
 * 팀장·국장만 할 수 있는 일(계정 추가, 참여 활동·출력 양식 관리, 이용 종결·재개, 다른 직원의 일지·카드 손보기)은
 * 모두 isManager로 확인한다. 기준이 바뀌면 여기만 고친다.
 */
public final class Roles {

    public static final String STAFF = "staff";
    public static final String TEAM_LEAD = "teamLead";
    public static final String DIRECTOR = "director";

    private Roles() {
    }

    /** 팀장·국장인지 */
    public static boolean isManager(String role) {
        return TEAM_LEAD.equals(role) || DIRECTOR.equals(role);
    }
}
