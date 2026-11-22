#!/usr/bin/env python3
"""차 WebView(Chrome 30)용 clamp() 예비 값 넣기.

Chrome 30 은 clamp() 를 모르므로 그 선언을 버리고 앞의 예비 값(대개 작은 px)을 쓴다. 그래서 차에서만
글자·간격이 작아지거나 어긋났다. 이 도구는 `속성: ...clamp(A, B, C)...` 선언마다 바로 앞에 차 화면 폭
(1280 CSS px)에서 계산한 값을 같은 속성으로 넣는다. 최신 엔진은 뒤의 clamp() 를, 차는 계산값을 쓴다.
이미 넣은 자리는 건너뛰므로 여러 번 돌려도 같다.

사용: python tools/legacy_clamp.py assets/index.html [--check]
"""
import re
import sys

VIEWPORT = 1280.0
MARK = '/*c30*/'
CLAMP = re.compile(r'clamp\(\s*([^,()]+?)\s*,\s*([^,()]+?)\s*,\s*([^,()]+?)\s*\)')
DECL = re.compile(r'(?P<lead>[;{"\s])(?P<prop>[a-z-]+)\s*:\s*(?P<val>[^;{}"<>]*clamp\([^;{}"<>]*?)(?=[;}"])')


def px(term):
    term = term.strip()
    m = re.fullmatch(r'(-?[\d.]+)(px|vw|vh)?', term)
    if not m:
        raise ValueError('unsupported clamp term: ' + term)
    n = float(m.group(1))
    unit = m.group(2) or 'px'
    if unit == 'vw':
        return n * VIEWPORT / 100.0
    if unit == 'vh':
        raise ValueError('vh in clamp is not supported: ' + term)
    return n


def resolve(value):
    def one(m):
        lo, mid, hi = (px(t) for t in m.groups())
        v = min(max(lo, mid), hi)
        return ('%.2f' % v).rstrip('0').rstrip('.') + 'px'
    return CLAMP.sub(one, value)


def convert(text):
    out, pos, count = [], 0, 0
    for m in DECL.finditer(text):
        start = m.start('prop')
        before = text[max(0, start - 200):start]
        if before.rstrip().endswith(MARK):
            continue  # already done
        out.append(text[pos:start])
        out.append('%s: %s; %s ' % (m.group('prop'), resolve(m.group('val')).strip(), MARK))
        pos = start
        count += 1
    out.append(text[pos:])
    return ''.join(out), count


def main():
    path = sys.argv[1]
    text = open(path, encoding='utf-8').read()
    new, count = convert(text)
    if '--check' in sys.argv:
        print('%s: %d clamp() declarations without a Chrome 30 value' % (path, count))
        sys.exit(1 if count else 0)
    open(path, 'w', encoding='utf-8', newline='').write(new)
    print('%s: added %d Chrome 30 values' % (path, count))


if __name__ == '__main__':
    main()
