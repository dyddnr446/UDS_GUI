UDS Diagnostic GUI & AI Advisor
Course: 2026-2 Java

================================================================================
What this repository contains
================================================================================
  src/                    Java sources
  catalog/                uds_system_catalog.xlsx + uds_guide_catalog.xlsx
  fixtures/               Offline simulation scenarios + demo_session.log
  tools/                  resolve-runtime.bat (JDK/Maven detect)
  pom.xml  mvnw*  .mvn/   Maven
  run.bat                 Launch GUI
  ai_advisor_prompt.txt   AI prompt
  README.txt              This file
  output/.gitkeep         Placeholder for runtime output

Not published (local only / .gitignore)
  .env  target/  doc/  test/  제출문서/  .cursorrules  process drafts

================================================================================
Run
================================================================================
1) JDK 25+ (Maven optional — mvnw is included)
2) Create .env at repo root (not in git)
     TEST=TRUE
     UDS_DEMO_PASSWORD=uds-demo-2026
     UDS_AI_API_KEY=   (optional)
3) run.bat
   → writes live_session.log etc. under output\

================================================================================
Mode
================================================================================
  TEST=TRUE   Offline Simulation (no hardware)
  TEST=FALSE  CAN placeholder (not implemented)
