from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
src_roots = [root / "src/main/java", root / "src/test/java"]

CLASS_PKG = {
    "ControllerProfile": "uds.catalog.model",
    "DidDefinition": "uds.catalog.model",
    "DidProfileSupport": "uds.catalog.model",
    "SymptomDidMap": "uds.catalog.model",
    "ServiceDefinition": "uds.catalog.model",
    "SubFunctionDefinition": "uds.catalog.model",
    "NrcDefinition": "uds.catalog.model",
    "DtcStatusBitDefinition": "uds.catalog.model",
    "LayoutDefinition": "uds.catalog.model",
    "CatalogLoader": "uds.catalog.load",
    "UdsCatalog": "uds.catalog.load",
    "DecodeKind": "uds.interpreter.model",
    "DecodeResult": "uds.interpreter.model",
    "SidDecoder": "uds.interpreter.decode",
    "UdsParser": "uds.interpreter.decode",
    "UdsInterpreter": "uds.interpreter.decode",
    "DiagnosticAccessState": "uds.access.state",
    "AccessSnapshot": "uds.access.state",
    "ActiveDiagnosticContext": "uds.access.state",
    "DemoSeedKeyAlgorithm": "uds.access.crypto",
    "SessionControlService": "uds.access.service",
    "SecurityAccessService": "uds.access.service",
    "AccessOutcome": "uds.access.service",
    "DiagnosticAccessGuard": "uds.access.guard",
    "GuardDecision": "uds.access.guard",
    "DiagnosticAccessFacade": "uds.access.facade",
    "DiagnosticExchangePort": "uds.exchange.api",
    "ExchangeRequest": "uds.exchange.api",
    "ExchangeResponse": "uds.exchange.api",
    "ExchangePolicy": "uds.exchange.policy",
    "ExchangeCoordinator": "uds.exchange.policy",
    "RecordingDiagnosticExchangePort": "uds.exchange.recording",
    "FixtureRunConfig": "uds.fixture.config",
    "Clock": "uds.fixture.config",
    "SystemClock": "uds.fixture.config",
    "FixtureRepository": "uds.fixture.repo",
    "AccessStep": "uds.fixture.repo",
    "ReadScenario": "uds.fixture.repo",
    "WriteScenario": "uds.fixture.repo",
    "PasswordFileCredentialStore": "uds.fixture.credential",
    "CredentialConfigException": "uds.fixture.credential",
    "StatefulFixtureEcuSimulator": "uds.fixture.sim",
    "DemoHmacKeyAlgorithm": "uds.fixture.sim",
    "FileDiagnosticExchangePort": "uds.fixture.port",
    "AccessEvidence": "uds.analysis.evidence",
    "SafeExchangeRecorder": "uds.analysis.record",
    "IoAndCatalogLoaderTest": "uds.catalog.load",
    "UdsInterpreterTest": "uds.interpreter.decode",
    "CredentialAndSimulatorTest": "uds.fixture.credential",
    "RecordingPortRedactionTest": "uds.exchange.recording",
    "DemoSeedKeyAndAccessFlowTest": "uds.access.facade",
}

OLD_PKG = {
    "ControllerProfile": "uds.catalog",
    "DidDefinition": "uds.catalog",
    "DidProfileSupport": "uds.catalog",
    "SymptomDidMap": "uds.catalog",
    "ServiceDefinition": "uds.catalog",
    "SubFunctionDefinition": "uds.catalog",
    "NrcDefinition": "uds.catalog",
    "DtcStatusBitDefinition": "uds.catalog",
    "LayoutDefinition": "uds.catalog",
    "CatalogLoader": "uds.catalog",
    "UdsCatalog": "uds.catalog",
    "DecodeKind": "uds.interpreter",
    "DecodeResult": "uds.interpreter",
    "SidDecoder": "uds.interpreter",
    "UdsParser": "uds.interpreter",
    "UdsInterpreter": "uds.interpreter",
    "DiagnosticAccessState": "uds.access",
    "AccessSnapshot": "uds.access",
    "ActiveDiagnosticContext": "uds.access",
    "DemoSeedKeyAlgorithm": "uds.access",
    "SessionControlService": "uds.access",
    "SecurityAccessService": "uds.access",
    "AccessOutcome": "uds.access",
    "DiagnosticAccessGuard": "uds.access",
    "GuardDecision": "uds.access",
    "DiagnosticAccessFacade": "uds.access",
    "DiagnosticExchangePort": "uds.exchange",
    "ExchangeRequest": "uds.exchange",
    "ExchangeResponse": "uds.exchange",
    "ExchangePolicy": "uds.exchange",
    "ExchangeCoordinator": "uds.exchange",
    "RecordingDiagnosticExchangePort": "uds.exchange",
    "FixtureRunConfig": "uds.fixture",
    "Clock": "uds.fixture",
    "SystemClock": "uds.fixture",
    "FixtureRepository": "uds.fixture",
    "AccessStep": "uds.fixture",
    "ReadScenario": "uds.fixture",
    "WriteScenario": "uds.fixture",
    "PasswordFileCredentialStore": "uds.fixture",
    "CredentialConfigException": "uds.fixture",
    "StatefulFixtureEcuSimulator": "uds.fixture",
    "DemoHmacKeyAlgorithm": "uds.fixture",
    "FileDiagnosticExchangePort": "uds.fixture",
    "AccessEvidence": "uds.analysis",
    "SafeExchangeRecorder": "uds.analysis",
}

moves = []
for src_root in src_roots:
    for path in src_root.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        m = re.search(
            r"^(?:public\s+)?(?:final\s+)?(?:class|record|enum|interface)\s+(\w+)",
            text,
            re.M,
        )
        if not m:
            print("NO CLASS", path)
            continue
        cls = m.group(1)
        if cls not in CLASS_PKG:
            pkg_m = re.search(r"^package\s+([\w.]+);", text, re.M)
            old_pkg = pkg_m.group(1) if pkg_m else ""
            if old_pkg.startswith("uds.io"):
                continue
            print("UNMAPPED", cls, path)
            continue
        new_pkg = CLASS_PKG[cls]
        new_path = src_root.joinpath(*new_pkg.split(".")) / path.name
        if path.resolve() == new_path.resolve():
            continue
        moves.append((path, new_path, new_pkg, cls))

print("moves", len(moves))
for old, new, pkg, cls in moves:
    new.parent.mkdir(parents=True, exist_ok=True)
    text = old.read_text(encoding="utf-8")
    text2, n = re.subn(r"^package\s+[\w.]+;", f"package {pkg};", text, count=1, flags=re.M)
    if n != 1:
        raise SystemExit(f"package replace failed: {old}")
    new.write_text(text2, encoding="utf-8")
    old.unlink()
    print("MOVED", cls)

for src_root in src_roots:
    for d in sorted(src_root.rglob("*"), reverse=True):
        if d.is_dir() and not any(d.iterdir()):
            d.rmdir()

replacements = []
for cls, new_pkg in CLASS_PKG.items():
    if cls.endswith("Test"):
        continue
    old_pkg = OLD_PKG.get(cls)
    if not old_pkg or old_pkg == new_pkg:
        continue
    replacements.append((f"import {old_pkg}.{cls};", f"import {new_pkg}.{cls};"))
    replacements.append((f"{old_pkg}.{cls}", f"{new_pkg}.{cls}"))

# longest first to avoid partial overlaps
replacements.sort(key=lambda x: len(x[0]), reverse=True)

all_java = []
for src_root in src_roots:
    all_java.extend(src_root.rglob("*.java"))

for path in all_java:
    text = path.read_text(encoding="utf-8")
    orig = text
    for a, b in replacements:
        text = text.replace(a, b)
    if text != orig:
        path.write_text(text, encoding="utf-8")
        print("REWROTE", path.relative_to(root))

print("DONE")
