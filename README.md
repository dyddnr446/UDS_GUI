# UDS Diagnostic GUI & AI Advisor

차량 ECU의 UDS(Unified Diagnostic Services) 진단 흐름을 JavaFX 화면에서 체험하는 교육용 도구입니다. 오프라인 시뮬레이터에서 진단 세션을 열고, ECU 데이터를 읽거나 쓰고, 고장 코드와 요청·응답 로그를 확인할 수 있습니다.

**2026년 2학기 Java 과제** · Windows · JDK 25 · JavaFX

## 빠른 시작

1. Windows에 **JDK 25 이상**을 설치합니다. Maven은 포함된 Maven Wrapper로 실행할 수 있습니다. 첫 실행에서는 빌드 의존성을 내려받기 위해 인터넷 연결이 필요할 수 있습니다.
2. 저장소를 클론하거나 ZIP을 풀고, 프로젝트 루트의 [`run.bat`](run.bat)을 실행합니다.
3. **Connection**에 **Offline Simulation**이 표시되는지 확인하고 **Target ECU**를 선택합니다.
4. **Session Control → Extended Session → Apply**를 누릅니다. 비밀번호 창에서 입력칸을 비운 채 **Unlock**을 누르면 실행 설정에 지정된 데모 비밀번호를 사용합니다.
5. 하단 상태가 `EXTENDED_UNLOCKED`로 바뀌면 **ReadData** 탭에서 DID를 선택하고 **Read**를 눌러 첫 진단을 시작합니다.

`TEST=TRUE`가 기본값이며, 로컬 Excel 픽스처로 ECU 응답을 시뮬레이션합니다. 실차 CAN 연결은 구현 예정입니다.

## 탭별 기능

| 탭 | 할 수 있는 작업 |
| --- | --- |
| **Interpreter** | **이번 세션 로그**에서 UDS 요청·응답, 서비스, 결과를 표로 확인합니다. 로그 수준을 바꾸거나 해석 결과를 JSON으로 내보낼 수 있습니다. |
| **ReadData** · `0x22` | DID(Data Identifier)를 선택해 데이터를 읽습니다. 원시 HEX, 단위가 적용된 물리값, 정상 범위와 판정 결과를 확인합니다. |
| **WriteData** · `0x2E` | 쓰기 가능한 DID에 값을 입력합니다. 입력값 검증, 세션·보안 권한, ECU 응답을 단계별로 확인합니다. |
| **DTC** · `0x19`, `0x14` | 상태 마스크로 DTC(Diagnostic Trouble Code)를 조회하거나, 확인 창을 거쳐 시뮬레이터의 고장 코드를 지웁니다. |
| **AI Advisor** | 증상에 맞는 DID를 제안하고, 읽기 결과를 근거로 진단 조언을 생성합니다. 수집한 사실과 AI 설명이 화면에 각각 표시됩니다. |

**Default Session**의 상태는 `DEFAULT_LOCKED`입니다. ReadData와 WriteData에서 사용할 수 있는 DID는 ECU 프로필과 세션 권한에 따라 달라집니다. DTC와 AI Advisor는 **Extended Session**에서 보안 잠금을 해제한 뒤 사용할 수 있습니다. ECU를 바꾸면 세션 상태가 초기화됩니다.

## AI Advisor 설정

Interpreter, ReadData, WriteData, DTC는 AI API 키 없이 사용할 수 있습니다. AI Advisor에서 원클릭 진단이나 조언 생성을 실행하려면 외부 API에 연결할 수 있어야 합니다. 앱의 기본 설정은 Gemini와 `gemini-2.5-flash`입니다.

프로젝트 루트에 이미 로컬 `.env`가 있으면 해당 파일을 사용합니다. 새로 받은 프로젝트라면 [`.env.example`](.env.example)을 `.env`로 복사합니다. `.env`의 `UDS_AI_API_KEY`에 본인 Gemini 키를 넣고 앱을 다시 실행합니다. Git은 로컬 `.env`를 추적하지 않습니다.

PowerShell 환경 변수로 설정할 수도 있습니다. 환경 변수 값이 프로젝트의 `.env` 설정보다 우선합니다.

```powershell
$env:UDS_AI_API_KEY = 'YOUR_GEMINI_API_KEY'
.\run.bat
```

잠금 해제 후 **AI Advisor** 탭에 `전압 이상`과 같은 증상을 입력하고 **원클릭 진단 실행**을 누릅니다. 단계별로 실행하려면 **Suggest DIDs → Read suggested → Ask AI**를 사용합니다.

## 프로젝트 구조

| 경로 | 내용 |
| --- | --- |
| [`src/main/java/uds/`](src/main/java/uds/) | JavaFX 화면, 세션·보안, 진단 서비스, 로그 해석, AI 연동 |
| [`catalog/`](catalog/) | Excel 진단 카탈로그. ECU 프로필, DID, DTC, 서비스, 서브펑션, 부정 응답 코드 정의 |
| [`fixtures/`](fixtures/) | 시뮬레이터용 시나리오와 [샘플 UDS 로그](fixtures/demo_session.log) |
| [`ai_advisor_prompt.txt`](ai_advisor_prompt.txt) | AI Advisor 프롬프트 템플릿 |
| [`tools/`](tools/) | Windows 실행·패키징 스크립트 |
| [`output/`](output/) | 실행 로그와 이번 세션의 UDS 통신 로그가 생성되는 위치 |

## 문제 해결

| 증상 | 확인할 항목 |
| --- | --- |
| GUI 창이 열리지 않음 | JDK 25 설치 경로 또는 `JAVA_HOME`을 확인하고 `output/launch.log`와 `output/crash.log`를 확인합니다. |
| 탭이 비활성화되거나 DID 목록이 비어 있음 | **Target ECU** 선택, **Extended Session** 적용, 하단 상태 `EXTENDED_UNLOCKED`를 확인합니다. |
| 데모 비밀번호 오류 | 실행 환경 변수 또는 프로젝트의 `.env`에 지정된 `UDS_DEMO_PASSWORD`를 확인합니다. |
| 카탈로그나 픽스처를 찾지 못함 | 프로젝트 루트에 `catalog/`와 `fixtures/`가 있는지 확인합니다. |
| AI 조언을 가져오지 못함 | `UDS_AI_API_KEY`와 외부 API 네트워크 연결을 확인합니다. |
