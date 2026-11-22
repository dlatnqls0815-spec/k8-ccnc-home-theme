"""ccNC 설정 화면 오른쪽 선 그림(CCNC_ART) 생성기. 좌표는 기아 ccNC 설명서 그림(945x354)의 오른쪽 칸 (690,28)~(945,304) 기준.
실행하면 ccnc-art.json 과 preview.html 을 현재 폴더에 만든다. 결과 JSON 을 assets/index.html 의 var CCNC_ART 에 넣는다.
설명서 그림 파일은 저장소에 넣지 않는다."""
import math, json
G = '#8e8e8e'; T = '#1fae9b'; D = '#3c3c3c'


def f(v):
    return ('%.1f' % v).rstrip('0').rstrip('.')


def rpoly(pts, r):
    n = len(pts); out = []
    for i in range(n):
        p0 = pts[i - 1]; p1 = pts[i]; p2 = pts[(i + 1) % n]

        def towards(a, b, d):
            dx, dy = b[0] - a[0], b[1] - a[1]; L = math.hypot(dx, dy)
            return (a[0] + dx / L * d, a[1] + dy / L * d)
        a = towards(p1, p0, r); b = towards(p1, p2, r)
        out.append(('M' if i == 0 else 'L') + f(a[0]) + ' ' + f(a[1]) + 'Q' + f(p1[0]) + ' ' + f(p1[1]) + ' ' + f(b[0]) + ' ' + f(b[1]))
    return ''.join(out) + 'Z'


def inset(pts, d):
    cx = sum(p[0] for p in pts) / len(pts); cy = sum(p[1] for p in pts) / len(pts)
    res = []
    for p in pts:
        L = math.hypot(cx - p[0], cy - p[1])
        res.append((p[0] + (cx - p[0]) * d / L, p[1] + (cy - p[1]) * d / L))
    return res


def gear(cx, cy, ro, ri, n, tw=0.42):
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        for da, r in [(-0.5, ri), (-tw / 2 * 1.25, ri), (-tw / 2, ro), (tw / 2, ro), (tw / 2 * 1.25, ri)]:
            aa = a + da * 2 * math.pi / n
            pts.append((cx + r * math.cos(aa), cy + r * math.sin(aa)))
    return 'M' + 'L'.join(f(x) + ' ' + f(y) for x, y in pts) + 'Z'


def star(cx, cy, R, r):
    pts = []
    for k in range(10):
        a = -math.pi / 2 + k * math.pi / 5; rr = R if k % 2 == 0 else r
        pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
    return 'M' + 'L'.join(f(x) + ' ' + f(y) for x, y in pts) + 'Z'


def g(body, sw=1.8, col=G):
    return '<g fill="none" stroke="%s" stroke-width="%s" stroke-linecap="round" stroke-linejoin="round">%s</g>' % (col, sw, body)


def path(d, **k):
    return '<path d="%s"%s/>' % (d, ''.join(' %s="%s"' % (a.replace('_', '-'), v) for a, v in k.items()))


def arc(cx, cy, r, a0, a1):
    return 'M%s %sA%s %s 0 %d 1 %s %s' % (
        f(cx + r * math.cos(math.radians(a0))), f(cy + r * math.sin(math.radians(a0))), f(r), f(r),
        1 if a1 - a0 > 180 else 0,
        f(cx + r * math.cos(math.radians(a1))), f(cy + r * math.sin(math.radians(a1))))


def earc(cx, cy, rx, ry, rot, t0, t1):
    r = math.radians(rot); pts = []
    for k in range(41):
        t = math.radians(t0 + (t1 - t0) * k / 40)
        x = rx * math.cos(t); y = ry * math.sin(t)
        pts.append((cx + x * math.cos(r) - y * math.sin(r), cy + x * math.sin(r) + y * math.cos(r)))
    return 'M' + 'L'.join(f(x) + ' ' + f(y) for x, y in pts)


A = {}


def keybox(filled):
    s = g(path(rpoly([(73, 114.5), (195, 114.5), (195, 172), (73, 172)], 8)) +
          path(rpoly([(80.5, 120), (188.5, 120), (188.5, 165), (80.5, 165)], 4), fill='#1d1d1d'))
    st = star(134, 144, 12, 5)
    return s + (path(st, fill=G) if filled else g(path(st), 1.2))


A['btnNavi'] = keybox(False)
A['btnHandle'] = keybox(True)
mode = g(path('M54 157.5V129l11.5 24 11.5-24v28.5M95 129a14.25 14.25 0 1 0 .1 0zM115 129v28.5h6a14.25 14.25 0 0 0 0-28.5zM144 129v28.5h16M144 129h16M144 143h14'), 1.8)
circ = ''
for a0 in (-80, 40, 160):
    circ += arc(196, 143.5, 15.5, a0, a0 + 95)
    a1 = math.radians(a0 + 95); x = 196 + 15.5 * math.cos(a1); y = 143.5 + 15.5 * math.sin(a1)
    tx, ty = -math.sin(a1), math.cos(a1); nx, ny = math.cos(a1), math.sin(a1)
    circ += 'M%s %sL%s %sL%s %s' % (f(x - tx * 5 + nx * 3), f(y - ty * 5 + ny * 3), f(x), f(y), f(x - tx * 5 - nx * 3), f(y - ty * 5 - ny * 3))
A['btnMode'] = mode + g(path(circ), 2.6)
A['btnUpDown'] = g(path('M78 157 99.7 129l21.8 28M145 129l21.7 28 21.8-28'), 2.8)
A['btnVolTune'] = (g(path('M72 134h9l14-7.5v33.5l-14-7.5h-9z') +
                     path(arc(97, 143.5, 6, -50, 50) + arc(97, 143.5, 11, -50, 50) + arc(97, 143.5, 16, -50, 50)), 2.8) +
                   g(path('M134 122.5v40'), 1.8) +
                   g(path(arc(169, 144, 17.5, -60, 240) + 'M169 123v22'), 3))
ph = [(130, 72.5), (191.5, 85), (140, 222.5), (75, 200)]
A['phone'] = g(path(rpoly(ph, 11)) + path(rpoly(inset(ph, 3), 9), stroke_width='0.9') + path('M152.5 79.5l15 2.5'), 1.8)
scr = [(68.5, 102.5), (199, 80), (199, 205), (68.5, 191.5)]
sin_ = [(74, 108), (193, 88), (193, 197), (74, 186.5)]


def screen(icon):
    glare = '<path d="M74 108L193 88V104L74 160Z" fill="#2a2a2a"/>'
    return (path(rpoly(sin_, 2), fill='#141414') + glare +
            g(path(rpoly(scr, 4)) + path(rpoly(sin_, 2), stroke_width='0.8') + path('M195.5 81v123.5'), 1.8) + icon)


rays = ''
for k in range(8):
    a = math.radians(k * 45)
    rays += 'M%s %sL%s %s' % (f(125 + 18 * math.cos(a)), f(144 + 18 * math.sin(a)), f(125 + 27 * math.cos(a)), f(144 + 27 * math.sin(a)))
A['brightness'] = screen(g(path(rays) + path('M125 130.5a13.5 13.5 0 0 0 0 27z'), 2, T) + path('M125 130.5a13.5 13.5 0 0 1 0 27z', fill=T))
A['bluelight'] = screen(g(path('M101.5 143Q129 115.5 156.5 143Q129 170.5 101.5 143Z'), 2, T) + '<circle cx="129" cy="143" r="10" fill="%s"/>' % T)
A['camera'] = g(path(gear(129, 143.5, 47.5, 40, 10)) + '<circle cx="129" cy="143.5" r="29"/><ellipse cx="135" cy="143.5" rx="13" ry="20"/>' +
                path(gear(167.5, 176.5, 32.5, 27, 9), fill='#000') + '<circle cx="167.5" cy="176.5" r="15"/><ellipse cx="170" cy="176.5" rx="7" ry="10"/>', 1.8)
cal = [(90, 95), (182.5, 86), (192.5, 194), (77.5, 185)]
cells = ''
for r_ in range(3):
    for c_ in range(4):
        x = 98 + c_ * 18.5 - r_ * 1.0; y = 133 + r_ * 16 - c_ * 1.3
        cells += 'M%s %sh8v7h-8z' % (f(x), f(y))
A['calendar'] = g(path(rpoly(cal, 3)) + path('M87 98 180 89.5 189 191') + path('M85.5 117 184 108') + path(cells, stroke_width='1') +
                  '<path d="M104 100v-13a8 8 0 1 1 13 6"/><path d="M148 96v-13a8 8 0 1 1 13 6"/>' +
                  '<circle cx="159" cy="182.5" r="26" fill="#000"/><circle cx="161" cy="182.5" r="25"/>' +
                  path('M146 175.5l13 7 16-8', stroke_width='2'), 1.8)
cont = ('M118 92c6 2 10 8 18 7 6 3 3 9 9 12 4 6-2 10-8 9-5 4-2 11-8 12-5 2-9-5-13-8-4-6-11-6-12-12 1-7 6-14 14-20z'
        'M150 152c7 0 12 5 13 11 2 7-4 12-6 19-4 4-9-1-10-7-3-7-4-15 3-23z'
        'M160 105c6 0 10 4 15 9 2 6-3 9-8 7-5-3-9-9-7-16z')
A['globe'] = (g('<circle cx="135" cy="132.5" r="56"/>' + path(cont, stroke_width='1'), 1.8) +
              g(path(earc(135, 132.5, 74, 30, -32, 150, 345) + earc(135, 132.5, 72, 22, 38, 160, 360) +
                     earc(135, 132.5, 70, 18, 78, 170, 365)), 1.6, T))
top = [(64, 146), (152, 124), (207, 162), (118, 190)]
pins = ''
for k in range(9):
    t = k / 8; x = 64 + (118 - 64) * t; y = 146 + (190 - 146) * t
    pins += 'M%s %sl-6 4v8' % (f(x - 1), f(y + 6))
for k in range(11):
    t = k / 10; x = 118 + (207 - 118) * t; y = 190 + (162 - 190) * t
    pins += 'M%s %sv10' % (f(x + 1), f(y + 7))
A['chip'] = (g(path(rpoly(top, 3)) + path(rpoly(inset(top, 5), 2), stroke_width='0.9') + path('M64 146v8l54 45 89-29v-8') +
               path(pins, stroke_width='1'), 1.8) +
             g('<ellipse cx="132.5" cy="151.5" rx="30" ry="13"/>', 2.2, T) +
             '<path d="M132 146v12" stroke="%s" stroke-width="3.5" stroke-linecap="round"/><circle cx="132" cy="141.5" r="2" fill="%s"/>' % (T, T))
def smooth(pts, closed=False):
    # Catmull-Rom through points -> cubic bezier path
    P = pts[:]
    n = len(P); d = 'M' + f(P[0][0]) + ' ' + f(P[0][1])
    for i in range(n - 1 + (1 if closed else 0)):
        p0 = P[i - 1] if (i > 0 or closed) else P[i]
        p1 = P[i]; p2 = P[(i + 1) % n]
        p3 = P[(i + 2) % n] if (i + 2 < n or closed) else p2
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        d += 'C%s %s %s %s %s %s' % (f(c1[0]), f(c1[1]), f(c2[0]), f(c2[1]), f(p2[0]), f(p2[1]))
    return d + ('Z' if closed else '')


top_b = [(114, 133), (108, 125), (104, 113), (105, 101), (110, 93), (124, 89), (139, 91), (149, 97), (150, 106), (145, 113), (136, 118), (130, 126), (126, 135)]
right_b = [(126, 135), (131, 127), (138, 122), (149, 120), (161, 122), (170, 128), (175, 137), (177, 148)]
right_low = [(145, 157), (137, 153), (127, 147)]
bottom_b = [(127, 147), (134, 154), (139, 168), (135, 183), (124, 190), (111, 188), (104, 177), (106, 164), (113, 155), (115, 146)]
left_b = [(115, 146), (110, 151), (100, 155), (88, 155), (81, 148), (79, 138), (83, 125), (92, 116), (100, 113), (105, 117), (108, 125), (114, 133)]
fan_d = smooth(top_b) + smooth(right_b) + smooth(right_low) + smooth(bottom_b) + smooth(left_b)
edge = smooth([(131, 151), (137, 166), (134, 182), (126, 189)]) + smooth([(84, 152), (96, 157), (108, 153)]) + smooth([(106, 117), (110, 126), (114, 133)])
gear_d = gear(167, 165, 24, 19.5, 10, 0.46)
A['fan'] = (g(path(edge), 1.4, '#5a5a5a') + g(path(fan_d), 1.8) +
            path(gear_d, fill='#000') + g(path(gear_d) + '<circle cx="167" cy="165" r="12" stroke="#4a4a4a"/>' +
            '<ellipse cx="168" cy="164" rx="5.5" ry="8"/><path d="M162 158a8 8 0 0 0 0 13"/>', 1.8))


def mic(col, waves, fill='#151515'):
    w = ''
    if waves:
        def sine(ph, amp):
            pts = [(22.5 + t * 2, 130 + amp * math.sin((t * 2) / 36 + ph)) for t in range(111)]
            return 'M' + 'L'.join(f(x) + ' ' + f(y) for x, y in pts)
        w = g(path(sine(0, 10) + sine(1.6, 12) + sine(3.2, 8)), 1.7, T)
    head = ('<g transform="rotate(-9 140 120)">'
            '<rect x="117" y="66" width="42" height="98" rx="21" fill="%s"/>'
            '<rect x="117" y="66" width="42" height="98" rx="21"/>'
            '<path d="M117 116h42"/><path d="M110 120v14a28 26 0 0 0 56 0v-14"/>'
            '<circle cx="110" cy="122" r="3.5"/><circle cx="166" cy="122" r="3.5"/></g>') % fill
    stand = '<path d="M131 172v28h14v-28"/><ellipse cx="137.5" cy="203" rx="35" ry="11"/><path d="M102.5 203v6a35 11 0 0 0 70 0v-6"/>'
    return w + g(head + stand, 1.8, col)


A['mic'] = mic(G, True)
lock = ('<g fill="none" stroke="%s" stroke-width="1.4"><rect x="70" y="80" width="15" height="12" rx="2"/>'
        '<path d="M73 80v-4a4.5 4.5 0 0 1 9 0v4M77.5 84.5v3"/></g>'
        '<text x="77.5" y="100" font-size="4.5" text-anchor="middle" fill="%s" font-family="sans-serif">LOCK</text>') % (D, D)
A['micLock'] = lock + mic(D, False, '#0d0d0d')
# ---------------------------------------------------------------- 사운드 화면 그림(0.5.0-test7)
def wave(x0, y0, x1, y1, bulge):
    # 설명서의 파문: 위/아래 끝점이 같고 오른쪽으로 bulge 만큼 부푼 곡선
    cx = 2 * bulge - (x0 + x1) / 2
    return 'M%s %sQ%s %s %s %s' % (f(x0), f(y0), f(cx), f((y0 + y1) / 2), f(x1), f(y1))


# 스피커 + 청록 파문 (D-settings_sound_position.png / _guide.png 오른쪽 칸)
cone = ('<g transform="rotate(-32 108 148)">'
        '<ellipse cx="108" cy="148" rx="41" ry="45"/>'
        '<ellipse cx="111" cy="145" rx="28" ry="34"/>'
        '<ellipse cx="108" cy="158" rx="12" ry="15"/></g>'
        '<path d="M79 143c-9 4-13 12-12 21 1 10 7 17 15 19"/>')
waves = (path(wave(140, 68, 142, 175, 163), stroke=T, stroke_width='2.2') +
         path(wave(140, 68, 142, 175, 187), stroke=T, stroke_width='2.2') +
         path(wave(140, 68, 142, 175, 210), stroke=T, stroke_width='2.2'))
A['soundSpeaker'] = g(cone, 1.6) + g(waves, 2.2, T)

# 차 실내 위치(전후/좌우 화면의 본문 그림)
cabin = ('<path d="M60 40h140v150H60z"/>'
         '<path d="M60 40c14 6 126 6 140 0M60 190c14-6 126-6 140 0"/>'
         '<path d="M60 40v150M200 40v150"/>'
         '<circle cx="130" cy="115" r="21" stroke="%s"/>'
         '<circle cx="130" cy="115" r="11" fill="%s"/>'
         '<path d="M130 60v100M75 115h110"/>') % (T, T)
A['soundCabin'] = g(cabin, 1.6)

# 고음/중음/저음
A['soundTone'] = g('<path d="M78 46v180M128 46v180M178 46v180"/>'
                   '<rect x="66" y="90" width="24" height="16" rx="4"/>'
                   '<rect x="116" y="140" width="24" height="16" rx="4"/>'
                   '<rect x="166" y="112" width="24" height="16" rx="4"/>', 1.8)

# 운전자 보조(방패)
A['soundShield'] = g('<path d="M130 42L188 66v46C188 142 170 166 130 186 90 166 72 142 72 112V66z"/>'
                      '<path d="M130 108v34M130 166v3"/>', 1.8)

# 라디오 음질(안테나+파문)
A['soundRadio'] = g('<rect x="92" y="120" width="76" height="64" rx="8"/>'
                     '<path d="M110 120l16-58M150 120l-16-58"/>'
                     '<path d="M186 104a44 44 0 0 1 0 40M196 92a60 60 0 0 1 0 64"/>', 1.8)

# 후석 취침(침대)
A['soundRear'] = g('<path d="M64 200v-40a16 16 0 0 1 16-16h96a16 16 0 0 1 16 16v40z"/>'
                    '<path d="M64 176h128M80 200v22M180 200v22"/>', 1.8)

# ---------------------------------------------------------------- 클러스터/HUD (0.5.0-test7)
# 설명서 그림은 음영 있는 렌더링이다. 선으로 그릴 수 있는 부분만 옮기고 음영은 생략했다.
dial = []
for cx in (72, 188):
    dial.append('<circle cx="%d" cy="150" r="42"/>' % cx)
    for k in range(13):
        a = math.radians(150 + k * 20)
        dial.append('<path d="M%s %sl4.4 4.4"/>' % (f(cx + 34 * math.cos(a)), f(150 + 34 * math.sin(a))))
dial.append('<circle cx="72" cy="150" r="5" fill="%s"/>' % D)
dial.append('<circle cx="188" cy="150" r="5" fill="%s"/>' % D)
dial.append('<path d="M72 150l22-18M188 150l-20-22"/>')
dial.append('<path d="M96 196c6 14 16 24 34 30 18-6 28-16 34-30"/>')
A['cluster'] = (g(path(earc(130, 196, 104, 104, 0, 208, 332) + earc(130, 196, 74, 74, 0, 208, 332)), 1.7) +
                g(''.join(dial), 1.6))

hud = ('<rect x="104" y="52" width="52" height="30" rx="6"/>'
       '<path d="M104 68h-60M156 68h60"/>'
       '<text x="112" y="134" font-size="28" text-anchor="middle" fill="%s" font-family="sans-serif">100</text>'
       '<text x="156" y="136" font-size="12" text-anchor="middle" fill="%s" font-family="sans-serif">km/h</text>') % (G, G)
hud += path(earc(130, 246, 92, 92, 0, 205, 335) + earc(130, 246, 66, 66, 0, 205, 335))
A['hud'] = g(hud, 1.7)

open('ccnc-art.json', 'w', encoding='utf-8').write(json.dumps(A, ensure_ascii=False))
html = '<html><body style="margin:0;background:#000">' + ''.join(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 255 276" width="510" height="552" style="background:#000">%s</svg>' % v
    for v in A.values()) + '</body></html>'
open('preview.html', 'w', encoding='utf-8').write(html)
print(list(A))
