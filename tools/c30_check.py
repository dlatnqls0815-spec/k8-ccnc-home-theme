#!/usr/bin/env python3
"""차 WebView(Chrome 30)에서 화면 비율을 깨는 CSS·JS 찾기.

PC 브라우저에서는 멀쩡한데 차에서만 크기가 사라지거나 줄이 꺾이거나 그림이 잘린 원인을 모았다.
assets/ 의 HTML(<style>, style="", <script>)과 JS 를 읽어 걸리는 곳을 파일:줄로 보여 주고,
하나라도 있으면 실패 코드(1)로 끝난다. build.py 가 APK 를 만들기 전에 이 검사를 돌린다.

예비 값 규칙: 같은 블록 안에서 바로 앞 선언이 같은 속성이고 Chrome 30 이 아는 값이면 통과
(차는 앞의 값을, 새 엔진은 뒤의 값을 쓴다). tools/legacy_clamp.py 가 넣는 `/*c30*/` 값이 이 모양이다.
한 줄을 일부러 남길 때는 그 줄에 `c30-ok` 를 적는다.

사용: python tools/c30_check.py [파일 ...]   (파일을 안 주면 assets/ 전체)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'assets'

# 값 안에 있으면 Chrome 30 이 선언 전체를 버리는 것. 앞에 예비 값이 있으면 통과.
VALUE_RULES = [
    (re.compile(r'\bclamp\('), 'clamp() 는 무시된다'),
    (re.compile(r'(?<![\w-])(min|max)\('), 'min()/max() 는 무시된다'),
    (re.compile(r'calc\([^;]*?\d(vw|vh|vmin|vmax)'), 'calc() 안의 vw/vh 는 무시된다'),
    (re.compile(r'\bvar\(--'), 'CSS 변수(var) 는 무시된다'),
]
# 이 속성은 Chrome 30 이 모른다.
PROP_RULES = {
    'gap': 'flex/grid gap 은 무시된다(margin 으로)',
    'row-gap': 'row-gap 은 무시된다',
    'column-gap': 'column-gap 은 무시된다',
    'aspect-ratio': 'aspect-ratio 는 무시된다(고정 크기로)',
    'inset': 'inset 은 무시된다(top/right/bottom/left 로)',
    'place-items': 'place-items 는 무시된다',
    'place-content': 'place-content 는 무시된다',
    'object-fit': 'object-fit 은 무시된다(Chrome 31부터)',
    'backdrop-filter': 'backdrop-filter 는 무시된다',
}
# 접두어 없는 이름을 모르는 속성: 같은 블록에 -webkit- 짝이 있어야 한다.
PREFIX_PROPS = {'transform', 'transform-origin', 'animation', 'animation-name', 'animation-duration',
                'animation-delay', 'animation-timing-function', 'animation-iteration-count',
                'animation-fill-mode', 'animation-direction', 'animation-play-state', 'filter',
                'mask', 'mask-image', 'clip-path', 'user-select', 'appearance', 'backface-visibility',
                'perspective', 'box-decoration-break'}
DISPLAY_BAD = {'grid': 'display:grid 는 모른다', 'inline-grid': 'display:inline-grid 는 모른다',
               'contents': 'display:contents 는 모른다', 'flow-root': 'display:flow-root 는 모른다'}
SELECTOR_RULES = [
    (re.compile(r':focus-within'), ':focus-within 는 모른다'),
    (re.compile(r':(is|where|has)\('), ':is()/:where()/:has() 는 모른다'),
    (re.compile(r':not\([^)]*[,\s>+~][^)]*\)'), ':not() 안에 복합 선택자는 모른다'),
]
POSITION_BAD = {'sticky': 'position:sticky 는 모른다'}
JUSTIFY_BAD = re.compile(r'\b(space-evenly)\b')

# 스크립트(ES5 만 된다).
JS_RULES = [
    (re.compile(r'=>'), '화살표 함수(=>) 는 안 된다'),
    (re.compile(r'(?<![\w$.])(let|const)\s+[\w$\[{]'), 'let/const 는 엄격 모드 밖에서 안 된다'),
    (re.compile(r'`'), '템플릿 문자열(`) 은 안 된다'),
    (re.compile(r'(?<![\w$.])class\s+[A-Z]\w*\s*(extends|\{)'), 'class 문법은 안 된다'),
    (re.compile(r'(?<![\w$])(Promise|fetch|Symbol|Map|Set|WeakMap)\s*[.(]'), 'Promise/fetch/Map/Set 은 없다'),
    (re.compile(r'\bObject\.(assign|entries|values)\('), 'Object.assign/entries/values 는 없다'),
    (re.compile(r'\.(includes|startsWith|endsWith|padStart|padEnd|repeat|find|findIndex)\('),
     'ES6 문자열·배열 메서드는 없다'),
    (re.compile(r'\b(Array\.from|Array\.of|Number\.isNaN|Number\.isFinite)\('), 'ES6 내장 함수는 없다'),
    (re.compile(r'querySelectorAll\([^)]*\)\s*\.forEach'), 'NodeList.forEach 는 없다'),
    (re.compile(r'\.(closest|matches|append|prepend|remove|before|after|replaceWith)\(\s*\)?'),
     None),  # 요소 메서드: 아래에서 따로 본다
    (re.compile(r'\.\.\.\w'), '펼침 문법(...) 은 안 된다'),
    (re.compile(r'\?\.|\?\?'), '?. / ?? 는 안 된다'),
]
ELEMENT_METHODS = re.compile(r'\.(closest|append|prepend|replaceWith)\(')


class Finding:
    def __init__(self, path, line, text, why):
        self.path, self.line, self.text, self.why = path, line, text.strip(), why

    def __str__(self):
        return '%s:%d: %s\n    %s' % (self.path, self.line, self.why, self.text[:160])


def line_of(src, pos):
    return src.count('\n', 0, pos) + 1


def strip_comments(css):
    # 주석을 같은 길이의 공백으로(줄 번호 유지).
    return re.sub(r'/\*.*?\*/', lambda m: re.sub(r'[^\n]', ' ', m.group(0)), css, flags=re.S)


def legacy_value(prop, value):
    """이 선언을 Chrome 30 이 받아들이나."""
    if any(rx.search(value) for rx, _ in VALUE_RULES):
        return False
    if prop in PROP_RULES:
        return False
    return True


LEGACY = {'text': '', 'zero_gap': ''}


def legacy_covers(selector):
    """차 전용(.legacy) 규칙이나 뒤의 gap:0 규칙이 이 선택자의 모든 부분을 대신하나."""
    if not selector:
        return False
    for part in selector.split(','):
        part = ' '.join(part.split())
        if not part:
            continue
        key = part.split()[-1]
        pat = r'(^|[\s,]){0}(?=[\s,>:.#\[]|$)'.format(re.escape(key))
        if not (re.search(r'\.legacy [^,]*' + re.escape(key) + r'(?=[\s,>:.#\[]|$)', LEGACY['text'])
                or re.search(pat, LEGACY['zero_gap'])):
            return False
    return True


def check_decls(path, src, start, body, findings, raw_lines, selector=None):
    """한 블록의 선언 목록. start 는 body 가 시작하는 src 위치."""
    decls = []
    for m in re.finditer(r'([a-zA-Z-]+)\s*:\s*([^;]*)', body):
        prop = m.group(1).lower()
        value = m.group(2).strip()
        decls.append((prop, value, start + m.start()))
    props = {d[0] for d in decls}
    for i, (prop, value, pos) in enumerate(decls):
        ln = line_of(src, pos)
        text = raw_lines[ln - 1] if ln - 1 < len(raw_lines) else ''
        if 'c30-ok' in text:
            continue
        if prop in PREFIX_PROPS and ('-webkit-' + prop) in props:
            continue  # 차는 접두어 없는 이 선언을 원래 버리고 -webkit- 쪽을 쓴다
        bad = [why for rx, why in VALUE_RULES if rx.search(value)]
        if prop in PROP_RULES and not re.fullmatch(r'0(px)?', value):
            bad.append(PROP_RULES[prop])
        if bad:
            prev = decls[i - 1] if i else None
            if prev and prev[0] == prop and legacy_value(prev[0], prev[1]):
                continue  # 예비 값이 앞에 있다
            if prop in ('gap', 'row-gap', 'column-gap') and legacy_covers(selector):
                continue  # 차는 .legacy 자식 margin 규칙(또는 뒤의 gap:0)을 쓴다
            findings.append(Finding(path, ln, text, ', '.join(bad)))
            continue
        if prop in PREFIX_PROPS and ('-webkit-' + prop) not in props and value not in ('none', 'auto', 'initial'):
            findings.append(Finding(path, ln, text, '%s 는 -webkit-%s 짝이 있어야 한다' % (prop, prop)))
        if prop == 'display':
            v = value.replace('!important', '').strip()
            if v in DISPLAY_BAD:
                prev = decls[i - 1] if i else None
                if not (prev and prev[0] == 'display' and prev[1] not in DISPLAY_BAD) and not legacy_covers(selector):
                    findings.append(Finding(path, ln, text, DISPLAY_BAD[v]))
        if prop == 'position' and value.replace('!important', '').strip() in POSITION_BAD:
            findings.append(Finding(path, ln, text, POSITION_BAD['sticky']))
        if prop in ('justify-content', 'align-content') and JUSTIFY_BAD.search(value):
            findings.append(Finding(path, ln, text, 'space-evenly 는 모른다(space-around 로)'))


def check_css(path, src, offset_src, offset, findings, raw_lines):
    """css 텍스트(주석 제거본). offset_src 는 줄 번호 계산용 원본, offset 은 그 안의 시작 위치."""
    css = strip_comments(src)
    # @keyframes 는 접두어 짝이 있어야 한다.
    for m in re.finditer(r'@keyframes\s+([\w-]+)', css):
        if not re.search(r'@-webkit-keyframes\s+' + re.escape(m.group(1)) + r'\b', css):
            ln = line_of(offset_src, offset + m.start())
            findings.append(Finding(path, ln, raw_lines[ln - 1], '@keyframes 는 @-webkit-keyframes 짝이 있어야 한다'))
    depth_stack = []
    pos = 0
    sel_start = 0
    for m in re.finditer(r'[{}]', css):
        if m.group(0) == '{':
            selector = css[sel_start:m.start()]
            depth_stack.append((selector, m.end()))
            sel_start = m.end()
        else:
            if not depth_stack:
                continue
            selector, body_start = depth_stack.pop()
            body = css[body_start:m.start()]
            if '{' not in body:
                sel = selector.strip()
                if not sel.startswith('@'):
                    for rx, why in SELECTOR_RULES:
                        sm = rx.search(sel)
                        if sm:
                            ln = line_of(offset_src, offset + body_start - len(selector) + selector.find(sm.group(0)))
                            if 'c30-ok' not in raw_lines[ln - 1]:
                                findings.append(Finding(path, ln, raw_lines[ln - 1], why))
                    if re.search(r'display\s*:\s*inline-flex', body) and re.search(r'\bbutton\b', sel) \
                            and not legacy_covers(sel):
                        ln = line_of(offset_src, offset + body_start)
                        if 'c30-ok' not in raw_lines[ln - 1]:
                            findings.append(Finding(path, ln, raw_lines[ln - 1],
                                                    'button 의 inline-flex 는 차에서 무너진다(.legacy 대체 필요)'))
                check_decls(path, offset_src, offset + body_start, body, findings, raw_lines, sel)
            sel_start = m.end()


def check_svg(path, src, css, findings, raw_lines):
    """SVG 크기 이중 지정: style="" 크기와 같은 SVG 를 겨냥한 CSS !important 크기가 같이 있으면
    차는 틀과 그림을 서로 다른 크기로 그려 잘렸다(0.5.0-test16 재생 버튼)."""
    sized = []
    for sel, body in re.findall(r'([^{}]+)\{([^{}]*)\}', strip_comments(css)):
        if re.search(r'(width|height)\s*:[^;]*!important', body):
            # 조상 없는 선택자만(조상이 있으면 그 영역 안에서만 겹치므로 화면별로 따로 본다)
            sized.extend(p.strip() for p in sel.split(',') if p.strip() and ' ' not in p.strip())
    for m in re.finditer(r'<svg\b[^>]*>', src, flags=re.I):
        tag = m.group(0)
        st = re.search(r'style\s*=\s*"([^"]*)"', tag)
        if not (st and re.search(r'(^|;)\s*(width|height)\s*:', st.group(1))):
            continue
        names = ['.' + c for c in (re.search(r'class\s*=\s*"([^"]*)"', tag) or [None, ''])[1].split()]
        idm = re.search(r'\bid\s*=\s*"([^"]+)"', tag)
        if idm:
            names.append('#' + idm.group(1))
        names.append('svg')
        hit = [k for k in sized if k in names]
        if hit:
            ln = line_of(src, m.start())
            if 'c30-ok' not in raw_lines[ln - 1]:
                findings.append(Finding(path, ln, raw_lines[ln - 1],
                                        'SVG 크기 이중 지정(style 과 CSS !important %s): 그림이 잘린다' % hit[0]))


def check_js(path, src, offset_src, offset, findings, raw_lines):
    code = re.sub(r'/\*.*?\*/', lambda m: re.sub(r'[^\n]', ' ', m.group(0)), src, flags=re.S)
    code = re.sub(r'(?m)(^|[^:\\])//[^\n]*', lambda m: m.group(1) + ' ' * (len(m.group(0)) - len(m.group(1))), code)
    # 문자열 안은 보지 않는다(CSS 문자열의 '-' 등). 같은 길이의 공백으로.
    code = re.sub(r"'(?:\\.|[^'\\\n])*'|\"(?:\\.|[^\"\\\n])*\"",
                  lambda m: m.group(0)[0] + ' ' * (len(m.group(0)) - 2) + m.group(0)[0], code)
    for rx, why in JS_RULES:
        if why is None:
            continue
        for m in rx.finditer(code):
            ln = line_of(offset_src, offset + m.start())
            if 'c30-ok' in raw_lines[ln - 1]:
                continue
            findings.append(Finding(path, ln, raw_lines[ln - 1], why))
    for m in ELEMENT_METHODS.finditer(code):
        ln = line_of(offset_src, offset + m.start())
        if 'c30-ok' not in raw_lines[ln - 1]:
            findings.append(Finding(path, ln, raw_lines[ln - 1], '요소의 %s() 는 없다' % m.group(1)))
    # SVG 요소에는 .hidden 이 없다.
    for m in re.finditer(r'(svg|Svg|SVG|icon|Icon)\w*\.hidden\s*=', code):
        ln = line_of(offset_src, offset + m.start())
        if 'c30-ok' not in raw_lines[ln - 1]:
            findings.append(Finding(path, ln, raw_lines[ln - 1], 'SVG 에는 .hidden 이 없다(setAttribute 로)'))


def check_file(path):
    src = path.read_text(encoding='utf-8')
    raw_lines = src.split('\n')
    rel = path.relative_to(ROOT).as_posix()
    findings = []
    css_all = ''.join(re.findall(r'<style[^>]*>(.*?)</style>', src, flags=re.S | re.I)) \
        if path.suffix == '.html' else (src if path.suffix == '.css' else '')
    rules = re.findall(r'([^{}]+)\{([^{}]*)\}', strip_comments(css_all))
    LEGACY['text'] = ' , '.join(' '.join(sel.split()) for sel, _ in rules if '.legacy' in sel)
    LEGACY['zero_gap'] = ' , '.join(' '.join(sel.split()) for sel, body in rules
                                    if re.search(r'(?<![\w-])gap\s*:\s*0(px)?\s*(;|$)', body))
    if path.suffix == '.html':
        for m in re.finditer(r'<style[^>]*>(.*?)</style>', src, flags=re.S | re.I):
            check_css(rel, m.group(1), src, m.start(1), findings, raw_lines)
        for m in re.finditer(r'\sstyle\s*=\s*"([^"]*)"', src):
            check_decls(rel, src, m.start(1), strip_comments(m.group(1)), findings, raw_lines)
        for m in re.finditer(r'<script(?![^>]*\bsrc=)[^>]*>(.*?)</script>', src, flags=re.S | re.I):
            check_js(rel, m.group(1), src, m.start(1), findings, raw_lines)
        check_svg(rel, src, css_all, findings, raw_lines)
    elif path.suffix == '.css':
        check_css(rel, src, src, 0, findings, raw_lines)
    elif path.suffix == '.js':
        check_js(rel, src, src, 0, findings, raw_lines)
    return findings


def main(argv):
    files = [Path(a).resolve() for a in argv] if argv else sorted(
        p for p in ASSETS.rglob('*') if p.suffix in ('.html', '.css', '.js') and not p.name.startswith('__'))
    total = []
    for f in files:
        total.extend(check_file(f))
    for f in total:
        print(f)
    print('c30_check: %d 곳' % len(total))
    return 1 if total else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
