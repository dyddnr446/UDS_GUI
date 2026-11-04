문서 모음 (개요서 / 실행 계획 / Step 프롬프트 분리)

관리 원칙
  개요·제출·설계 …… PROJECT_PLAN.txt
  구현 Slice·Micro-Gate 맵 …… IMPLEMENTATION_SLICES.txt
  Cursor Step 프롬프트 …… prompts/steps/  (1 Step = 1 파일, CURRENT 포인터)
  기능·비기능·유스케이스 …… requirements.xlsx
  UDS 표 …… uds_catalog.xlsx
  UDS 개념 입문 …… UDS_GUIDE.txt
  그 외 새 txt는 가급적 추가하지 않음

파일
  PROJECT_PLAN.txt           교수 제출용 개요서
  IMPLEMENTATION_SLICES.txt  Slice 1~8 + 13 Micro-Gate 맵
  prompts/steps/             CURRENT.txt + STEP_00~08 + STOP_RECOVERY
  prompts/ux_phases/         DONE 이후 UX Phase 프롬프트
  UDS_GUIDE.txt              UDS 입문
  requirements.xlsx          FR/NFR/UC/DoD
  uds_catalog.xlsx           서비스·DID·프로필·SymptomDidMap
  README.txt                 이 안내

Cursor 컨텍스트 규칙 (중요)
  한 세션에 붙일 것:
    @IMPLEMENTATION_SLICES.txt
    @prompts/steps/CURRENT.txt
    @prompts/steps/<CURRENT Step 파일>  하나만
  붙이지 말 것: Step 여러 개, Gemini 장문 전체

읽는 순서
  1) PROJECT_PLAN …… 무엇을 만드는지
  2) IMPLEMENTATION_SLICES …… 어떤 게이트 순서인지
  3) prompts/steps/CURRENT + STEP_* …… 지금 코딩할 지시
  4) requirements / uds_catalog …… 세부 능력·표

재생성
  py -3 tool\build_requirements_xlsx.py
  py -3 tool\build_uds_catalog.py

샘플
  ../samples/demo_session.log
  ../samples/*.csv
  ../config/demo_password.txt
