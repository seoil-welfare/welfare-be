# 기능명세서 v0.4

원광장애인종합복지관 일지 관리 앱 · 프론트·백엔드 구현 기준 · 2026. 11. 4.

> 기능 120개 — 구현 113 · 부분 구현 1 · 미구현 3 · 시연용 3

## 목차

1. [문서 개요](#1-문서-개요)
2. [화면 구성](#2-화면-구성)
3. [기능 상세 명세](#3-기능-상세-명세)
4. [API 명세](#4-api-명세)
5. [추가 API](#5-추가-api)
6. [프론트와 결정 사항이 다른 부분](#6-프론트와-결정-사항이-다른-부분)
7. [데이터 대응과 DB 반영 사항](#7-데이터-대응과-db-반영-사항)
8. [운영 정책](#8-운영-정책)
9. [협의 사항 현황](#9-협의-사항-현황)


## 1. 문서 개요

### 1.1. 목적

프론트엔드(welfare-fe)와 백엔드(welfare-be)에 실제로 구현된 화면과 동작을 기능 단위로 정리하고, 각 기능이 서버에서 어떻게 처리되는지(서버 처리, API, 데이터)를 함께 적는다. 남은 작업과 회의에서 정할 사항을 한곳에서 보기 위한 기준 문서다.

### 1.2. 기준 자료

- 프론트엔드 저장소 seoil-welfare/welfare-fe, main 브랜치 커밋 46e2653 (2026. 11. 3.)
- 백엔드 저장소 seoil-welfare/welfare-be, main 브랜치 커밋 66e62e2 (2026. 11. 1.)
- 프론트 백엔드 연동 명세 (welfare-fe docs/backend-integration.md) — 협의 사항 B-1~B-13
- 개발계획서 (FR-01~FR-92), 복지관 답변지 (2026. 9. 21.), 운영 DB 스키마 (src/main/resources/db/migration V1·V3·V4)

### 1.3. 버전별 변경

| 버전 | 변경 내용 |
| --- | --- |
| v0.1 | 개발계획서 기준으로 화면 14개, 기능 95개 정리 |
| v0.2 | 기준을 프론트 구현(welfare-fe)으로 변경. 기능마다 서버 처리·API 열 추가, 프론트에서 생긴 화면 6개와 신규 기능 추가 |
| v0.3 | 리뷰 반영. 공통 에러 코드 표(4.2), 일지 상태 전이 규칙(4.5), 동시 수정 규칙(4.6), 제안 API 장 신설(5장: 선택 항목 코드화, 통계 서버 집계, 직원 사용 중지), 운영 정책 장 신설(8장: 권한 기준안, 직원 사용 중지, 임시저장, 활동 시간), 관련 기능 행 수정(LG-01, JW-08~10, AC-02, JD-07, JT-06~07, MY-08~09, SF-01, SF-03 신규) |
| v0.4 | 백엔드 구현과 프론트 연결 상태 반영(fe PR #10~#21, be PR #1~#11. #7은 main에 빠져 #10으로 다시 들어감). 기준을 mock이 아닌 실제 서버 연결로 바꿈. 화면 2개(SU 회원가입, PA 참여 활동 관리)와 기능 10개 추가(LG-05→SU-01, JD-11, MY-14, PA-01~03, EX-03~04, SF-04~05). 4.3 엔드포인트를 실제 백엔드 31개로 갱신, 5장을 '추가 API'(구현됨)로 바꿈, 6·7·8·9장 현재 상태 갱신 |

기능 ID는 v0.1부터 같은 번호를 이어 쓴다. 번호 체계(FR 통일 / 약어 통일)는 팀에서 확정 후 맞춘다.

### 1.4. 표기

| 표기 | 의미 |
| --- | --- |
| 구현 | 프론트와 백엔드에 구현되어 실제 서버(http 모드)에서 동작함. 서버가 필요 없는 화면 동작은 프론트 구현만으로 '구현' |
| 부분 구현 | 화면은 있으나 일부 값이 임시이거나 동작이 덜 됨 |
| 미구현 | 회의나 기획서에서 정했지만 아직 없음 |
| 시연용 | 회의 비교·시연을 위한 임시 동작. 운영 규칙이 아님 |
| (저장 시 함께) | 별도 API 없이 일지 저장(POST·PUT /diaries) 요청 안에 들어가는 값 |
| 기준안 | 근거 자료(복지관 답변, 양식)가 있어 개발은 이 방향으로 진행하되, 회의에서 최종 확인하는 안 |
| 제안 | 이 문서가 제시하는 방법. 프론트 수정이나 회의 합의가 있어야 적용 |
| 회의 확인 | 복지관 또는 팀이 결정해야 하는 사항. 명세서가 정하지 않음 |
| B-n, U-n | 프론트 연동 명세의 협의 항목 번호와 미확정 항목 번호 (9장) |

## 2. 화면 구성

### 2.1. 앱 전체 지도

![앱 전체 지도](images/app-map.png)

v0.3 때 그린 지도라 회원가입(SU)과 참여 활동 관리(PA)는 들어 있지 않다.

### 2.2. 화면 목록과 구현 현황

| 화면 ID | 화면명 | 탭 | 프론트 경로 | 기능 수 | 구현 | 부분 구현 | 미구현 | 시연용 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| LG | 로그인 | - | /login | 4 | 3 | 0 | 1 | 0 |
| SU | 회원가입 | - | /signup | 1 | 1 | 0 | 0 | 0 |
| HM | 일지 홈 | 홈 | /home | 4 | 4 | 0 | 0 | 0 |
| NW | 오늘의 소식 | 홈 | /news | 3 | 3 | 0 | 0 | 0 |
| JW | 일지 작성 | 홈 | /diaries/new | 11 | 11 | 0 | 0 | 0 |
| AC | 활동 입력/수정 | 홈 | /diaries/new/activities/:id | 11 | 11 | 0 | 0 | 0 |
| BH | 도전적 행동 선택 | 홈 | …/activities/:id/behaviors/:type | 4 | 3 | 0 | 1 | 0 |
| CL | 달력 | 달력 | /calendar | 3 | 3 | 0 | 0 | 0 |
| ST | 통계 메인 | 통계 | /stats | 11 | 11 | 0 | 0 | 0 |
| SB | 도전적 행동 상세 통계 | 통계 | /stats/behaviors | 7 | 7 | 0 | 0 | 0 |
| SA | 참여 활동 상세 통계 | 통계 | /stats/activities | 6 | 6 | 0 | 0 | 0 |
| SC | 항목별 상세 통계 | 통계 | /stats/:category | 1 | 0 | 0 | 0 | 1 |
| JD | 일지 상세 (작성 완료) | 공통 | /diaries/:id | 11 | 10 | 0 | 0 | 1 |
| JT | 일지 상세 (작성 중) | 공통 | /diaries/:id | 8 | 7 | 0 | 0 | 1 |
| MY | 마이페이지 | 마이페이지 | /mypage | 11 | 11 | 0 | 0 | 0 |
| US | 이용자 목록·등록 | 마이페이지 | /users, /users/new | 2 | 2 | 0 | 0 | 0 |
| CU | 이용자 정보 | 마이페이지 | /users/:id | 8 | 8 | 0 | 0 | 0 |
| SF | 담당자 목록·계정 추가 | 마이페이지 | /staff, /staff/new | 5 | 4 | 1 | 0 | 0 |
| PA | 참여 활동 관리 | 마이페이지 | /settings/participation | 3 | 3 | 0 | 0 | 0 |
| SE | 내 정보·비밀번호 변경 | 마이페이지 | /profile, /settings/password | 2 | 2 | 0 | 0 | 0 |
| EX | 파일 출력 | 마이페이지 | /exports/:kind | 4 | 3 | 0 | 1 | 0 |
| 합계 |  |  |  | 120 | 113 | 1 | 3 | 3 |

기능 수는 행 기준이다(MY-05~07처럼 묶은 행은 1개로 셈). 경로는 프론트 개발용 라우팅이며 공식 요구사항은 아니다.

## 3. 기능 상세 명세

### 3.1. 로그인 (LG)  —  /login

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **LG-01** 로그인<br><sub>기획서 FR-01</sub> | 아이디·비밀번호 입력. 빈칸 오류와 인증 실패 문구를 구분한다. 비밀번호 보기 토글. 성공하면 이전 사용자의 캐시를 지우고 홈으로 이동. 회원가입 직후에는 가입한 아이디를 채워 둔다. | 아이디로 직원을 찾아 비밀번호 해시를 비교하고 세션(쿠키)을 만든다. 실패하면 401(INVALID_CREDENTIALS). 사용 중지 계정은 비밀번호가 맞아도 403(ACCOUNT_DISABLED). | POST /auth/login<br>*staff* | 구현<br>(mock: 비밀번호 demo1234) |
| **LG-02** 자동 로그인<br><sub>기획서 FR-02</sub> | 체크 여부를 autoLogin 값으로 로그인 요청에 함께 보낸다. | 세션 유지 기간: 기본 1시간, autoLogin이면 30일(움직임이 없을 때 기준). | POST /auth/login<br>*세션* | 구현<br>기간은 회의 확인(B-9) |
| **LG-03** 비밀번호 재설정<br><sub>기획서 FR-03</sub> | 안내 창("담당자에게 문의해 주세요")만 띄운다. 실제 절차 없음. | 없음. 방식 미정. 제안: 관리자가 임시 비밀번호 발급, 첫 로그인 때 변경 강제. | -<br>*staff* | 미구현<br>방식 회의 확인 |
| **LG-04** 로그인 상태 확인<br><sub>기획서 신규</sub> | 앱을 시작할 때 내 정보를 요청한다. 401이면 로그인 화면, 네트워크·서버 오류면 "다시 시도"를 보여 준다(세션이 살아 있을 수 있으므로). 쓰는 도중 세션이 만료돼 로그인 화면으로 오면 "로그인이 만료됐어요" 안내 창을 띄운다. | 세션의 직원 정보를 돌려준다. 세션이 없으면 401. | GET /auth/me<br>*staff* | 구현<br>기획서에 없던 기능 |

### 3.2. 회원가입 (SU)  —  /signup

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **SU-01** 회원가입<br><sub>기획서 신규</sub> | 아이디, 비밀번호·재확인, 이름, 생년월일(년·월·일), 직위를 입력한다. 칸을 벗어날 때 검사하고, 가입하면 로그인 화면으로 가서 아이디를 채워 둔다. | loginId, password, name, department, birthDate(선택)만 받는다. 권한(role)과 직위는 받지 않고 항상 staff(사회복지사)로 만든다. 로그인 전에 누구나 부를 수 있어서, 받으면 아무나 팀장·국장이 될 수 있기 때문이다. 아이디 중복 409(DUPLICATE_LOGIN_ID), 비밀번호 규칙 위반 400(PASSWORD_POLICY). 생년월일은 YYYY-MM-DD, 미래·1900년 이전이면 400 (be PR #10, V5). | POST /auth/signup<br>*staff* | 구현 |

### 3.3. 일지 홈 (HM)  —  /home

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **HM-01** 오늘 일지 작성<br><sub>기획서 FR-04</sub> | 일지 작성 화면으로 이동. 대상 이용자를 지정하지 않으면 그날 일지가 없는 첫 이용자를 기본으로 고른다. 모두 있으면 "오늘은 모든 이용자의 일지가 이미 있어요". | - | GET /diaries<br>(from=to=오늘)<br>*daily_log* | 구현 |
| **HM-02** 날짜·사용자 표시<br><sub>기획서 FR-05</sub> | 오늘 날짜, 로그인한 직원 이름, "대상 이용자 N명 · 오늘 저장된 일지 N건"을 표시한다. | - | GET /auth/me<br>GET /service-users<br>GET /diaries<br>*staff, client,<br>daily_log* | 구현 |
| **HM-03** 오늘의 소식<br><sub>기획서 FR-06</sub> | 오늘 발생한 등록·수정 이력을 최신순 4건까지 표시(원안 3건에서 늘림). 없으면 "오늘의 소식이 아직 없습니다". | 이력을 발생 시각 최신순으로 돌려준다. | GET /news<br>(since=오늘)<br>*log_history* | 구현 |
| **HM-04** 소식 상세 이동<br><sub>기획서 FR-07</sub> | 영역 전체를 누르면 소식 화면으로 이동. '더 보기'는 소식 개수와 상관없이 항상 보인다(원안: 3건 이상일 때만). | - | -<br>*-* | 구현 |

### 3.4. 오늘의 소식 (NW)  —  /news

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **NW-01** 활동 내역 조회<br><sub>기획서 FR-08</sub> | 오늘·어제 이력을 날짜별로 끊어 소식 1건당 카드 1장으로 표시(이용자, 일지 날짜, 발생 시각, 행위자). '지난 소식 더 보기'는 소식이 없는 날을 건너뛰고 소식이 있는 가장 가까운 날까지 불러온다(최대 365일 전). 화면을 열면 알림을 전부 읽음으로 바꾼다. | since 날짜(포함) 이후 이력을 최신순으로, 일지 요약(id·이용자·날짜)과 함께 돌려준다. 작성 중 일지는 이력이 없다. | GET /news?since<br>POST /notifications/read<br>*log_history,<br>daily_log* | 구현 |
| **NW-02** 활동 필터<br><sub>기획서 FR-09~11</sub> | 전체·등록만·수정만과 각 건수를 표시. 추가로 불러온 목록에도 같은 필터를 적용한다. | - (프론트가 거른다) | -<br>*log_history.kind* | 구현 |
| **NW-03** 일지 상세 이동<br><sub>기획서 FR-12</sub> | 해당 일지 상세로 이동한다. 수정 이력을 눌러도 최신 내용을 보여 준다. | - | GET /diaries/:id<br>*daily_log* | 구현 |

### 3.5. 일지 작성 (JW)  —  /diaries/new

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **JW-01** 이용자·날짜 표시<br><sub>기획서 FR-08*</sub> | 대상 이용자 선택 상자(이용 중인 이용자만)와 일지 날짜를 표시한다. 주소로 오늘 이후 날짜가 오면 작성을 시작하지 않고 "오늘 이후 날짜의 일지는 쓸 수 없어요." 안내와 달력으로 돌아가는 버튼만 보여 준다. | 새 일지(POST)는 오늘(한국 날짜)까지만 받는다. 내일 이후면 400(VALIDATION_ERROR). 수정(PUT)은 날짜를 바꿀 수 없어(B-8) 따로 막지 않는다. | GET /service-users<br>*client* | 구현 |
| **JW-02** 대상 이용자 변경<br><sub>기획서 FR-09*</sub> | 입력 내용을 유지한 채 대상만 바꾼다. 같은 날 일지가 이미 있는 이용자를 고르면 경고한다. 서버에 한 번이라도 저장된 일지(자동 임시저장 포함)는 이용자를 바꿀 수 없게 막는다(B-8). | 이용자·날짜당 1건. 이미 있으면 덮어쓰지 않고 409(DIARY_ALREADY_EXISTS)와 기존 일지 번호를 돌려준다(B-1). | GET /diaries<br>(from=to=작성일)<br>*daily_log<br>UNIQUE(client_id,<br>log_date)* | 구현<br>409 때 기존 일지로 이동은 없음(문구만) |
| **JW-03** 날씨 선택<br><sub>기획서 FR-10*</sub> | 서버 목록(GET /categories) 중 1개, 기본값 맑음. 받기 전·실패하면 앱 기본 목록. | 목록에 있는 값인지 검사한다. | (저장 시 함께)<br>*weather* | 구현 |
| **JW-04** 컨디션 선택<br><sub>기획서 FR-11*</sub> | 서버 목록 중 1개, 기본값 양호. '기타'면 입력란 표시. | '기타'일 때 입력 내용을 저장한다. | (저장 시 함께)<br>*condition_status,<br>condition_etc* | 구현 |
| **JW-05** 활동 요약<br><sub>기획서 FR-12*</sub> | 시작 시각순으로 시간·요약을 보여 주고, 도전적 행동이 있으면 "도전적 행동 N건" 배지와 배경으로 강조. 누르면 활동 수정. | - | -<br>*-* | 구현 |
| **JW-06** 활동 추가<br><sub>기획서 FR-13</sub> | "활동 N건"으로 표시(N/7 표기 제거). 빈 시간 칸이 남아 있는 동안 추가할 수 있다. | 같은 일지 안에서 활동 시간이 겹치면 거부한다(8.5). | -<br>*-* | 구현 |
| **JW-07** 특이사항<br><sub>기획서 FR-14</sub> | 자유 입력. | 그대로 저장한다. | (저장 시 함께)<br>*daily_log.remark* | 구현 |
| **JW-08** 나중에 이어서<br><sub>기획서 FR-15</sub> | 상태 draft로 저장하고 성공하면 입력을 비운 뒤 일지 상세로 이동. 실패하면 입력 유지, 자동 재시도 안 함. 응답이 없으면 "저장 여부를 확인하지 못했어요" 안내. | 일지와 활동 전체를 한 트랜잭션으로 저장한다(기존 활동 삭제 후 새로 넣기). 소식 이력은 남기지 않는다. 수정(PUT)이면 updatedAt을 비교해 다르면 409(DIARY_UPDATED_BY_OTHER) (4.6). 다른 직원의 작성 중 일지면 403(FORBIDDEN). | POST /diaries<br>PUT /diaries/:id<br>*daily_log.status,<br>activity_record,<br>record_** | 구현 |
| **JW-09** 작성 완료<br><sub>기획서 FR-16</sub> | 상태 completed로 저장하고 일지 상세로 이동. | JW-08과 같이 저장. 모든 활동에 시간이 있어야 한다. 처음 완료면 '등록', 완료 일지를 다시 저장하면 '수정' 이력과 수정자를 기록(바뀐 내용이 없으면 이력을 남기지 않음). 완료 → 작성 중 되돌리기는 400(INVALID_STATUS_TRANSITION)으로 거부(4.5). | POST /diaries<br>PUT /diaries/:id<br>*daily_log,<br>log_history* | 구현 |
| **JW-10** 임시저장<br><sub>기획서 신규</sub> | 입력이 2초 멈추거나 화면이 가려지면 status draft로 서버에 자동 저장한다. 상단 '임시저장'은 그 저장을 바로 실행하고 화면에 머문다. 첫 저장 응답의 id를 넣어 두고 이후는 PUT으로 보낸다. 409(동시 수정)이면 자동 저장을 멈추고 최신 내용을 불러올지 묻는다. | JW-08과 같다. | POST → PUT /diaries<br>*daily_log.status* | 구현<br>버튼 구성은 U1 회의 확인 |
| **JW-11** 중복 제출 막기<br><sub>기획서 신규</sub> | 저장 중에는 두 버튼을 모두 비활성화한다. | 새 일지 중복은 B-1의 409가 막는다(B-2). | -<br>*-* | 구현 |

### 3.6. 활동 입력/수정 (AC)  —  /diaries/new/activities/:id

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **AC-01** 화면 제목<br><sub>기획서 FR-17</sub> | 신규는 '활동 입력', 수정은 '활동 수정'. | - | -<br>*-* | 구현 |
| **AC-02** 활동 시간<br><sub>기획서 FR-18</sub> | 10~17시, 30분 단위 칸 중에서 고른다. 같은 일지의 다른 활동이 쓴 칸은 고를 수 없다. | HH:mm 형식, 10:00~17:00, 30분 단위, 종료가 시작보다 늦은지, 같은 일지 안에서 겹치지 않는지 검사한다(프론트 ACTIVITY_SLOTS와 같음). 작성 중 일지는 시간이 비어 있어도 된다. | (저장 시 함께)<br>*activity_record.<br>start_time,<br>end_time* | 구현<br>기준안(8.5) 반영 |
| **AC-03** 참여 활동<br><sub>기획서 FR-19</sub> | 서버 목록(GET /categories) 복수 선택, '기타'면 입력란. 숨긴 항목은 이미 고른 일지에서만 보인다. | 목록 값인지 검사(띄어쓰기 무시), 기타 내용 저장. 숨긴 항목도 저장은 받아 준다(과거 일지 재저장용). | (저장 시 함께)<br>*record_activity_<br>type* | 구현 |
| **AC-04** 활동 상세 내용<br><sub>기획서 FR-20</sub> | 자유 서술. 길면 줄바꿈되며 입력란이 늘어난다. | 그대로 저장한다. | (저장 시 함께)<br>*activity_record.<br>content* | 구현 |
| **AC-05** 사전 지원<br><sub>기획서 FR-21</sub> | 서버 목록 복수 선택, '없음' 배타 동작(시연 설정에서 끌 수 있음). | 목록 값 검사. '없음'과 다른 항목이 같이 오는 경우의 처리는 미정. | (저장 시 함께)<br>*record_pre_<br>support* | 구현<br>'없음' 배타는 U2 확인 |
| **AC-06** 이용자 반응<br><sub>기획서 FR-22</sub> | 서버 목록 복수 선택, '기타'면 입력란. | 목록 값 검사. | (저장 시 함께)<br>*record_reaction* | 구현<br>'도전적 행동' 항목 없음(양식에는 있음) |
| **AC-07** 긍정행동지원<br><sub>기획서 FR-23</sub> | 서버 목록 복수 선택, '기타'면 입력란. | 목록 값 검사. | (저장 시 함께)<br>*record_pbs_<br>support* | 구현 |
| **AC-08** 도전적 행동<br><sub>기획서 FR-24</sub> | '있음'을 켜면 4개 유형이 나타난다. 타해·자해·파괴행동은 세부 선택 화면으로 이동, 기타는 바로 입력. '있음'을 끄면 선택한 내용도 비운다. | hasBehavior가 false인데 행동이 함께 오면 거부(또는 무시)한다. | (저장 시 함께)<br>*activity_record.<br>has_behavior,<br>record_behavior* | 구현 |
| **AC-09** 저장하고 다음 활동<br><sub>기획서 FR-25</sub> | 작성 중 목록에 반영하고 일지 작성 화면으로 돌아간다. 바뀐 내용은 자동 임시저장(JW-10)으로 서버에 저장된다. | - | -<br>*(앱 메모리 → JW-10)* | 구현<br>협의 B-5 |
| **AC-10** 이전 활동과 같게<br><sub>기획서 신규</sub> | 직전 활동의 참여 활동·사전 지원·반응·긍정행동지원 선택을 그대로 가져온다(시간·내용 제외). | - | -<br>*-* | 구현<br>기획서에 없던 기능 |
| **AC-11** 취소<br><sub>기획서 신규</sub> | 입력을 버리고 일지 작성 화면으로 돌아간다. | - | -<br>*-* | 구현 |

### 3.7. 도전적 행동 선택 (BH)  —  …/activities/:id/behaviors/:type

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **BH-01** 모두 해제<br><sub>기획서 FR-26</sub> | 모든 체크를 해제하고, 입력해 둔 '기타' 내용은 남긴다. | - | -<br>*-* | 구현 |
| **BH-02** 세부 행동 선택<br><sub>기획서 FR-27</sub> | 복지관 확정 목록(타해·자해·파괴행동)을 서버 목록(GET /categories의 behaviorDetails)으로 그린다. 복수 선택, '기타'면 입력란. | 세부 항목 코드가 해당 유형에 속하는지 검사한다. | (저장 시 함께)<br>*behavior_area,<br>behavior_item* | 구현 |
| **BH-03** 세부 행동 검색<br><sub>기획서 신규</sub> | 없음. | - | -<br>*behavior_item* | 미구현<br>회의 결정 사항 |
| **BH-04** 선택 완료<br><sub>기획서 FR-28</sub> | "N개 선택 완료". 하나도 고르지 않으면 "선택 없이 완료"로 바뀌어 그대로 완료할 수 있다. | 0개 선택 허용 여부 결정(U3). | -<br>*(앱 메모리)* | 구현 |

### 3.8. 달력 (CL)  —  /calendar

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **CL-01** 월 달력<br><sub>기획서 미작성</sub> | 화면에 보이는 6주(42일)만 불러온다. 날짜마다 "완료 수/대상 이용자 수"(예: 2/3)와 도전적 행동 여부를 표시. 오늘 칸은 테두리. 원형 게이지는 비교용 설정. | 날짜별 completed·draft·target·hasBehavior를 돌려준다. 일지가 없는 날은 빠진다. 분모(target)는 현재 이용 중 인원, 종결된 이용자 일지는 제외. 최대 42일, 형식 오류 400. | GET /calendar?from&to<br>*daily_log, client* | 구현<br>분모 기준 회의 확인 |
| **CL-02** 날짜별 이용자 목록<br><sub>기획서 미작성</sub> | 선택한 날의 이용자별 상태(등록·작성 중·미작성)와 도전적 행동 건수. 일지가 있으면 상세로, 미작성이면 그 이용자·날짜로 새 일지 작성. canCreate가 false인 미작성 줄(오늘 이후 날짜)은 누를 수 없고 "오늘 이후 날짜는 아직 쓸 수 없어요"로 표시한다. | 이용 중인 이용자마다 status(completed·draft·none), diaryId, authorId, behaviorCount, canCreate를 돌려준다. canCreate는 그날 일지가 없고 오늘(한국 날짜) 이후가 아닐 때만 true. 작성자 이름은 보내지 않는다(계정 재사용 문제, 회의 확인). | GET /calendar/days/:date<br>*daily_log, client,<br>staff* | 구현 |
| **CL-03** 새 작성 제한<br><sub>기획서 미작성</sub> | 그날 모든 이용자에게 일지가 있거나 오늘 이후 날짜면 작성 버튼을 숨기고 이유를 표시한다. | B-1과 같은 규칙에 오늘 이후 날짜 금지를 더한 것(canCreate). | -<br>*UNIQUE 제약* | 구현 |

### 3.9. 통계 메인 (ST)  —  /stats

통계 화면(ST, SB, SA, SC)은 서버가 집계한 응답 하나(GET /stats/summary, 5.2)를 함께 쓴다. 상세 화면으로 옮겨도 다시 부르지 않는다.

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **ST-01** 이용자 선택<br><sub>기획서 FR-29</sub> | 선택한 이용자 기준으로 통계를 다시 불러온다. 고른 이용자·기간·단위는 상세 화면까지 유지. | - | GET /service-users<br>*client* | 구현 |
| **ST-02** 조회 기간 선택<br><sub>기획서 FR-30</sub> | 바텀시트 휠로 연·월·분기·주차를 고르고 '기간 적용' 버튼으로 적용한다. | - | -<br>*-* | 구현 |
| **ST-03** 조회 단위<br><sub>기획서 FR-31</sub> | 주·월·분기·연간, 기본은 월. 주 탭을 유지. | - | -<br>*-* | 구현<br>복지관도 주별 필요 |
| **ST-04** 도전적 행동 기록 건수<br><sub>기획서 FR-32</sub> | 선택된 세부 행동 개수의 합. | 서버가 집계한다(같은 활동의 같은 행동은 1건). | GET /stats/summary<br>(userId, unit, 기간)<br>*record_behavior* | 구현 |
| **ST-05** 발생 일수<br><sub>기획서 FR-33</sub> | 도전적 행동이 있는 날짜 수. | 서버가 집계한다(날짜 중복 없이). | (ST-04와 같음)<br>*daily_log* | 구현 |
| **ST-06** 이전 기간 대비 증감<br><sub>기획서 FR-34</sub> | 이전 기간이 0건이면 "–"와 "이전 기간 기록 없음". | 서버가 계산한다(previousEmpty). | (ST-04와 같음)<br>*-* | 구현 |
| **ST-07** 추이 그래프<br><sub>기획서 FR-35</sub> | 막대그래프. 구간은 주 6주·월 6개월·분기 4분기·연 5년. | 서버가 구간별로 집계해 숫자만 보낸다(5년치 일지를 받지 않음). | (ST-04와 같음)<br>*record_behavior* | 구현 |
| **ST-08** 유형별 도넛<br><sub>기획서 FR-36</sub> | 자해·타해·파괴행동·기타 건수와 비율. 색은 유형별로 고정. | 서버가 집계한다. | (ST-04와 같음)<br>*behavior_area* | 구현 |
| **ST-09** 도전적 행동 상세 이동<br><sub>기획서 FR-37</sub> | '자세히' → SB. | - | -<br>*-* | 구현 |
| **ST-10** 항목별 통계<br><sub>기획서 FR-38~42</sub> | 참여 활동·반응·사전 지원·긍정행동지원(활동 기준)과 컨디션(일지 기준). 많은 순 가로 막대, 처음엔 상위 3개, '더 보기'로 전체. | 서버가 집계한다. '기타' 입력은 '기타'로 합산. 0건 항목도 보내고 통계 메인이 뺀다. | (ST-04와 같음)<br>*record_* 4종,<br>daily_log* | 구현 |
| **ST-11** 항목별 상세 이동<br><sub>기획서 FR-43</sub> | 참여 활동은 SA, 나머지 4개는 SC로 이동. | - | -<br>*-* | 구현 |

### 3.10. 도전적 행동 상세 통계 (SB)  —  /stats/behaviors

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **SB-01** 대상·기간 표시<br><sub>기획서 FR-44</sub> | 이용자명과 기간. 메인의 선택을 이어받는다. | - | -<br>*client* | 구현 |
| **SB-02** 조회 단위<br><sub>기획서 FR-45</sub> | 메인 단위 유지, 바꾸면 다시 불러온다. | - | -<br>*-* | 구현 |
| **SB-03** 요약 통계<br><sub>기획서 FR-46</sub> | 기록 건수·발생 일수·증감. 발생일 평균 없음. | 서버가 집계한다(5.2). | GET /stats/summary<br>*record_behavior* | 구현 |
| **SB-04** 유형별 필터<br><sub>기획서 FR-47</sub> | 전체·타해·자해·파괴행동·기타와 건수. 필터를 바꾸면 상단 요약도 바뀐다(설정에서 끌 수 있음). 필터는 프론트가 응답에서 고른다. | - | -<br>*behavior_area* | 구현 |
| **SB-05** 세부 행동별 건수<br><sub>기획서 FR-48</sub> | 많은 순 가로 막대. | 서버가 집계한다(details). | (SB-03과 같음)<br>*behavior_item* | 구현 |
| **SB-06** 발생 기록 목록<br><sub>기획서 FR-49</sub> | 날짜·시작 시각 최신순, 5건씩 더 보기. 날짜·시간·세부 행동 표시. | 도전적 행동이 있던 활동 1개 = 1줄로 돌려준다(records). | (SB-03과 같음)<br>*activity_record,<br>record_behavior* | 구현<br>5건 단위는 시연값 |
| **SB-07** 발생 기록 상세 이동<br><sub>기획서 FR-50</sub> | 해당 일지 상세(JD)로 이동. | - | GET /diaries/:id<br>*daily_log* | 구현 |

### 3.11. 참여 활동 상세 통계 (SA)  —  /stats/activities

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **SA-01** 대상·기간 표시<br><sub>기획서 FR-51</sub> | 메인의 선택 유지. | - | -<br>*client* | 구현 |
| **SA-02** 조회 단위<br><sub>기획서 FR-52</sub> | 메인 단위 유지. | - | -<br>*-* | 구현 |
| **SA-03** 요약 통계<br><sub>기획서 FR-53</sub> | 전체 건수, 활동 종류 수, '기타' 입력 건수. | 서버가 집계한다(activities). | GET /stats/summary<br>*record_activity_<br>type* | 구현 |
| **SA-04** 활동별 참여 건수<br><sub>기획서 FR-54</sub> | 모든 종류를 많은 순 가로 막대로. 0건도 표시. | 서버가 집계한다. | (SA-03과 같음)<br>*record_activity_<br>type* | 구현 |
| **SA-05** 활동별 도전적 행동<br><sub>기획서 FR-55</sub> | 선택된 참여 활동 모두에 같은 건수를 넣는다(합계가 전체보다 클 수 있음을 화면에 표시). | 서버가 같은 규칙으로 집계한다(behaviorByParticipation). | (SA-03과 같음)<br>*record_activity_<br>type,<br>has_behavior* | 구현<br>배분 방식 협의 |
| **SA-06** 활동별 이용자 반응<br><sub>기획서 FR-56</sub> | 기준 참여 활동을 고르는 선택 상자를 추가했다. 선택지는 응답의 활동 목록이라 새로 추가한 활동도 고를 수 있다. | 활동별 반응을 함께 보낸다(reactionByParticipation). | (SA-03과 같음)<br>*record_reaction* | 구현<br>선택 상자는 시연안 |

### 3.12. 항목별 상세 통계 (SC)  —  /stats/:category

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **SC-01** 항목별 상세 통계<br><sub>기획서 FR-43</sub> | 이용자 반응·사전 지원·긍정행동지원·컨디션 각각의 상세 화면. 항목별 건수를 많은 순으로 표시. | 서버가 집계한다(5.2). | GET /stats/summary<br>*record_* ,<br>daily_log* | 시연용<br>기획서에 화면 정의 없음 |

### 3.13. 일지 상세 (작성 완료) (JD)  —  /diaries/:id

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **JD-01** 기본 정보<br><sub>기획서 FR-57</sub> | 이용자명, 작성일, 작성자, 최종 수정자와 수정 시각, 상태 배지. | - | GET /diaries/:id<br>*daily_log.<br>created_by,<br>updated_by* | 구현 |
| **JD-02** 요약 수치<br><sub>기획서 FR-58</sub> | 기록된 활동 수, 도전적 행동 건수, 날씨를 카드로 표시. | - | (같음)<br>*record_behavior* | 구현 |
| **JD-03** 컨디션<br><sub>기획서 FR-59</sub> | 컨디션 표시. '기타'면 입력 내용, 비어 있으면 "입력된 내용이 없어요". | - | (같음)<br>*daily_log* | 구현 |
| **JD-04** 활동 목록<br><sub>기획서 FR-60</sub> | "활동 N건"과 시간순 목록. | 활동을 시작 시각순으로 돌려준다. | (같음)<br>*activity_record* | 구현 |
| **JD-05** 활동 펼치기<br><sub>기획서 FR-61</sub> | 아코디언으로 상세 내용·선택 항목·도전적 행동 표시. 긴 글은 줄바꿈된다. | - | -<br>*record_** | 구현 |
| **JD-06** 도전적 행동 강조<br><sub>기획서 FR-62</sub> | 도전적 행동이 있는 활동을 색으로 구분. | - | -<br>*has_behavior* | 구현 |
| **JD-07** 이 활동만 수정<br><sub>기획서 FR-63</sub> | 완료 일지에서만 보인다. 일지 전체를 작성 중 데이터로 불러온 뒤 활동 수정 화면으로 보낸다. 저장은 다시 일지 전체 저장. | PUT으로 일지 전체를 받는다. 상태는 completed로 유지하고 '수정' 이력과 수정자를 기록한다. updatedAt 비교(4.6). | GET /diaries/:id<br>PUT /diaries/:id<br>*daily_log,<br>log_history* | 구현<br>수정 가능 기간 회의 확인 |
| **JD-08** 특이사항<br><sub>기획서 FR-64</sub> | 없으면 "기록된 특이사항이 없어요". | - | -<br>*daily_log.remark* | 구현 |
| **JD-09** 일지 인쇄<br><sub>기획서 신규</sub> | '출력하기'를 누르면 일일 기록지 양식의 docx를 내려받는다. 화면에 불러온 일지로 브라우저에서 만든다. 작성 완료 일지에만 보인다. | 없음(출력 API 없이 기존 조회 결과만 씀). | GET /diaries/:id<br>*-* | 구현<br>docx 방식(B-12) |
| **JD-10** 없는 일지<br><sub>기획서 신규</sub> | "찾을 수 없는 일지예요"와 달력 이동 버튼. | 없는 id면 404. | GET /diaries/:id<br>*-* | 구현 |
| **JD-11** 공유<br><sub>기획서 FR-72</sub> | '출력하기' 옆 '공유하기' 버튼. "공유 기능은 준비 중이에요" 알림만. | - | -<br>*-* | 시연용<br>동작 회의 확인 |

### 3.14. 일지 상세 (작성 중) (JT)  —  /diaries/:id

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **JT-01** 작성 중 기본 정보<br><sub>기획서 FR-65</sub> | '작성 중' 배지와 작성자·날짜. | - | GET /diaries/:id<br>*daily_log* | 구현 |
| **JT-02** 날씨·컨디션<br><sub>기획서 FR-66</sub> | 임시 저장된 값 표시. | - | (같음)<br>*daily_log* | 구현 |
| **JT-03** 활동 현황<br><sub>기획서 FR-67</sub> | 임시 저장된 활동을 시간순으로. | - | (같음)<br>*activity_record* | 구현 |
| **JT-04** 남은 활동 안내<br><sub>기획서 FR-68</sub> | 미작성 영역 안내. | - | -<br>*-* | 구현<br>개수 제한 미정 |
| **JT-05** 특이사항<br><sub>기획서 FR-69</sub> | 없으면 "아직 작성 전이에요". | - | -<br>*daily_log.remark* | 구현 |
| **JT-06** 이어서 작성<br><sub>기획서 FR-70</sub> | 본인이 작성 중인 일지에만 버튼이 보인다. 기존 내용을 불러와 일지 작성 화면으로. | 작성 중 일지는 작성자만 수정할 수 있다. 다른 직원이면 403(FORBIDDEN). | PUT /diaries/:id<br>*created_by* | 구현<br>규칙은 회의 확인(8.1) |
| **JT-07** 다른 직원의 작성 중 일지<br><sub>기획서 FR-71</sub> | 하단 안내바("OO 님이 작성 중이에요"), 수정 버튼 없음. | JT-06과 같은 규칙으로 서버도 막는다. 주소를 직접 입력해도 저장할 수 없다. | -<br>*created_by* | 구현<br>규칙은 회의 확인(8.1) |
| **JT-08** 공유<br><sub>기획서 FR-72</sub> | 본인이 작성 중인 일지의 헤더 공유 아이콘. "공유 기능은 준비 중이에요" 알림만. | - | -<br>*-* | 시연용 |

### 3.15. 마이페이지 (MY)  —  /mypage

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **MY-01** 사용자 정보<br><sub>기획서 FR-73</sub> | 이름, 부서, 직위, 팀장 배지. | - | GET /auth/me<br>*staff* | 구현 |
| **MY-02** 내 정보 이동<br><sub>기획서 FR-74</sub> | SE-01로 이동. | - | -<br>*-* | 구현 |
| **MY-03** 이용자 목록 이동<br><sub>기획서 FR-75</sub> | US-01로 이동. | - | -<br>*-* | 구현 |
| **MY-04** 이용자 등록 이동<br><sub>기획서 FR-76</sub> | US-02로 이동. | - | -<br>*-* | 구현 |
| **MY-05~07** 파일 출력 이동<br><sub>기획서 FR-77~79</sub> | 일지 모아 출력·월간 기록 출력·엑셀 내보내기 → EX. | - | -<br>*-* | 구현 |
| **MY-08~09** 담당자 관리 이동<br><sub>기획서 FR-80~81</sub> | 팀장·국장에게만 메뉴가 보인다. | 권한은 서버가 검사한다(팀장·국장만, 국장 계정은 국장만). 누가 계정을 관리할지는 회의 확인(8.1). | -<br>*staff.role* | 구현<br>권한은 회의 확인 |
| **MY-10** 새 일지 알림<br><sub>기획서 FR-82</sub> | 스위치. 서버에 저장한다. 켜져 있고 마지막으로 읽은 뒤 다른 직원의 소식이 있으면 헤더 종 아이콘에 점을 띄운다. 푸시 알림은 없다. | 직원별로 설정을 저장하고, 마지막으로 읽은 뒤 다른 직원의 소식 수를 센다(알림을 꺼도 센다). 가상 데이터 계정은 기본 꺼짐. | GET·PUT /notifications/settings<br>GET /notifications/summary<br>*staff.notify_new_log,<br>notification read_at* | 구현<br>푸시 여부 회의 확인 |
| **MY-11** 비밀번호 변경 이동<br><sub>기획서 FR-83</sub> | SE-02로 이동. | - | -<br>*-* | 구현 |
| **MY-12** 로그아웃<br><sub>기획서 FR-84</sub> | 확인 창("저장하지 않은 내용은 사라져요"). 서버 요청이 실패해도 이 기기의 로그인 상태와 작성 중 일지를 지운다. | 세션을 지운다. | POST /auth/logout<br>*세션* | 구현 |
| **MY-13** 앱 버전<br><sub>기획서 FR-85</sub> | 하단에 버전 표시. | - | -<br>*-* | 구현 |
| **MY-14** 일지 항목 관리 이동<br><sub>기획서 신규</sub> | 팀장·국장에게만 '일지 항목 관리 > 참여 활동 관리' 메뉴가 보인다. PA로 이동. | 권한은 서버가 검사한다(PA). | -<br>*staff.role* | 구현<br>권한은 회의 확인(8.1) |

### 3.16. 이용자 목록·등록 (US)  —  /users, /users/new

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **US-01** 이용자 목록<br><sub>기획서 FR-75</sub> | 이용 중과 이용 종결을 나눠 표시하고, 담당 직원 이름(없으면 "미지정")을 함께 보여 준다. | 종결된 이용자도 포함해서 돌려준다(과거 일지에 이름을 표시해야 하므로). | GET /service-users<br>*client* | 구현 |
| **US-02** 이용자 등록<br><sub>기획서 FR-76</sub> | 이름·생년월일·담당 직원 입력. 이름 빈칸, 생년월일 YYYY-MM-DD 형식 검사. 사용 중지된 직원은 담당 직원 선택지에서 뺀다. | 같은 검사를 서버에서도 하고(미래 날짜 거부), 상태는 '이용 중'으로 만든다. | POST /service-users<br>*client* | 구현<br>불일치: 복지관은 6자리 |

### 3.17. 이용자 정보 (CU)  —  /users/:id

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **CU-01** 이용자 정보 표시<br><sub>기획서 FR-86</sub> | 이름·생년월일·담당 직원. | - | GET /service-users<br>*client* | 구현 |
| **CU-02** 이름 수정<br><sub>기획서 FR-87</sub> | 필수 입력. | 빈 이름 거부. | (저장 시 함께)<br>*client.name* | 구현 |
| **CU-03** 생년월일 수정<br><sub>기획서 FR-88</sub> | YYYY-MM-DD 형식 검사. | 실제 있는 날짜인지, 미래가 아닌지 검사. | (저장 시 함께)<br>*client.birth_date* | 구현<br>불일치: 복지관은 6자리 |
| **CU-04** 담당 직원 변경<br><sub>기획서 FR-89</sub> | 직원 선택 상자. 사용 중지된 직원은 빼되, 지금 담당자라면 그대로 보인다. | 존재하는 직원인지 검사. | (저장 시 함께)<br>*client.staff_id* | 구현 |
| **CU-05** 저장<br><sub>기획서 FR-90</sub> | 저장을 눌렀을 때만 반영. | id는 바꾸지 않는다. | PUT /service-users/:id<br>*client* | 구현 |
| **CU-06** 이용 종결<br><sub>기획서 FR-91</sub> | 확인 창("삭제가 아닙니다. 기존 일지와 통계는 그대로 남아요"). | 상태만 '종결'로 바꾸고 종결 시각을 남긴다. 과거 일지는 지우지 않는다. | POST /service-users/<br>:id/close<br>*client.status,<br>closed_at* | 구현<br>재이용 기능 없음 |
| **CU-07** 수정 취소<br><sub>기획서 FR-92</sub> | 이전 화면으로. | - | -<br>*-* | 구현 |
| **CU-08** 없는 이용자<br><sub>기획서 신규</sub> | "찾을 수 없는 이용자예요". | 없는 id면 404. | -<br>*-* | 구현 |

### 3.18. 담당자 목록·계정 추가 (SF)  —  /staff, /staff/new

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **SF-01** 담당자 목록<br><sub>기획서 FR-80</sub> | 이름, 부서·아이디, 팀장 배지. 사용 중지된 계정은 '사용 중지' 배지와 인원 수를 함께 표시. | 사용 중지된 직원도 포함해 돌려준다(과거 일지의 작성자 이름 표시용). 응답에 active 포함. 아이디(loginId)를 누구에게 보여 줄지는 결정 필요. | GET /staff<br>*staff* | 구현 |
| **SF-02** 계정 추가<br><sub>기획서 FR-81</sub> | 이름·아이디·부서(기본 '주간활동지원팀') 입력. 담당자로만 만든다. 비밀번호 입력란은 없다. 아이디 중복이면 입력란에 바로 오류. | 팀장·국장만. role은 비우거나 staff만 받고 그 밖의 값은 400(팀장은 SF-04, 국장은 SF-05로만). 아이디 중복 409(DUPLICATE_LOGIN_ID). 12자 임시 비밀번호를 만들어 응답에 한 번만 담는다(DB에는 해시만). | POST /staff<br>*staff* | 부분 구현<br>임시 비밀번호를 화면에 안 보여 줌(논의 중) |
| **SF-03** 계정 사용 중지 · 재개<br><sub>기획서 신규</sub> | 목록의 각 행에 '사용 중지' / '다시 사용' 버튼과 확인 창. 내 계정에는 버튼이 없다. | 팀장·국장만, 국장 계정은 국장만, 내 계정은 중지 불가(400). 사용 중지하면 로그인 거부(403 ACCOUNT_DISABLED)하고 이미 로그인된 기기도 다음 요청부터 막는다. 일지·작성자·담당 배정은 지우지 않는다(8.3). | POST /staff/:id/deactivate<br>POST /staff/:id/activate<br>*staff.is_active* | 구현 |
| **SF-04** 역할 변경<br><sub>기획서 신규</sub> | 담당자를 팀장으로 올리거나 팀장을 담당자로 내린다. 국장 계정·내 계정·사용 중지 계정에는 버튼이 없다. 오류는 message를 토스트로 보여 준다. | 팀장·국장만(403). 국장 계정 대상 403, 내 계정·사용 중지 계정 400, role이 staff·teamLead가 아니면 400(director 포함). 이미 그 역할이면 그대로 200. position도 함께 바꾼다(팀장 / 사회복지사). | POST /staff/:id/role<br>*staff.role, position* | 구현(be PR #11 검토 중) |
| **SF-05** 관리 권한 이양<br><sub>기획서 신규</sub> | 국장만 보인다. 받을 사람을 고르고, 팝업에서 내 계정을 '팀장으로 남기' 또는 '계정 사용 중지'로 고른다. 사용 중지를 고르면 로그인 화면으로 간다. | 국장만(403). 국장은 항상 1명. 받는 사람은 사용 중인 다른 계정(400). 한 트랜잭션에서 요청한 국장 줄을 잠그고, 원래 국장을 먼저 팀장으로 내린 뒤 대상을 국장으로 올린다. deactivate면 원래 국장을 사용 중지하고 그 세션을 끊는다. | POST /staff/:id/transfer-director<br>*staff.role, is_active* | 구현(be PR #11 검토 중) |

### 3.19. 참여 활동 관리 (PA)  —  /settings/participation

참여 활동 선택지를 팀장·국장이 관리한다. 다른 선택 항목(날씨, 사전 지원 등)은 아직 관리 화면이 없다.

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **PA-01** 참여 활동 목록<br><sub>기획서 신규</sub> | 숨긴 활동까지 순서대로 보여 준다. | 숨긴 항목도 함께 돌려준다(active). | GET /categories<br>*activity_type* | 구현 |
| **PA-02** 참여 활동 추가<br><sub>기획서 신규</sub> | 위쪽 입력칸에 이름을 넣어 추가(최대 30자). 이름 중복·형식 오류는 입력칸 아래에 표시. | 팀장·국장만(403). 빈 이름·30자 초과 400, 띄어쓰기만 다른 이름도 중복으로 보고 409(DUPLICATE_CATEGORY). 새 항목은 '기타' 바로 앞에 들어간다. | POST /categories/participation<br>*activity_type* | 구현 |
| **PA-03** 이름 변경 · 숨기기<br><sub>기획서 신규</sub> | 행을 누르면 창이 뜬다. 이름 변경, 숨기기·다시 보이기. '기타'는 숨기기 버튼이 없다. 바뀌면 선택 항목과 통계를 다시 불러온다. | 지우지 않고 숨긴다(active = false). 숨긴 활동은 새 일지에서만 빠지고 지난 일지·통계에는 남는다. '기타' 숨기기 400. | PUT /categories/participation/:code<br>*activity_type* | 구현<br>이름 변경은 코드 저장 전환 전에는 주의(5.1) |

### 3.20. 내 정보·비밀번호 변경 (SE)  —  /profile, /settings/password

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **SE-01** 내 정보<br><sub>기획서 FR-74</sub> | 아이디, 소속 팀, 직책, 권한 표시. | - | GET /auth/me<br>*staff* | 구현 |
| **SE-02** 비밀번호 변경<br><sub>기획서 FR-83</sub> | 현재·새 비밀번호·확인 입력. 빈칸과 새 비밀번호 불일치를 검사하고, 서버 오류 문구를 그대로 보여 준다. | 현재 비밀번호를 확인하고 규칙을 검사한 뒤 해시로 저장. 현재 비밀번호 불일치 400(CURRENT_PASSWORD_MISMATCH), 규칙 위반 400(PASSWORD_POLICY). | PUT /auth/password<br>*staff* | 구현<br>mock은 실제로 안 바꿈 |

### 3.21. 파일 출력 (EX)  —  /exports/:kind

파일은 출력 전용 API 없이, 작성 완료 일지를 GET /diaries로 받아 브라우저에서 만든다. 서버에서 만들기로 정해지면 화면은 그대로 두고 만드는 부분만 바꾼다(B-12).

| 기능 | 화면 동작 (프론트) | 서버 처리 (백엔드) | API · 데이터 | 상태 |
| --- | --- | --- | --- | --- |
| **EX-01** 출력 대상·기간 선택<br><sub>기획서 FR-77~79</sub> | 일지 모아 출력: 이용자 한 명·시작일·종료일. 월간 기록: 이용자 여러 명(기본 전원)·연월(기본 지난 달, 5년 전까지). 엑셀: 이용자 여러 명·기간. | - | GET /service-users<br>*client* | 구현 |
| **EX-02** 일지 모아 출력<br><sub>기획서 FR-77</sub> | 작성 완료 일지를 일일 기록지 docx 한 파일로 내려받는다. 하루 한 페이지, 일지 없는 날은 건너뜀. 한 번에 92일까지. 기간에 일지가 없으면 안내. | 없음. | GET /diaries<br>(userId, from, to,<br>status=completed)<br>*daily_log 및<br>하위 테이블* | 구현 |
| **EX-03** 월간 기록 출력<br><sub>기획서 FR-78</sub> | 이용자마다 월별활동일지 docx를 하나씩 연달아 내려받는다. 일별 표, 대표 활동·지원·반응, 지원 결과, 도전적 행동 횟수와 그림 2개, 월간 요약 문장을 채운다(주의사항·종합평가·서명란은 빈칸). 그 달 일지가 없는 이용자는 건너뛰고 결과에 알린다. | 없음. | GET /diaries<br>(그 달 1일~말일)<br>*daily_log 및<br>하위 테이블* | 구현<br>값 규칙은 회의 확인 |
| **EX-04** 엑셀 내보내기<br><sub>기획서 FR-79</sub> | 진행 표시 후 "출력 기능은 준비 중이에요" 안내. 파일이 만들어진 것처럼 표시하지 않는다. | 형식(열 구성)·생성 방식 결정 후 구현. | -<br>*daily_log 및<br>하위 테이블* | 미구현 |

## 4. API 명세

프론트 httpApi.ts가 부르는 경로와 백엔드가 지키는 규칙이다. 모든 경로는 백엔드에 구현되어 있다.

### 4.1. 공통 약속

| 항목 | 내용 |
| --- | --- |
| 기본 경로 · 형식 | /api 아래, JSON(UTF-8) |
| 날짜 · 시각 | 날짜는 YYYY-MM-DD(한국 시간 업무일), 시각은 오프셋이 붙은 ISO 8601(예: 2026-10-13T09:00:00+09:00), 활동 시간은 HH:mm |
| 오류 응답 | { "message": "...", "code": "...", "details": { ... } } — 코드 목록은 4.2 |
| 인증 | 쿠키 세션. 모든 요청에 쿠키를 보냄(credentials: include). 어느 요청이든 401이면 로그인 화면으로 이동 |
| 식별자 | id는 서버가 발급하고 프론트는 문자열로 다룸. 활동 id는 프론트가 만든 임시 값(act-…)이며 서버가 새로 발급해도 됨 |
| 재시도 | 조회는 5xx·네트워크 오류 시 최대 2회 자동 재시도, 저장은 자동 재시도하지 않음 |
| 권한 검사 | 모든 권한 규칙은 서버에서 검사한다. 화면의 메뉴 숨김은 보안이 아니다 (NFR-06) |

### 4.2. 공통 에러 코드

| HTTP | code | 발생 상황 | 프론트 동작 | 구분 |
| --- | --- | --- | --- | --- |
| 400 | VALIDATION_ERROR | 필수값 누락, 형식 오류(날짜·시간·목록에 없는 값 등) | 안내 문구 표시 | 구현 |
| 400 | INVALID_STATUS_TRANSITION | 작성 완료 일지를 작성 중으로 저장 (4.5) | 안내 문구 표시 | 구현 |
| 400 | CURRENT_PASSWORD_MISMATCH | 비밀번호 변경 시 현재 비밀번호가 틀림 | 입력란 오류 문구 | 구현 |
| 400 | PASSWORD_POLICY | 새 비밀번호가 규칙에 맞지 않음(가입·변경) | 입력란 오류 문구 | 구현 |
| 401 | UNAUTHENTICATED | 세션 없음 · 만료 | 로그인 상태를 비우고 로그인 화면으로. 쓰던 중이었으면 만료 안내 창 | 구현 |
| 401 | INVALID_CREDENTIALS | 로그인 아이디 · 비밀번호 불일치 | 로그인 화면 문구 | 구현 |
| 403 | ACCOUNT_DISABLED | 사용 중지된 계정으로 로그인하거나 요청 (8.3) | 로그인 화면 문구 | 구현 |
| 403 | FORBIDDEN | 권한이 없는 기능 요청, 다른 직원의 작성 중 일지 수정 (8.1) | 조회: 문구 + 다시 시도 / 저장: 알림 | 구현 |
| 404 | NOT_FOUND | 일지 · 이용자 · 직원 · 선택 항목 없음 | 상세 화면은 "찾을 수 없음" 화면 | 구현 |
| 409 | DIARY_ALREADY_EXISTS | 같은 이용자 · 날짜 일지가 이미 있음. 기존 일지 번호 포함 (B-1) | 안내 문구 표시(기존 일지로 이동은 없음) | 구현 |
| 409 | DIARY_UPDATED_BY_OTHER | 다른 사람이 먼저 수정함, updatedAt 불일치 (4.6) | 확인 후 최신 내용 다시 불러오기 | 구현 |
| 409 | DUPLICATE_LOGIN_ID | 가입 · 계정 추가 시 아이디 중복 | 아이디 입력란 오류 | 구현 |
| 409 | DUPLICATE_CATEGORY | 참여 활동 이름 중복(띄어쓰기 무시) | 입력란 오류 | 구현 |
| 500 | INTERNAL_ERROR | 서버 내부 오류 | 조회는 2회 자동 재시도 후 다시 시도, 저장은 재시도 안 함 | 구현 |

message는 화면에 그대로 표시되므로 사용자가 이해할 수 있는 한국어 문장으로 쓰고, SQL이나 예외 이름 같은 내부 정보를 넣지 않는다. details는 선택 항목이다.

### 4.3. 엔드포인트

| 메서드 · 경로 | 요청 | 응답 | 서버 규칙 | 관련 기능 |
| --- | --- | --- | --- | --- |
| POST /auth/signup | { loginId, password, name, department?, birthDate? } | Staff / 400 / 409 | role은 항상 staff. 아이디 중복 DUPLICATE_LOGIN_ID. 프론트가 함께 보내는 role은 무시 | SU-01 |
| POST /auth/login | { loginId, password, autoLogin } | Staff / 401 / 403 | 세션 생성(1시간, autoLogin이면 30일). 실패 INVALID_CREDENTIALS, 사용 중지 계정 ACCOUNT_DISABLED | LG-01, LG-02 |
| POST /auth/logout | - | 204 | 세션 삭제 | MY-12 |
| GET /auth/me | - | Staff / 401 | 로그인 전이면 401 (프론트는 로그아웃 상태로 처리) | LG-04, MY-01, SE-01 |
| PUT /auth/password | { currentPassword, newPassword } | 204 / 400 | 현재 비밀번호 확인, 규칙 검사, 해시 저장 | SE-02 |
| GET /diaries | userId, from, to, status (모두 선택, from·to 포함) | Diary[] | 날짜 → 이용자 순 정렬 | HM, JW, EX |
| GET /diaries/:id | - | Diary / 404 | 활동 포함 전체 | JD, JT, NW-03 |
| POST /diaries | SaveDiaryInput (diaryId 없음) | Diary / 400 / 409 | id 발급, 작성자 = 로그인 직원. completed면 'created' 이력. 같은 이용자·날짜가 있으면 409 DIARY_ALREADY_EXISTS. 오늘(한국 날짜) 이후 날짜면 400 VALIDATION_ERROR | JW-01, JW-08~10 |
| PUT /diaries/:id | SaveDiaryInput + updatedAt | Diary / 400 / 403 / 404 / 409 | 작성자 유지, 활동은 통째로 교체. 상태 전이(4.5), 동시 수정(4.6), 작성 중 일지는 작성자만(403) | JW-08~10, JD-07, JT-06 |
| GET /calendar | from, to (최대 42일) | 날짜별 { date, completed, draft, target, hasBehavior }[] | 일지 없는 날은 빠짐. 분모는 현재 이용 중 인원 | CL-01 |
| GET /calendar/days/:date | - | 이용자별 { status, diaryId, authorId, behaviorCount, canCreate }[] | 이용 중인 이용자만. 오늘 이후 날짜면 canCreate 전부 false | CL-02, CL-03 |
| GET /stats/summary | userId, unit, year · month · quarter · week | StatsSummary (5.2) | 작성 완료 일지만 집계 | ST, SB, SA, SC |
| GET /news | since (포함) | NewsEntry[] | 발생 시각 최신순. 작성 중 저장은 이력 없음 | HM-03, NW |
| GET /notifications/summary | - | { unreadCount, lastReadAt? } | 마지막으로 읽은 뒤 다른 직원의 소식 수 | MY-10 |
| POST /notifications/read | - | 204 | 지금까지 전부 읽음 | NW-01 |
| GET /notifications/settings | - | { notifyNewLog } | - | MY-10 |
| PUT /notifications/settings | { notifyNewLog } | { notifyNewLog } | - | MY-10 |
| GET /service-users | - | ServiceUser[] | 종결된 이용자도 포함 | JW, US, CU, ST, EX |
| POST /service-users | { name, birthDate, assignedStaffId } | ServiceUser | 상태 active로 생성 | US-02 |
| PUT /service-users/:id | (같음) | ServiceUser / 404 | id 유지 | CU-05 |
| POST /service-users/:id/close | - | ServiceUser | status를 closed로. 삭제 아님 | CU-06 |
| GET /staff | - | Staff[] | 사용 중지 직원 포함, active 포함 | SF-01 |
| POST /staff | { name, loginId, department, role?, birthDate? } | Staff + temporaryPassword / 400 / 403 / 409 | 팀장·국장만. role은 비우거나 staff만(그 밖은 400). 아이디 중복 DUPLICATE_LOGIN_ID | SF-02 |
| POST /staff/:id/deactivate | - | Staff (active: false) | 5.3 | SF-03 |
| POST /staff/:id/activate | - | Staff (active: true) | 5.3 | SF-03 |
| POST /staff/:id/role | { role: staff \| teamLead } | Staff / 400 / 403 / 404 | 5.4 | SF-04 |
| POST /staff/:id/transfer-director | { previousRole?: teamLead \| deactivate } | { director: Staff, previous: Staff } / 400 / 403 / 404 | 5.4 | SF-05 |
| GET /categories | - | Categories (5.1) | 숨긴 항목 포함 | JW, AC, BH, PA |
| POST /categories/participation | { label } | CategoryItem (201) / 400 / 403 / 409 | 5.1 | PA-02 |
| PUT /categories/participation/:code | { label?, active? } | CategoryItem / 400 / 403 / 404 / 409 | 5.1 | PA-03 |
| GET /health | - | { status, db, tables, … } | 개발 확인용 | - |

### 4.4. 일지 저장 데이터 (SaveDiaryInput)

| 필드 | 형식 | 설명 |
| --- | --- | --- |
| diaryId | 문자열, 선택 | 없으면 새 일지(POST), 있으면 수정(PUT) |
| updatedAt | ISO 8601 | PUT일 때 필수(없으면 400). 불러오거나 마지막으로 저장할 때 받은 값을 그대로 돌려보냄 (4.6) |
| userId · date | 문자열 · YYYY-MM-DD | 대상 이용자와 작성일 |
| status | draft \| completed | 작성 중 / 작성 완료 (4.5) |
| weather · condition · conditionOther | 현재 한글 라벨 | 예: "맑음", "기타" + 직접 입력. 코드 전환 후에는 코드 (5.1) |
| notes | 문자열 | 특이사항 |
| activities[] | 배열 | id, startTime, endTime, detail, hasBehavior, behaviors[], 그리고 아래 선택 항목 |
| participation[] · preSupport[] · reaction[] · positiveSupport[] | 현재 한글 라벨 배열 | 각각 *Other 필드에 '기타' 직접 입력. 서버는 띄어쓰기를 무시하고 찾는다. 코드 전환 후에는 코드 배열 (5.1) |
| behaviors[] | { type, detailIds[], otherText } | type: otherHarm \| selfHarm \| destruction \| other, detailIds: oh-1, sh-1 같은 코드 (이미 코드 방식) |

### 4.5. 일지 상태 전이 규칙

| 저장 전 상태 | 요청 status | 결과 | 소식 이력 · 기록 |
| --- | --- | --- | --- |
| 없음 (새 일지) | draft | 허용 | 이력 없음. 작성자 = 로그인 직원 |
| 없음 (새 일지) | completed | 허용 | '등록' 이력 |
| draft (작성 중) | draft | 허용(작성자만) | 이력 없음 |
| draft (작성 중) | completed | 허용(작성자만) | '등록' 이력 |
| completed (작성 완료) | completed | 허용(누구나) | '수정' 이력, 수정자·수정 시각 기록. 바뀐 내용이 없으면 아무것도 바꾸지 않음 |
| completed (작성 완료) | draft | 거부 | 400 INVALID_STATUS_TRANSITION. 저장하지 않음 |

작성 완료된 일지도 계속 수정할 수 있다. 상태는 completed로 유지되고 수정 이력이 남는다. 실수로 '작성 완료'를 누른 경우에도 이 방식으로 고친다. 되돌리기를 막는 이유는, 되돌리면 그 일지가 통계에서 갑자기 빠지고 소식에도 기록이 남지 않기 때문이다.

### 4.6. 동시 수정 규칙 (B-3)

1. 프론트는 일지를 불러올 때 받은 updatedAt을 작성 중 데이터에 보관하고, PUT 요청에 그대로 담아 보낸다.

2. 서버는 요청의 updatedAt과 DB의 updated_at을 비교한다. 같으면 저장하고 새 updatedAt을 돌려준다.

3. 다르면 저장하지 않고 409 DIARY_UPDATED_BY_OTHER를 돌려준다. details에 마지막 수정자와 수정 시각을 넣는다.

4. 프론트는 "다른 분이 먼저 수정했어요"라고 안내하고 최신 내용을 다시 불러온다. 입력하던 내용은 버리기 전에 확인을 받는다. 자동 임시저장도 이때 멈춘다.

이용자가 10명 미만이라 충돌은 드물지만, 같은 일지를 휴대폰과 PC에서 동시에 열거나 두 직원이 같은 이용자의 일지를 고치면 생긴다. 이 규칙이 없으면 먼저 저장한 사람의 기록이 말없이 사라진다.

## 5. 추가 API

5.1~5.3은 v0.3에서 '제안 API'로 적었던 것이다. 셋 다 구현되어 프론트에 연결됐다. 남은 일은 5.1의 저장 값 코드 전환뿐이다. 5.4는 v0.4에서 새로 정했다.

### 5.1. 선택 항목 (B-4)

**조회와 참여 활동 관리는 구현됨.** 일지 작성(날씨·컨디션), 활동 입력(참여 활동·사전 지원·이용자 반응·긍정행동지원), 도전적 행동 세부 항목 화면이 GET /categories 목록으로 그린다. 받기 전이나 실패하면 앱 기본 목록(catalog.ts)으로 그린다. **저장 값은 아직 한글 라벨**이고(서버가 띄어쓰기를 무시하고 찾음), 코드 저장 전환은 남아 있다.

라벨로 저장하면 이름을 바꾸거나 띄어쓰기가 다를 때 통계가 쪼개진다. 예전에는 사전 지원 '선택지제공'과 긍정행동지원 '선택지 제공'이 어긋나 있었는데, 지금은 화면이 서버 라벨('선택지 제공')을 쓴다. 그래서 코드 전환 전에 참여 활동 이름을 바꾸면(PA-03) 과거 일지와 통계가 이어지지 않을 수 있다.

| GET /categories 응답 | 내용 |
| --- | --- |
| weather · condition · participation · preSupport · reaction · positiveSupport | [ { code, label, order, active, etc } ] 배열. etc는 '기타'(직접 입력) 여부 |
| behaviorTypes | [ { code, label, order } ]: otherHarm, selfHarm, destruction, other |
| behaviorDetails | { otherHarm: [ { code, label, order, active, etc } ], selfHarm: [...], destruction: [...], other: [...] } |
| 규칙 | 화면은 label로 표시. 항목을 없앨 때는 삭제하지 않고 active: false로 숨긴다. 숨긴 항목은 새 일지에서만 빠지고, 이미 고른 일지를 고칠 때는 보인다 |

| 참여 활동 관리 | 규칙 |
| --- | --- |
| POST /categories/participation { label } | 팀장·국장만. 이름 앞뒤 공백 제거, 빈칸·30자 초과 400, 띄어쓰기만 다른 이름도 409 DUPLICATE_CATEGORY. code는 서버가 발급(custom-번호). '기타' 바로 앞에 들어감 |
| PUT /categories/participation/:code { label?, active? } | 팀장·국장만. 보내지 않은 칸은 그대로. 바꿀 값이 없으면 400. '기타' 숨기기 400. 없는 code 404 |

**코드 규칙**

| 분류 | 코드 = 라벨 |
| --- | --- |
| 날씨 | sunny = 맑음 · partly-cloudy = 구름조금 · cloudy = 흐림 · rain = 비 · snow = 눈 |
| 컨디션 | good = 양호 · normal = 보통 · low = 저조 · observe = 관찰요망 · other = 기타 |
| 참여 활동 | daily-living = 일상생활훈련 · art = 예술활동 · sports = 체육활동 · preferred = 선호활동 · community = 지역참여활동 · cooking = 요리활동 · field-trip = 현장학습 · other = 기타 (관리 화면에서 추가한 활동은 custom-번호) |
| 사전 지원 | reinforcer = 강화물 · preparation = 활동준비 · guidance = 활동안내 · choice = 선택지 제공 · modeling = 모델링 · safety = 안전교육 · rest = 휴식 · none = 없음 · other = 기타 |
| 이용자 반응 | active = 적극적 참여 · stable = 안정적 참여 · partial = 부분적 참여 · not-joined = 참여하지 않음 · other = 기타  ('도전적 행동' 추가 여부는 회의 확인) |
| 긍정행동지원 | reinforcer = 강화물 · encouragement = 언어 촉진 및 격려 · choice = 선택지 제공 · rest = 휴식 · alternative = 대체행동 지원 · other = 기타 |
| 도전적 행동 세부 | 타해 oh-1 ~ oh-9 + oh-other, 자해 sh-1 ~ sh-10 + sh-other, 파괴행동 ds-1 ~ ds-8 + ds-other (복지관 확정 목록 순서) |

남은 전환 순서: ① 저장 값을 라벨에서 코드로 변경 → ② 통계 정렬 기준을 order로 변경. 실제 운영 데이터가 생기기 전에 하는 것이 좋다(지금은 가상 데이터뿐이라 이전 작업이 필요 없다).

### 5.2. 통계 서버 집계 (B-11)

**구현됨.** 예전에는 프론트가 작성 완료 일지를 모두 받아 직접 계산해서, 연 단위 통계를 보려면 5년치 일지를 휴대폰으로 받아야 했다. 서버가 PostgreSQL의 date_trunc(기간 묶기), GROUP BY(집계) 등으로 계산하고 숫자만 보낸다.

| 공통 파라미터 | 내용 |
| --- | --- |
| userId | 필수. 이용자 한 명 기준 (전체 합산 통계는 제공하지 않음) |
| unit | week \| month \| quarter \| year |
| year · month · quarter · week | unit에 맞는 값. week는 '해당 월의 N주차'(프론트 StatisticPeriod와 같음). 주차 계산과 월 경계 처리는 회의 확인 |

| 경로 | 응답 | 대응 화면 |
| --- | --- | --- |
| GET /stats/summary | { period, completedDiaries, behavior: { count, days, delta, previousEmpty, byType, typeSummary, trend[], details[], records[] }, participation[], reaction[], preSupport[], positiveSupport[], condition[], activities: { totalCount, kindCount, otherCount, behaviorByParticipation[], reactionByParticipation[] } } | ST, SB, SA, SC |

- 처음 제안한 /stats/behaviors · /stats/activities · /stats/categories는 따로 만들지 않았다. 유형 필터·기준 참여 활동은 프론트가 이 응답에서 고른다.
- 목록 항목(CountItem)은 { code, label, count }. 많은 순, 같으면 목록 순서이며 0건 항목도 넣는다. 통계 메인은 0건을 빼고 보여 준다.
- details는 세부 행동 { type, detailId, count } (1건 이상만). records는 도전적 행동이 있었던 활동 1개 = 1줄, 날짜·시작 시각 최신순.
- 규칙은 프론트 statsService.ts의 statsSummary와 같다. 같은 DB 일지로 둘을 비교해 이용자 4명 × 기간 8개가 모두 같은 값임을 확인했다. mock 모드는 지금도 프론트 계산을 쓴다.

**집계 규칙 (개발계획서 「통계 집계 공통 규칙」을 서버에서 그대로 적용)**

- 작성 완료(completed) 일지만 집계. 작성 중 일지는 제외. 기간 경계는 한국 시간 기준
- 도전적 행동은 세부 항목 선택 건수. 같은 활동의 같은 행동은 1건, 최상위 '기타'는 내용이 있으면 1건. 발생 일수는 날짜 중복 없이
- 참여 활동·반응·사전 지원·긍정행동지원은 활동 기준, 컨디션은 일지 기준. '기타' 자유 입력은 '기타'로 합산
- 정렬은 건수 많은 순, 같으면 정해진 항목 순서(order). 이전 기간이 0건이면 previousEmpty: true

### 5.3. 직원 사용 중지 (SF-03)

**구현됨.**

| 메서드 · 경로 | 응답 | 서버 규칙 |
| --- | --- | --- |
| POST /staff/:id/deactivate | Staff (active: false) | is_active = false. 팀장·국장만, 국장 계정은 국장만, 내 계정은 400. 사용 중지된 계정은 로그인과 이후 요청이 403 ACCOUNT_DISABLED. 일지·작성자 기록·담당 배정은 그대로 |
| POST /staff/:id/activate | Staff (active: true) | 다시 로그인할 수 있게 한다 |

### 5.4. 직원 역할 변경 · 관리 권한 이양 (SF-04, SF-05)

**구현됨(be PR #11 검토 중).** 역할은 국장(director) > 팀장(teamLead) > 담당자(staff)이고 국장은 항상 1명이다. 오류는 4.2 형식이며 새 code는 없다(400 VALIDATION_ERROR, 403 FORBIDDEN, 404 NOT_FOUND). message는 화면에 그대로 띄우는 문장이다.

| 메서드 · 경로 | 응답 | 서버 규칙 |
| --- | --- | --- |
| POST /staff/:id/role { role } | Staff (role·position 바뀐 뒤) | 팀장·국장만(403 "역할 변경은 팀장·국장만 할 수 있어요."). 국장 계정 대상 403. 내 계정(국장 본인 포함)·사용 중지 계정 400. role은 staff·teamLead만(director·빈 값·대소문자 다름 400). 없는 직원·숫자 아닌 id 404. 이미 그 역할이면 바꾸지 않고 200 |
| POST /staff/:id/transfer-director { previousRole? } | { director, previous } | 국장만(403 "관리 권한 이양은 국장만 할 수 있어요."). :id는 받을 사람, 사용 중인 다른 계정만(400). previousRole은 teamLead(기본) · deactivate, 그 밖은 400. previous는 role teamLead, position 팀장. deactivate면 previous.active false이고 요청 세션을 끊는다(응답은 200) |

- 이양은 한 트랜잭션이다. 요청한 국장 줄을 SELECT ... FOR UPDATE로 잠근 뒤 국장인지 다시 확인하므로, 동시에 두 번 눌러도 두 번째는 403이다.
- 원래 국장을 먼저 내리고 대상을 올린다. DB는 V6의 부분 유일 인덱스(staff_single_director)로 국장 1명을 보장한다.
- 국장 계정은 사용 중지 API로는 막히지 않는다(국장 계정은 국장만 바꿀 수 있고 내 계정은 중지 불가). 이양의 deactivate로만 중지된다.

## 6. 프론트와 결정 사항이 다른 부분

프론트는 복지관 답변지(9. 21.)가 반영되기 전 기준으로 만들어졌다. v0.3에서 정리한 차이와 지금 상태다.

| No | 항목 | 결정 사항 · 양식 | 지금 상태 | 남은 일 |
| --- | --- | --- | --- | --- |
| 1 | 도전적 행동 세부 목록 | 복지관 답변 2: 타해 10·자해 11·파괴행동 9개(기타 포함), '타인' 표현 제거 | **반영됨.** 확정 목록을 서버 목록으로 쓴다 | - |
| 2 | 생년월일 형식 | 복지관 답변 4: 6자리 | 이용자 등록·수정은 YYYY-MM-DD 입력, DB는 날짜 타입 | 화면 입력을 6자리로 바꿀지(DB는 날짜 유지. 6자리만 저장하면 1900년대·2000년대 구분 불가) |
| 3 | 활동 시간 | 복지관 양식: 10~17시, 30분 단위 | **반영됨.** 프론트 선택 칸과 서버 검사가 같다 (8.5) | - |
| 4 | 이용자 반응 '도전적 행동' | 복지관 양식에는 있음 (개발계획서에는 없음) | 항목 없음 | 복지관에 넣을지 확인 |
| 5 | '선택지 제공' 띄어쓰기 | 양식: 둘 다 '선택지 제공' | 화면은 서버 라벨('선택지 제공')을 씀. 앱 기본 목록(서버 응답 전)만 '선택지제공' | 코드 저장 전환으로 근본 해결 (5.1) |
| 6 | 세부 행동 검색 · 일지 단건 인쇄 | 회의에서 넣기로 결정 | 인쇄는 **반영됨**(JD-09, docx). 검색은 없음 | BH-03 구현 |
| 7 | 권한 구분 | 복지관 답변 7: 담당자·관리자 동일 권한, 전원 조회·작성 가능 | 담당자 관리·참여 활동 관리는 팀장·국장만(서버 검사), 작성 중 일지는 본인만 수정(서버 검사) | 회의 확인 (8.1) |
| 8 | 상단 임시저장 | - | **반영됨.** 서버 draft 저장 + 자동 임시저장 (8.4) | 버튼 구성(U1) |

## 7. 데이터 대응과 DB 반영 사항

운영 스키마는 백엔드 src/main/resources/db/migration(V1·V3·V4)이다. [schema_v0.3.sql](schema_v0.3.sql)과 아래 ERD는 v0.3 때 자료라 이후 바뀐 칸이 빠져 있을 수 있다.

![ERD 핵심 — 사람·일지·이력](images/erd-core.png)

![ERD 활동 기록 — 선택 항목 연결](images/erd-activity.png)

| 프론트 타입 (types/index.ts) | DB 테이블 | 반영 상태 |
| --- | --- | --- |
| Staff | staff | **반영됨(V1).** department, role(staff · teamLead · director), is_active(응답은 active), notify_new_log. birth_date DATE(V5, 응답은 birthDate). 국장 1명 부분 유일 인덱스(V6) |
| ServiceUser | client | **반영됨(V1).** staff_id(담당 직원), status(active · closed) + closed_at, birth_date DATE |
| Diary | daily_log | **반영됨(V1).** status(draft · completed). 동시 수정 비교에 updated_at 사용. 활동 시간은 작성 중일 때 비어도 됨(V3) |
| Activity | activity_record | 맞음 (start_time, end_time, content, has_behavior) |
| Activity.participation 등 4종 + *Other | record_* 4개 + etc_text | 맞음. '기타' 행의 etc_text에 직접 입력 저장 |
| 선택 항목 목록 | weather 등 목록 테이블 7개 | **반영됨(V1).** 각 테이블에 code, sort_order, is_active, 기타 여부 |
| BehaviorSelection | record_behavior + behavior_item | 맞음. behavior_item.code = oh-1 등 |
| DiaryEvent | log_history | **반영됨(V1).** 일지, 구분 created · updated, 행위자, 발생 시각 |
| 알림 읽은 시각 | (V4) | **반영됨(V4).** 직원별 마지막으로 읽은 시각 |

## 8. 운영 정책

명세서가 정할 수 없는 정책은 '기준안'(근거 자료가 있어 개발은 이 방향으로 진행)과 '회의 확인'(복지관·팀이 결정)으로 나눠 적는다.

### 8.1. 권한

| 구분 | 지금 동작 (서버 검사) | 근거 | 회의 확인 |
| --- | --- | --- | --- |
| 일지 조회 | 모든 직원이 모든 이용자의 일지를 조회 | 복지관 답변 7 | - |
| 작성 완료 일지 수정 | 모든 직원 가능, 수정자 기록 | 복지관 답변 7 | 수정 가능 기간을 둘지 |
| 다른 직원의 작성 중 일지 이어 쓰기 | 작성자만 가능(다른 직원은 403) | 답변 7은 작성 중 일지를 따로 언급하지 않음 | 확인 필요. 8.3(퇴사 직원의 작성 중 일지)과 함께 결정 |
| 이용자 등록 · 수정 · 종결 | 모든 직원 가능 | 답변 7 '별도 권한 구분 불필요' | 확인 필요 |
| 계정 추가 · 사용 중지 | 팀장·국장만. 계정 추가는 담당자로만. 국장 계정은 국장만, 내 계정은 중지 불가 | 답변 7은 기록 권한만 언급 | 확인 필요 |
| 역할 변경 (담당자 ↔ 팀장) | 팀장·국장만. 국장 계정·내 계정·사용 중지 계정은 대상 아님 (SF-04) | 2026-11-05 팀 결정 | - |
| 관리 권한 이양 | 국장만. 국장은 항상 1명, 이양으로만 국장이 된다 (SF-05) | 2026-11-05 팀 결정 | - |
| 참여 활동 관리 (일지 항목 수정) | 팀장·국장만 | 개발계획서 1.3은 '일지 카테고리 수정'을 국장만 적고 "팀장·국장 권한은 추가 협의 후 확정"이라 함. 복지관 답변 7은 전원 동일 권한. 지금은 계정 관리와 같은 기준으로 둠 | 국장만 / 팀장·국장 / 전원 중 결정 |
| 회원가입 | 누구나 가입, 항상 담당자(staff) 권한 | 가입 화면은 로그인 전이라 권한을 받지 않음 | 가입을 열어 둘지(계정 추가만 쓸지) |

### 8.2. 일지 상태

작성 중 → 작성 완료는 한 방향으로만 바뀐다. 작성 완료 일지는 완료 상태 그대로 수정한다. 자세한 규칙은 4.5.

### 8.3. 직원 사용 중지

- 퇴사·직무 정지 직원은 삭제하지 않고 사용 중지한다(구현). 로그인을 거부하고(403 ACCOUNT_DISABLED), 이미 로그인된 기기도 다음 요청부터 막는다.
- 직원 목록(GET /staff)에는 계속 포함한다. 과거 일지에 작성자 이름을 보여 줘야 하기 때문이다 (이용자 종결과 같은 원리).
- 서버는 담당 배정을 풀지 않는다. 그래서 확인 창에서 "담당 직원을 바꿔 주세요"라고 안내하고, 이용자 담당 직원 선택지에서는 사용 중지된 직원을 뺀다.
- 그 직원이 작성 중이던 일지: '작성자만 이어 쓰기' 규칙이라 아무도 완료할 수 없게 된다. 8.1의 이어 쓰기 권한을 정할 때 이 경우를 함께 정해야 한다 (회의 확인).
- 계정 재사용(이름만 바꿔 다른 사람이 쓰기)은 하지 않는다. 과거 일지의 작성자 이름과 본인 판정이 함께 넘어가기 때문이다. 사용 중지 후 새 계정을 만든다(기준안, 회의 확인).

### 8.4. 임시저장

- **구현됨.** 입력이 2초 멈추거나 화면이 가려지면 status draft로 서버에 자동 저장한다. 상단 '임시저장'은 같은 저장을 바로 실행한다.
- 새 일지의 첫 저장은 POST, 응답으로 받은 id를 작성 중 데이터에 넣고 그다음 저장부터는 PUT으로 보낸다. 그래야 두 번째 저장이 409(DIARY_ALREADY_EXISTS)가 되지 않는다.
- 상단 '임시저장'과 하단 '나중에 이어서'의 차이는 화면에 머무르는지 나가는지뿐이다. 버튼을 둘 다 둘지는 U1 안건으로 함께 결정한다 (회의 확인).
- 개인정보가 담긴 기록이므로 브라우저 저장소(localStorage)에 보관하는 방식은 쓰지 않는다.

### 8.5. 활동 시간

- **반영됨.** 복지관 양식대로 10시~17시, 30분 단위. 프론트 선택 칸(ACTIVITY_SLOTS)과 서버 검사가 같다.
- 같은 일지 안에서 활동 시간이 겹치면 서버가 거부한다. 작성 중 일지는 시간이 비어 있어도 되고, 작성 완료로 저장할 때는 모든 활동에 시간이 있어야 한다.

## 9. 협의 사항 현황

프론트 연동 명세 6장의 협의 항목과 지금 상태다.

| ID | 항목 | 지금 상태 | 남은 일 |
| --- | --- | --- | --- |
| B-1 | 이용자 1명당 하루 1건 | **구현.** DB UNIQUE 제약 + 409 DIARY_ALREADY_EXISTS + 기존 일지 번호 | 409 때 기존 일지로 이동할지(프론트) |
| B-2 | 저장 중복 방지 | B-1의 409가 새 일지 중복을 막음. PUT은 같은 내용을 다시 보내도 결과가 같음 | - |
| B-3 | 동시 수정 | **구현.** updatedAt 비교 + 409 DIARY_UPDATED_BY_OTHER (4.6) | - |
| B-4 | 선택 항목 값 형식 | GET /categories 조회·참여 활동 관리 **구현.** 저장은 라벨 | 코드 저장 전환 (5.1) |
| B-5 | 작성 중 일지 보존 | **구현.** 서버 draft 저장 + 자동 임시저장 (8.4) | 버튼 구성(U1) |
| B-6 | 화면 접근 제한 | 메뉴 숨김 + **서버 권한 검사 구현** | 권한 기준 회의 확인 (8.1) |
| B-7 | 다른 직원의 작성 중 일지 | 화면은 읽기만, **서버도 403** | 이어 쓰기 권한 회의 확인 (8.1, 8.3) |
| B-8 | 저장된 일지의 이용자 변경 | **막음.** 서버에 저장된 일지는 이용자 선택 상자를 잠근다 | - |
| B-9 | 자동 로그인 | **구현.** 기본 1시간, 자동 로그인 30일 | 기간과 공용 기기 정책 회의 확인 |
| B-10 | 소식 조회 단위 | 소식이 있는 가장 가까운 날까지 건너뛰어 불러옴(최대 365일) | 이용자 10명 미만 규모라 유지 가능 |
| B-11 | 통계 집계 위치 | **구현.** 서버 집계 (5.2) | - |
| B-12 | 파일 출력 | 일일 기록지(단건)·일지 모아 출력·월간 기록은 docx로 **구현**(브라우저에서 생성) | 엑셀 형식·생성 방식. 서버 생성으로 옮길지. PDF·HWPX는 양식 확정 후 |
| B-13 | 알림 설정 | **구현.** 서버 저장 + 안 읽은 소식 수 | 푸시 알림이 필요한지. 가상 데이터 계정 기본값(꺼짐) |
| 신규 | 완료 → 작성 중 되돌리기 | **구현.** 서버에서 거부 (4.5) | - |
| 신규 | 직원 사용 중지 | **구현** (5.3, 8.3) | 작성 중 일지 처리 |
| 신규 | 직원 생년월일 | **구현.** be PR #10(V5). 회원가입·계정 추가에서 저장, Staff 응답에 birthDate (SU-01) | - |
| 신규 | 비밀번호 재설정 | 없음 (LG-03) | 방식 결정 후 백엔드·프론트 구현 |
| 신규 | 계정 추가 임시 비밀번호 | 서버는 응답에 담음, 화면은 안 보여 줌 (SF-02) | 전달 방식 논의 중 |
| 신규 | 공유 | 버튼만 있음 (JD-11, JT-08) | 무엇을 누구에게 공유할지(개인정보) |
