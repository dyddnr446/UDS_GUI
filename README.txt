UDS Diagnostic GUI & AI Advisor
과목: 2026-2 Java

UDS(Unified Diagnostic Services)는 차량 ECU와 진단기로 대화할 때 쓰는
표준 프로토콜입니다. 이 프로그램은 그 대화를 GUI로 보여 주는 교육용 도구입니다.

실장비(CAN) 없이, 오프라인 시뮬레이션(TEST=TRUE)으로
세션 접속 → DID 읽기/쓰기 → HEX 로그 해석 → AI 진단 조언까지
한 화면에서 확인할 수 있습니다.


================================================================================
준비
================================================================================
1) JDK 25 이상
   - Maven은 없어도 됩니다. 저장소에 mvnw(Maven Wrapper)가 포함되어 있습니다.

2) .env (환경 설정)
   - 원래 .env / API 키는 Git에 올리면 안 됩니다.
   - 이 저장소는 과제 제출·채점용이라 예외적으로 .env 와
     데모용(무료) UDS_AI_API_KEY 를 함께 넣어 두었습니다.
     → 클론 후 바로 GUI·AI 조언까지 실행 가능

   포함되어 있는 주요 값:

     TEST=TRUE
     UDS_DEMO_PASSWORD=1234
     UDS_AI_API_KEY=...   (과제용 데모 키)

   - TEST=TRUE  … 장비 없이 fixture ECU로 동작 (기본·권장)
   - TEST=FALSE … CAN 실장비 자리 (현재 미구현)
   - UDS_DEMO_PASSWORD … Extended Session Security Unlock 데모 비밀번호
   - UDS_AI_API_KEY … AI 조언용 Gemini 키 (과제 제출용으로 포함)


================================================================================
실행
================================================================================
저장소 루트에서 run.bat 을 실행합니다.
잠시 후 JavaFX 창이 열립니다. 로그·산출물은 output\ 아래에 쌓입니다.


================================================================================
화면 구성 (위에서 아래로)
================================================================================

[상단 제어 바]  — 모든 탭에 공통
  Connection
    - Offline Simulation : 시뮬레이션 모드 (TEST=TRUE일 때)
  Target ECU
    - 진단할 컨트롤러(프로필)를 고릅니다. 예: 배터리/모터 관련 ECU 이름
    - 바꾸면 세션 상태가 초기화되므로, 먼저 ECU를 고른 뒤 세션을 적용하세요.
  Session Control + Apply
    - Default Session / Extended Session 등을 고른 뒤 Apply 로 적용합니다.
    - 자세한 의미는 아래 「세션·보안」 절을 보세요.

[하단 상태]
  - 현재 접속 상태(DEFAULT_LOCKED, EXTENDED_UNLOCKED 등)가 표시됩니다.

[탭]
  Interpreter | ReadData | WriteData | DTC | AI Advisor
  - Read/Write/DTC/AI 는 Unlock 후에 열립니다.
  - 0x27 seed/key는 GUI·로그·AI 모두 마스킹됩니다 (Learning UI 없음).


================================================================================
세션·보안 (UDS를 몰라도 따라 하기)
================================================================================
실제 차량 진단에서도 “아무 때나 아무 데이터를 읽고 쓰는” 것이 아니라,
진단 세션을 연 뒤에야 권한이 열립니다. 이 GUI도 같은 규칙을 따릅니다.

1) Default Session (기본)
   - 프로그램 시작 직 기본 상태입니다.
   - 상태 이름: DEFAULT_LOCKED
   - 의미: 기본 진단 세션. 읽기/쓰기·AI 탭은 대부분 잠겨 있습니다.
   - Session Control 에서 Default Session 선택 → Apply

2) Extended Session (확장) + Security Unlock (보안 해제)
   - 데이터 읽기·쓰기·AI 진단을 쓰려면 여기까지 와야 합니다.
   - Session Control 에서 Extended Session 선택 → Apply
   - 비밀번호 창이 뜹니다.
       · .env 의 UDS_DEMO_PASSWORD 를 입력하거나
       · 비밀번호칸을 비운 채 확인 → 환경변수/ .env 데모 비밀번호 사용
       · 이 제출본 기본값: 1234
   - 성공 시 상태: EXTENDED_UNLOCKED
   - 이 상태에서 ReadData / WriteData / DTC / AI Advisor 탭이 열립니다.

3) 다시 Default 로 돌아가기
   - Session Control → Default Session → Apply
   - 상태가 DEFAULT_LOCKED 로 돌아가고, Write/AI 등은 다시 잠길 수 있습니다.

한 줄 요약:
  Target ECU 선택 → Extended Session → Apply → 비밀번호(1234) →
  상태 EXTENDED_UNLOCKED 확인 → 이후 탭 사용


================================================================================
탭별 기능
================================================================================

1) Interpreter (통신 로그 해석)
   - ECU와 주고 요청/응답 HEX 줄을 사람이 읽기 쉬운 형태로 풀어 줍니다.
   - 「이번 세션 로그」: 지금 GUI에서 일어난 통신(live_session.log)을 불러옵니다.
   - 샘플/파일 로그를 열어 서비스 ID, 긍정/부정 응답, DID 등을 표로 확인합니다.
   - UDS 전문 지식이 없어도 “무엇이 오갔는지”를 보는 용도입니다.

2) ReadData (데이터 읽기, UDS 0x22)
   - Extended Session 적용 후 사용하세요.
     (힌트: 「사용 가능한 DID 없음 — 상단에서 Extended Session 후 사용」)
   - DID(Data Identifier) = ECU 안의 한 측정값/상태에 붙인 ID
   - 목록에서 DID를 고르면 이름·단위·정상 범위·변환식 요약이 오른쪽에 나옵니다.
   - Read 버튼 → Raw(HEX) / 물리값 / NORMAL·OUT_OF_RANGE·ERROR 상태를 표시합니다.
   - 예: 전압·전류·온도처럼 카탈로그에 정의된 값을 시뮬레이션 ECU에서 읽습니다.

3) WriteData (데이터 쓰기, UDS 0x2E)
   - Security Unlock 까지 끝난 상태(EXTENDED_UNLOCKED)에서 사용하세요.
   - 쓸 수 있는 DID를 고르고, 허용 범위의 값을 입력한 뒤 Write 를 누릅니다.
   - 화면의 Guard / Encode / Exchange 배지로
     “권한 검사 → 값 인코딩 → ECU 전송” 단계가 성공했는지 볼 수 있습니다.
   - 범위 밖·타입 오류면 VALIDATION_ERROR 등으로 거절됩니다 (의도된 동작).

4) DTC (고장 코드, UDS 0x19 / 0x14)
   - Unlock 후 Sub-Function·Status Mask 선택 → Read, Clear(확인 창)
   - Specification / Pipeline / 다중 DTC 테이블로 결과를 확인합니다.

5) AI Advisor (증상 기반 진단 조언)
   - EXTENDED_UNLOCKED 상태에서만 탭이 활성화됩니다.
   - 증상 입력란에 이상 현상·키워드를 적습니다. 예: 급정지, 전압 이상
   - 「원클릭 진단 실행 (Suggest → Read → AI)」 권장:
       ① 증상·카탈로그를 보고 관련 DID 후보를 AI가 고르고
       ② 그 DID들을 실제로 Read 하고
       ③ 읽은 Finding 만 모아 엔지니어용 조언을 생성합니다
   - Advanced (단계를 나눠 실행할 때):
       Suggest DIDs → Read suggested → Ask AI
   - 왼쪽: 수집된 Facts/Findings 목록
   - 오른쪽: Evidence 칩 + AI 설명 문구
   - Cancel AI 로 진행 중 요청을 취소할 수 있습니다.


================================================================================
추천 체험 순서 (채점·데모용)
================================================================================
1. run.bat 실행
2. Target ECU 에서 프로필 하나 선택 (기본으로 첫 ECU가 잡혀 있을 수 있음)
3. Session Control → Extended Session → Apply
4. 비밀번호 1234 (또는 빈칸 확인으로 데모 비밀번호 사용)
5. 상태가 EXTENDED_UNLOCKED 인지 확인
6. ReadData 탭 → DID 하나 선택 → Read
7. WriteData 탭 → (선택) 값 입력 후 Write
8. DTC 탭 → Mask 선택 → Read → Clear(확인)
9. Interpreter → 「이번 세션 로그」
10. AI Advisor → 증상 입력 → 원클릭 진단 실행


================================================================================
디렉터리 구조
================================================================================
  run.bat                 GUI 실행 (더블클릭)
  TUTORIAL.txt            카탈로그·UDS 읽는 법 (사람용)
  .env                    과제 제출용 환경 설정 (채점·실행용으로 포함)
  pom.xml / mvnw* / .mvn/ Maven 빌드
  ai_advisor_prompt.txt   AI 시스템·사용자 프롬프트 템플릿

  src/                    애플리케이션 Java 소스
  catalog/                진단 카탈로그 (마스터 엑셀 2개)
    uds_did_dtc_catalog.xlsx   (DID·DTC·프로필·증상)
    uds_sid_catalog.xlsx       (Services·SubFunctions·NRC)
  fixtures/               오프라인 시뮬레이션용 시나리오·샘플 로그
    uds_fixture_scenarios.xlsx
    demo_session.log
  tools/                  JDK/Maven 자동 탐지 등 실행 보조 스크립트
  output/                 실행 결과 (live_session.log 등)


================================================================================
문제 해결
================================================================================
- 창이 바로 닫히면 output\launch.log , output\crash.log 를 확인하세요.
- Read/Write/AI 탭이 회색이면 Extended Session + 비밀번호 Unlock 부터 하세요.
- 비밀번호 오류 시 .env 의 UDS_DEMO_PASSWORD(기본 1234)를 확인하세요.
- 카탈로그/픽스처를 못 찾으면 catalog\ , fixtures\ 폴더가 루트에 있는지 보세요.
- AI가 동작하지 않으면 .env 의 UDS_AI_API_KEY 와 네트워크를 확인하세요.
