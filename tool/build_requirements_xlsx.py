#!/usr/bin/env python3
"""Build slim requirements: Overview → Priority → Apps → FR/NFR/UC → Completion → Meta."""

from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.table import Table, TableStyleInfo

OUT = Path(__file__).resolve().parents[1] / "docs" / "requirements.xlsx"
HEADER_FILL = PatternFill("solid", fgColor="1F4E79")
HEADER_FONT = Font(color="FFFFFF", bold=True, size=11)
CELL_FONT = Font(size=10)
MUST_FILL = PatternFill("solid", fgColor="E8F5E9")
SHOULD_FILL = PatternFill("solid", fgColor="FFF8E1")
COULD_FILL = PatternFill("solid", fgColor="E3F2FD")
GRID = Border(
    left=Side(style="thin", color="D9D9D9"),
    right=Side(style="thin", color="D9D9D9"),
    top=Side(style="thin", color="D9D9D9"),
    bottom=Side(style="thin", color="D9D9D9"),
)

COL_WIDTHS = {
    "Overview": {"항목": 14, "내용": 64},
    "Priority": {"코드": 8, "영문": 10, "한글": 10, "의미": 40, "행색": 12},
    "Apps": {
        "app": 12,
        "한줄정의": 34,
        "하는일": 36,
        "안하는일": 28,
        "관련FR": 16,
    },
    "Functional": {
        "id": 8,
        "제목": 14,
        "app": 11,
        "우선순위": 9,
        "요구": 40,
        "입출력": 28,
        "완료": 26,
        "확인": 32,
    },
    "NonFunctional": {
        "id": 8,
        "제목": 12,
        "app": 11,
        "우선순위": 9,
        "요구": 40,
        "완료": 26,
        "확인": 32,
    },
    "UseCases": {
        "id": 8,
        "이름": 18,
        "우선순위": 9,
        "목표": 34,
        "관련FR": 16,
        "확인": 32,
    },
    "Completion": {
        "id": 8,
        "app": 11,
        "항목": 18,
        "완료": 36,
        "확인방법": 28,
    },
    "Meta": {"key": 18, "value": 56},
}

CENTER_HEADERS = {"id", "app", "우선순위", "코드", "영문", "한글", "관련FR", "항목"}


def priority_fill(val: str):
    v = (val or "").strip().upper()
    if v.startswith("M"):
        return MUST_FILL
    if v.startswith("S"):
        return SHOULD_FILL
    if v.startswith("C"):
        return COULD_FILL
    return None


def write_sheet(wb, title, headers, rows, table_name, priority_col=None):
    ws = wb.create_sheet(title)
    ws.append(headers)
    for row in rows:
        ws.append(list(row))

    widths = COL_WIDTHS.get(title, {})
    for i, h in enumerate(headers, start=1):
        cell = ws.cell(1, i)
        cell.fill = HEADER_FILL
        cell.font = HEADER_FONT
        cell.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center")
        cell.border = GRID
        ws.column_dimensions[get_column_letter(i)].width = widths.get(h, 14)

    ws.row_dimensions[1].height = 26
    ws.freeze_panes = "A2"

    for r in range(2, ws.max_row + 1):
        fill = None
        if priority_col is not None:
            fill = priority_fill(str(ws.cell(r, priority_col).value or ""))

        max_lines = 1
        for c, h in enumerate(headers, start=1):
            cell = ws.cell(r, c)
            cell.font = CELL_FONT
            cell.border = GRID
            if fill:
                cell.fill = fill
            center = h in CENTER_HEADERS
            cell.alignment = Alignment(
                wrap_text=True,
                vertical="center",
                horizontal="center" if center else "left",
            )
            text = str(cell.value or "")
            w = widths.get(h, 14)
            usable = max(8, int(w) - 1)
            lines = 0
            for part in text.split("\n"):
                lines += max(1, (len(part) + usable - 1) // usable)
            max_lines = max(max_lines, lines)
        ws.row_dimensions[r].height = max(26, min(64, 12 + 12 * max_lines))

    ws.auto_filter.ref = ws.dimensions
    table = Table(displayName=table_name, ref=ws.dimensions)
    table.tableStyleInfo = TableStyleInfo(
        name="TableStyleMedium2",
        showRowStripes=priority_col is None,
        showColumnStripes=False,
        showFirstColumn=False,
        showLastColumn=False,
    )
    ws.add_table(table)

    for i in range(1, len(headers) + 1):
        cell = ws.cell(1, i)
        cell.fill = HEADER_FILL
        cell.font = HEADER_FONT
        cell.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center")
        cell.border = GRID
    return ws


# 항목, 내용
OVERVIEW = [
    [
        "한줄",
        "JavaFX UDS GUI에서 로그와 프로젝트 DID의 Read/WriteData 프레임을 생성·저장·해석하고, 외부 AI가 관측 결과를 설명한다.",
    ],
    ["누가", "JAVA 수업 시연자 + 자작자동차 동아리 임베디드 팀원"],
    [
        "흐름",
        "JavaFX GUI → Excel 프로필 선택 → Extended Session → SecurityAccess → Guarded Read/WriteData → 결과 → AI Advisor → Analysis Session 저장",
    ],
    ["용어 SID", "서비스 번호. 예: 0x22 = 데이터 읽기"],
    ["용어 NRC", "거절 이유 코드. 부정응답(7F) 뒤에 옴"],
    ["용어 0x22", "DID로 값을 읽는 요청 (ReadData)"],
    ["용어 0x62", "0x22에 대한 성공 응답 (값 포함)"],
    ["용어 DID", "읽을 데이터 항목 ID"],
    [
        "입력 계약",
        "UTF-8 텍스트. 헤더 ts,direction,hex. 본문 '시각,방향,HEX'. # 주석·빈 줄 무시. CAN/TP 재조립 없음.",
    ],
    [
        "시연 SID",
        "깊게 해석: 0x10,0x11,0x14,0x19,0x22,0x27,0x3E,0x7F 및 대응 긍정응답.",
    ],
    [
        "AI 원칙",
        "외부 AI API는 민감정보가 제거된 현상·DecodeResult·DID 값·Finding·WriteResult·AccessEvidence만 해석한다. SID/NRC/DID 사실과 결정론적 결과를 바꾸지 못한다.",
    ],
    [
        "더미 데이터",
        "read/write 시나리오 CSV의 응답 프레임. 같은 controller_profile+scenario_id는 같은 결과.",
    ],
    [
        "범위 밖",
        "실제 Socket/CAN/UART/ISO-TP, ECU 상태 변경, 액추에이터 제어, OEM 보안키 알고리즘, 팀 문서 RAG",
    ],
    ["다음에", "Apps에서 역할 → Functional의 「요구」열만 세로로 훑기"],
]

PRIORITY_ROWS = [
    ["M", "Must", "필수", "Must. 없으면 현장·팀에서 못 씀.", "연한 초록"],
    ["S", "Should", "권장", "Should. 있으면 편함. 없어도 핵심은 됨.", "연한 노랑"],
    ["C", "Could", "후속", "Could. 나중에 넣어도 됨.", "연한 파랑"],
]

# app, 한줄정의, 하는일, 안하는일, 관련FR
APPS = [
    [
        "Interpreter",
        "HEX 로그를 사람이 읽는 해석으로 바꾼다.",
        "로드·HEX검증·SID/NRC/DID 카탈로그·필드 분해",
        "AI 설명, 더미 시나리오 선택, GUI 상태 변경",
        "FR-01~07",
    ],
    [
        "Diagnostic Access",
        "Extended Session과 교육용 SecurityAccess 권한을 관리한다.",
        "Excel 프로필 선택, 0x10 세션 전환, 0x27 seed/key, credential fixture, access guard와 상태 초기화",
        "실제 ECU 보안 알고리즘, 비밀번호 저장·로그·AI 전송, Read/Write 값 해석",
        "FR-23~27",
    ],
    [
        "AI Advisor",
        "관측된 UDS 사실·값을 외부 AI로 설명한다.",
        "구조화 컨텍스트(WriteResult·AccessEvidence 포함)·민감정보 제거·현상-DID 제안·AI 호출·최종 목록",
        "SID/NRC/DID 사실 변경, 실제 통신, 문서 RAG",
        "FR-09,12,13,17,18",
    ],
    [
        "ReadData",
        "0x22 요청·응답 프레임을 저장하고 값을 본다.",
        "요청 생성, File Port 교환, 0x62/7F/오류 파싱, Finding, (권장) 연속 시나리오",
        "실제 Socket/CAN/UART, 쓰기",
        "FR-14~16,19",
    ],
    [
        "WriteData",
        "교육용 DID의 0x2E 요청·응답을 저장하고 결과를 본다.",
        "값 인코딩, 요청 생성, File Port 교환, 0x6E/7F/오류 파싱, WriteResult",
        "실제 ECU 값 변경, Socket/CAN/UART",
        "FR-21",
    ],
    [
        "Exchange",
        "UDS 요청·응답 교환과 안전한 기록 경계를 제공한다.",
        "Port 계약, Fixture ECU, Recording Decorator, 0x27 payload 제거, request correlation",
        "GUI 상태, UDS 값 판정, 실제 Socket/CAN 구현",
        "FR-14,21,28",
    ],
    [
        "GUI App",
        "JavaFX 탭과 세션을 관리한다.",
        "Access·Interpreter·ReadData·WriteData·AI Advisor·Analysis Session/Settings 탭, Excel 선택, 상태·기록",
        "UDS 사실 판정, 실제 하위 통신",
        "FR-08,10,11,20,22",
    ],
]

# id, 제목, app, 우선순위, 요구, 입출력, 완료, 확인
FUNCTIONAL = [
    [
        "FR-01", "로그 로드", "Interpreter", "M",
        "UTF-8 로그 파일을 열어 프레임 목록을 만든다. 헤더 ts,direction,hex. 본문 '시각,방향,HEX'. # 주석·빈 줄 무시.",
        "파일 경로 → Frame(시각·방향·HEX)[] 또는 줄 오류",
        "유효 줄만 로드. 깨진 줄은 줄번호+사유로 건너뛴다.",
        "정상: samples/demo_session.log 전체 로드 / 실패: 없는 경로→오류·앱 유지",
    ],
    [
        "FR-02", "HEX 검증", "Interpreter", "M",
        "HEX를 바이트로 만들고 잘못된 줄만 오류 낸다. 공백·대소문자 허용.",
        "raw HEX → byte[] 또는 LineError",
        "유효만 바이트화. 홀수 길이·비HEX는 해당 줄만 오류.",
        "정상: 22 F1 90·22f190→동일 / 실패: 22 GG 00→LineError(줄번호)",
    ],
    [
        "FR-03", "카탈로그", "Interpreter", "M",
        "카탈로그로 SID·NRC 이름과 DID 스키마(길이·타입·스케일·단위·정상범위)·증상-DID 매핑을 조회한다.",
        "uds_catalog.xlsx → 조회 맵",
        "영·한 이름·DID 메타를 코드로 찾는다.",
        "정상: 0x22·OemExtensions DID 조회 / 실패: 파일 없음→로드 실패",
    ],
    [
        "FR-04", "유형 분류", "Interpreter", "M",
        "프레임을 요청·긍정·부정·Unknown으로 나눈다.",
        "바이트 → 유형 라벨",
        "대표 3유형이 맞다.",
        "정상: 10/50/7F / 실패: 빈 바이트→Unknown",
    ],
    [
        "FR-05", "부정응답", "Interpreter", "M",
        "거절 응답(7F)의 요청 SID·NRC 이름을 붙인다.",
        "7F|SID|NRC → 이름+raw",
        "NRC 이름=카탈로그.",
        "정상: 7F 22 31→requestOutOfRange / 실패: 짧음→포맷 오류",
    ],
    [
        "FR-06", "서브펑션", "Interpreter", "M",
        "카탈로그에서 서브펑션·suppress 적용 SID에만 suppress 비트와 서브펑션 이름을 표시한다. 0x22 등 DID SID에는 마스킹하지 않는다.",
        "서비스 bytes+카탈로그 → 이름+suppress 또는 DID 필드",
        "조회 키=value&0x7F(적용 SID만). DID SID는 상위 바이트를 suppress로 오인하지 않음.",
        "정상: 10 83→sub=0x03·suppress=true / 실패: 22 80 01→DID=0x8001(마스킹 없음)",
    ],
    [
        "FR-07", "SID 필드", "Interpreter", "M",
        "시연 SID(0x10,0x11,0x14,0x19,0x22,0x27,0x3E,0x7F 및 대응 긍정응답)의 DID·DTC·세션 등 필드를 분해한다.",
        "SID bytes → 필드 맵",
        "시연 SID 대표 HEX 필드가 맞다. 그 외 SID는 Unknown/raw.",
        "정상: 22 F1 90→DID / 19 02 FF→DTC마스크 / 실패: 짧음→필드 오류",
    ],
    [
        "FR-08", "해석 화면", "GUI App", "M",
        "JavaFX Interpreter 탭에서 raw 프레임·카탈로그 해석·줄 오류를 AI 설명과 분리해 보여 준다.",
        "DecodeResult[]+LineError[] → Interpreter 탭",
        "사실 해석과 AI 해석 구역이 시각적으로 분리된다.",
        "정상: SID/NRC 이름 표시 / 실패: Unknown·깨진 줄→raw+사유",
    ],
    [
        "FR-09", "AI 컨텍스트", "AI Advisor", "M",
        "현상·DecodeResult·ParsedDidValue·Finding·WriteResult·AccessEvidence를 사실/관측/오류 필드로 구조화하고 0x27 payload를 제거한다.",
        "현상+해석+값+Finding+WriteResult+AccessEvidence → Sanitized AdvisorContext",
        "raw 전체 로그·API 키·password·seed·key 없이 근거 ID가 포함된 컨텍스트를 만든다.",
        "정상: 7F 22 31·저전압 Finding 포함 / 실패: 입력 없음→빈 컨텍스트 오류",
    ],
    [
        "FR-10", "JavaFX GUI", "GUI App", "M",
        "Access·Interpreter·ReadData·WriteData·AI Advisor·Analysis Session/Settings 탭을 제공한다.",
        "사용자 조작 → 탭 상태·화면 전환",
        "수업 시연의 모든 Must UC가 GUI에서 시작·완료된다.",
        "정상: 탭 이동·상태 유지 / 실패: 처리 오류→팝업·앱 유지",
    ],
    [
        "FR-11", "레퍼런스 정합", "GUI App", "S",
        "팀 레퍼런스 로그가 같은 스토리로 끝나는지 확인한다.",
        "레퍼런스 로그 → 정합 결과",
        "동일 스토리 완주.",
        "정상: samples 통과 / 실패: 누락→불일치",
    ],
    [
        "FR-12", "AI 해석", "AI Advisor", "M",
        "외부 AI가 현상·DecodeResult·ParsedDidValue·Finding·WriteResult·AccessEvidence를 바탕으로 설명과 확인 목록을 만든다.",
        "AdvisorContext → AiAdviceResult 또는 AiError",
        "AI 결과는 별도 구역에 표시하고 입력 근거 ID를 연결한다.",
        "정상: 저전압 Finding→전원 확인 설명 / 실패: API 오류→결정론적 결과 유지+경고",
    ],
    [
        "FR-13", "현상·DID 제안", "AI Advisor", "M",
        "현상 문장과 SymptomDidMap으로 확인 후보 DID를 제안하고, 실행 가능 DID만 runtime_enabled=Y로 필터한다.",
        "현상(+DecodeResult)+SymptomDidMap → 매핑 후보 DID[] + runtime_enabled 실행 DID[] + AdvisorContext",
        "매핑 후보는 전체 표시하되 GUI 실행·fixture는 runtime_enabled=Y만. 0xF186은 정적 로그 참고만.",
        "정상: 「급정지」후보 표시 후 ND 실행=0x1001,0x1002,0x1007 / Flipsky 실행=0x1001 / 실패: 빈 입력→입력 안내",
    ],
    [
        "FR-14", "ReadData 교환", "ReadData", "M",
        "DiagnosticAccessGuard 통과 후 controller_profile·DID로 0x22 UdsRequest를 만들고 DiagnosticExchangePort에서 응답을 받는다. scenario_id는 File Port 전용 FixtureRunConfig에만 둔다.",
        "profile+DID+UdsRequest+DiagnosticExchangePort → UdsExchange",
        "requests/responses.jsonl에 같은 requestId로 저장하고 0x62/7F/오류를 구분한다.",
        "정상: 0x22→0x62 저장 / 실패: 없는 scenario·저장 오류→ExchangeError, 직전 성공값 유지",
    ],
    [
        "FR-15", "0x62 파싱", "ReadData", "M",
        "더미 데이터셋 또는 정적 로그의 0x62에서 DID 값을 카탈로그 스키마로 변환하고 0x7F·포맷 오류를 구분한다. DidValueParser는 runtime_enabled와 무관하게 파싱한다.",
        "0x62 바이트+OemExtensions(길이·타입·스케일·단위) → 표시값 또는 ParseError",
        "에코 DID·길이가 맞을 때만 변환. BigDecimal 표시 자릿수=scale 소수 자릿수. 0x7F는 NRC, 불일치·짧음은 ParseError.",
        "정상: 62 10 01 01 F4→50.0 V / 실패: 7F 22 31·DID 불일치·길이 부족",
    ],
    [
        "FR-16", "Finding", "ReadData", "M",
        "파싱값과 카탈로그 정상범위(normal_min/max)로 Finding을 만들고 CSV expected_status와 1:1 매핑한다.",
        "파싱값+OemExtensions 범위 → Finding status",
        "Read status enum: NORMAL|ABNORMAL|REJECTED|PARSE_ERROR_*. normal_min/max 없으면 RANGE_NOT_APPLICABLE.",
        "정상: 전압 하한 미만→ABNORMAL / 실패: 범위 없음→RANGE_NOT_APPLICABLE",
    ],
    [
        "FR-17", "최종 목록", "AI Advisor", "M",
        "결정론적 Finding·WriteResult·AccessEvidence와 AI 설명을 구분해 근거 있는 체크리스트로 합친다.",
        "DecodeResult+Finding+WriteResult+AccessEvidence+AiAdviceResult → 체크리스트",
        "항목마다 로그/DID/Finding/WriteResult/AccessEvidence 근거 ID ≥1. AI 문구는 AI 라벨 표시.",
        "정상: 근거 있는 목록 / 실패: AI 없음→Finding 중심 목록",
    ],
    [
        "FR-18", "AI Provider", "AI Advisor", "M",
        "외부 AI Provider를 호출하고 API 키·모델·타임아웃을 설정한다.",
        "AdvisorContext(WriteResult 포함)+설정 → AiAdviceResult 또는 AiError",
        "API 키를 로그·리포트·프롬프트에 남기지 않고 오류를 GUI에 표시한다.",
        "정상: 구조화 응답 파싱 / 실패: 키 없음·망 오류·타임아웃→경고·로컬 결과 유지",
    ],
    [
        "FR-19", "다중 시나리오", "ReadData", "S",
        "여러 더미 ReadData 시나리오를 순서대로 적용해 Finding을 누적 비교한다.",
        "scenario_id[] → FramePair[]+Finding[]",
        "선택 순서와 각 예상 결과를 세션에 보존한다.",
        "정상: 전압→모터전류→온도 3건 / 실패: 잘못된 행만 DatasetError",
    ],
    [
        "FR-20", "Analysis Session 기록", "GUI App", "M",
        "schemaVersion·analysisSessionId·recordId/requestId와 uds_catalog.xlsx SHA-256 catalogVersion을 포함해 요청·응답·값·Finding·WriteResult·AccessEvidence·AI 목록을 파일로 남기고 analysis-session.json에서 복원한다.",
        "분석 결과 → requests/responses.jsonl+analysis.json+analysis-session.json",
        "JSONL은 append 후 flush하고 analysis-session.json은 임시 파일에서 원자 교체한다. 부분 저장 실패는 PersistenceError로 남기고 메모리 결과를 유지한다.",
        "정상: 저장·복원 후 근거 ID 일치 / 실패: 파일 누락·버전 불일치→부분 읽기 전용 복원+경고",
    ],
    [
        "FR-21", "WriteData 교환", "WriteData", "M",
        "DiagnosticAccessGuard 통과 후 writable 교육용 DID와 값을 검증·인코딩해 0x2E UdsRequest를 만들고 DiagnosticExchangePort의 0x6E/7F/오류를 WriteResult로 분석한다. scenario_id는 File Port 구성에만 둔다.",
        "profile+DID+값+DiagnosticExchangePort → UdsExchange+WriteResult",
        "Write status는 CSV expected_status와 1:1(ACCEPTED|REJECTED|VALIDATION_ERROR_*|PARSE_ERROR_*). WD-ERR-004/005는 Validator만 차단·Port 미호출. 실제 ECU 미변경.",
        "정상: 2E 20 02 ...→6E 20 02·ACCEPTED / 실패: VALIDATION_ERROR_RANGE(Port 미호출)·7F 2E 31",
    ],
    [
        "FR-22", "Excel 기반 GUI 선택", "GUI App", "M",
        "앱 시작 또는 reload 시 ControllerProfiles·OemExtensions·DidProfileSupport를 불변 카탈로그로 읽고 활성 profile에 허용된 runtime DID만 ComboBox에 표시한다.",
        "uds_catalog.xlsx → ControllerProfile[]+Read DID[]+Write DID[]",
        "POI 객체는 CatalogLoader 밖으로 나오지 않고 profile 변경/reload 시 DID 선택과 접근 권한을 초기화한다.",
        "정상: ND/Flipsky별 정렬된 DID 목록 / 실패: header·FK·enum 오류→기존 snapshot 유지+경고",
    ],
    [
        "FR-23", "Extended Session", "Diagnostic Access", "M",
        "선택 profile에 10 03 요청을 교환하고 byte0==0x50 && (byte1&0x7F)==0x03 인 응답에서만 DEFAULT_LOCKED를 EXTENDED_LOCKED로 전환한다.",
        "profile+10 03+DiagnosticExchangePort → DiagnosticAccessState+AccessEvidence",
        "0x7F·SESSION_ECHO_MISMATCH(예: 50 01)·교환 오류 시 DEFAULT_LOCKED를 유지한다.",
        "정상: 10 03→50 03·EXTENDED_LOCKED / 실패: 50 01→SESSION_ECHO_MISMATCH·DEFAULT_LOCKED",
    ],
    [
        "FR-24", "SecurityAccess", "Diagnostic Access", "M",
        "EXTENDED_LOCKED에서 27 01/67 01 seed를 받고 PasswordField 입력으로 교육용 key를 계산해 27 02/67 02 성공 시에만 EXTENDED_UNLOCKED로 전환한다.",
        "profile+password+seed/key exchange → DiagnosticAccessState+AccessEvidence",
        "fixture <SEED>는 고정 AA BB CC DD, <KEY>는 27 02+4바이트 길이 매칭 후 HMAC 상수시간 비교. ACCESS-INVALID-KEY 등 NEGATIVE는 CSV kind 우선. 0x36/0x37 행은 고정 부정 응답만 반환(카운터/Clock 후속).",
        "정상: seed AA BB CC DD + key D3 AC 3F 47→67 02·UNLOCKED / 실패: 7F 27 35→LOCKED",
    ],
    [
        "FR-25", "fixture 비밀번호 파일", "Diagnostic Access", "M",
        "File ECU simulator는 config/demo_password.txt의 UTF-8 한 줄 평문을 PasswordFileCredentialStore로 읽어 기대 key를 계산한다.",
        "credential file+seed+profile → expected key 또는 CredentialConfigError",
        "끝단 CRLF/LF만 제거하고 중간·선행 공백은 보존. BOM·빈 값·다중 행·128 UTF-8 bytes 초과·잘못된 UTF-8을 거절한다.",
        "정상: trailing CRLF/LF 제거·공백 보존 / 실패: 파일 누락→CREDENTIAL_CONFIG_ERROR",
    ],
    [
        "FR-26", "Read/Write AccessGuard", "Diagnostic Access", "M",
        "ReadData·WriteData는 요청 생성 전에 활성 profile·DID의 required session/security와 ActiveDiagnosticContext를 검증한다.",
        "access snapshot+DidDefinition → ALLOW 또는 ACCESS_DENIED",
        "EXTENDED_UNLOCKED와 metadata가 모두 맞을 때만 요청을 만들며 거절 시 프레임·JSONL을 생성하지 않는다.",
        "정상: unlock 후 0x22/0x2E / 실패: locked→ACCESS_DENIED·산출물 0건",
    ],
    [
        "FR-27", "접근 상태 초기화", "Diagnostic Access", "M",
        "profile 변경·catalog reload·Port 교체·새 실행·Analysis Session 복원 시 접근 상태와 DID 선택을 원자적으로 DEFAULT_LOCKED로 초기화한다.",
        "context reset event → 새 generation+DEFAULT_LOCKED",
        "이전 generation 비동기 결과는 화면·상태에 반영하지 않고 저장된 과거 권한을 복구하지 않는다.",
        "정상: profile 변경→재인증 필요 / 실패: 이전 67 02 지연 도착→폐기",
    ],
    [
        "FR-28", "안전한 교환 기록", "Exchange", "M",
        "RecordingDiagnosticExchangePort가 선택된 Port를 감싸 모든 교환 기록을 통일하고 0x27 raw는 즉시 폐기한다. resultingState 포함 AccessEvidence는 Context 전이 후 SafeExchangeRecorder.recordAccessEvidence(...)로만 기록한다.",
        "UdsExchange → 일반 JSONL 또는 마스킹 메타; AccessEvidence는 SessionControl/SecurityAccess가 기록",
        "password·seed·key byte sequence가 JSONL·JSON·AI context에 없다. Interpreter는 정적 로그 0x27/0x67 seed/key를 <REDACTED>로 마스킹한다. evidenceId 접두: LOG:/READ:/WRITE:/FINDING:/ACCESS:.",
        "정상: 10/22/2E raw·0x27 redacted AccessEvidence / 실패: recorder 오류→메모리 결과 유지+PersistenceError",
    ],
]

# id, 제목, app, 우선순위, 요구, 완료, 확인
NONFUNCTIONAL = [
    [
        "NFR-01", "재현성", "AI Advisor", "M",
        "같은 프레임은 DecodeResult·표시값·Finding이 같다. AI 문구 변동은 허용하되 근거 ID는 유지한다.",
        "결정론적 결과 2회 일치",
        "정상: 값·Finding 동일 / 실패: AI 문장 차이는 AI 구역에만",
    ],
    [
        "NFR-02", "오류 격리", "Interpreter", "M",
        "깨진 줄만 오류 처리하고 나머지는 계속한다.",
        "나머지 프레임 성공",
        "정상: 중간 1줄 깨져도 전후 OK / 실패: 전부 깨져도 앱 유지",
    ],
    [
        "NFR-03", "사용성", "GUI App", "M",
        "EXTENDED_UNLOCKED 이후 JavaFX GUI에서 DID 선택 후 3회 이하 조작으로 Read/Write 결과를 본다. 접속·인증은 별도 완료 기준으로 측정한다.",
        "unlock 이후 핵심 흐름 조작 ≤3회",
        "정상: DID 선택→실행→결과 / 실패: 필수 입력 누락→화면 안내",
    ],
    [
        "NFR-04", "응답 시간", "Interpreter", "S",
        "200프레임 픽스처에서 해석+조언이 평균 3.0초 이하다.",
        "200프레임 평균 ≤3.0초",
        "정상: ≤3.0초 / 실패: 초과→표시 또는 문서화",
    ],
    [
        "NFR-05", "구조 분리", "GUI App", "M",
        "Feature ViewModel·Interpreter·Diagnostic Access·Read/Write·Exchange Policy·Recording Port·Transport Port·AI Provider·Analysis Session을 분리한다.",
        "도메인 서비스 유지, transport lifecycle·controller adapter만 추가/교체",
        "정상: File/Socket delegate가 같은 Port·Recorder 사용 / 실패: MainViewModel·FilePort에 복수 책임 집중 없음",
    ],
    [
        "NFR-06", "실행 환경", "GUI App", "M",
        "JDK 17+Maven+JavaFX에서 GUI가 돈다. AI만 인터넷이 필요하며 Interpreter·File Port Read/Write는 독립 동작한다.",
        "AI 없이 사실 해석·ReadResult·WriteResult 표시",
        "정상: 온라인 AI 포함 / 실패: 오프라인→AI 경고, 나머지 유지",
    ],
    [
        "NFR-07", "확장", "GUI App", "S",
        "AiProvider와 DiagnosticExchangePort로 외부 연동을 격리한다. 미래 Socket/CAN은 도메인을 유지하고 transport lifecycle·controller adapter를 추가한다.",
        "도메인 코어 유지",
        "정상: delegate Port 교체·lifecycle composition 추가 / 실패: 실제 통신 구현 없음",
    ],
    [
        "NFR-08", "안전", "GUI App", "M",
        "0x10/0x27/0x22/0x2E 프레임은 File ECU simulator에서만 교환하며 실제 ECU·액추에이터 상태를 변경하지 않는다.",
        "File Port만 제공·실제 Socket/CAN/UART 구현 부재",
        "정상: requests/responses 저장 / 실패: 네트워크·CAN send API 없음",
    ],
    [
        "NFR-09", "추적", "GUI App", "M",
        "FR/NFR/UC ID로 이 엑셀이 SSOT다.",
        "PLAN·코드와 ID 대응",
        "정상: 필수 FR 대응 / 실패: 삭제 ID 잔존 없음",
    ],
    [
        "NFR-10", "입력 전제", "Interpreter", "M",
        "입력은 완성 HEX payload 로그(FR-01 형식)다. CAN/TP 재조립 없음.",
        "로그만으로 해석",
        "정상: HEX→해석 / 실패: CAN 입력 기능 없음",
    ],
    [
        "NFR-11", "시연 대비", "GUI App", "M",
        "저장 로그와 Access/Read/Write 시나리오·상태형 File ECU만으로 핵심 UDS 흐름을 재현한다.",
        "장비 없이 GUI 시연 완주",
        "정상: UC-01·02·04·05·06 / 실패: 하드웨어 요구 없음",
    ],
    [
        "NFR-12", "AI 실패 격리", "AI Advisor", "M",
        "AI 실패·타임아웃 시 DecodeResult·ParsedDidValue·Finding·WriteResult·AccessEvidence와 Analysis Session을 유지한다.",
        "AI 실패 후 로컬 결과 표시",
        "정상: AI 설명 / 실패: AiError+로컬 결과",
    ],
    [
        "NFR-13", "fixture 재현성", "Exchange", "M",
        "같은 controller_profile+scenario_id와 주입 Clock은 항상 같은 Access/Read/Write 요청·응답·상태 결과를 낸다.",
        "2회 실행 결과 일치",
        "정상: 같은 시나리오 결과 동일 / 실패: 중복 ID·미지원 DID→데이터셋 검증 실패",
    ],
    [
        "NFR-14", "사실 격리", "AI Advisor", "M",
        "AI가 SID/NRC/DID 이름·파싱값·Finding 상태를 변경하지 못한다.",
        "사실 구역=카탈로그/파서 결과",
        "정상: AI는 별도 설명 / 실패: AI 충돌 문구는 경고·미반영",
    ],
    [
        "NFR-15", "AI 응답 지연", "AI Advisor", "S",
        "AI 요청은 설정된 15초 안에 완료하거나 취소되고 진행 상태를 표시한다.",
        "≤15초 또는 취소",
        "정상: 완료 / 실패: 15초→AiError·재시도 안내",
    ],
    [
        "NFR-16", "근거 필수", "AI Advisor", "M",
        "AI 체크리스트 항목마다 근거(로그/DID/Finding/WriteResult/AccessEvidence) ID ≥1.",
        "무근거 항목 없음",
        "정상: 근거 필드 / 실패: 누락→비표시",
    ],
    [
        "NFR-17", "UI 비차단", "GUI App", "M",
        "외부 AI 호출 중 JavaFX UI 스레드를 막지 않고 진행·취소 상태를 보여 준다.",
        "AI 호출 중 탭 이동·취소 가능",
        "정상: 백그라운드 Task / 실패: UI 멈춤 없음",
    ],
    [
        "NFR-18", "비밀·데이터 보호", "AI Advisor", "M",
        "API 키와 password·seed·key는 로그·리포트·프롬프트에 남기지 않는다. PasswordField는 즉시 비우고 char[]은 finally에서 지운다.",
        "API 키·password·seed·key 노출 0건",
        "정상: sanitized context만 전송 / 실패: 키 없음·credential 오류→호출/인증 차단+안내",
    ],
    [
        "NFR-19", "접근 상태 단일 소유", "Diagnostic Access", "M",
        "ActiveDiagnosticContext만 profile·catalogVersion·generation·DiagnosticAccessState를 소유하며 GUI와 Guard는 snapshot을 사용한다.",
        "상태 변경 경로 단일·stale 결과 미반영",
        "정상: 검증된 50/67만 전이 / 실패: 이전 generation 응답→폐기",
    ],
    [
        "NFR-20", "민감 payload 제거", "Exchange", "M",
        "0x27 seed/key payload를 GUI history·JSONL·JSON·AI context에 남기지 않는다. Recording Port는 raw 즉시 폐기·마스킹 메타만 반환하고, Interpreter는 정적 로그 0x27/0x67을 <REDACTED>로 표시한다.",
        "민감 byte sequence 저장·표시 0건",
        "정상: payloadRedacted=true·정적 로그 <REDACTED> / 실패: seed/key 평문 잔존 시 NFR 실패",
    ],
    [
        "NFR-21", "권한 비복원", "Diagnostic Access", "M",
        "Analysis Session 복원은 과거 접근 결과만 읽기 전용 표시하고 런타임 권한은 DEFAULT_LOCKED로 시작한다.",
        "복원 후 Read/Write disabled",
        "정상: 재인증 후 실행 / 실패: 저장된 UNLOCKED 문자열이 권한에 영향 없음",
    ],
]

# id, 이름, 우선순위, 목표, 관련FR, 확인
USE_CASES = [
    [
        "UC-01", "GUI 로그 해석", "M",
        "JavaFX에서 HEX 로그를 열어 결정론적 해석을 본다.",
        "FR-01~08,10",
        "정상: SID/NRC+필드 / 실패: 깨진 줄만 오류·GUI 유지",
    ],
    [
        "UC-02", "현상→더미 ReadData→AI", "M",
        "UC-06으로 접근 권한을 얻은 뒤 현상에서 DID를 제안하고 더미 응답 값·Finding을 AI가 해석해 목록을 만든다.",
        "FR-09,10,12~18,20,22~28",
        "정상: unlock→급정지(Flipsky 실행 DID 0x1001)→RD-VESC-001→50.0V·ABNORMAL→AI / 실패: locked→ACCESS_DENIED·요청 0건",
    ],
    [
        "UC-03", "팀 공유", "S",
        "outputs/<analysisSessionId> 폴더를 전달하고 analysis-session.json에서 결정론적 결과와 AI 근거를 읽기 전용 복원한다.",
        "FR-08, FR-09, FR-20",
        "정상: 같은 사실 결과·근거 ID 복원 / 실패: 파일 누락·schema/catalog 버전 불일치→부분 복원+경고",
    ],
    [
        "UC-04", "도착 프레임 AI 분석", "M",
        "정적 로그 또는 더미 데이터의 0x62/7F 프레임을 값·Finding으로 만든 뒤 AI 설명을 본다.",
        "FR-01~04,07,10,12,15~18,20",
        "정상: 0x62→값·Finding→AI / 실패: 0x7F·포맷 오류→사실 오류+AI 입력 제한",
    ],
    [
        "UC-05", "WriteData→AI", "M",
        "UC-06으로 접근 권한을 얻은 뒤 교육용 writable DID 값을 0x2E로 생성하고 0x6E/7F WriteResult를 AI가 설명한다.",
        "FR-09,10,12,17,18,20~28",
        "정상: unlock→targetRpm→0x2E→0x6E→ACCEPTED→AI / 실패: locked→ACCESS_DENIED·요청 0건",
    ],
    [
        "UC-06", "Extended Session·인증", "M",
        "Excel에서 profile을 선택하고 Extended Session과 교육용 SecurityAccess를 통과해 Read/Write 권한을 얻는다.",
        "FR-10,22~28",
        "정상: 10 03→50 03→27 01/67 01→PasswordField→27 02/67 02→EXTENDED_UNLOCKED / 실패: 7F 27 35→EXTENDED_LOCKED",
    ],
]

COMPLETION = [
    ["DOD-01", "Interpreter", "GUI 해석", "JavaFX에서 HEX→SID/NRC·시연 SID 필드", "UC-01, FR-01~08,10"],
    ["DOD-02", "Interpreter", "오류 격리", "깨진 줄·비HEX 있어도 나머지 해석", "FR-01,02, NFR-02"],
    ["DOD-03", "AI Advisor", "AI 해석", "WriteResult 포함 구조화 컨텍스트→AI 설명. 사실 구역 분리", "FR-09,12,18, NFR-14"],
    ["DOD-04", "AI Advisor", "최종 목록", "항목마다 로그/DID/Finding/WriteResult/AccessEvidence 근거 ≥1", "FR-17, NFR-16"],
    ["DOD-05", "ReadData", "File Port", "0x22 요청·0x62/7F/오류 응답을 JSONL로 저장", "FR-14,20, NFR-13"],
    ["DOD-06", "ReadData", "값·Finding", "카탈로그 스키마 표시값과 Finding(또는 정상 범위)", "FR-15,16, UC-04"],
    ["DOD-07", "AI Advisor", "degrade", "AI 실패→결정론적 해석·Finding·WriteResult+경고 유지", "NFR-12"],
    ["DOD-08", "GUI App", "UI 비차단", "AI 호출 중 GUI 조작·취소 가능", "NFR-17"],
    ["DOD-09", "GUI App", "장비 없는 시연", "저장 로그·상태형 File ECU로 UC-01/02/04/05/06 완주", "NFR-06,11"],
    ["DOD-10", "GUI App", "Analysis Session", "schemaVersion과 ID를 가진 프레임·값·Finding·WriteResult·AccessEvidence·AI 파일을 저장·부분 복원", "FR-20"],
    ["DOD-11", "WriteData", "쓰기 결과", "0x2E·0x6E/7F 저장과 WriteResult. 실제 ECU 변경 없음", "FR-21, NFR-08, UC-05"],
    ["DOD-12", "전체", "Must 통과", "우선순위 M 완료 기준 통과", "각 시트 M 필터"],
    ["DOD-13", "전체", "역할 일치", "Apps 경계와 FR app 일치. M UC가 S/C-only에 의존하지 않음", "Apps↔FR↔UC"],
    ["DOD-14", "Diagnostic Access", "Extended·unlock", "검증된 50 03과 67 02에서만 EXTENDED_UNLOCKED", "FR-23~25, UC-06"],
    ["DOD-15", "Diagnostic Access", "Guard", "locked Read/Write는 ACCESS_DENIED이며 요청 프레임·JSONL 0건", "FR-26, NFR-21"],
    ["DOD-16", "Exchange", "민감정보 제거", "password·seed·key byte sequence가 output·AI context에 0건", "FR-28, NFR-18,20"],
    ["DOD-17", "GUI App", "Excel 선택", "runtime-enabled profile/DID/action과 GUI ComboBox·fixture가 일치", "FR-22, NFR-13"],
]


def main() -> None:
    wb = Workbook()
    wb.remove(wb.active)

    write_sheet(wb, "Overview", ["항목", "내용"], OVERVIEW, "OverviewCore")
    write_sheet(
        wb, "Priority", ["코드", "영문", "한글", "의미", "행색"], PRIORITY_ROWS, "PriorityLegend"
    )
    write_sheet(
        wb,
        "Apps",
        ["app", "한줄정의", "하는일", "안하는일", "관련FR"],
        APPS,
        "AppOverview",
    )
    write_sheet(
        wb,
        "Functional",
        ["id", "제목", "app", "우선순위", "요구", "입출력", "완료", "확인"],
        FUNCTIONAL,
        "FunctionalReqs",
        priority_col=4,
    )
    write_sheet(
        wb,
        "NonFunctional",
        ["id", "제목", "app", "우선순위", "요구", "완료", "확인"],
        NONFUNCTIONAL,
        "NonFunctionalReqs",
        priority_col=4,
    )
    write_sheet(
        wb,
        "UseCases",
        ["id", "이름", "우선순위", "목표", "관련FR", "확인"],
        USE_CASES,
        "UseCases",
        priority_col=3,
    )
    write_sheet(
        wb,
        "Completion",
        ["id", "app", "항목", "완료", "확인방법"],
        COMPLETION,
        "CompletionDoD",
    )
    write_sheet(
        wb,
        "Meta",
        ["key", "value"],
        [
            ["title", "UDS Log Interpreter & Advisor - Requirements"],
            ["read_order", "Overview → Priority → Apps → Functional(요구 열) → …"],
            ["core", "JavaFX GUI → Excel profile/DID → Extended Session → SecurityAccess → Guarded Read/Write → AI → Analysis Session"],
            ["priority", "M=Must / S=Should / C=Could"],
            ["how_to_read_fr", "요구만 세로로 훑기 → 필요 시 입출력·완료·확인"],
            ["sheets", "Overview · Priority · Apps · Functional · NonFunctional · UseCases · Completion · Meta"],
            ["test_env_note", "상태형 File ECU가 access/read/write fixture를 실행하고 Recording Port가 민감정보 제거 후 저장"],
            ["log_format", "UTF-8; header ts,direction,hex; body time,direction,HEX; # comments"],
            ["demo_sids", "0x10,0x11,0x14,0x19,0x22,0x27,0x3E,0x7F (+ positive counterparts)"],
            ["exchange_policy", "ExchangeCoordinator + ExchangePolicy + RecordingDiagnosticExchangePort + selected delegate Port"],
            ["access_policy", "project policy: EXTENDED + LEVEL_1 required; restored Analysis Session never restores authorization"],
            ["credential_policy", "config/demo_password.txt is plaintext classroom fixture; never persist or send password/seed/key"],
            ["ai_policy", "External AI receives sanitized structured context; catalog facts remain authoritative"],
            ["write_data_policy", "FR-21 Must; create/save 0x2E and parse/save 0x6E/7F; no ECU mutation"],
            ["dummy_dataset", "samples/read_data_scenarios.csv"],
            ["write_dataset", "samples/write_data_scenarios.csv"],
            ["access_dataset", "samples/access_scenarios.csv"],
            ["controller_profiles", "FARDRIVER_ND72530, FLIPSKY_75100_VESC"],
            ["related_plan", "docs/PROJECT_PLAN.txt"],
            ["related_guide", "docs/UDS_GUIDE.txt"],
            ["related_catalog", "docs/uds_catalog.xlsx (OemExtensions+SymptomDidMap)"],
            ["generated_by", "tool/build_requirements_xlsx.py"],
            ["output_path", "docs/requirements.xlsx"],
        ],
        "RequirementsMeta",
    )

    wb.save(OUT)
    print(f"Wrote {OUT}")
    print(
        f"Overview={len(OVERVIEW)} Apps={len(APPS)} "
        f"FR={len(FUNCTIONAL)} NFR={len(NONFUNCTIONAL)} "
        f"UC={len(USE_CASES)} DOD={len(COMPLETION)}"
    )


if __name__ == "__main__":
    main()
