================================================================================
  Micro-Gate 단계 프롬프트 (Cursor 컨텍스트 좁히기용)
================================================================================

□ 역할
- 이 폴더: 실행 게이트별 Cursor 붙여넣기 프롬프트 (1 Step = 1 파일)
- 게이트 맵: docs/IMPLEMENTATION_SLICES.txt
- 패키지 배치: docs/PACKAGE_MAP.txt
- 개요서: docs/PROJECT_PLAN.txt
- Gemini UX 브리핑/수용: docs/prompts/gemini_*.txt (Micro-Gate SSOT 아님)
- DONE 이후 UX: docs/UX_USABILITY_BACKLOG.txt

□ 매 세션 입력 (이것만)
1. @docs/IMPLEMENTATION_SLICES.txt
2. @docs/PACKAGE_MAP.txt
3. @docs/prompts/steps/CURRENT.txt
4. @docs/prompts/steps/<CURRENT가 가리키는 STEP_*.txt>  하나만
   — CURRENT=DONE 이면 STEP 파일 생략. UX 작업 시 @docs/UX_USABILITY_BACKLOG.txt
5. 필요 시에만 @docs/PROJECT_PLAN.txt (해당 절)

□ CURRENT = DONE 이후
- Micro-Gate Step을 되감지 않는다 (STEP_09 신설 없음).
- GUI 사용성: docs/prompts/ux_phases/ (CURRENT_UX + PHASE_*.txt 하나만) +
  UX_USABILITY_BACKLOG + PACKAGE_MAP. 도메인/requirements Must 변경 금지.

□ 금지
- 한 채팅에 Step 여러 개 붙이기
- Gemini/Argon 장문 전체를 다시 붙이기 (수용본·백로그만)
- CURRENT 갱신 없이 다음 Step 시작
- DONE인데 Micro-Gate Step 프롬프트로 도메인 재착수

□ Hard Gate
- 지정 mvn test가 Failures:0 Errors:0 이 아니면 다음 Step 금지
- 실패 시 STOP_RECOVERY.txt 사용

□ 파일 목록
  CURRENT.txt
  STEP_00_ssot_audit.txt
  STEP_01A_io_catalog.txt
  STEP_01B_interpreter.txt
  STEP_02A_fixture.txt
  STEP_02B_exchange_recorder.txt
  STEP_03_access.txt
  STEP_03P_package_align.txt
  STEP_04A_read.txt
  STEP_04B_gui_milestone.txt
  STEP_05_write.txt
  STEP_06_analysis_session.txt
  STEP_07A_ai_domain.txt
  STEP_07B_ai_ui.txt
  STEP_08_full_regression.txt
  STOP_RECOVERY.txt
