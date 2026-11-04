UDS Diagnostic GUI & AI Advisor
JAVA 개인 프로젝트 (자작자동차 동아리 임베디드)

실행 (원클릭)
  추천(시연): dist\UdsDiagnosticGui\UdsDiagnosticGui.exe 또는 실행.bat
  개발: 프로젝트 루트 run.bat 더블클릭  (Maven 필요, 창을 닫지 말 것)
  exe 다시 만들기: scripts\package-win.bat

환경설정 (.env)
  1) .env.example 을 복사해 .env 생성  (git에 올리지 말 것)
  2) UDS_AI_API_KEY=...  등 로컬 값 입력
  3) OS 환경변수가 있으면 .env보다 우선
  수업용 ECU 비밀번호는 계속 config/demo_password.txt (fixture)

시작 문서 (역할 분리)
  docs/PROJECT_PLAN.txt            개요서 · 제품 정의 · 상세 설계
  docs/IMPLEMENTATION_SLICES.txt   실행 계획 · Slice · Micro-Gate 맵
  docs/PACKAGE_MAP.txt             패키지·클래스 배치 SSOT (IDE 탐색)
  docs/prompts/steps/CURRENT.txt   지금 할 Step ID
  docs/prompts/steps/STEP_*.txt    해당 Step Cursor 프롬프트 (하나만 @)
  docs/UDS_GUIDE.txt               UDS 입문

폴더
  docs/     개요서 + 실행 계획 + prompts/steps + 엑셀 SSOT
  tool/     엑셀 재생성 스크립트
  samples/  시연 로그 + fixture CSV
  config/   demo_password.txt

구현 시작
  1) .cursorrules 자동 적용 확인
  2) CURRENT.txt가 STEP_00인지 확인
  3) @IMPLEMENTATION_SLICES + @CURRENT + @STEP_00_ssot_audit 만 붙여 Step 0 실행
  4) 통과 후 CURRENT를 다음 Step으로 갱신하고 반복
  5) 실패 시 prompts/steps/STOP_RECOVERY.txt

핵심 범위
  JavaFX GUI + Excel profile/DID + Access(0x10/0x27) + File Port Read/Write
  1차 마일스톤: ACCESS-VESC-SUCCESS → RD-VESC-001 (50.0 V / ABNORMAL)
  제외: 실제 Socket/CAN/UART/ISO-TP, 로컬 Knowledge RAG
