# UDS_GUI

UDS Diagnostic GUI & AI Advisor — JavaFX desktop app for classroom UDS demos (JDK 17, Maven, OpenJFX).

## Quick start

- **Packaged app:** `실행.bat` or `dist/UdsDiagnosticGui/UdsDiagnosticGui.exe` (build with `scripts/package-win.bat`)
- **Dev:** double-click `run.bat` (Maven + JDK required)
- **Config:** copy `.env.example` → `.env` and set `UDS_AI_API_KEY` (do not commit `.env`)
- **Fixture password:** `config/demo_password.txt` (demo ECU only)

More detail: see [`README.txt`](README.txt) and `docs/`.

## Docs

| File | Role |
|------|------|
| `docs/PROJECT_PLAN.txt` | Product / design |
| `docs/PACKAGE_MAP.txt` | Package layout |
| `docs/UX_USABILITY_BACKLOG.txt` | Post Micro-Gate UX |
| `docs/prompts/ux_phases/` | UX phase runbook |
