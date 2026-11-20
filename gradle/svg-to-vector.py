"""FestiCore iOS 아이콘 SVG를 VectorDrawable로 바꾼다. 의미를 그대로 옮길 수 있는 형태만 받고 나머지는 실패한다.

받는 형태: <svg width height viewBox fill="none"> 안의 <path d fill>, 그리고 캔버스 전체를 자르는(=효과 없는) clipPath를 건 <g>.
transform · opacity · stroke · 그 밖의 요소나 속성이 있으면 변환하지 않고 실패한다.
사용: python3 -I gradle/svg-to-vector.py <iOS ImageAssets.xcassets 경로> ui/designkit/src/main/res/drawable   규칙: .claude/docs/design-system.md
"""
import pathlib
import sys
import xml.etree.ElementTree as ET

NS = "{http://www.w3.org/2000/svg}"
COLORS = {"black": "#000000", "white": "#FFFFFF"}
ALLOWED = {
    "svg": {"width", "height", "viewBox", "fill", "xmlns"},
    "path": {"d", "fill"},
    "g": {"clip-path"},
    "defs": set(),
    "clipPath": {"id"},
    "rect": {"width", "height", "fill"},
}


def fail(message):
    raise SystemExit(message)


def color(value, src):
    """SVG #RRGGBB(AA) → Android #RRGGBB / #AARRGGBB."""
    value = COLORS.get(value, value)
    hexpart = value[1:]
    if not (value.startswith("#") and len(hexpart) in (6, 8) and all(c in "0123456789abcdefABCDEF" for c in hexpart)):
        fail(f"지원하지 않는 색 {value}: {src}")
    hexpart = hexpart.upper()
    return f"#{hexpart}" if len(hexpart) == 6 else f"#{hexpart[6:]}{hexpart[:6]}"


def check_attributes(el, tag, src):
    extra = set(el.attrib) - ALLOWED[tag]
    if extra:
        fail(f"지원하지 않는 속성 {sorted(extra)} ({tag}): {src}")


def full_canvas_clips(root, vw, vh, src):
    """캔버스 전체 사각형 하나로만 된 clipPath id. 이런 clip은 그리지 않아도 결과가 같다."""
    ids = set()
    for clip in root.iter(f"{NS}clipPath"):
        children = list(clip)
        if len(children) != 1 or children[0].tag != f"{NS}rect":
            fail(f"사각형 하나가 아닌 clipPath: {src}")
        rect = children[0]
        check_attributes(rect, "rect", src)
        if rect.get("width") != vw or rect.get("height") != vh:
            fail(f"캔버스 전체가 아닌 clipPath: {src}")
        ids.add(clip.get("id"))
    return ids


def convert(src: pathlib.Path, rel: str) -> str:
    root = ET.parse(src).getroot()
    check_attributes(root, "svg", src)
    if root.get("fill", "none") != "none":
        fail(f"svg 기본 fill은 none만 받는다: {src}")
    vx, vy, vw, vh = root.get("viewBox").split()
    if (vx, vy) != ("0", "0"):
        fail(f"viewBox 원점이 0이 아님: {src}")
    clips = full_canvas_clips(root, vw, vh, src)
    paths = []

    def walk(el, in_defs=False):
        tag = el.tag.replace(NS, "")
        if tag not in ALLOWED:
            fail(f"지원하지 않는 요소 {tag}: {src}")
        check_attributes(el, tag, src)
        if tag == "g":
            ref = el.get("clip-path", "")
            if ref and ref.removeprefix("url(#").removesuffix(")") not in clips:
                fail(f"캔버스 전체가 아닌 clip-path 참조 {ref}: {src}")
        if tag == "path" and not in_defs:
            fill = el.get("fill", "black")
            if fill == "none":
                fail(f"fill 없는 path: {src}")
            paths.append((color(fill, src), el.get("d")))
        for child in el:
            walk(child, in_defs or tag in ("defs", "clipPath"))

    walk(root)
    if not paths:
        fail(f"그릴 path가 없음: {src}")
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- 원본: FestiCore-iOS FestiCoreDesignKit/Resources/ImageAssets.xcassets/{rel} -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{root.get("width")}dp"',
        f'    android:height="{root.get("height")}dp"',
        f'    android:viewportWidth="{vw}"',
        f'    android:viewportHeight="{vh}">',
    ]
    for fill, d in paths:
        lines += ["    <path", f'        android:fillColor="{fill}"', f'        android:pathData="{d}" />']
    lines += ["</vector>", ""]
    return "\n".join(lines)


def main(src_root, dest):
    src_root, dest = pathlib.Path(src_root), pathlib.Path(dest)
    count = 0
    for svg in sorted(src_root.rglob("*.svg")):
        rel = svg.relative_to(src_root).as_posix()
        group, stem = rel.split("/")[0], svg.stem.replace("-", "_")
        if group == "SocialLogin":
            continue  # Apple 로그인은 옮기지 않는다
        name = {"Icon": f"ic_{stem}", "TabBar": stem, "Logo": stem}[group]
        (dest / f"{name}.xml").write_text(convert(svg, rel), encoding="utf-8")
        count += 1
    print(f"변환 {count}개")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
