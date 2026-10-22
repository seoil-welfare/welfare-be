package kr.seoil.welfare;                                       // 이 파일이 속한 패키지(폴더) 이름

import org.springframework.boot.SpringApplication;              // 스프링 앱을 실행하는 클래스
import org.springframework.boot.autoconfigure.SpringBootApplication; // "여기가 스프링 앱의 시작점"이라는 표시

@SpringBootApplication                                          // 이 패키지 아래의 @RestController 등을 자동으로 찾아 등록
public class WelfareApplication {

    public static void main(String[] args) {                    // 자바 프로그램의 시작 지점
        SpringApplication.run(WelfareApplication.class, args);  // 서버 실행 (DB 연결 → Flyway로 테이블 생성 → 8080 포트 열기)
    }
}
