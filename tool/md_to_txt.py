#!/usr/bin/env python3
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def md_to_txt(text: str) -> str:
    text = re.sub(
        r"```mermaid[\s\S]*?```",
        "[다이어그램 생략: 설계 문서의 파이프라인 설명 참고]",
        text,
    )

    def fence(match: re.Match[str]) -> str:
        body = match.group(1)
        return "\n".join(("    " + line) if line.strip() else "" for line in body.splitlines())

    text = re.sub(r"```\w*\n([\s\S]*?)```", fence, text)
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r"\1 (\2)", text)
    text = re.sub(r"!\[([^\]]*)\]\(([^)]+)\)", r"\1", text)
    text = re.sub(r"^#{1,6}\s*", "", text, flags=re.M)
    text = text.replace("**", "").replace("__", "")
    text = re.sub(r"(?<!\w)\*(?!\*)([^*]+)\*(?!\*)", r"\1", text)
    text = text.replace("`", "")
    text = re.sub(r"^---+\s*$", "-" * 60, text, flags=re.M)

    lines: list[str] = []
    for line in text.splitlines():
        if re.match(r"^\|(\s*:?-+:?\s*\|)+\s*$", line):
            continue
        stripped = line.strip()
        if stripped.startswith("|") and stripped.endswith("|"):
            cells = [c.strip() for c in stripped.strip("|").split("|")]
            lines.append("  |  ".join(cells))
        else:
            lines.append(line)

    text = "\n".join(lines)
    text = text.replace(".md)", ".txt)")
    text = text.replace(".md", ".txt")
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip() + "\n"


def main() -> None:
    md_files = sorted(ROOT.rglob("*.md"))
    # skip this converter folder scripts if any md appears later
    for md in md_files:
        if "tools" in md.parts and md.name == "md_to_txt.py":
            continue
        txt_path = md.with_suffix(".txt")
        txt_path.write_text(md_to_txt(md.read_text(encoding="utf-8")), encoding="utf-8")
        md.unlink()
        print(f"converted {md.relative_to(ROOT)} -> {txt_path.relative_to(ROOT)}")
    print(f"total {len(md_files)}")


if __name__ == "__main__":
    main()
