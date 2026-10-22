# 커밋 메시지 규칙

형식이 맞지 않으면 커밋이 취소돼요. 빌드를 한 번 하면(`./gradlew build`, IntelliJ 실행 등) 검사가 자동으로 켜집니다.
프론트(welfare-fe)와 같은 규칙입니다.

## 형식

```
<타입>: <무엇을 했는지>

<필요하면 한 줄 띄우고 이유나 자세한 내용>
```

```
feat: 달력 날짜별 이용자 상태 API 구현

GET /api/calendar/days/{date} — 그날 이용자별 일지 상태를 돌려준다
```

## 타입

| 타입 | 언제 쓰나 | 예시 |
|---|---|---|
| `feat` | 새 기능, 새 API | `feat: 이용자 등록 API 구현` |
| `fix` | 버그 수정 | `fix: 종결된 이용자가 달력 대상에 포함되는 문제 수정` |
| `style` | 동작과 무관한 코드 형식 (들여쓰기, 줄바꿈, import 정리) | `style: DiaryService 들여쓰기 정리` |
| `refactor` | 동작은 같고 코드 구조만 바꿈 | `refactor: 통계 집계 쿼리를 Repository로 분리` |
| `docs` | 문서, 주석 | `docs: API 명세에 오류 코드 추가` |
| `chore` | 빌드 설정, 의존성, DB 시드 데이터 | `chore: Spring Boot 3.5.6으로 업데이트` |
| `test` | 테스트 추가·수정 | `test: 로그인 실패 케이스 테스트 추가` |

DB 스키마를 바꾸는 마이그레이션(`db/migration`)은 그 변경이 필요한 기능 커밋(`feat`, `fix`)에 같이 넣습니다.

## 규칙

- 타입은 위 7개 중 하나, 소문자로 씁니다.
- 콜론(`:`) 뒤에 한 칸 띄웁니다.
- 설명은 한국어로 50자 이내, 끝에 마침표를 붙이지 않습니다. 공백·괄호도 글자 수에 들어갑니다.
- API 경로처럼 길어지는 내용은 제목 대신 본문에 씁니다.
- 한 커밋에는 한 가지 일만 담습니다. "A 구현, B 수정"처럼 쓰게 되면 커밋을 나눕니다.
- 범위를 밝히고 싶으면 타입 뒤 괄호에 영어 소문자로 씁니다. 예: `fix(calendar): 주말 날짜 계산 오류 수정`

## 검사 통과 예외

git이 자동으로 만드는 메시지는 검사하지 않습니다: `Merge ...`, `Revert ...`, `fixup! ...`, `squash! ...`

## 검사가 동작하는 방식

- `.githooks/commit-msg`가 커밋할 때마다 메시지를 검사합니다. git에 들어 있는 `sh`만 쓰므로 따로 설치할 것이 없습니다.
- `build.gradle`의 `installGitHooks` 작업이 컴파일 전에 `git config core.hooksPath .githooks`를 설정합니다.
- 빌드 전에 바로 켜려면 저장소 폴더에서 `git config core.hooksPath .githooks`를 직접 실행하면 됩니다.
- `.gitattributes`가 hook 파일을 항상 LF 줄바꿈으로 받게 합니다. Windows에서 CRLF로 바뀌면 hook이 실행되지 않습니다.
- `git commit --no-verify`로 검사를 건너뛸 수는 있지만 쓰지 않습니다.
