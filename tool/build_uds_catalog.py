#!/usr/bin/env python3
"""Build human-readable full UDS catalog (when/why explanations, no MVP column)."""

from __future__ import annotations

from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.table import Table, TableStyleInfo

_DOCS = Path(__file__).resolve().parents[1] / "docs"
OUT = _DOCS / "uds_catalog.xlsx"
OUT_FALLBACK = _DOCS / "uds_catalog_new.xlsx"
HEADER_FILL = PatternFill("solid", fgColor="1F4E79")
HEADER_FONT = Font(color="FFFFFF", bold=True)
TITLE_FONT = Font(bold=True, size=14)
GROUP_FILL_A = PatternFill("solid", fgColor="F8FAFC")
GROUP_FILL_B = PatternFill("solid", fgColor="FFFFFF")
GRID_BORDER = Border(
    left=Side(style="thin", color="D9D9D9"),
    right=Side(style="thin", color="D9D9D9"),
    top=Side(style="thin", color="D9D9D9"),
    bottom=Side(style="thin", color="D9D9D9"),
)
GROUP_TOP_SIDE = Side(style="thin", color="B0B8C4")

CENTER_COLS = {
    "sid",
    "code",
    "sub_function",
    "parent_sid",
    "has_subfunction",
    "suppress_bit_applicable",
    "response_needed_practice",
    "positive_response_sid",
    "bit",
    "mask",
    "id_type",
    "id_offset",
    "data_offset",
    "is_variable_length",
    "data_type",
    "byte_length",
    "scale",
    "unit",
    "normal_min",
    "normal_max",
    "min_length_bytes",
    "kind",
    "type",
    "id",
    "related_sid",
    "did_id",
    "priority",
    "category",
    "sid_or_marker",
    "example_hex",
    "example_req_hex",
    "example_res_hex",
    "sub_byte_offset",
    "구분",
}


def style_header(ws, ncols: int) -> None:
    for col in range(1, ncols + 1):
        cell = ws.cell(1, col)
        cell.fill = HEADER_FILL
        cell.font = HEADER_FONT
        cell.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center")
    ws.freeze_panes = "A2"
    ws.auto_filter.ref = ws.dimensions
    ws.row_dimensions[1].height = 30


def _col_width(ws, col_idx: int, widths: dict[str, int] | None) -> float:
    header = str(ws.cell(1, col_idx).value or "")
    letter = get_column_letter(col_idx)
    if widths and header in widths:
        return float(widths[header])
    dim = ws.column_dimensions[letter].width
    return float(dim) if dim else 12.0


def _estimate_lines(text: str, col_width: float) -> int:
    if not text:
        return 1
    lines = 0
    usable = max(8, int(col_width) - 1)
    for part in str(text).split("\n"):
        lines += max(1, (len(part) + usable - 1) // usable)
    return lines


def apply_row_heights(ws, widths: dict[str, int] | None = None) -> None:
    ncols = ws.max_column
    for r in range(2, ws.max_row + 1):
        max_lines = 1
        for c in range(1, ncols + 1):
            val = ws.cell(r, c).value
            if val is None:
                continue
            max_lines = max(max_lines, _estimate_lines(str(val), _col_width(ws, c, widths)))
        ws.row_dimensions[r].height = max(22, min(90, 15 + 14 * max_lines))


def autosize(ws, widths: dict[str, int] | None = None) -> None:
    headers = [str(ws.cell(1, c).value or "") for c in range(1, ws.max_column + 1)]
    for col in ws.columns:
        col_idx = col[0].column
        letter = get_column_letter(col_idx)
        header = headers[col_idx - 1]
        if widths and header in widths:
            ws.column_dimensions[letter].width = widths[header]
        else:
            length = 12
            for cell in col[:80]:
                if cell.value is None:
                    continue
                length = max(length, min(40, len(str(cell.value).split("\n")[0]) + 2))
            ws.column_dimensions[letter].width = length
        center = header in CENTER_COLS
        for cell in col:
            if cell.row == 1:
                continue
            cell.alignment = Alignment(
                wrap_text=True,
                vertical="center" if center else "top",
                horizontal="center" if center else "left",
            )


def add_table(ws, name: str, striped: bool = True) -> None:
    table = Table(displayName=name, ref=ws.dimensions)
    table.tableStyleInfo = TableStyleInfo(
        name="TableStyleMedium2",
        showFirstColumn=False,
        showLastColumn=False,
        showRowStripes=striped,
        showColumnStripes=False,
    )
    ws.add_table(table)


def apply_grid_borders(ws) -> None:
    for row in ws.iter_rows(min_row=1, max_row=ws.max_row, max_col=ws.max_column):
        for cell in row:
            cell.border = GRID_BORDER


def apply_group_fills(
    ws,
    key_col: int,
    key_fn=None,
    start_row: int = 2,
) -> None:
    """Alternate background when key changes. key_fn(cell_value) -> group key."""
    prev = object()
    use_a = False
    for r in range(start_row, ws.max_row + 1):
        raw = ws.cell(r, key_col).value
        key = key_fn(raw) if key_fn else raw
        changed = key != prev
        if changed:
            use_a = not use_a
            prev = key
        fill = GROUP_FILL_A if use_a else GROUP_FILL_B
        for c in range(1, ws.max_column + 1):
            cell = ws.cell(r, c)
            cell.fill = fill
            if changed and r > start_row:
                cell.border = Border(
                    top=GROUP_TOP_SIDE,
                    left=Side(style="thin", color="D9D9D9"),
                    right=Side(style="thin", color="D9D9D9"),
                    bottom=Side(style="thin", color="D9D9D9"),
                )


def nrc_band(code: str | None) -> str:
    if not code or not str(code).startswith("0x"):
        return "other"
    try:
        v = int(str(code), 16)
    except ValueError:
        return "other"
    if 0x10 <= v <= 0x1F:
        return "10"
    if 0x20 <= v <= 0x2F:
        return "20"
    if 0x30 <= v <= 0x3F:
        return "30"
    if 0x50 <= v <= 0x5F:
        return "50"
    if 0x70 <= v <= 0x7F:
        return "70"
    if 0x80 <= v <= 0x93:
        return "80"
    return "other"


def write_sheet(
    wb,
    title,
    headers,
    rows,
    table_name,
    widths=None,
    group_key_col=None,
    group_key_fn=None,
    table_stripes=True,
):
    ws = wb.create_sheet(title)
    ws.append(headers)
    for row in rows:
        ws.append(list(row))
    style_header(ws, len(headers))
    autosize(ws, widths)
    apply_grid_borders(ws)
    if group_key_col is not None:
        apply_group_fills(ws, group_key_col, group_key_fn)
        table_stripes = False
    apply_row_heights(ws, widths)
    add_table(ws, table_name, striped=table_stripes)
    return ws


# sid, name, pos, category, summary, when_to_use, why_use, typical_flow, session_note, security_note, has_sf, notes
SERVICES = [
    [
        "0x10", "DiagnosticSessionControl", "0x50", "Session",
        "진단 세션을 바꾼다.",
        "기본 세션으로는 못 하는 기능(쓰기·루틴·일부 DID)을 쓰기 직전, 또는 프로그래밍 전에.",
        "세션이 바뀌면 ECU가 허용하는 서비스 집합과 타임아웃이 달라지기 때문.",
        "보통 맨 앞: 10 03(extended) → 이후 22/2E/31 등. 끝나면 default로 돌아가거나 타임아웃.",
        "세션 전환 자체", "보통 없음", "Y",
        "긍정응답 0x50. suppress bit로 응답 생략 가능. "
        "세션별 허용 SID 매트릭스는 ECU 스펙에만 존재(7F serviceNotSupportedInActiveSession / "
        "7E subFunctionNotSupportedInActiveSession 원인 설명).",
    ],
    [
        "0x11", "ECUReset", "0x51", "ECUControl",
        "ECU를 리셋한다.",
        "설정 반영, 플래시 후 재시작, 이상 상태 초기 후 깨끗이 다시 시작할 때.",
        "소프트웨어/하드웨어 상태를 초기화해 이후 진단·운전을 안정화하려고.",
        "필요 세션/조건 확인 → 11 01/03 등 → 리셋 후 대개 default 세션부터 다시.",
        "세션·차속 등 조건 제한", "경우에 따라", "Y",
        "실차에서는 안전 조건(차속 등) 때문에 NRC 0x22가 흔함.",
    ],
    [
        "0x14", "ClearDiagnosticInformation", "0x54", "DTC",
        "저장된 DTC(및 관련 정보)를 지운다.",
        "수리/재현 시험 전후에 고장 코드를 비우고 싶을 때. 보통 0x19로 확인한 뒤.",
        "이전 DTC가 남아 있으면 새 시험 결과를 구분하기 어려워서.",
        "19로 현황 확인 → (조건 충족) 14 FF FF FF → 다시 19로 잔존 여부 확인.",
        "보통 extended 이상", "경우에 따라", "N",
        "groupOfDTC 3바이트. FFFFFF=전체 삭제가 관례.",
    ],
    [
        "0x19", "ReadDTCInformation", "0x59", "DTC",
        "고장 코드·스냅샷·확장 데이터를 읽는다.",
        "차에서 뭐가 고장으로 잡혔는지, 확정/미확정 DTC, 지원 DTC 목록이 궁금할 때.",
        "정비·디버깅의 출발점. HEX만으로는 ‘어떤 고장인지’를 알 수 없어서.",
        "가장 흔함: 19 02 FF (상태 마스크로 DTC 목록). 필요 시 04/06으로 스냅샷·확장.",
        "default에서도 일부 가능", "보통 없음", "Y",
        "서브펑션이 매우 많음. SubFunctions 시트 참고.",
    ],
    [
        "0x22", "ReadDataByIdentifier", "0x62", "Data",
        "DID로 ECU 내부 데이터를 읽는다.",
        "VIN, SW/HW 번호, 센서값, 설정값 등 ‘이름 붙은 데이터’를 읽을 때.",
        "주소 몰라도 ID만으로 안전하게 읽기 위한 표준 창구.",
        "세션 맞춘 뒤 22 F1 90 처럼 DID 요청 → 62 + DID + 데이터.",
        "DID별 세션 제한", "DID별 보안 가능", "N",
        "요청에 DID를 여러 개 넣을 수 있는 ECU도 있음. "
        "예시 HEX는 시연용 축약(실차 VIN 0xF190은 17바이트 ASCII). 1바이트 예는 OemExtensions 0xF186.",
    ],
    [
        "0x23", "ReadMemoryByAddress", "0x63", "Memory",
        "물리/논리 주소로 메모리를 읽는다.",
        "DID로 안 열리는 영역, 덤프·분석, 맵 데이터가 필요할 때.",
        "주소 단위 접근이 필요할 때. 잘못 쓰면 위험해 보안·세션이 빡센 경우가 많음.",
        "ALFI로 주소/길이 바이트 수 지정 → 주소·크기 → 데이터 응답.",
        "extended/programming", "자주 보안", "N",
        "포맷 식별자(ALFI)에 따라 길이 변동.",
    ],
    [
        "0x24", "ReadScalingDataByIdentifier", "0x64", "Data",
        "DID 값의 스케일(물리량 변환) 정보를 읽는다.",
        "원시 바이트를 온도·전압 같은 단위로 해석하는 방법을 ECU에 물어볼 때.",
        "툴이 표시 단위를 맞추려면 스케일 정의가 필요해서.",
        "24 + DID → 스케일 레코드.",
        "DID별", "DID별", "N", "",
    ],
    [
        "0x27", "SecurityAccess", "0x67", "Security",
        "시드/키로 보안 잠금을 푼다.",
        "쓰기(2E), 일부 루틴(31), 메모리/플래시 등 ‘잠긴’ 기능을 쓰기 직전.",
        "임의 변경을 막기 위한 관문. 잠금 상태면 NRC 0x33이 난다.",
        "27 odd(시드요청) → 67+seed → 키 계산 → 27 even(키전송) → 성공 시 이후 서비스.",
        "세션 의존", "이 서비스 자체", "Y",
        "패턴: subFunction(value&0x7F)이 홀수=requestSeed, 짝수=sendKey. "
        "SubFunctions 표에 없는 레벨도 동일 홀수/짝수 규칙으로 해석. "
        "알고리즘·레벨 번호는 OEM 비밀·스펙 확인.",
    ],
    [
        "0x28", "CommunicationControl", "0x68", "Comm",
        "네트워크 통신(수신/송신)을 잠시 끄거나 켠다.",
        "플래시·특정 시험 중 다른 노드 통신을 줄이거나, 시험 후 원복할 때.",
        "버스 부하·간섭을 통제하거나 안전상 통신을 제한하려고.",
        "extended 세션 → 28 + controlType + communicationType → 작업 → 다시 enable.",
        "보통 extended", "경우에 따라", "Y",
        "잘못 끄면 차량 기능에 영향. 시연·실차 주의.",
    ],
    [
        "0x29", "Authentication", "0x69", "Security",
        "인증서 기반 등 최신 인증 절차.",
        "시드/키(0x27) 대신/추가로 강한 인증이 요구되는 ECU에서.",
        "키 유출에 강한 접근제어가 필요할 때.",
        "서브펑션별 인증서·챌린지·소유권 증명 순서는 OEM/판본 문서 따름.",
        "세션 의존", "인증 흐름", "Y",
        "판본·OEM 의존이 큼. SubFunctions·NRC 0x50대 참고.",
    ],
    [
        "0x2A", "ReadDataByPeriodicIdentifier", "0x6A", "Data",
        "주기적으로 DID 값을 보내게 설정한다.",
        "실시간으로 값이 변하는 신호를 반복 폴링 없이 보고 싶을 때.",
        "대역·부하를 줄이면서 연속 관측하려고.",
        "전송 모드(slow/medium/fast) 선택 → periodic DID 지정 → stop으로 종료.",
        "세션 의존", "DID별", "N",
        "둘째 바이트는 Sub-function이 아니라 transmissionMode 파라미터. suppress bit(bit7) 미적용.",
    ],
    [
        "0x2C", "DynamicallyDefineDataIdentifier", "0x6C", "Data",
        "여러 소스를 묶어 동적 DID를 만든다.",
        "한 번에 보고 싶은 신호 조합을 임시 DID로 정의할 때.",
        "고정 DID만으로는 조합이 안 될 때 관측 효율을 높이려고.",
        "defineByIdentifier/Memory → 이후 22로 동적 DID 읽기 → clear.",
        "세션 의존", "경우에 따라", "Y", "",
    ],
    [
        "0x2E", "WriteDataByIdentifier", "0x6E", "Data",
        "DID로 ECU 데이터를 쓴다.",
        "설정값·코딩·캘리브레이션 등 쓰기 가능한 DID를 변경할 때.",
        "읽기(22)만으로는 구성을 바꿀 수 없어서.",
        "대개 10 03 → (27 보안) → 2E + DID + data → 필요 시 11 리셋.",
        "extended/programming", "자주 보안", "N",
        "잘못된 쓰기는 차량 동작에 영향. 실차 주의.",
    ],
    [
        "0x2F", "InputOutputControlByIdentifier", "0x6F", "IO",
        "입출력을 강제로 제어하거나 ECU에 되돌린다.",
        "액추에이터 강제 구동, 센서 대치, 시험 후 제어권 반환할 때.",
        "실차/벤치에서 입출력 경로를 분리 시험하려고.",
        "returnControl / reset / freeze / shortTermAdjustment 중 선택.",
        "보통 extended", "자주 보안", "N",
        "둘째 바이트는 Sub-function이 아니라 ioControlParameter. suppress bit(bit7) 미적용. 안전 IO는 NRC 0x22 흔함.",
    ],
    [
        "0x31", "RoutineControl", "0x71", "Routine",
        "ECU 내부 루틴을 시작/중지/결과 조회한다.",
        "셀프테스트, 학습, 특수 점검, 프로그래밍 보조 루틴 실행 시.",
        "단순 DID 읽기/쓰기로는 표현 안 되는 ‘절차’를 돌리려고.",
        "31 01 + routineId 시작 → (대기/0x78) → 31 03 결과. 중지는 31 02.",
        "세션 의존", "루틴별 보안", "Y",
        "routineId는 OEM 정의. OemExtensions에 예시.",
    ],
    [
        "0x34", "RequestDownload", "0x74", "Transfer",
        "테스터→ECU 다운로드(쓰기)를 시작한다.",
        "펌웨어/캘리브 플래시 시퀀스 맨 앞.",
        "이후 36으로 블록을 넣기 전에 크기·포맷을 합의하려고.",
        "34 → (max block length 수신) → 36 반복 → 37 종료.",
        "programming", "보안 흔함", "N", "",
    ],
    [
        "0x35", "RequestUpload", "0x75", "Transfer",
        "ECU→테스터 업로드(읽기 덤프)를 시작한다.",
        "메모리/이미지 덤프가 필요할 때.",
        "대용량을 블록 단위로 받기 위한 사전 합의.",
        "35 → 36으로 수신 → 37.",
        "programming/extended", "보안 가능", "N", "",
    ],
    [
        "0x36", "TransferData", "0x76", "Transfer",
        "실제 데이터 블록을 주고받는다.",
        "0x34/0x35 성공 직후.",
        "합의된 블록 크기·시퀀스로 본문을 전송하려고.",
        "blockSequenceCounter를 1씩 증가시키며 반복. 틀리면 NRC 0x73.",
        "전송 중", "선행 보안", "N", "",
    ],
    [
        "0x37", "RequestTransferExit", "0x77", "Transfer",
        "전송을 정상 종료한다.",
        "필요한 블록을 모두 보낸/받은 뒤.",
        "전송 세션을 닫고 ECU가 후처리(검증 등)하게 하려고.",
        "36 완료 → 37 → (필요 시 체크섬 루틴/리셋).",
        "전송 중", "선행 보안", "N", "",
    ],
    [
        "0x38", "RequestFileTransfer", "0x78", "Transfer",
        "파일 단위 add/delete/read 등을 요청한다.",
        "파일시스템 있는 ECU에서 로그·맵 파일을 다룰 때.",
        "주소 덤프보다 파일 단위가 맞을 때.",
        "modeOfOperation + path → 이후 전송 절차는 스펙/OEM.",
        "세션 의존", "보안 가능", "N",
        "둘째 바이트는 Sub-function이 아니라 modeOfOperation 파라미터. suppress bit(bit7) 미적용.",
    ],
    [
        "0x3D", "WriteMemoryByAddress", "0x7D", "Memory",
        "주소로 메모리를 쓴다.",
        "DID/파일로 못 쓰는 영역에 직접 기록이 필요할 때(개발·특수 공정).",
        "저수준 기록. 위험도 높아 보통 programming+보안.",
        "ALFI+주소+크기+data. 실수 시 벽돌 위험.",
        "programming", "보안 흔함", "N", "",
    ],
    [
        "0x3E", "TesterPresent", "0x7E", "Session",
        "테스터가 살아 있음을 알린다.",
        "extended 등 비기본 세션을 유지하는 동안 주기적으로.",
        "안 보내면 세션 타임아웃으로 default로 떨어져 이후 서비스가 거절됨.",
        "보통 3E 80 (suppress)로 주기 전송. 응답이 없어도 정상인 경우가 많음.",
        "모든 세션", "없음", "Y",
        "시연·실무 모두 필수에 가깝다.",
    ],
    [
        "0x83", "AccessTimingParameter", "0xC3", "Network",
        "통신 타이밍 파라미터를 읽거나 바꾼다.",
        "느린/특수 링크에서 타임아웃을 맞출 때.",
        "기본 타이밍으로는 응답이 안 맞을 때 조정하려고.",
        "read/set default/set given 중 선택.",
        "세션 의존", "경우에 따라", "Y", "",
    ],
    [
        "0x84", "SecuredDataTransmission", "0xC4", "Security",
        "보안 채널로 데이터를 실어 보낸다.",
        "이미 맺어진 보안 컨텍스트 위에서 민감 데이터를 주고받을 때.",
        "평문 진단 프레임으로 보내면 안 되는 내용을 보호하려고.",
        "선행 인증/키 합의 후 사용. OEM 의존.",
        "세션 의존", "보안 컨텍스트", "N", "",
    ],
    [
        "0x85", "ControlDTCSetting", "0xC5", "DTC",
        "DTC 기록(설정)을 잠시 끄거나 켠다.",
        "액추에이터 강제/특정 시험 중 의도치 않은 DTC가 쌓이는 것을 막을 때.",
        "시험 노이즈로 고장코드가 오염되는 것을 줄이려고.",
        "85 02 off → 시험 → 85 01 on. 끝나면 반드시 원복.",
        "보통 extended", "경우에 따라", "Y", "",
    ],
    [
        "0x86", "ResponseOnEvent", "0xC6", "Event",
        "특정 이벤트 때 ECU가 알아서 응답하게 한다.",
        "DTC 상태 변화·타이머·DID 변화 등을 계속 폴링하기 싫을 때.",
        "이벤트 드리븐으로 관측 효율을 높이려고.",
        "이벤트 정의 → start → (이벤트 발생 시 지정 서비스 응답) → stop/clear.",
        "세션 의존", "경우에 따라", "Y", "",
    ],
    [
        "0x87", "LinkControl", "0xC7", "Network",
        "링크 모드(예: 보드레이트) 전환을 검증·실행한다.",
        "고속 다운로드 등으로 통신 속도를 바꿀 때.",
        "현재 링크로는 대역이 부족할 때 모드를 바꾸려고.",
        "verify → transition 순을 지키는 경우가 많음.",
        "세션 의존", "경우에 따라", "Y", "",
    ],
    [
        "0x7F", "NegativeResponse", "-", "Meta",
        "요청이 거절됐을 때 ECU가 보내는 공통 포맷.",
        "로그에 7F가 보이면 ‘실패 응답’이다. 서비스 요청이 아님.",
        "왜 거절됐는지(NRC)를 표준 방식으로 알려 주려고.",
        "포맷: 7F | 원래요청SID | NRC. NRC 시트에서 세 번째 바이트 해석.",
        "해당 요청 세션", "-", "N",
        "해석기·Advisor의 핵심 입력.",
    ],
]


def build_subfunctions() -> list[list]:
    """parent, sub, name, suppress, response_needed, params, when, why, notes"""
    rows: list[list] = []

    def add(
        sid, sub, name, suppress, response_needed, params, when_use, why_use, notes=""
    ):
        rows.append(
            [sid, sub, name, suppress, response_needed, params, when_use, why_use, notes]
        )

    add("0x10", "0x01", "defaultSession", "Y", "Y", "없음",
        "작업 종료 후 또는 초기 상태.", "가장 제한된 기본 권한으로 돌아가려고.", "")
    add("0x10", "0x02", "programmingSession", "Y", "Y", "없음",
        "펌웨어/메모리 프로그래밍 직전.", "플래시·다운로드 관련 서비스를 열려면.", "")
    add("0x10", "0x03", "extendedDiagnosticSession", "Y", "Y", "없음",
        "일반 확장 진단(쓰기·IO·많은 DID) 시작 시.", "default보다 많은 서비스를 허용하려고.",
        "실무에서 가장 흔함")
    add("0x10", "0x04", "safetySystemDiagnosticSession", "Y", "Y", "없음",
        "안전 관련 시스템 진단이 필요할 때.", "안전 도메인 전용 세션이 요구될 때.", "")

    add("0x11", "0x01", "hardReset", "Y", "Y", "없음",
        "전원 리셋에 가까운 재시작이 필요할 때.", "하드웨어까지 포함한 초기 효과.", "")
    add("0x11", "0x02", "keyOffOnReset", "Y", "Y", "없음",
        "키 Off/On을 흉내 내야 할 때.", "실제 키 사이클과 유사한 초기화.", "")
    add("0x11", "0x03", "softReset", "Y", "Y", "없음",
        "앱 수준 재시작으로 충분할 때.", "더 가벼운 리셋.", "")
    add("0x11", "0x04", "enableRapidPowerShutDown", "Y", "Y", "없음",
        "빠른 전원 차단 모드가 필요할 때.", "전원 관리 시험.", "")
    add("0x11", "0x05", "disableRapidPowerShutDown", "Y", "Y", "없음",
        "빠른 전원 차단을 끌 때.", "위 모드 원복.", "")

    dtc = [
        ("0x01", "reportNumberOfDTCByStatusMask", "DTCStatusMask",
         "개수만 빠르게 보고 싶을 때", "목록 전체가 필요 없을 때 부하를 줄이려고"),
        ("0x02", "reportDTCByStatusMask", "DTCStatusMask",
         "현재 조건에 맞는 DTC 목록이 필요할 때", "가장 흔한 ‘고장 뭐 있나’ 조회"),
        ("0x03", "reportDTCSnapshotIdentification", "없음",
         "스냅샷이 있는 DTC 식별이 필요할 때", "어떤 기록에 프리즈프레임이 있는지 보려고"),
        ("0x04", "reportDTCSnapshotRecordByDTCNumber", "DTC+recordNumber",
         "특정 DTC 발생 당시 데이터 확인", "재현·원인 분석"),
        ("0x05", "reportDTCStoredDataByRecordNumber", "recordNumber",
         "레코드 번호로 저장 데이터 조회", "판본별 이름 차이 가능"),
        ("0x06", "reportDTCExtDataRecordByDTCNumber", "DTC+extRecord",
         "확장 데이터(추가 환경정보) 확인", "스냅샷 이상의 상세가 필요할 때"),
        ("0x07", "reportNumberOfDTCBySeverityMaskRecord", "severity+status",
         "심각도 기준으로 개수 파악", "우선순위 높은 고장만 빠르게"),
        ("0x08", "reportDTCBySeverityMaskRecord", "severity+status",
         "심각도 기준 DTC 목록", "정비 우선순위 결정"),
        ("0x09", "reportSeverityInformationOfDTC", "DTCMaskRecord",
         "특정 DTC의 심각도 정보", "위험도 판단"),
        ("0x0A", "reportSupportedDTC", "없음",
         "이 ECU가 지원하는 DTC 목록", "마스킹/미지원과 구분"),
        ("0x0B", "reportFirstTestFailedDTC", "없음", "첫 시험 실패 DTC", "초기 이상 단서"),
        ("0x0C", "reportFirstConfirmedDTC", "없음", "첫 확정 DTC", "확정 고장 시작점"),
        ("0x0D", "reportMostRecentTestFailedDTC", "없음", "가장 최근 시험 실패", "방금 재현된 이상"),
        ("0x0E", "reportMostRecentConfirmedDTC", "없음", "가장 최근 확정 DTC", "최근 확정 고장"),
        ("0x14", "reportDTCFaultDetectionCounter", "없음", "검출 카운터 확인", "간헐 고장 분석"),
        ("0x15", "reportDTCWithPermanentStatus", "없음", "영구 DTC 조회", "클리어 후에도 남는 코드"),
        ("0x16", "reportDTCExtDataRecordByRecordNumber", "extDataRecordNumber",
         "확장 레코드 번호로 조회", "DTC 번호 없이 레코드 번호만으로 확장 데이터 뽑을 때"),
        ("0x17", "reportUserDefMemoryDTCByStatusMask", "status+memorySelection",
         "사용자 정의 메모리의 DTC", "다중 메모리 뱅크 ECU"),
        ("0x18", "reportUserDefMemoryDTCSnapshotRecordByDTCNumber", "DTC+record+memory",
         "사용자 메모리 스냅샷", "기본 메모리 외 뱅크의 프리즈프레임이 필요할 때"),
        ("0x19", "reportUserDefMemoryDTCExtDataRecordByDTCNumber", "DTC+ext+memory",
         "사용자 메모리 확장 데이터", "사용자 정의 메모리의 확장 환경정보를 볼 때"),
        ("0x1A", "reportSupportedDTCExtDataRecord", "없음/판본의존",
         "지원 확장 레코드 확인", "판본 확인 필요"),
        ("0x1C", "reportDTCExtendedDataRecordInformation", "판본의존",
         "확장 데이터 레코드 정보", "지원 여부는 ECU 스펙"),
        ("0x42", "reportWWHOBDDTCByMaskRecord", "functionalGroup+mask",
         "WWH-OBD DTC 조회", "상용/세계 OBD"),
        ("0x55", "reportWWHOBDDTCWithPermanentStatus", "functionalGroup 등",
         "WWH-OBD 영구 DTC", "WWH-OBD에서 클리어 후에도 남는 permanent DTC 확인"),
        ("0x56", "reportDTCInformationByDTCReadinessGroupIdentifier", "판본의존",
         "준비도 그룹별 DTC 정보", "WWH-OBD 계열"),
    ]
    for sub, name, params, when_use, why_use in dtc:
        add("0x19", sub, name, "Y", "Y", params, when_use, why_use, "")

    for pair_index, odd, even in [
        (1, "0x01", "0x02"), (2, "0x03", "0x04"), (3, "0x05", "0x06"),
        (4, "0x07", "0x08"), (5, "0x09", "0x0A"),
    ]:
        add("0x27", odd, f"requestSeed_type{odd[2:]}", "Y", "Y", "없음(또는 추가)",
            f"OEM 정의 access type pair {pair_index} 시작", "시드를 받아 키를 계산하려고",
            "홀수=requestSeed. 권한 의미·레벨 번호는 OEM 정의")
        add("0x27", even, f"sendKey_type{even[2:]}", "Y", "Y", "securityKey",
            f"access type {odd} 시드 받은 직후", "계산한 키를 제출해 잠금 해제",
            "짝수=sendKey. 권한 의미·레벨 번호는 OEM 정의")
    add("0x27", "0x61", "requestSeed_oemCommonExample", "Y", "Y", "OEM",
        "OEM이 이 레벨을 쓸 때", "레벨 번호는 OEM 마음", "예시")
    add("0x27", "0x62", "sendKey_oemCommonExample", "Y", "Y", "securityKey",
        "위 시드에 대한 키 전송", "짝 맞춤", "예시")

    for sub, name, when_use, why_use in [
        ("0x00", "enableRxAndTx", "통신 원복", "시험 후 정상 통신 복구"),
        ("0x01", "enableRxAndDisableTx", "수신만 유지하고 송신 억제", "버스 송신 간섭 줄이기"),
        ("0x02", "disableRxAndEnableTx", "특수 시험", "수신 차단이 필요할 때"),
        ("0x03", "disableRxAndTx", "플래시 등 동안 통신 최소화", "충돌·부하 감소"),
        ("0x04", "enableRxAndDisableTxWithEnhancedAddressInfo", "확장 주소 정보와 함께", "게이트웨이/확장 주소"),
        ("0x05", "enableRxAndTxWithEnhancedAddressInfo", "확장 주소로 정상화", "원복"),
    ]:
        add("0x28", sub, name, "Y", "Y", "communicationType 등", when_use, why_use, "")

    for sub, name, when_use, why_use in [
        ("0x00", "deAuthenticate", "인증 세션 종료", "권한 회수"),
        ("0x01", "verifyCertificateUnidirectional", "단방향 인증서 검증", "신뢰 확립"),
        ("0x02", "verifyCertificateBidirectional", "양방향 인증서 검증", "상호 인증"),
        ("0x03", "proofOfOwnership", "소유권 증명 제출", "키 보유 증명"),
        ("0x04", "transmitCertificate", "인증서 전달", "상대가 검증하게"),
        ("0x05", "requestChallenge", "챌린지 요청", "소유권 검증 준비"),
        ("0x06", "verifyProofOfOwnershipUnidirectional", "단방향 소유권 검증",
         "단방향 proof 결과를 ECU가 검증하게 하려고"),
        ("0x07", "verifyProofOfOwnershipBidirectional", "양방향 소유권 검증",
         "테스터·ECU 양쪽 소유권을 상호 확인하려고"),
        ("0x08", "authenticationConfiguration", "인증 설정", "역할/권한 구성"),
    ]:
        add("0x29", sub, name, "Y", "Y", "판본의존", when_use, why_use, "")

    for sub, name, when_use, why_use in [
        ("0x01", "sendAtSlowRate", "천천히 주기 전송", "대역을 아끼며 느린 주기로 관측하려고"),
        ("0x02", "sendAtMediumRate", "중간 주기", "느림과 빠름 사이의 균형으로 관측하려고"),
        ("0x03", "sendAtFastRate", "빠른 주기 관측", "변화가 빠른 신호를 고해상도로 보려고"),
        ("0x04", "stopSending", "주기 전송 중지", "더 이상 주기 보고가 필요 없을 때 부하를 끊으려고"),
    ]:
        add("0x2A", sub, name, "N", "Y", "periodic DID(s)", when_use, why_use, "")

    add("0x2C", "0x01", "defineByIdentifier", "Y", "Y", "dynamicDID + source DIDs",
        "기존 DID들을 묶을 때", "한 번에 여러 신호 관측", "")
    add("0x2C", "0x02", "defineByMemoryAddress", "Y", "Y", "dynamicDID + address",
        "메모리 조각을 동적 DID로", "DID 없는 영역 관측", "")
    add("0x2C", "0x03", "clearDynamicallyDefinedDataIdentifier", "Y", "Y", "dynamicDID",
        "동적 DID 삭제", "임시 정의 정리", "")

    add("0x2F", "0x00", "returnControlToECU", "N", "Y", "DID",
        "강제 제어 종료", "ECU 정상 제어로 반환", "")
    add("0x2F", "0x01", "resetToDefault", "N", "Y", "DID",
        "IO 기본값 복귀", "시험 설정 초기", "")
    add("0x2F", "0x02", "freezeCurrentState", "N", "Y", "DID",
        "현재 출력 상태 고정", "재현·측정 안정화", "")
    add("0x2F", "0x03", "shortTermAdjustment", "N", "Y", "DID + controlState",
        "단기 강제 값 인가", "액추에이터/대체값 시험", "")

    add("0x31", "0x01", "startRoutine", "Y", "Y", "routineId + option",
        "루틴 실행 시작", "셀프테스트·학습 등 절차 수행", "")
    add("0x31", "0x02", "stopRoutine", "Y", "Y", "routineId + option",
        "실행 중 루틴 중지", "중단·타임아웃 대응", "")
    add("0x31", "0x03", "requestRoutineResults", "Y", "Y", "routineId",
        "결과/상태 조회", "비동기 루틴 완료 확인", "")

    for sub, name, when_use, why_use in [
        ("0x01", "addFile", "파일 추가", "새 파일을 ECU 파일시스템에 올리려고"),
        ("0x02", "deleteFile", "파일 삭제", "불필요·손상 파일을 지우려고"),
        ("0x03", "replaceFile", "파일 교체", "기존 파일을 새 내용으로 덮어쓰려고"),
        ("0x04", "readFile", "파일 읽기", "저장된 로그·맵 내용을 가져오려고"),
        ("0x05", "readDir", "디렉터리 목록", "어떤 파일이 있는지 목록을 보려고"),
        ("0x06", "resumeFile", "전송 재개", "끊긴 파일 전송을 이어서 받거나 보내려고"),
    ]:
        add("0x38", sub, name, "N", "Y", "path/params", when_use, why_use, "")

    add("0x3E", "0x00", "zeroSubFunction", "Y", "N", "없음",
        "세션 유지 하트비트", "타임아웃으로 세션 떨어짐 방지",
        "보통 3E 80(suppress)으로 보냄")

    add("0x83", "0x01", "readExtendedTimingParameterSet", "Y", "Y", "없음",
        "확장 타이밍 세트 확인", "현재 가능 세트 파악", "")
    add("0x83", "0x02", "setTimingParametersToDefaultValues", "Y", "Y", "없음",
        "타이밍 기본값 복귀", "시험 후 원복", "")
    add("0x83", "0x03", "readCurrentlyActiveTimingParameters", "Y", "Y", "없음",
        "현재 활성 타이밍 확인", "문제 재현/튜닝", "")
    add("0x83", "0x04", "setTimingParametersToGivenValues", "Y", "Y", "record",
        "원하는 타이밍으로 설정", "느린 링크 등 맞춤", "")

    add("0x85", "0x01", "on", "Y", "Y", "없음/DTCGroup",
        "DTC 기록 재개", "시험 종료 후 정상 감시", "")
    add("0x85", "0x02", "off", "Y", "Y", "없음/DTCGroup",
        "시험 중 DTC 억제", "의도치 않은 코드 방지", "끝나면 on으로 원복")

    for sub, name, suppress, when_use, why_use in [
        ("0x00", "stopResponseOnEvent", "Y", "이벤트 응답 중지", "구독 종료"),
        ("0x01", "onDTCStatusChange", "Y", "DTC 상태 변화 감시", "폴링 없이 고장 변화 감지"),
        ("0x02", "onTimerInterrupt", "Y", "주기 타이머 이벤트", "시간 기반 보고"),
        ("0x03", "onChangeOfDataIdentifier", "Y", "DID 값 변화 시", "신호 변화 트리거"),
        ("0x04", "reportActivatedEvents", "Y", "활성 이벤트 목록", "현재 구독 확인"),
        ("0x05", "startResponseOnEvent", "Y", "이벤트 응답 시작", "구독 활성화"),
        ("0x06", "clearResponseOnEvent", "Y", "이벤트 설정 클리어", "정리"),
        ("0x07", "onComparisonOfValues", "Y", "비교 조건 만족 시", "임계값 감시"),
    ]:
        add("0x86", sub, name, suppress, "Y", "event params", when_use, why_use, "")

    add("0x87", "0x01", "verifyModeTransitionWithFixedParameter", "Y", "Y", "modeId",
        "고정 파라미터로 모드 전환 가능 여부 확인", "실제 전환 전 검증", "")
    add("0x87", "0x02", "verifyModeTransitionWithSpecificParameter", "Y", "Y", "modeId+record",
        "특정 파라미터로 검증", "세밀한 링크 설정", "")
    add("0x87", "0x03", "transitionMode", "Y", "Y", "modeId",
        "검증 후 실제 모드 전환", "고속 링크 등으로 전환", "")

    return rows


SUBFUNCTIONS = build_subfunctions()

# code, name, meaning, typical, when_appears, what_to_check, notes
NRC = [
    ["0x10", "generalReject", "일반 거부", "여러 서비스",
     "더 구체적 NRC 없이 거절될 때", "요청 전체·ECU 상태 로그를 넓게 확인", ""],
    ["0x11", "serviceNotSupported", "서비스 미지원", "모든 요청",
     "ECU가 해당 SID를 모를 때", "SID 오타·잘못된 ECU 대상·게이트웨이 라우팅", ""],
    ["0x12", "subFunctionNotSupported", "서브펑션 미지원", "서브펑션 서비스",
     "SID는 맞지만 서브펑션이 없을 때", "SubFunctions 표·OEM 지원 목록", ""],
    ["0x13", "incorrectMessageLengthOrInvalidFormat", "길이/포맷 오류", "모든 요청",
     "바이트 수·필드가 깨졌을 때", "로그 HEX 길이, DID 2바이트 여부, 잘린 프레임", "UART 프레이밍 점검"],
    ["0x14", "responseTooLong", "응답이 너무 김", "0x19,0x22 등",
     "응답이 버퍼/한도를 넘을 때", "요청 범위 축소, 다중 DID 줄이기", ""],
    ["0x21", "busyRepeatRequest", "ECU busy", "여러 서비스",
     "처리 중이라 당장 못 할 때", "잠시 후 재시도, 동시 요청 줄이기", ""],
    ["0x22", "conditionsNotCorrect", "선행 조건 불충족", "0x10,0x11,0x28,0x31 등",
     "차속·전압·엔진·세션 등 조건 미달", "차량/벤치 상태, 직전 세션, 안전 인터락", "시연·실무 모두 흔함"],
    ["0x24", "requestSequenceError", "요청 순서 오류", "0x27,0x34~0x37",
     "시드 없이 키, 다운로드 없이 36 등", "시퀀스 다이어그램대로 다시", ""],
    ["0x25", "noResponseFromSubnetComponent", "서브넷 무응답", "게이트웨이",
     "뒤쪽 ECU가 안 답할 때", "대상 주소·게이트웨이·전원", ""],
    ["0x26", "failurePreventsExecutionOfRequestedAction", "고장으로 실행 불가", "IO/루틴",
     "이미 고장이 실행을 막을 때", "관련 DTC·하드웨어 상태", ""],
    ["0x31", "requestOutOfRange", "요청 범위 초과", "0x22,0x2E,0x31,0x14",
     "없는 DID/루틴/주소/그룹", "ID 유효성, OEM 지원 목록", "시연 단골"],
    ["0x33", "securityAccessDenied", "보안 접근 거부", "보안 필요 서비스",
     "잠긴 채 쓰기/루틴 등", "0x27/0x29로 해제했는지", ""],
    ["0x34", "authenticationRequired", "인증 필요", "0x29 등",
     "인증 없이 보호 자원 접근", "Authentication 흐름", ""],
    ["0x35", "invalidKey", "키 불일치", "0x27",
     "보낸 키가 틀릴 때", "시드·알고리즘·엔디안·레벨 짝", ""],
    ["0x36", "exceedNumberOfAttempts", "시도 횟수 초과", "0x27",
     "틀린 키를 너무 많이 넣음", "대기 시간(0x37) 후 재시도", ""],
    ["0x37", "requiredTimeDelayNotExpired", "지연 미경과", "0x27",
     "잠금 페널티 시간 중", "딜레이 후 다시 시드부터", ""],
    ["0x38", "secureDataTransmissionRequired", "보안 전송 필요", "보안 컨텍스트",
     "평문으로 보내면 안 될 때", "0x84 등 보안 채널", ""],
    ["0x39", "secureDataTransmissionNotAllowed", "보안 전송 불가", "보안 컨텍스트",
     "지금 상태로는 보안 전송 거부", "세션/인증 상태", ""],
    ["0x3A", "secureDataVerificationFailed", "보안 데이터 검증 실패", "0x84",
     "무결성/인증 실패", "키·카운터·프레임", ""],
    ["0x50", "certificateVerificationFailedInvalidTimePeriod", "인증서 기간 오류", "0x29",
     "인증서 유효기간 문제", "ECU/테스터 시간, 인증서", ""],
    ["0x51", "certificateVerificationFailedInvalidSignature", "인증서 서명 오류", "0x29",
     "서명 검증 실패", "인증서·루트 신뢰", ""],
    ["0x52", "certificateVerificationFailedInvalidChainOfTrust", "체인 오류", "0x29",
     "신뢰 체인 깨짐", "중간 CA 포함 여부", ""],
    ["0x53", "certificateVerificationFailedInvalidType", "타입 오류", "0x29",
     "인증서 종류 불일치", "역할/타입", ""],
    ["0x54", "certificateVerificationFailedInvalidFormat", "포맷 오류", "0x29",
     "인증서 형식 깨짐", "인코딩", ""],
    ["0x55", "certificateVerificationFailedInvalidContent", "내용 오류", "0x29",
     "필드 내용 부적합", "정책·프로파일", ""],
    ["0x56", "certificateVerificationFailedInvalidScope", "범위 오류", "0x29",
     "허용 범위 밖", "권한/스코프", ""],
    ["0x57", "certificateVerificationFailedInvalidCertificate", "인증서 무효", "0x29",
     "종합적으로 무효", "재발급·교체", ""],
    ["0x58", "ownershipVerificationFailed", "소유권 검증 실패", "0x29",
     "proof 실패", "개인키·챌린지 응답", ""],
    ["0x59", "challengeCalculationFailed", "챌린지 계산 실패", "0x29",
     "챌린지 생성/처리 실패", "ECU 상태·난수원", ""],
    ["0x5A", "settingAccessRightsFailed", "권한 설정 실패", "0x29",
     "권한 부여 단계 실패", "역할 매핑", ""],
    ["0x5B", "sessionKeyCreationOrDerivationFailed", "세션키 실패", "0x29",
     "세션키 파생 실패", "알고리즘·파라미터", ""],
    ["0x5C", "configurationDataUsageFailed", "설정 데이터 실패", "0x29",
     "구성 데이터 사용 불가", "설정 버전", ""],
    ["0x5D", "deAuthenticationFailed", "인증 해제 실패", "0x29",
     "로그아웃 실패", "상태머신", ""],
    ["0x70", "uploadDownloadNotAccepted", "업/다운로드 거부", "0x34/35",
     "전송 시작 거절", "세션·보안·주소·크기", ""],
    ["0x71", "transferDataSuspended", "전송 일시 중단", "0x36",
     "전송 중 중단", "재개 조건·오류 원인", ""],
    ["0x72", "generalProgrammingFailure", "프로그래밍 실패", "플래시",
     "플래시/검증 실패", "전원·이미지·체크섬", ""],
    ["0x73", "wrongBlockSequenceCounter", "블록 시퀀스 오류", "0x36",
     "카운터 불일치", "36 시퀀스 재동기화", ""],
    ["0x78", "requestCorrectlyReceivedResponsePending", "응답 대기(중간)", "긴 처리",
     "처리는 시작됐고 최종 응답 전", "최종 긍정/부정까지 대기. 반복만 길고 끝 없으면 타임아웃", "실패가 아닐 수 있음"],
    ["0x7E", "subFunctionNotSupportedInActiveSession", "현 세션에서 서브펑션 불가", "서브펑션 서비스",
     "SID는 세션 OK, 서브펑션만 불가", "0x10으로 세션 변경", ""],
    ["0x7F", "serviceNotSupportedInActiveSession", "현 세션에서 서비스 불가", "모든 요청",
     "지금 세션에선 그 SID 금지", "extended/programming으로 전환했는지", ""],
    ["0x81", "rpmTooHigh", "RPM 과다", "안전 제어", "엔진 회전 과다", "RPM 낮추고 재시도", "차량 상태"],
    ["0x82", "rpmTooLow", "RPM 과소", "안전 제어", "회전 부족", "조건 맞춘 뒤 재시도", ""],
    ["0x83", "engineIsRunning", "엔진 가동 중", "프로그래밍 등", "엔진 켜진 채 금지 동작", "키/엔진 상태", ""],
    ["0x84", "engineIsNotRunning", "엔진 정지", "특정 루틴", "엔진 켜야 하는 시험", "시동 후 재시도", ""],
    ["0x85", "engineRunTimeTooLow", "가동시간 부족", "특정 조건", "워밍업 부족", "대기 후 재시도", ""],
    ["0x86", "temperatureTooHigh", "온도 과다", "특정 조건", "과열", "냉각 후", ""],
    ["0x87", "temperatureTooLow", "온도 과소", "특정 조건", "저온", "조건 충족 후", ""],
    ["0x88", "vehicleSpeedTooHigh", "차속 과다", "리셋/제어", "움직이는 차에서 위험 동작", "정지 후", ""],
    ["0x89", "vehicleSpeedTooLow", "차속 과소", "특정 조건", "속도 조건 미달", "조건 맞추기", ""],
    ["0x8A", "throttlePedalTooHigh", "페달 과다", "특정 조건", "페달/스로틀 큼", "입력 줄이기", ""],
    ["0x8B", "throttlePedalTooLow", "페달 과소", "특정 조건", "페달/스로틀 작음", "입력 늘리기", ""],
    ["0x8C", "transmissionRangeNotInNeutral", "중립 아님", "특정 조건", "변속 위치", "N단 확인", ""],
    ["0x8D", "transmissionRangeNotInGear", "기어 아님", "특정 조건", "변속 위치", "요구 기어 확인", ""],
    ["0x8E", "ISOSAEReserved", "ISO/SAE 예약", "-", "표준상 예약 코드", "0x8F(brakeSwitchNotClosed)와 혼동하지 말 것", "ISO 14229-1"],
    ["0x8F", "brakeSwitchNotClosed", "브레이크 스위치 미닫힘", "특정 조건", "브레이크 밟기 요구", "브레이크 스위치", ""],
    ["0x90", "shifterLeverNotInPark", "P단 아님", "특정 조건", "주차 단 필요", "P 확인", ""],
    ["0x91", "torqueConverterClutchLocked", "토크컨버터 잠김", "특정 조건", "클러치 상태", "조건 해제", ""],
    ["0x92", "voltageTooHigh", "전압 과다", "프로그래밍 등", "전원 과전압", "전원 공급 점검", ""],
    ["0x93", "voltageTooLow", "전압 과소", "프로그래밍 등", "전원 저전압", "안정 전원 확보", ""],
]

# sid, min_len, layout, example_hex, when, id_type, id_offset, is_variable_length
REQUEST_LAYOUT = [
    ["0x10", 2, "SID | subFunction", "10 03", "세션 바꾸기 직전/직후 로그에서 확인", "NONE", -1, "N"],
    ["0x11", 2, "SID | subFunction", "11 01", "리셋 시도 프레임", "NONE", -1, "N"],
    ["0x14", 4, "SID | groupOfDTC[3]", "14 FF FF FF", "클리어 범위 확인", "DTC_GROUP", 1, "N"],
    ["0x19", 3, "SID | subFunction | statusMask", "19 02 FF", "서브펑션마다 길이 다름", "NONE", -1, "Y"],
    ["0x22", 3, "SID | DID[2] | [additional DID[2]...]", "22 F1 90",
     "DID 해석의 기본. 응답 예시는 시연용 축약(실차 VIN 0xF190=17바이트 ASCII; 1바이트 예 0xF186)",
     "DID", 1, "Y"],
    ["0x23", 4, "SID | ALFI | address[n] | size[m]", "23 24 12 34 56 78 00 10",
     "주소 덤프. ALFI high nibble=size length, low nibble=address length(예시 0x24 → addr4+size2)", "NONE", -1, "Y"],
    ["0x24", 3, "SID | DID[2]", "24 F1 90", "스케일 조회", "DID", 1, "N"],
    ["0x27", 2, "SID | accessType | [securityAccessDataRecord or key]", "27 01",
     "홀수=requestSeed, 짝수=sendKey. accessType의 권한 의미는 OEM 정의", "NONE", -1, "Y"],
    ["0x28", 3, "SID | control | commType", "28 00 01", "통신 제어", "NONE", -1, "N"],
    ["0x29", 2, "SID | sub | [auth]", "29 05", "인증 흐름", "NONE", -1, "Y"],
    ["0x2A", 3, "SID | transmissionMode | periodicDataIdentifier[1]...", "2A 01 F1",
     "periodicDataIdentifier는 1바이트이며 2바이트 DID와 동일 필드가 아님",
     "NONE", -1, "Y"],
    ["0x2C", 6, "SID | sub | dynDID | srcDID", "2C 01 F2 00 F1 90", "동적 DID 정의", "DID", 2, "Y"],
    ["0x2E", 4, "SID | DID[2] | data[1..n]", "2E F1 90 41 42 43",
     "쓰기. 예시 data는 시연용 축약(실차 VIN은 17바이트 ASCII)", "DID", 1, "Y"],
    ["0x2F", 4, "SID | DID[2] | ioControlParameter | [controlState...]", "2F F1 90 00", "IO 제어(returnControl)", "DID", 1, "Y"],
    ["0x31", 4, "SID | sub | routineId[2]", "31 01 02 03", "루틴 시작", "ROUTINE_ID", 2, "Y"],
    ["0x34", 5, "SID | dataFormat | ALFI | address[n] | size[m]", "34 00 44 12 34 56 78 00 00 10 00", "다운로드 시작(ALFI 의존 가변 길이)", "NONE", -1, "Y"],
    ["0x35", 5, "SID | dataFormat | ALFI | address[n] | size[m]", "35 00 44 12 34 56 78 00 00 10 00", "업로드 시작(ALFI 의존 가변 길이)", "NONE", -1, "Y"],
    ["0x36", 2, "SID | blockSequenceCounter | [transferRequestParameterRecord]", "36 01 AA BB CC", "블록 전송", "NONE", -1, "Y"],
    ["0x37", 1, "SID", "37", "전송 종료", "NONE", -1, "N"],
    ["0x38", 3, "SID | modeOfOperation | filePathAndNameLengthFormatIdentifier | operation-dependent fields",
     "38 01 01 04 2F 6C 6F 67", "파일 전송. mode와 length-format에 따라 뒤 필드가 달라짐", "NONE", -1, "Y"],
    ["0x3D", 5, "SID | ALFI | address[n] | size[m] | data", "3D 14 12 34 56 78 02 AB CD", "메모리 쓰기(ALFI 의존)", "NONE", -1, "Y"],
    ["0x3E", 2, "SID | subFunction", "3E 80", "세션 유지(suppress)", "NONE", -1, "N"],
    ["0x83", 2, "SID | accessType", "83 03", "타이밍", "NONE", -1, "Y"],
    ["0x84", 5, "SID | secureRecord", "84 01 02 03 04", "보안 전송", "NONE", -1, "Y"],
    ["0x85", 2, "SID | onOff", "85 02", "DTC 설정 off", "NONE", -1, "Y"],
    ["0x86", 2, "SID | eventType", "86 05", "이벤트 시작", "NONE", -1, "Y"],
    ["0x87", 3, "SID | type | modeId", "87 01 01", "링크 모드 검증", "NONE", -1, "Y"],
]

# kind, sid_or_marker, layout, example_hex, when, id_type, id_offset, data_offset, notes
RESPONSE_LAYOUT = [
    ["Positive", "0x50", "POS | sub | P2server | P2*server", "50 03 00 32 01 F4",
     "세션 전환 성공", "NONE", -1, 2, "data=P2 timing params"],
    ["Positive", "0x51", "POS | sub", "51 01", "리셋 수락", "NONE", -1, -1, ""],
    ["Positive", "0x54", "POS", "54", "클리어 성공", "NONE", -1, -1, ""],
    ["Positive", "0x59", "POS | sub | availabilityMask | DTC+status", "59 02 FF 01 23 45 2F",
     "DTC 목록", "DTC_STATUS", 2, 3, "id_offset=availabilityMask; data=DTC records"],
    ["Positive", "0x62", "POS | DID | data", "62 F1 90 57 58 59 5A",
     "DID 읽기 성공. data는 시연용 축약(실차 VIN 0xF190=17바이트 ASCII; 1바이트 예 0xF186)",
     "DID", 1, 3, ""],
    ["Positive", "0x63", "POS | data", "63 11 22 33 44", "메모리 읽기", "NONE", -1, 1, ""],
    ["Positive", "0x64", "POS | DID | scalingByte", "64 F1 90 01", "스케일", "DID", 1, 3, ""],
    ["Positive", "0x67", "POS | type | seed", "67 01 AA BB CC DD", "시드 응답", "NONE", -1, 2, "type 다음이 seed/ACK"],
    ["Positive", "0x68", "POS | controlType", "68 00", "통신제어 ACK", "NONE", -1, -1, ""],
    ["Positive", "0x69", "POS | sub | authReturn", "69 05 00", "인증 응답", "NONE", -1, 2, ""],
    ["Positive", "0x6A", "POS | transmissionMode", "6A 01", "주기전송 ACK", "NONE", -1, -1, ""],
    ["Positive", "0x6C", "POS | sub | dynamicDID", "6C 01 F2 00", "동적 DID ACK", "DID", 2, -1, ""],
    ["Positive", "0x6E", "POS | DID", "6E F1 90", "쓰기 성공", "DID", 1, -1, ""],
    ["Positive", "0x6F", "POS | DID | ioControlParameter", "6F F1 90 00", "IO ACK", "DID", 1, 3, ""],
    ["Positive", "0x71", "POS | sub | routineId | status", "71 01 02 03 00",
     "루틴 응답", "ROUTINE_ID", 2, 4, ""],
    ["Positive", "0x74", "POS | lengthFormat | maxNumberOfBlockLength", "74 20 0F FF",
     "다운로드 수락", "NONE", -1, 1, ""],
    ["Positive", "0x75", "POS | lengthFormat | maxNumberOfBlockLength", "75 20 0F FF",
     "업로드 수락", "NONE", -1, 1, ""],
    ["Positive", "0x76", "POS | counter", "76 01", "블록 ACK", "NONE", -1, -1, ""],
    ["Positive", "0x77", "POS", "77", "전송 종료 ACK", "NONE", -1, -1, ""],
    ["Positive", "0x78", "POS | modeOfOperation | lengthFormat | maxBlock", "78 01 20 0F FF",
     "파일전송 긍정(요청 0x38+0x40)", "NONE", -1, 2, "Positive 0x78 ≠ NRC 0x78"],
    ["Positive", "0x7D", "POS", "7D", "메모리 쓰기 긍정", "NONE", -1, -1, ""],
    ["Positive", "0x7E", "POS | sub", "7E 00", "TesterPresent 긍정(suppress 아닐 때)", "NONE", -1, -1, ""],
    ["Positive", "0xC3", "POS | accessType | record", "C3 03 00 32 01 F4", "타이밍", "NONE", -1, 2, ""],
    ["Positive", "0xC4", "POS | secureRecord", "C4 01 02 03 04", "보안전송", "NONE", -1, 1, ""],
    ["Positive", "0xC5", "POS | settingType", "C5 02", "DTC setting", "NONE", -1, -1, ""],
    ["Positive", "0xC6", "POS | eventType", "C6 05", "ROE", "NONE", -1, -1, ""],
    ["Positive", "0xC7", "POS | type | modeId", "C7 01 01", "링크", "NONE", -1, 2, ""],
    ["Negative", "0x7F", "7F | reqSID | NRC", "7F 22 31",
     "거절. NRC 시트 조회", "NONE", -1, -1, "일반 부정응답"],
    ["NegativePending", "0x7F", "7F | reqSID | 78", "7F 31 78",
     "대기 중. 최종 응답 더 기다림", "NONE", -1, -1,
     "sid_or_marker는 항상 0x7F. NRC=0x78(requestCorrectlyReceivedResponsePending)"],
]

# bit, mask, name_en, meaning_ko, check_point_ko
DTC_STATUS_BITS = [
    ["0", "0x01", "testFailed",
     "이번 동작 주기에서 시험 실패(현재 실패)",
     "방금/지금 조건에서 고장이 잡혔는지. 간헐이면 재현 직후 확인"],
    ["1", "0x02", "testFailedThisOperationCycle",
     "이번 동작 주기 중 한 번이라도 시험 실패",
     "키 사이클 내 과거 실패 여부. 지금 정상(bit0=0)이어도 남을 수 있음"],
    ["2", "0x04", "pendingDTC",
     "펜딩(확정 전) DTC",
     "확정(confirmed) 전 단계. 재현·클리어 정책과 함께 볼 것"],
    ["3", "0x08", "confirmedDTC",
     "확정 DTC(저장됨)",
     "정비·보고에서 가장 흔히 보는 ‘진짜 고장 코드’ 여부"],
    ["4", "0x10", "testNotCompletedSinceLastClear",
     "마지막 클리어 이후 해당 시험 미완료",
     "주행/준비도 부족으로 아직 시험을 못 끝냈는지"],
    ["5", "0x20", "testFailedSinceLastClear",
     "마지막 클리어 이후 한 번이라도 실패",
     "클리어 후 재발 여부. 간헐 고장 추적"],
    ["6", "0x40", "testNotCompletedThisOperationCycle",
     "이번 동작 주기에서 해당 시험 미완료",
     "이번 키 사이클에 아직 시험 기회가 없었는지"],
    ["7", "0x80", "warningIndicatorRequested",
     "경고등(MIL 등) 점등 요청",
     "계기판 경고와 DTC 상태 불일치 시 점검"],
]

# type, id, name, related_sid, data_type, byte_length, scale, unit, normal_min, normal_max,
# access, write_min, write_max, supported_profiles, speed_basis, source_ref,
# example, when, why, notes
OEM = [
    ["DID", "0xF186", "activeDiagnosticSessionDataIdentifier", "0x22", "UINT8", 1,
     "1", "", "", "", "READ", "", "", "ALL", "", "UDS convention", "22 F1 86",
     "지금 세션이 뭔지 읽을 때", "세션 꼬임 디버깅", "관례 DID"],
    ["DID", "0xF187", "vehicleManufacturerSparePartNumber", "0x22", "ASCII", 10,
     "1", "", "", "", "READ", "", "", "ALL", "", "UDS convention", "22 F1 87",
     "부품번호 확인", "부품 식별", "관례"],
    ["DID", "0xF18A", "systemSupplierIdentifier", "0x22", "ASCII", 10,
     "1", "", "", "", "READ", "", "", "ALL", "", "UDS convention", "22 F1 8A",
     "공급사 식별", "SW 출처 확인", "관례"],
    ["DID", "0xF18C", "ECUSerialNumberDataIdentifier", "0x22", "ASCII", 16,
     "1", "", "", "", "READ", "", "", "ALL", "", "UDS convention", "22 F1 8C",
     "ECU 일련번호", "개체 추적", "관례"],
    ["DID", "0xF190", "VINDataIdentifier", "0x22/0x2E", "ASCII", 17,
     "1", "", "", "", "READ", "", "", "ALL", "", "UDS convention", "22 F1 90",
     "차대번호 확인", "차량 식별의 기본",
     "관례·시연 단골. 본 프로젝트 WriteData 대상 아님"],
    ["DID", "0xF191", "vehicleManufacturerECUHardwareNumber", "0x22", "ASCII", 10,
     "1", "", "", "", "READ", "", "", "ALL", "", "UDS convention", "22 F1 91",
     "HW 번호", "하드웨어 리비전", "관례"],
    ["DID", "0xF195", "systemSupplierECUSoftwareNumber", "0x22", "ASCII", 10,
     "1", "", "", "", "READ", "", "", "ALL", "", "UDS convention", "22 F1 95",
     "공급사 SW 번호", "소프트웨어 식별", "관례"],
    ["DID", "0xF189", "vehicleManufacturerECUSoftwareNumber", "0x22", "ASCII", 10,
     "1", "", "", "", "READ", "", "", "ALL", "", "UDS convention", "22 F1 89",
     "OEM SW 번호", "소프트웨어 식별", "관례"],
    ["DID", "0xF1A0", "(OEM write example)", "0x2E", "HEX", 3,
     "1", "", "", "", "NONE", "", "", "NONE", "", "generic example", "2E F1 A0 01 02 03",
     "쓰기 예제 자리", "실차 DID는 OEM 문서", "예시·제품 실행 범위 밖"],
    # 공통 정규화 Read DID — 프로젝트 전용, 제조사 공식 UDS DID 아님
    ["DID", "0x1001", "inputVoltage", "0x22", "UINT16", 2,
     "0.1", "V", "60.0", "84.0", "READ", "", "", "BOTH", "", "FarDriver manual; VESC telemetry",
     "22 10 01", "배터리 입력 전압 확인", "저전압·과전압 진단", "교육용 판정 범위"],
    ["DID", "0x1002", "motorCurrent", "0x22", "INT32", 4,
     "0.01", "A", "-100.0", "100.0", "READ", "", "", "BOTH", "", "FarDriver current; VESC avg_motor_current",
     "22 10 02", "모터 전류 확인", "과전류·토크 부하 진단", "Port가 제조사 raw scale을 정규화"],
    ["DID", "0x1003", "inputCurrent", "0x22", "INT32", 4,
     "0.01", "A", "-60.0", "60.0", "READ", "", "", "BOTH", "", "FarDriver line current; VESC avg_input_current",
     "22 10 03", "배터리 입력 전류 확인", "전력·회생 상태 진단", "교육용 판정 범위"],
    ["DID", "0x1004", "controllerTemperature", "0x22", "INT16", 2,
     "0.1", "C", "-20.0", "85.0", "READ", "", "", "BOTH", "", "FarDriver controller temp; VESC temp_mosfet",
     "22 10 04", "컨트롤러 온도 확인", "열 제한·보호 진단", "교육용 판정 범위"],
    ["DID", "0x1005", "motorTemperature", "0x22", "INT16", 2,
     "0.1", "C", "-20.0", "85.0", "READ", "", "", "BOTH", "", "FarDriver motor temp; VESC temp_motor",
     "22 10 05", "모터 온도 확인", "모터 열보호 진단", "센서 미구성 시 unavailable 가능"],
    ["DID", "0x1006", "motorSpeed", "0x22", "INT32", 4,
     "1", "RPM", "-150000", "150000", "READ", "", "", "BOTH", "PROFILE", "FarDriver RPM; VESC ERPM",
     "22 10 06", "회전 속도 확인", "정지·속도 이상 진단", "ND=RPM, Flipsky=ERPM; speedBasis 필수"],
    ["DID", "0x1007", "faultCode", "0x22", "UINT16", 2,
     "1", "", "0", "0", "READ", "", "", "BOTH", "", "FarDriver alarm; VESC mc_fault_code",
     "22 10 07", "활성 fault 확인", "보호정지 원인 분류", "코드 의미는 profile별 표"],
    ["DID", "0x1008", "dutyOrThrottle", "0x22", "INT16", 2,
     "0.001", "ratio", "-1.0", "1.0", "READ", "", "", "BOTH", "PROFILE", "FarDriver throttle; VESC duty_cycle",
     "22 10 08", "명령 비율 확인", "입력 명령과 출력 비교", "ND=throttle, Flipsky=duty"],
    # ND72530 전용 Read DID
    ["DID", "0x1101", "phaseCurrent", "0x22", "INT16", 2,
     "0.1", "A", "-530.0", "530.0", "READ", "", "", "FARDRIVER_ND72530", "", "FarDriver manual",
     "22 11 01", "상전류 확인", "모터 부하 진단", "모델명 기반 한계로 사용하지 않음"],
    ["DID", "0x1102", "torque", "0x22", "INT16", 2,
     "0.1", "Nm", "", "", "READ", "", "", "FARDRIVER_ND72530", "", "FarDriver manual",
     "22 11 02", "토크 확인", "부하 상태 비교", "교육용"],
    ["DID", "0x1103", "throttleVoltage", "0x22", "UINT16", 2,
     "0.01", "V", "0", "5.0", "READ", "", "", "FARDRIVER_ND72530", "", "FarDriver manual",
     "22 11 03", "스로틀 전압 확인", "입력 센서 진단", "교육용"],
    ["DID", "0x1104", "batteryLevel", "0x22", "UINT8", 1,
     "1", "%", "0", "100", "READ", "", "", "FARDRIVER_ND72530", "", "FarDriver manual",
     "22 11 04", "배터리 레벨 확인", "전압과 표시값 비교", "교육용"],
    ["DID", "0x1105", "hallState", "0x22", "UINT8", 1,
     "1", "", "0", "7", "READ", "", "", "FARDRIVER_ND72530", "", "FarDriver manual",
     "22 11 05", "Hall 상태 확인", "Hall fault 진단", "enum"],
    ["DID", "0x1106", "gearState", "0x22", "UINT8", 1,
     "1", "", "0", "3", "READ", "", "", "FARDRIVER_ND72530", "", "FarDriver manual",
     "22 11 06", "기어 상태 확인", "D/R/N 상태 확인", "enum"],
    # Flipsky 75100(VESC) 전용 Read DID
    ["DID", "0x1201", "electricalRpm", "0x22", "INT32", 4,
     "1", "ERPM", "-150000", "150000", "READ", "", "", "FLIPSKY_75100_VESC", "ERPM", "VESC COMM_GET_VALUES",
     "22 12 01", "전기적 RPM 확인", "회전 상태 진단", "기계 RPM 변환에는 pole count 필요"],
    ["DID", "0x1202", "ampHours", "0x22", "INT32", 4,
     "0.0001", "Ah", "", "", "READ", "", "", "FLIPSKY_75100_VESC", "", "VESC COMM_GET_VALUES",
     "22 12 02", "누적 Ah 확인", "에너지 사용량 확인", "교육용"],
    ["DID", "0x1203", "wattHours", "0x22", "INT32", 4,
     "0.0001", "Wh", "", "", "READ", "", "", "FLIPSKY_75100_VESC", "", "VESC COMM_GET_VALUES",
     "22 12 03", "누적 Wh 확인", "에너지 사용량 확인", "교육용"],
    ["DID", "0x1204", "tachometer", "0x22", "INT32", 4,
     "1", "steps", "", "", "READ", "", "", "FLIPSKY_75100_VESC", "", "VESC COMM_GET_VALUES",
     "22 12 04", "tachometer 확인", "회전 누적량 확인", "교육용"],
    ["DID", "0x1205", "controllerId", "0x22", "UINT8", 1,
     "1", "", "0", "254", "READ", "", "", "FLIPSKY_75100_VESC", "", "VESC COMM_GET_VALUES",
     "22 12 05", "VESC controller ID 확인", "노드 식별", "교육용"],
    ["DID", "0x1206", "vescStatus", "0x22", "UINT8", 1,
     "1", "", "0", "255", "READ", "", "", "FLIPSKY_75100_VESC", "", "VESC telemetry",
     "22 12 06", "VESC 상태 확인", "상태 비트 확인", "교육용"],
    # 공통 정규화 Write DID — File Port 산출물 전용
    ["DID", "0x2001", "enableRequest", "0x2E", "UINT8", 1,
     "1", "", "", "", "WRITE", "0", "1", "BOTH", "", "project normalized control",
     "2E 20 01 01", "enable 요청 생성", "제어 흐름 시연", "실제 ECU 변경 없음"],
    ["DID", "0x2002", "targetRpm", "0x2E", "INT32", 4,
     "1", "RPM", "", "", "WRITE", "-20000", "20000", "BOTH", "PROFILE", "project normalized control",
     "2E 20 02 00 00 0B B8", "목표 RPM/ERPM 요청", "속도 제어 프레임 시연", "profile speedBasis 필수"],
    ["DID", "0x2003", "targetMotorCurrent", "0x2E", "INT32", 4,
     "0.01", "A", "", "", "WRITE", "-100", "100", "BOTH", "", "project normalized control",
     "2E 20 03 00 00 07 D0", "목표 모터 전류 요청", "토크 제어 프레임 시연", "시연용 허용 범위"],
    ["DID", "0x2004", "targetBrakeCurrent", "0x2E", "INT32", 4,
     "0.01", "A", "", "", "WRITE", "0", "60", "BOTH", "", "project normalized control",
     "2E 20 04 00 00 03 E8", "회생/제동 전류 요청", "제동 프레임 시연", "실제 안전 한계 아님"],
    ["DID", "0x2005", "targetDuty", "0x2E", "INT16", 2,
     "0.001", "ratio", "", "", "WRITE", "-1", "1", "BOTH", "", "project normalized control",
     "2E 20 05 01 F4", "duty/throttle 요청", "출력 비율 제어 시연", "실제 ECU 변경 없음"],
    ["DID", "0x2006", "directionRequest", "0x2E", "UINT8", 1,
     "1", "", "", "", "WRITE", "0", "2", "BOTH", "", "project normalized control",
     "2E 20 06 01", "방향 요청", "정/역/중립 시연", "0=neutral,1=forward,2=reverse"],
    ["Routine", "0x0203", "(OEM routine example)", "0x31", "HEX", 2,
     "1", "", "", "", "NONE", "", "", "NONE", "", "generic example", "31 01 02 03",
     "루틴 호출 예", "루틴 ID는 OEM", "예시·제품 실행 범위 밖"],
    ["DTC", "0x012345", "(OEM DTC example)", "0x19/0x14", "HEX", 3,
     "1", "", "", "", "READ", "", "", "ALL", "", "generic example", "19 02 FF",
     "DTC 3바이트 예", "실제 코드는 ECU", "예시. 0x19는 로그 해석만"],
]

# symptom_key, keywords_ko, did_id, priority, reason_ko, notes
SYMPTOM_DID_MAP = [
    ["motor_sudden_stop", "급정지|갑자기 정지|모터 멈춤", "0x1001", 1,
     "전원 전압이 하한 아래인지 확인", "UC-02 핵심"],
    ["motor_sudden_stop", "급정지|갑자기 정지|모터 멈춤", "0x1002", 2,
     "모터 전류 급변·과전류 여부 확인", "UC-02 핵심"],
    ["motor_sudden_stop", "급정지|갑자기 정지|모터 멈춤", "0x1003", 3,
     "입력 전류·전원 공급 상태 확인", "UC-02"],
    ["motor_sudden_stop", "급정지|갑자기 정지|모터 멈춤", "0x1004", 4,
     "컨트롤러 과온 여부 확인", "UC-02"],
    ["motor_sudden_stop", "급정지|갑자기 정지|모터 멈춤", "0x1005", 5,
     "모터 과온 여부 확인", "UC-02"],
    ["motor_sudden_stop", "급정지|갑자기 정지|모터 멈춤", "0x1007", 6,
     "프로필별 fault code 확인", "UC-02"],
    ["comm_session", "통신|세션|TesterPresent", "0xF186", 1,
     "현재 진단 세션 확인", "세션 NRC·급정지 직전 통신"],
]

# profile_id, display_name, family, speed_basis, telemetry_reference, did_policy, notes
CONTROLLER_PROFILES = [
    [
        "FARDRIVER_ND72530", "FarDriver ND72530", "FarDriver", "RPM",
        "FarDriver public manual CAN telemetry", "Project educational DID mapping",
        "input voltage/current, phase current, RPM, controller/motor temp, throttle, torque, Hall/gear/fault",
        "Y", 1,
    ],
    [
        "FLIPSKY_75100_VESC", "Flipsky 75100 (VESC)", "VESC", "ERPM",
        "VESC COMM_GET_VALUES and Flipsky 75100 public specs", "Project educational DID mapping",
        "14-84V product family; motor/input current, MOSFET/motor temp, ERPM, duty, energy, fault",
        "Y", 2,
    ],
]

PROFILE_IDS = ("FARDRIVER_ND72530", "FLIPSKY_75100_VESC")
COMMON_READ_DIDS = {f"0x{x:04X}" for x in range(0x1001, 0x1009)}
ND_READ_DIDS = {f"0x{x:04X}" for x in range(0x1101, 0x1107)}
VESC_READ_DIDS = {f"0x{x:04X}" for x in range(0x1201, 0x1207)}
COMMON_WRITE_DIDS = {f"0x{x:04X}" for x in range(0x2001, 0x2007)}
PROJECT_DIDS = COMMON_READ_DIDS | ND_READ_DIDS | VESC_READ_DIDS | COMMON_WRITE_DIDS

# 첫 GUI 수직 슬라이스에서 실제 positive fixture가 있는 profile+DID만 노출한다.
RUNTIME_PROFILE_DIDS = {
    "FARDRIVER_ND72530": {
        "0x1001", "0x1002", "0x1007", "0x1101",
        "0x2001", "0x2002", "0x2003",
    },
    "FLIPSKY_75100_VESC": {
        "0x1001", "0x1008", "0x1201", "0x1205",
        "0x2002", "0x2004", "0x2005", "0x2006",
    },
}


def enrich_oem_extensions() -> list[list]:
    """Append runtime/access metadata without duplicating it in every source row."""
    enriched = []
    oem_fixed_length_ids = {
        "0xF187", "0xF18A", "0xF18C", "0xF189", "0xF191", "0xF195",
    }
    for row in OEM:
        item_id = row[1]
        is_project_did = row[0] == "DID" and item_id in PROJECT_DIDS
        runtime_enabled = (
            "Y"
            if any(item_id in dids for dids in RUNTIME_PROFILE_DIDS.values())
            else "N"
        )
        sort_order = int(item_id[2:], 16) if row[0] == "DID" else ""
        required_session = "EXTENDED" if is_project_did else ""
        security_mechanism = "SECURITY_ACCESS" if is_project_did else "NONE"
        security_level = 1 if is_project_did else ""
        if is_project_did:
            length_policy = "PROJECT_SCHEMA"
        elif item_id in oem_fixed_length_ids:
            length_policy = "OEM_ASSUMPTION"
        else:
            length_policy = "REFERENCE_ONLY"
        enriched.append(
            list(row)
            + [
                runtime_enabled,
                sort_order,
                required_session,
                security_mechanism,
                security_level,
                length_policy,
            ]
        )
    return enriched


def build_did_profile_support() -> list[list]:
    """Normalize profile-to-project-DID support for runtime GUI filtering."""
    rows = []
    for profile_id in PROFILE_IDS:
        supported_read = COMMON_READ_DIDS | (
            ND_READ_DIDS if profile_id == "FARDRIVER_ND72530" else VESC_READ_DIDS
        )
        supported = supported_read | COMMON_WRITE_DIDS
        for did_id in sorted(supported):
            rows.append(
                [
                    profile_id,
                    did_id,
                    "Y" if did_id in supported_read else "N",
                    "Y" if did_id in COMMON_WRITE_DIDS else "N",
                    "Y" if did_id in RUNTIME_PROFILE_DIDS[profile_id] else "N",
                    int(did_id[2:], 16),
                ]
            )
    return rows


SUB_BYTE_OFFSET_1 = {
    "0x10", "0x11", "0x19", "0x27", "0x28", "0x29", "0x2A", "0x2C",
    "0x31", "0x38", "0x3E", "0x83", "0x85", "0x86", "0x87",
}
SUB_BYTE_OFFSET_3 = {"0x2F"}


def enrich_services() -> list[list]:
    """Append example_req/res hex and sub_byte_offset."""
    req_map = {r[0]: r[3] for r in REQUEST_LAYOUT}
    res_map: dict[str, str] = {}
    for row in RESPONSE_LAYOUT:
        kind, sid_or, _layout, example = row[0], row[1], row[2], row[3]
        if kind == "Positive":
            res_map[sid_or] = example
        elif kind == "Negative" and sid_or == "0x7F":
            res_map["0x7F"] = example
    rows = []
    for s in SERVICES:
        sid, pos = s[0], s[2]
        if sid == "0x7F":
            req, res = "-", res_map.get("0x7F", "7F 22 31")
        else:
            req = req_map.get(sid, "")
            res = res_map.get(pos, "")
        if sid in SUB_BYTE_OFFSET_3:
            sub_off = 3
        elif sid in SUB_BYTE_OFFSET_1:
            sub_off = 1
        else:
            sub_off = -1
        rows.append(list(s) + [req, res, sub_off])
    return rows


def write_howto(wb) -> None:
    headers = ["구분", "시트_또는_항목", "핵심_역할_및_해석_가이드"]
    rows = [
        ["목적", "전체", "UDS HEX 로그를 사람이 이해하고 Java 해석기가 이름으로 풀 때 쓰는 전체 참고표"],
        ["목적", "범위", "CAN/ISO-TP 제외. 완성 payload(예: 22 F1 90, 7F 22 31) 기준"],
        ["시트", "HowToRead", "이 안내 표"],
        ["시트", "Services", "SID별 목적·언제/왜·example_req/res·sub_byte_offset"],
        ["시트", "SubFunctions", "서브펑션·파라미터 값. parent_sid와 함께 조회. sub_function은 0xNN HEX만"],
        ["시트", "NRC", "부정응답 코드. 7F의 세 번째 바이트"],
        ["시트", "DTCStatusBits", "0x19 응답 DTCStatusOfDTC 1바이트 bit0~7"],
        ["시트", "RequestLayout", "요청 레이아웃 + id_type/id_offset/is_variable_length"],
        ["시트", "ResponseLayout", "응답 레이아웃 + id_type/id_offset/data_offset"],
        ["시트", "ControllerProfiles", "프로젝트 대상 컨트롤러와 RPM/ERPM 의미 및 자료 출처"],
        ["시트", "DidProfileSupport", "GUI runtime profile-DID Read/Write 지원 관계와 정렬 순서"],
        ["시트", "OemExtensions", "표준 관례 항목과 프로젝트 정의 Read/Write DID 스키마"],
        ["시트", "OemExtensions", "관례·교육용 DID·Routine·DTC. id는 HEX만. data_type/byte_length/scale/unit/normal_*로 표시·Finding"],
        ["시트", "SymptomDidMap", "현상 키워드→0x22 DID 후보. FR-13은 후보 표시+runtime_enabled 실행 필터"],
        ["시트", "Meta", "생성 규칙·고지"],
        ["해석순서", "1_Negative", "첫 바이트 7F → 둘째=요청 SID, 셋째=NRC → NRC 시트"],
        ["해석순서", "2_Services", "Services에서 SID(긍정응답이면 SID-0x40) 조회 → when/why·예시 HEX"],
        ["해석순서", "3_SubFunctions",
         "sub_byte_offset>=0이면 해당 오프셋 바이트로 SubFunctions 조회. "
         "has_subfunction=Y일 때만 value&0x7F 마스킹. N이면(0x2A/2F/38) 마스킹 없이 파라미터 값으로 조회"],
        ["해석순서", "4_DTCStatus", "0x19 DTC 상태 바이트는 DTCStatusBits"],
        ["해석순서", "5_Layout", "필드 분해·오프셋은 RequestLayout/ResponseLayout"],
        ["열가이드", "sub_byte_offset", "SubFunctions 조회 바이트 오프셋. 없으면 -1. 0x2F는 3(DID 뒤 ioControl)"],
        ["열가이드", "when_to_use_ko / why_use_ko", "상황 / 목적"],
        ["열가이드", "suppress_bit_applicable", "문법상 bit7 마스킹 허용(Y/N). 공통 규칙은 HowToRead/Meta"],
        ["열가이드", "response_needed_practice", "실무에서 긍정응답이 대개 필요한지(Y/N)"],
        ["열가이드", "example_req_hex / example_res_hex", "Services에서 Layout 없이도 예시 확인. 0x7F req는 '-'"],
        ["열가이드", "id_type / id_offset / data_offset", "Request/Response 식별자·페이로드 오프셋. 없으면 -1"],
        ["열가이드", "data_type / byte_length", "OemExtensions 응답 표시(ASCII/HEX/UINT8/UINT16/INT16 등)"],
        ["열가이드", "scale / unit", "물리값 = raw * scale. 단위 문자열(V,A,C 등). 비물리는 scale=1·unit 빈칸"],
        ["열가이드", "normal_min / normal_max", "Finding 정상범위(물리값). 비어 있으면 RANGE_NOT_APPLICABLE"],
        ["열가이드", "SymptomDidMap.priority", "같은 현상 후보 우선순위. 실행은 DidProfileSupport.runtime_enabled=Y만"],
        ["열가이드", "0x27 패턴", "홀수=requestSeed·짝수=sendKey. accessType의 권한 의미는 OEM 정의"],
        ["열가이드", "required_session / required_security_level", "프로젝트 runtime DID 실행 전 AccessGuard 정책"],
        ["열가이드", "runtime_enabled", "GUI 선택 노출 여부. DidProfileSupport의 profile별 값도 함께 Y여야 함"],
        ["중요", "교육용 요약", "ISO 원문 복제 아님. 실차 OEM 스펙을 대체하지 않음"],
        ["중요", "시연 범위", "깊게 해석 SID와 File ECU의 0x10/0x27/0x22/0x2E: docs/requirements.xlsx"],
        ["중요", "0x19 vs 0x22", "0x19 DTC는 로그 해석·근거만 사용하고 DTC 능동 조회는 현재 범위 밖"],
    ]
    write_sheet(
        wb,
        "HowToRead",
        headers,
        rows,
        "HowToRead",
        {"구분": 12, "시트_또는_항목": 28, "핵심_역할_및_해석_가이드": 78},
        group_key_col=1,
    )
    wb.move_sheet("HowToRead", offset=-len(wb.sheetnames) + 1)


def main() -> None:
    wb = Workbook()
    wb.remove(wb.active)

    services_rows = enrich_services()

    svc_w = {
        "when_to_use_ko": 36,
        "why_use_ko": 36,
        "typical_flow_ko": 40,
        "purpose_summary_ko": 28,
        "notes": 36,
        "example_req_hex": 28,
        "example_res_hex": 28,
        "sub_byte_offset": 14,
    }
    write_sheet(
        wb,
        "Services",
        [
            "sid",
            "name",
            "positive_response_sid",
            "category",
            "purpose_summary_ko",
            "when_to_use_ko",
            "why_use_ko",
            "typical_flow_ko",
            "session_note",
            "security_note",
            "has_subfunction",
            "notes",
            "example_req_hex",
            "example_res_hex",
            "sub_byte_offset",
        ],
        services_rows,
        "Services",
        svc_w,
        group_key_col=4,  # category
    )

    sf_w = {
        "when_to_use_ko": 34,
        "why_use_ko": 34,
        "params_summary": 28,
        "notes": 28,
        "response_needed_practice": 22,
    }
    write_sheet(
        wb,
        "SubFunctions",
        [
            "parent_sid",
            "sub_function",
            "name",
            "suppress_bit_applicable",
            "response_needed_practice",
            "params_summary",
            "when_to_use_ko",
            "why_use_ko",
            "notes",
        ],
        SUBFUNCTIONS,
        "SubFunctions",
        sf_w,
        group_key_col=1,
    )

    nrc_w = {"when_appears_ko": 32, "what_to_check_ko": 36, "meaning_ko": 22, "notes": 22}
    write_sheet(
        wb,
        "NRC",
        [
            "code",
            "name",
            "meaning_ko",
            "typical_services",
            "when_appears_ko",
            "what_to_check_ko",
            "notes",
        ],
        NRC,
        "NRC",
        nrc_w,
        group_key_col=1,
        group_key_fn=nrc_band,
    )

    write_sheet(
        wb,
        "DTCStatusBits",
        ["bit", "mask", "name_en", "meaning_ko", "check_point_ko"],
        DTC_STATUS_BITS,
        "DTCStatusBits",
        {"meaning_ko": 36, "check_point_ko": 40, "name_en": 36},
    )

    write_sheet(
        wb,
        "RequestLayout",
        [
            "sid",
            "min_length_bytes",
            "layout",
            "example_hex",
            "when_you_need_this_ko",
            "id_type",
            "id_offset",
            "is_variable_length",
        ],
        REQUEST_LAYOUT,
        "RequestLayout",
        {"when_you_need_this_ko": 28, "layout": 48, "example_hex": 40},
    )
    write_sheet(
        wb,
        "ResponseLayout",
        [
            "kind",
            "sid_or_marker",
            "layout",
            "example_hex",
            "when_you_need_this_ko",
            "id_type",
            "id_offset",
            "data_offset",
            "notes",
        ],
        RESPONSE_LAYOUT,
        "ResponseLayout",
        {"when_you_need_this_ko": 36, "layout": 48, "example_hex": 40, "notes": 36},
    )
    write_sheet(
        wb,
        "OemExtensions",
        [
            "type",
            "id",
            "name",
            "related_sid",
            "data_type",
            "byte_length",
            "scale",
            "unit",
            "normal_min",
            "normal_max",
            "access",
            "write_min",
            "write_max",
            "supported_profiles",
            "speed_basis",
            "source_ref",
            "example",
            "when_to_use_ko",
            "why_use_ko",
            "notes",
            "runtime_enabled",
            "sort_order",
            "required_session",
            "required_security_mechanism",
            "required_security_level",
            "length_policy",
        ],
        enrich_oem_extensions(),
        "OemExtensions",
        {
            "when_to_use_ko": 28,
            "why_use_ko": 28,
            "name": 36,
            "scale": 10,
            "unit": 8,
            "normal_min": 12,
            "normal_max": 12,
            "supported_profiles": 24,
            "speed_basis": 14,
            "source_ref": 32,
            "required_session": 18,
            "required_security_mechanism": 28,
            "length_policy": 22,
        },
    )
    write_sheet(
        wb,
        "ControllerProfiles",
        [
            "profile_id",
            "display_name",
            "family",
            "speed_basis",
            "telemetry_reference",
            "did_policy",
            "notes",
            "runtime_enabled",
            "sort_order",
        ],
        CONTROLLER_PROFILES,
        "ControllerProfiles",
        {
            "profile_id": 24,
            "display_name": 24,
            "telemetry_reference": 42,
            "did_policy": 34,
            "notes": 72,
        },
    )
    write_sheet(
        wb,
        "DidProfileSupport",
        [
            "profile_id",
            "did_id",
            "read_enabled",
            "write_enabled",
            "runtime_enabled",
            "sort_order",
        ],
        build_did_profile_support(),
        "DidProfileSupport",
        {"profile_id": 24, "did_id": 12},
    )
    write_sheet(
        wb,
        "SymptomDidMap",
        [
            "symptom_key",
            "keywords_ko",
            "did_id",
            "priority",
            "reason_ko",
            "notes",
        ],
        SYMPTOM_DID_MAP,
        "SymptomDidMap",
        {"keywords_ko": 28, "reason_ko": 36, "notes": 36, "symptom_key": 20},
    )
    write_sheet(
        wb,
        "Meta",
        ["key", "value"],
        [
            ["title", "UDS human-readable full catalog"],
            ["audience", "사람(팀원/시연) + Java 해석기 룩업"],
            ["mvp_policy", "No MVP column. Full reference."],
            ["impl_scope", "Demo code depth = docs/requirements.xlsx only"],
            ["columns_focus", "when_to_use_ko / why_use_ko explain situations and purpose"],
            [
                "join_keys",
                "Services.sid = SubFunctions.parent_sid; 7F uses NRC.code; "
                "0x19 status uses DTCStatusBits; SymptomDidMap.did_id = OemExtensions.id; "
                "DidProfileSupport joins ControllerProfiles.profile_id to OemExtensions.id",
            ],
            ["catalog_schema_version", "2"],
            ["project_did_policy", "0x1001~0x1206 Read, 0x2001~0x2006 Write는 교육용 프로젝트 정의이며 제조사 공식 UDS DID가 아님"],
            ["speed_policy", "ND72530 motorSpeed는 RPM, Flipsky/VESC motorSpeed는 ERPM. 차량 속도 변환은 pole count·기어비·타이어 둘레 확정 전 금지"],
            ["write_policy", "Write DID는 현재 FileDiagnosticExchangePort 결과물 생성/더미 응답 시연 전용이며 실 ECU 상태를 변경하지 않음"],
            ["access_policy", "runtime project DIDs require EXTENDED + SECURITY_ACCESS level 1 (educational project policy)"],
            [
                "demo_seed_key",
                "HMAC-SHA256(UTF-8(password), seed||00||UTF-8(profileId))[:4]; "
                "vector password=uds-demo-2026 seed=AABBCCDD profile=FARDRIVER_ND72530 key=D3AC3F47",
            ],
            ["runtime_filter", "GUI exposes only ControllerProfiles.runtime_enabled=Y and DidProfileSupport.runtime_enabled=Y rows"],
            ["positive_sid_rule", "positive ~= request + 0x40 (except 0x7F)"],
            ["suppress_bit", "Sub-function bit7 may suppress POS; 0x2A/2F/38 are NOT Sub-functions"],
            [
                "suppress_vs_practice",
                "suppress_bit_applicable=grammar; response_needed_practice=usual need for POS",
            ],
            ["dtc_status_sheet", "DTCStatusBits: bit0..7 of DTCStatusOfDTC in 0x19 responses"],
            ["oem_id_hex_only", "OemExtensions.id is always 0xHEX"],
            [
                "oem_value_schema",
                "data_type/byte_length/scale/unit/normal_min/normal_max for FR-15/16 Finding",
            ],
            [
                "symptom_map",
                "SymptomDidMap=FR-13 candidates; executable DIDs filtered by runtime_enabled "
                "(ND sudden-stop: 0x1001/1002/1007; Flipsky: 0x1001; 0xF186 static-log only)",
            ],
            ["services_examples", "Services.example_req_hex / example_res_hex mirrored from Layout sheets"],
            [
                "sub_byte_offset",
                "Services.sub_byte_offset>=0 → that byte indexes SubFunctions; "
                "mask value&0x7F only when has_subfunction=Y",
            ],
            [
                "request_id_meta",
                "RequestLayout.id_type NONE|DID|ROUTINE_ID|DTC_GROUP; id_offset=-1 if none",
            ],
            [
                "response_id_meta",
                "ResponseLayout.id_type NONE|DID|ROUTINE_ID|DTC_STATUS; "
                "id_offset/data_offset=-1 if none; NegativePending sid_or_marker=0x7F (NRC 0x78 in notes)",
            ],
            ["not_iso_copy", "Educational public summary, not ISO verbatim tables"],
            ["related_plan", "docs/PROJECT_PLAN.txt"],
            ["related_requirements", "docs/requirements.xlsx"],
            ["doc_policy", "Narrative in PROJECT_PLAN.txt; this workbook is UDS table SSOT"],
            ["generated_by", "tool/build_uds_catalog.py"],
            ["output_path", "docs/uds_catalog.xlsx"],
        ],
        "Meta",
    )

    write_howto(wb)

    try:
        wb.save(OUT)
        target = OUT
    except PermissionError:
        wb.save(OUT_FALLBACK)
        target = OUT_FALLBACK
        print(f"WARNING: {OUT.name} locked; wrote {target.name}. Close Excel and replace.")
    print(f"Wrote {target}")
    print(
        f"Services={len(services_rows)} SubFunctions={len(SUBFUNCTIONS)} NRC={len(NRC)} "
        f"RequestLayout={len(REQUEST_LAYOUT)} ResponseLayout={len(RESPONSE_LAYOUT)} "
        f"DTCStatusBits={len(DTC_STATUS_BITS)} OemExtensions={len(OEM)} "
        f"SymptomDidMap={len(SYMPTOM_DID_MAP)}"
    )


if __name__ == "__main__":
    main()
