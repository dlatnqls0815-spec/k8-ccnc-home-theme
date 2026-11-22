# -*- coding: utf-8 -*-
"""차 WebView(Chrome 30)에서 깨지는 CSS 사용을 찾아 낸다.

차의 WebView 는 Chrome 30 이라 다음 기능이 없다(무시되거나 크기가 사라진다).
  - clamp(), min(), max()      : 대체 값이 없으면 크기가 사라진다
  - flex gap                   : 무시된다
  - calc() 안의 vw             : 무시된다
  - button 안의 inline-flex    : 무너진다
  - :focus-visible             : 지원하지 않는다(button:focus{outline:none} 을 쓴다)
  - SVG 크기 이중 지정(인라인 + CSS): 그림이 잘린다
  - SVG 의 .hidden 속성        : 없다(setAttribute/removeAttribute 를 쓴다)

`clamp()`·`min()`·`max()`·`gap` 은 같은 줄에 `/*c30*/` px 대체 값이 있으면 통과시킨다
(tools/legacy_clamp.py --check 와 같은 규칙).

사용법:  python tools/c30_check.py [대상파일...]
대상 기본값: assets/*.html assets/*.css assets/*.js
끝나면 걸린 것이 하나라도 있을 때 종료 코드 1, 없으면 0.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'assets')
MARK = '/*c30*/'

RE_STYLE = re.compile(r'<style[^>]*>(?P<css>.*?)</style>', re.S | re.I)
RE_CLAMP = re.compile(r'(?<![\w-])(clamp|min|max)\s*\(')
RE_GAP = re.compile(r'(^|[\s;{])(row-gap|column-gap|grid-gap|gap)\s*:\s*([^;}]*)')
RE_CALC_VW = re.compile(r'calc\([^)]*vw')
RE_FOCUS_VISIBLE = re.compile(r':focus-visible')
RE_INLINE_FLEX = re.compile(r'display\s*:\s*inline-flex')
RE_SVG_HIDDEN = re.compile(r'\.hidden\s*=')
RE_INLINE_SVG_SIZE = re.compile(r'<svg\b[^>]*\bwidth\s*=\s*["\']?\d', re.I)
RE_SVG_ID = re.compile(r'<svg\b[^>]*\bid\s*=\s*["\']([^"\']+)["\']', re.I)
RE_RULE = re.compile(r'(?P<sel>[^{}]*)\{(?P<body>[^{}]*)\}')


def target_files(extra):
    files = list(extra)
    if not files:
        if not os.path.isdir(ASSETS):
            return []
        for name in sorted(os.listdir(ASSETS)):
            if name.endswith(('.html', '.css', '.js')):
                files.append(os.path.join(ASSETS, name))
    out = []
    for f in files:
        if not os.path.isabs(f):
            f = os.path.join(ROOT, f)
        if os.path.isfile(f) and f.endswith(('.html', '.css', '.js')):
            out.append(f)
    return out


def css_blocks(path, text):
    """(css 텍스트, 파일 내 절대 시작 위치) 목록."""
    if path.endswith('.css'):
        return [(text, 0)]
    if path.endswith('.js'):
        return []
    return [(m.group('css'), m.start('css')) for m in RE_STYLE.finditer(text)]


def has_fallback(line, value_start):
    """같은 줄에 /*c30*/ 가 있으면(값 앞) 통과."""
    return MARK in line[:value_start]


def check(path):
    findings = []
    with io.open(path, encoding='utf-8', errors='replace') as handle:
        text = handle.read()
    svg_ids = set()
    for match in re.finditer(r'<svg\b[^>]*>', text, re.I):
        if RE_INLINE_SVG_SIZE.search(match.group(0)):
            found = RE_SVG_ID.search(match.group(0))
            if found:
                svg_ids.add(found.group(1))
    for css, base in css_blocks(path, text):
        comment_spans = [(m.start(), m.end()) for m in re.finditer(r'/\*.*?\*/', css, flags=re.S)]
        for rm in RE_RULE.finditer(css):
            if any(a <= rm.start() < b for a, b in comment_spans):
                continue  # 주석 안의 예시
            selector = rm.group('sel').strip()
            body = rm.group('body')
            if not selector or selector.startswith('@'):
                continue
            if '/*' in css[rm.start():rm.end()]:
                continue  # 주석이 섞인 규칙
            if 'c30-ok' in css[rm.start():css.find('\n', rm.end()) if css.find('\n', rm.end()) > 0 else len(css)]:
                continue  # 근거가 있는 예외(사유가 주석으로 적혀 있다)
            line_no = css[:rm.start()].count('\n') + 1
            # clamp/min/max: 값 앞에 /*c30*/ 대체 값이 있으면 통과
            for m in RE_CLAMP.finditer(body):
                if not has_fallback(body, m.start()):
                    findings.append((line_no, 'clamp/min/max 대체 값 없음',
                                     (selector + ' { ' + body.strip()[:100] + ' }')))
                    break
            # gap: 0 은 간격이 없어서 영향이 없다. /*c30*/ 대체 값이 있으면 통과.
            for m in RE_GAP.finditer(body):
                value = m.group(3).strip()
                if value in ('0', '0px', '0%'):
                    continue
                if has_fallback(body, m.start()):
                    continue
                findings.append((line_no, 'flex gap (Chrome 30 은 무시)',
                                 (selector + ' { ' + body.strip()[:100] + ' }')))
                break
            if RE_CALC_VW.search(body):
                findings.append((line_no, 'calc() 안의 vw (Chrome 30 은 무시)',
                                 (selector + ' { ' + body.strip()[:100] + ' }')))
            if RE_FOCUS_VISIBLE.search(body) and 'outline:none' not in body.replace(' ', ''):
                findings.append((line_no, ':focus-visible (Chrome 30 은 지원 안 함)',
                                 (selector + ' { ' + body.strip()[:100] + ' }')))
            if RE_INLINE_FLEX.search(body) and re.search(r'button[^{}]*[ >]', selector):
                findings.append((line_no, 'button 안쪽 inline-flex (무너짐)',
                                 (selector + ' { ' + body.strip()[:100] + ' }')))
        # SVG 크기 이중 지정
        for i, line in enumerate(css.split('\n')):
            if line.strip().startswith('/*') or line.strip().startswith('*'):
                continue
            for svg_id in svg_ids:
                if ('#' + svg_id) in line and re.search(r'(^|[;{\s])(width|height)\s*:', line):
                    findings.append((i + 1, 'SVG 크기 이중 지정(인라인 + CSS, 잘림)', line.strip()[:100]))
                    break
    if path.endswith('.js') or path.endswith('.html'):
        for i, line in enumerate(text.split('\n')):
            if RE_SVG_HIDDEN.search(line) and 'svg' in line.lower():
                findings.append((i + 1, 'SVG 에 .hidden 사용 가능성', line.strip()[:100]))
    return findings


def main():
    files = target_files(sys.argv[1:])
    if not files:
        sys.stderr.write('대상 파일을 찾지 못했다.\n')
        return 2
    total = 0
    for path in files:
        rel = os.path.relpath(path, ROOT)
        for line, kind, text in check(path):
            total += 1
            sys.stdout.write('%s:%d  %s  %s\n' % (rel, line, kind, text))
    sys.stdout.write('\n검사한 파일 %d개, 걸린 것 %d개\n' % (len(files), total))
    return 1 if total else 0


if __name__ == '__main__':
    sys.exit(main())
