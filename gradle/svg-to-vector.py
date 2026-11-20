"""FestiCore iOS 아이콘 SVG(svg · path · fill, 전체 캔버스 clipPath)만 VectorDrawable로 바꾼다. 다른 요소가 있으면 실패한다.

사용: python3 -I gradle/svg-to-vector.py <iOS ImageAssets.xcassets 경로> ui/designkit/src/main/res/drawable   규칙: .claude/docs/design-system.md
"""
import pathlib, sys, xml.etree.ElementTree as ET

NS = "{http://www.w3.org/2000/svg}"
COLORS = {"black": "#000000", "white": "#FFFFFF"}

def color(value):
    value = COLORS.get(value, value)
    if not (value.startswith("#") and len(value) in (7, 9)):
        raise SystemExit(f"지원하지 않는 색: {value}")
    return value.upper()

def convert(src: pathlib.Path, rel: str) -> str:
    root = ET.parse(src).getroot()
    vx, vy, vw, vh = root.get("viewBox").split()
    if (vx, vy) != ("0", "0"):
        raise SystemExit(f"viewBox 원점이 0이 아님: {src}")
    paths = []
    for el in root.iter():
        tag = el.tag.replace(NS, "")
        if tag == "path":
            fill = el.get("fill", "black")
            if fill == "none":
                raise SystemExit(f"fill 없는 path: {src}")
            paths.append((color(fill), el.get("d")))
        elif tag == "rect":  # clipPath 안의 전체 캔버스 사각형만 허용
            if el.get("width") != vw or el.get("height") != vh or el.get("x", "0") != "0" or el.get("y", "0") != "0":
                raise SystemExit(f"전체 캔버스가 아닌 clip rect: {src}")
        elif tag not in ("svg", "g", "defs", "clipPath"):
            raise SystemExit(f"지원하지 않는 요소 {tag}: {src}")
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             f"<!-- 원본: FestiCore-iOS FestiCoreDesignKit/Resources/ImageAssets.xcassets/{rel} -->",
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             f'    android:width="{root.get("width")}dp"',
             f'    android:height="{root.get("height")}dp"',
             f'    android:viewportWidth="{vw}"',
             f'    android:viewportHeight="{vh}">']
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

main(sys.argv[1], sys.argv[2])
