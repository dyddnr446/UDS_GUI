UDS Diagnostic GUI & AI Advisor
과목: 2026-2 Java

UDS(Unified Diagnostic Services) 진단 GUI입니다.
오프라인 시뮬레이션(TEST=TRUE)으로 Access / Read / Write / Interpreter /
Analysis / AI 조언을 한 화면에서 돌려 볼 수 있습니다.


================================================================================
준비
================================================================================
1) JDK 25 이상
   - Maven은 없어도 됩니다. 저장소에 mvnw(Maven Wrapper)가 포함되어 있습니다.

2) 프로젝트 루트에 .env 파일 생성 (Git에 올리지 않음)
   최소 예시:

     TEST=TRUE
     UDS_DEMO_PASSWORD=uds-demo-2026
     UDS_AI_API_KEY=

   - TEST=TRUE  … 장비 없이 fixture ECU로 동작 (기본·권장)
   - TEST=FALSE … CAN 실장비 자리 (현재 미구현)
   - UDS_DEMO_PASSWORD … Security Access(0x27) 데모 비밀번호
   - UDS_AI_API_KEY … AI 조언용 (비우면 AI 기능만 비활성)
   - 경로를 비우면 아래 기본 경로를 사용합니다.
       catalog/uds_system_catalog.xlsx
       catalog/uds_guide_catalog.xlsx
       fixtures/uds_fixture_scenarios.xlsx


================================================================================
실행
================================================================================
1) 저장소 루트에서 run.bat 실행
2) GUI가 뜨면 Profile / 증상을 고른 뒤 Access → Read/Write 순으로 사용
3) 실행 중 로그·세션 산출물은 output\ 아래에 생성됩니다.
   (live_session.log 등)

종료 후 같은 폴더의 output\ 을 보면 기록·복원용 파일을 확인할 수 있습니다.


================================================================================
디렉터리 구조
================================================================================
  run.bat                 GUI 실행 (더블클릭)
  pom.xml / mvnw* / .mvn/ Maven 빌드
  ai_advisor_prompt.txt   AI 시스템·사용자 프롬프트 템플릿

  src/                    애플리케이션 Java 소스
  catalog/                진단 카탈로그 (마스터·가이드 엑셀)
    uds_system_catalog.xlsx
    uds_guide_catalog.xlsx
  fixtures/               오프라인 시뮬레이션용 시나리오·샘플 로그
    uds_fixture_scenarios.xlsx
    demo_session.log
  tools/                  JDK/Maven 자동 탐지 등 실행 보조 스크립트
  output/                 실행 결과 (저장소에는 자리만 둠)

로컬에만 두고 GitHub에는 올리지 않는 것:
  .env, target/, doc/, test/, 제출문서/ 등


================================================================================
사용 흐름 (간단)
================================================================================
1. Access 탭에서 세션·보안 접근을 연다 (데모 비밀번호 사용)
2. Read / Write 탭에서 DID를 읽거나 쓴다
3. Interpreter 탭에서 HEX 로그를 해석한다
4. Analysis 탭에서 세션을 저장·복원한다
5. AI 조언: 증상 기준으로 DID를 고르고 Read한 뒤 엔지니어용 조언을 받는다
   (API 키가 있을 때만)


================================================================================
문제 해결
================================================================================
- 창이 바로 닫히면 output\launch.log , output\crash.log 를 확인하세요.
- 카탈로그/픽스처를 못 찾으면 .env 경로와 catalog\, fixtures\ 폴더를 확인하세요.
- AI가 동작하지 않으면 UDS_AI_API_KEY 와 네트워크를 확인하세요.
