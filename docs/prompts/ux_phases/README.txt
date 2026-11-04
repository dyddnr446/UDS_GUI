================================================================================
  UX Phase 프롬프트 팩 — Cursor Auto 실행 가이드
================================================================================

□ 역할
- Micro-Gate DONE 이후 GUI 사용성(UDS-UX-*) 작업의 붙여넣기용 Phase 프롬프트.
- 티켓 AC 원본: docs/UX_USABILITY_BACKLOG.txt
- 패키지: docs/PACKAGE_MAP.txt
- Hard Constraints: docs/prompts/ux_phases/GUARDRAILS.txt
  (= docs/prompts/ux_gui_guardrails.txt 와 동기화)
- Micro-Gate Step 폴더(docs/prompts/steps/)와 별개. CURRENT.txt=DONE 유지.

□ 실행 순서 (의존성 — Gemini 고정, 바꾸지 말 것)
  PHASE_01_UX01  = UDS-UX-01  ComboBox Observable
  PHASE_02_UX03  = UDS-UX-03  UiTaskRunner props · PauseTransition 제거
  PHASE_03_UX02  = UDS-UX-02  Status Bar · ACCESS_DENIED CTA
  PHASE_04_UX04  = UDS-UX-04  원클릭 진단 · Evidence Chip
  PHASE_05_UX05  = UDS-UX-05  Demo Stepper · 복원 보안 배너
  PHASE_06_SHOULD = P1-3 Demo Presets + P1-4 Interpreter UX

□ 매 세션 입력 (이것만)
1. @docs/UX_USABILITY_BACKLOG.txt
2. @docs/PACKAGE_MAP.txt
3. @docs/prompts/ux_phases/GUARDRAILS.txt
4. @docs/prompts/ux_phases/CURRENT_UX.txt
5. @docs/prompts/ux_phases/<CURRENT_UX가 가리키는 PHASE_*.txt>  하나만
6. 필요 시 @docs/PROJECT_PLAN.txt 「후속 UX」절만

□ 게이트
1. Phase 프롬프트 1개만 실행 (Implement → Test → mvn test).
2. Failures:0 Errors:0 이고 git 범위(gui·허용 docs만) 확인 후 정지.
3. 통과 시에만 사용자가/합의 하에 CURRENT_UX.txt 를 다음 PHASE로 갱신.
4. 실패 시 다음 Phase 금지. Micro-Gate STOP_RECOVERY 정신 준수(기대값 완화 금지).

□ 금지
- 한 채팅에 Phase 여러 개 붙이기 / 임의 Look-Ahead
- docs/prompts/steps/CURRENT.txt 또는 requirements.xlsx 수정
- 도메인 패키지(uds.io|catalog|interpreter|access|exchange|fixture|
  analysis|read|write|ai) 비즈니스 로직·시그니처 변경
- Stage/Alert를 ViewModel에 박아 headless mvn test 깨뜨리기
  (상태는 VM/Controller 프로퍼티, 위젯은 UdsGuiApplication)

□ 파일 목록
  README.txt
  CURRENT_UX.txt
  GUARDRAILS.txt
  PHASE_01_UX01.txt
  PHASE_02_UX03.txt
  PHASE_03_UX02.txt
  PHASE_04_UX04.txt
  PHASE_05_UX05.txt
  PHASE_06_SHOULD.txt

끝.
