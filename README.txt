UDS Diagnostic GUI & AI Advisor
과목: 2026-2 Java

================================================================================
GitHub에 올리는 것 (이 저장소)
================================================================================
  src/                    Java 소스
  test/                   JUnit + fixtures (시나리오 xlsx)
  기준정보/                 uds_system_catalog.xlsx
  doc/guide/              가이드 카탈로그 (런타임)
  doc/tools/resolve-runtime.bat   JDK/Maven 탐지
  제출문서/                 교수님용 개발문서·토폴로지
  pom.xml  mvnw*  .mvn/   Maven
  run.bat                 GUI 실행
  ai_advisor_prompt.txt   AI 프롬프트
  README.txt              이 파일
  output/.gitkeep         결과 폴더 자리만

올리지 않음 (.gitignore)
  .env  target/  output 로그  .cursorrules  doc/steps·ux·공정 초안 등

================================================================================
실행
================================================================================
1) JDK 25+, (선택) Maven — 없으면 mvnw 사용
2) 루트에 .env 생성 (저장소에 없음)
     TEST=TRUE
     UDS_DEMO_PASSWORD=uds-demo-2026
     UDS_AI_API_KEY=   (선택)
3) run.bat
   → output\ 에 live_session.log 등 생성

테스트
  mvnw.cmd test
  목표: Failures:0 Errors:0

================================================================================
제출 문서
================================================================================
  제출문서\제출_개발문서_초안.docx
  제출문서\DEVDOC_REQUIREMENTS_TEST.xlsx
  제출문서\topology\

================================================================================
환경
================================================================================
  TEST=TRUE   Offline Simulation (장비 불필요)
  TEST=FALSE  CAN 자리 (미구현)
