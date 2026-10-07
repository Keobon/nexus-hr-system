"""JUnit XML 결과를 GitHub Actions 요약(마크다운)으로 바꾼다. 사용: python3 test-summary.py <결과 폴더>"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

folder = sys.argv[1]
total = failed = skipped = 0
failures = []
for path in sorted(glob.glob(os.path.join(folder, "TEST-*.xml"))):
    suite = ET.parse(path).getroot()
    total += int(suite.get("tests", 0))
    skipped += int(suite.get("skipped", 0))
    for case in suite.iter("testcase"):
        problem = case.find("failure")
        if problem is None:
            problem = case.find("error")
        if problem is not None:
            failed += 1
            lines = [l.strip() for l in (problem.get("message") or "").splitlines() if l.strip()]
            first = " ".join(lines[:4])[:400].replace("|", "\\|")
            cls = case.get("classname", "").rsplit(".", 1)[-1]
            failures.append(f"| `{cls}` | {case.get('name')} | {first} |")

if total == 0:
    print("### ⚠️ 테스트 결과가 없습니다 (컴파일 실패 또는 테스트 전 단계에서 중단)")
    sys.exit(0)

icon = "✅" if failed == 0 else "❌"
print(f"### {icon} 테스트 {total}개 중 통과 {total - failed - skipped} · 실패 {failed} · 건너뜀 {skipped}")
if failures:
    print()
    print("| 테스트 클래스 | 테스트 | 메시지 |")
    print("|---|---|---|")
    print("\n".join(failures))
    print()
    print("자세한 내용: 이 실행 화면 아래 **Artifacts > test-report** 를 내려받아 `index.html` 을 연다.")
