#!/usr/bin/env python3
"""
يبني أصول علامة «رَنّة» كلها من هندسة واحدة.

العلامة هي نون «رَنّة»: حوضٌ واحد بقلم متفاوت العرض، ونقطته. تُرسم مرة واحدة
هنا على شبكة ٢٤×٢٤، ثم تُكتب حرفيًا في كل ملف تحتاجها: أيقونة التطبيق،
والطبقة أحادية اللون، وأيقونة الإشعار، وشاشة البداية، وأصول المستودع.

    python3 tools/brand/build_brand.py            # يكتب الأصول
    python3 tools/brand/build_brand.py --check    # يتحقق دون كتابة

يحتاج rsvg-convert وPillow للقياس فقط. الملفات المكتوبة لا تعتمد عليهما.
اختبار `MarkGeometryTest` يمنع انحراف أي نسخة عن هذه الهندسة.
"""
from __future__ import annotations

import argparse
import math
import os
import subprocess
import sys
import tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
ASSETS = os.path.join(ROOT, "docs", "assets")

# ---------------------------------------------------------------- الألوان
# القيم نفسها الموجودة في res/values/colors.xml وui/theme/Theme.kt.
INK = "#0F4C7A"        # brandPrimary: النهار
INK_NIGHT = "#8ACBEF"  # brandPrimary: الليل
PAPER = "#F6F3EC"      # surfaceBase: النهار
NIGHT = "#0F1418"      # surfaceBase: الليل

# ---------------------------------------------------------------- الهندسة
# حوض النون: قوس مركزه (12, 12.4) ونصف قطره 7.3، من ‎-26°‎ إلى ‎214°‎،
# بعرض يبدأ رفيعًا عند المدخل ويثقل عند القاع ثم يخفّ عند المخرج. النقطة
# فوق المخرج لا فوق مركز الحوض، فيصير المحور قطريًا ولا يُقرأ الشكل وجهًا.
BOWL = dict(cx=12.0, cy=12.4, r=7.3, a0=-26.0, a1=214.0)
PROFILE = [(0.0, 2.6), (0.25, 4.3), (0.52, 4.9), (0.8, 4.3), (1.0, 3.1)]
DOT = (17.9, 3.5, 2.85)

# صندوق العلامة النهائي على الشبكة: ٢٠ وحدة، مركزها (12, 12).
CANON_BOX = 20.0

# عدد العيّنات يوازن بين نعومة الحوض وطول المسار. عند ٩ عيّنات يصير المسار
# ٩٥٣ محرفًا وهو مطابق بصريًا لمرجع ١٥ عيّنة عند ٣٠٠ بكسل؛ ودونها يبدأ قاع
# الحوض في التسطّح ويظهر ضلعيًا.
SAMPLES = 9
CAP_POINTS = 4


def fmt(v: float) -> str:
    return f"{round(v, 2):g}"


def _catmull(pts, alpha=0.5):
    """
    Catmull-Rom مركزي (centripetal) محوَّل إلى منحنيات تكعيبية.

    التوسيط المنتظم ينتج التواءً عند تغيّر تباعد العيّنات فجأة، وهو ما يحدث
    عند طرفَي القلم حيث تلتقي أضلاع مرسومة كل ٢٠° بأنصاف دوائر مرسومة كل ٣٠°.
    التوسيط المركزي مضمون بلا عُقَد ولا رجوع، فيبقى الطرف نصف دائرة نظيفة.
    """
    n = len(pts)
    seq = [pts[-1]] + pts + [pts[0], pts[1]]
    out = []
    for i in range(n):
        p0, p1, p2, p3 = seq[i], seq[i + 1], seq[i + 2], seq[i + 3]
        d1 = math.dist(p1, p0) ** alpha or 1e-6
        d2 = math.dist(p2, p1) ** alpha or 1e-6
        d3 = math.dist(p3, p2) ** alpha or 1e-6
        c1 = tuple(
            (d1 * d1 * p2[k] - d2 * d2 * p0[k]
             + (2 * d1 * d1 + 3 * d1 * d2 + d2 * d2) * p1[k]) / (3 * d1 * (d1 + d2))
            for k in (0, 1)
        )
        c2 = tuple(
            (d3 * d3 * p1[k] - d2 * d2 * p3[k]
             + (2 * d3 * d3 + 3 * d3 * d2 + d2 * d2) * p2[k]) / (3 * d3 * (d3 + d2))
            for k in (0, 1)
        )
        out.append((c1, c2, p2))
    return out


def _closed(pts):
    d = [f"M{fmt(pts[0][0])},{fmt(pts[0][1])}"]
    for c1, c2, p in _catmull(pts):
        d.append(f"C{fmt(c1[0])},{fmt(c1[1])} {fmt(c2[0])},{fmt(c2[1])} {fmt(p[0])},{fmt(p[1])}")
    d.append("Z")
    return " ".join(d)


def _width_at(t: float) -> float:
    for j in range(len(PROFILE) - 1):
        t0, w0 = PROFILE[j]
        t1, w1 = PROFILE[j + 1]
        if t0 <= t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return w0 + (w1 - w0) * (f * f * (3 - 2 * f))
    return PROFILE[-1][1]


def _bowl_outline():
    a0, a1 = math.radians(BOWL["a0"]), math.radians(BOWL["a1"])
    cx, cy, r = BOWL["cx"], BOWL["cy"], BOWL["r"]
    centre, normal, half = [], [], []
    for i in range(SAMPLES):
        t = i / (SAMPLES - 1)
        a = a0 + (a1 - a0) * t
        centre.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        normal.append((math.cos(a), math.sin(a)))
        half.append(_width_at(t) / 2.0)

    left = [(centre[i][0] + normal[i][0] * half[i], centre[i][1] + normal[i][1] * half[i])
            for i in range(SAMPLES)]
    right = [(centre[i][0] - normal[i][0] * half[i], centre[i][1] - normal[i][1] * half[i])
             for i in range(SAMPLES)]

    def cap(idx, phase):
        """
        نصف دائرة تصل الضلع الخارجي بالداخلي عند طرف القلم.

        الضلع الخارجي عند الزاوية `base` والداخلي عند `base + π`، والقوس بينهما
        يمرّ بنقطة المماس. لذلك يبدأ طرف النهاية من `base` وطرف البداية من
        `base + π`: بغير هذه الإزاحة يقفز المسار عبر جسم القلم فينشأ نتوء.
        """
        c, h = centre[idx], half[idx]
        base = math.atan2(normal[idx][1], normal[idx][0]) + phase
        return [(c[0] + h * math.cos(base + math.pi * k / CAP_POINTS),
                 c[1] + h * math.sin(base + math.pi * k / CAP_POINTS))
                for k in range(1, CAP_POINTS)]

    return left + cap(SAMPLES - 1, 0.0) + list(reversed(right)) + cap(0, math.pi)


def _circle(cx, cy, r):
    k = 0.55228 * r
    return (
        f"M{fmt(cx)},{fmt(cy - r)} "
        f"C{fmt(cx + k)},{fmt(cy - r)} {fmt(cx + r)},{fmt(cy - k)} {fmt(cx + r)},{fmt(cy)} "
        f"C{fmt(cx + r)},{fmt(cy + k)} {fmt(cx + k)},{fmt(cy + r)} {fmt(cx)},{fmt(cy + r)} "
        f"C{fmt(cx - k)},{fmt(cy + r)} {fmt(cx - r)},{fmt(cy + k)} {fmt(cx - r)},{fmt(cy)} "
        f"C{fmt(cx - r)},{fmt(cy - k)} {fmt(cx - k)},{fmt(cy - r)} {fmt(cx)},{fmt(cy - r)} Z"
    )


def _measure(path: str):
    """يقيس صندوق الحبر وأبعد نقطة عن مركز الشبكة، بالرسم الفعلي."""
    from PIL import Image

    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "m.svg")
        png = os.path.join(tmp, "m.png")
        with open(src, "w") as fh:
            fh.write(
                '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" '
                f'width="24" height="24"><path d="{path}" fill="#000"/></svg>'
            )
        subprocess.run(["rsvg-convert", "-w", "960", "-h", "960", src, "-o", png], check=True)
        img = Image.open(png).convert("RGBA")
        alpha = img.split()[3]
        x0, y0, x1, y1 = [v * 24.0 / 960 for v in alpha.getbbox()]
        px = alpha.load()
        max_r = 0.0
        for X in range(0, 960, 2):
            for Y in range(0, 960, 2):
                if px[X, Y] > 96:
                    max_r = max(max_r, math.hypot(X * 24.0 / 960 - 12, Y * 24.0 / 960 - 12))
    return (x0, y0, x1, y1), max_r


def _transform(path: str, scale: float, dx: float, dy: float) -> str:
    import re

    def sub(m):
        x = float(m.group(1)) * scale + dx
        y = float(m.group(2)) * scale + dy
        return f"{fmt(x)},{fmt(y)}"

    return re.sub(r"(-?\d+\.?\d*),(-?\d+\.?\d*)", sub, path)


def canonical_path() -> tuple[str, float]:
    """المسار المُعاير على الشبكة ٢٤، وأبعد نقطة فيه عن المركز."""
    raw = _closed(_bowl_outline()) + " " + _circle(*DOT)
    (x0, y0, x1, y1), _ = _measure(raw)
    scale = CANON_BOX / max(x1 - x0, y1 - y0)
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    path = _transform(raw, scale, 12.0 - cx * scale, 12.0 - cy * scale)
    _, max_r = _measure(path)
    return path, max_r


# ---------------------------------------------------------------- الكتابة

VECTOR = """<?xml version="1.0" encoding="utf-8"?>
<!--
{note}

  مولَّد من tools/brand/build_brand.py: لا يُحرَّر يدويًا. الهندسة نفسها في
  كل نسخة، ويثبتها اختبار MarkGeometryTest.

  VectorPath: المسار ٩٥٣ محرفًا، فوق حدّ lint البالغ ٨٠٠. اختُصر من ١٥٠١ بخفض
  عدد العيّنات إلى أدنى قيمة تبقى مطابقة بصريًا؛ ودونها يتسطّح قاع الحوض.
  والمسار يُحلَّل مرة واحدة عند التضخيم ثم يُخزَّن Path مُهيّأً، وهذه الأيقونات
  لا تُرسم داخل قائمة تُمرَّر، فالكلفة الباقية لحظة واحدة عند الإقلاع.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="{size}"
    android:viewportHeight="{size}"
    tools:ignore="{ignore}">
    <group
        android:scaleX="{scale}"
        android:scaleY="{scale}"
        android:translateX="{tx}"
        android:translateY="{ty}">
        <path
            android:fillColor="{fill}"
            android:pathData="{path}" />
    </group>
</vector>
"""

SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="{px}" height="{px}" role="img" aria-label="رَنّة">
  <title>رَنّة</title>
  <path d="{path}" fill="{fill}"/>
</svg>
"""

KOTLIN = '''package com.bal.reminders.ui.components

// مولَّد من tools/brand/build_brand.py: لا يُحرَّر يدويًا.

/**
 * هندسة علامة «رَنّة» على شبكة ٢٤×٢٤: حوض النون بقلم متفاوت العرض، ونقطته.
 *
 * هذا هو المصدر الوحيد للشكل. ملفات `res/drawable` الأربعة تحمل النصّ نفسه
 * حرفيًا، ويثبت `MarkGeometryTest` أنها لم تنحرف عنه. لا يُحرَّر أي منها يدويًا:
 * يُعدَّل المولِّد ويُعاد تشغيله.
 */
internal const val MARK_PATH_DATA =
{lines}
'''


def _kotlin_literal(path: str, width: int = 84) -> str:
    """يقسّم المسار إلى أسطر مضمومة، بلا تغيير في محتواه."""
    lines, current = [], ""
    for token in path.split(" "):
        if current and len(current) + 1 + len(token) > width:
            lines.append(current)
            current = token
        else:
            current = f"{current} {token}".strip()
    if current:
        lines.append(current)
    body = [f'    "{lines[0]} " +']
    for line in lines[1:-1]:
        body.append(f'        "{line} " +')
    body.append(f'        "{lines[-1]}"')
    return "\n".join(body)


def vector(note, size, scale, fill, path, ignore="VectorPath"):
    """يضع العلامة في مركز لوحة `size` بمقياس `scale`."""
    offset = (size - 24.0 * scale) / 2.0
    return VECTOR.format(note=note, size=size, scale=fmt(scale), tx=fmt(offset),
                         ty=fmt(offset), fill=fill, path=path, ignore=ignore)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="قارن دون كتابة")
    args = ap.parse_args()

    path, max_r = canonical_path()

    # الأيقونة التكيفية: المحتوى داخل دائرة ٦٦dp من لوحة ١٠٨dp، أي نصف قطر ٣٣dp.
    # يُترك هامش حتى لا تلامس العلامة حدّ المنطقة الآمنة على أقنعة الشركات.
    launcher_scale = min(50.0 / CANON_BOX, 31.0 / max_r)
    # شاشة البداية: لوحة ٢٨٨dp، والمساحة المرئية للأيقونة بلا خلفية ١٩٢dp.
    splash_scale = min(152.0 / CANON_BOX, (192.0 / 2) / max_r)

    print(f"canonical path: {len(path)} chars")
    print(f"max ink radius on the 24 grid: {max_r:.2f}")
    print(f"launcher scale {launcher_scale:.3f} -> ink {CANON_BOX * launcher_scale:.1f}dp, "
          f"reach {max_r * launcher_scale:.1f}dp of the 33dp safe radius")
    print(f"splash scale   {splash_scale:.3f} -> ink {CANON_BOX * splash_scale:.1f}dp")

    files = {
        os.path.join(RES, "drawable", "ic_launcher_foreground.xml"): vector(
            "  طبقة الواجهة للأيقونة التكيفية. لا مربّع ولا دائرة مرسومة هنا:\n"
            "  القناع من النظام وحده، والعلامة داخل المنطقة الآمنة (دائرة ٦٦dp).",
            108, launcher_scale, "@color/brand_on_ink", path),
        os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"): vector(
            "  الطبقة أحادية اللون للأيقونات المُوحَّدة (أندرويد ١٣ فما فوق).\n"
            "  ملف مستقل بلون واحد مصمت، لا نسخة ملوّنة يُعاد استخدامها.",
            108, launcher_scale, "#FFFFFFFF", path),
        os.path.join(RES, "drawable", "ic_notification.xml"): vector(
            "  أيقونة شريط الحالة: يقرأ النظام قناة الشفافية ويضع تلوينه،\n"
            "  فالشكل أبيض مصمت على خلفية شفافة.",
            24, 1.0, "#FFFFFFFF", path),
        os.path.join(RES, "drawable", "ic_splash.xml"): vector(
            "  شاشة البداية: لوحة ٢٨٨dp، والعلامة داخل ١٩٢dp المرئية.\n"
            "  اللون يتبع المظهر، والخلفية هي أرضية التطبيق نفسها.\n\n"
            "  VectorRaster: ٢٨٨dp ليست اختيارًا بل المقاس الذي تنصّ عليه\n"
            "  windowSplashScreenAnimatedIcon؛ وهي تُرسم إطارًا واحدًا عند الإقلاع.",
            288, splash_scale, "@color/splash_mark", path,
            ignore="VectorPath,VectorRaster"),
        os.path.join(ASSETS, "rannah-mark.svg"): SVG.format(px=512, path=path, fill=INK),
        os.path.join(ASSETS, "rannah-mark-dark.svg"): SVG.format(px=512, path=path,
                                                                 fill=INK_NIGHT),
        os.path.join(ROOT, "app", "src", "main", "java", "com", "bal", "reminders",
                     "ui", "components", "MarkGeometry.kt"): KOTLIN.format(
            lines=_kotlin_literal(path)),
    }

    changed = []
    for dest, body in files.items():
        old = None
        if os.path.exists(dest):
            with open(dest, encoding="utf-8") as fh:
                old = fh.read()
        if old != body:
            changed.append(os.path.relpath(dest, ROOT))
            if not args.check:
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                with open(dest, "w", encoding="utf-8") as fh:
                    fh.write(body)

    if not args.check:
        for name, fill, bg in (("rannah-mark.png", INK, PAPER),
                               ("rannah-mark-dark.png", INK_NIGHT, NIGHT)):
            with tempfile.TemporaryDirectory() as tmp:
                src = os.path.join(tmp, "a.svg")
                with open(src, "w", encoding="utf-8") as fh:
                    fh.write(SVG.format(px=512, path=path, fill=fill))
                subprocess.run(["rsvg-convert", "-w", "384", "-h", "384", "-b", bg,
                                src, "-o", os.path.join(ASSETS, name)], check=True)

    print("\nCANONICAL_PATH for Kotlin:\n" + path)
    if args.check:
        print(f"\n{'drift: ' + ', '.join(changed) if changed else 'no drift'}")
        return 1 if changed else 0
    print(f"\nwrote: {', '.join(changed) if changed else '(nothing changed)'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
