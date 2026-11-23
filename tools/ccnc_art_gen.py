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


# 스피커 + 청록 동심원 파문 (D-settings_sound_position.png / _guide.png / _warning.png 오른쪽 칸)
cone = ('<g transform="rotate(-30 112 146)">'
        '<ellipse cx="116" cy="146" rx="34" ry="44" fill="#0b0d11"/>'
        '<ellipse cx="114" cy="146" rx="24" ry="32"/>'
        '<ellipse cx="108" cy="148" rx="10" ry="14"/>'
        '<path d="M90 120c-14 6-20 14-20 26s6 20 20 26"/></g>')
waves = (path(arc(118, 134, 48, -62, 30)) +
         path(arc(118, 134, 65, -66, 34)) +
         path(arc(118, 134, 82, -70, 38)))
A['soundSpeaker'] = g(cone, 1.8) + g(waves, 2.4, T)

# 안내음 (스피커 + 왼쪽 위 (i) 아이콘: D-settings_sound_guide.png)
A['soundGuide'] = (A['soundSpeaker'] +
                   g('<circle cx="56" cy="112" r="14"/>', 1.8, T) +
                   '<circle cx="56" cy="105.5" r="1.6" fill="%s"/>' % T +
                   '<path d="M56 110v9" stroke="%s" stroke-width="2.4" stroke-linecap="round"/>' % T)

# 운전자 보조 경고음 (스피커 + 빨간 삼각형 ! 경고 마크: D-settings_sound_warning.png)
A['soundWarning'] = (A['soundSpeaker'] +
                     '<path d="M56 96L71 122H41Z" fill="#000" stroke="#e03c31" stroke-width="2" stroke-linejoin="round"/>' +
                     '<path d="M56 104v9" stroke="#e03c31" stroke-width="2.2" stroke-linecap="round"/>' +
                     '<circle cx="56" cy="117.5" r="1.4" fill="#e03c31"/>')

# 미디어 동작 (스피커 + 왼쪽 아래 재생 (▷) 원형 배지: D-settings_general_mediaaction.png)
A['mediaAction'] = (A['soundSpeaker'] +
                    '<circle cx="66" cy="176" r="24" fill="#0d1117" stroke="%s" stroke-width="1.8"/>' % G +
                    '<path d="M59 166l17 10-17 10Z" fill="none" stroke="%s" stroke-width="2" stroke-linejoin="round"/>' % T)

# 차 실내 위치(전후/좌우 화면) -> 원본 ccNC는 soundSpeaker 사용, 호환용 유지
A['soundCabin'] = A['soundSpeaker']

# 고음/중음/저음 (3D 등각투영 믹서 보드: D-settings_sound_audio.png 정밀 재현)
m_top = [(34, 134), (146, 86), (222, 136), (110, 192)]
m_bot = 'M34 139v12l76 48 112-56v-12'
def iso_pt(u, v):
    # u: 0..1 along (34,134)->(146,86), v: 0..1 along (34,134)->(110,192)
    x = 34 + (146 - 34) * u + (110 - 34) * v
    y = 134 + (86 - 134) * u + (192 - 134) * v
    return (x, y)
m_tracks = ''
for u in (0.22, 0.48, 0.74):
    p0 = iso_pt(u, 0.14); p1 = iso_pt(u, 0.86)
    m_tracks += 'M%s %sL%s %s' % (f(p0[0]), f(p0[1]), f(p1[0]), f(p1[1]))
def m_knob(u, v):
    p0 = iso_pt(u - 0.08, v - 0.07); p1 = iso_pt(u + 0.08, v - 0.07)
    p2 = iso_pt(u + 0.08, v + 0.07); p3 = iso_pt(u - 0.08, v + 0.07)
    top_d = 'M%s %sL%s %sL%s %sL%s %sZ' % (f(p0[0]), f(p0[1]-7), f(p1[0]), f(p1[1]-7), f(p2[0]), f(p2[1]-7), f(p3[0]), f(p3[1]-7))
    side_d = 'M%s %sV%sL%s %sL%s %sV%s' % (f(p0[0]), f(p0[1]-7), f(p0[1]), f(p3[0]), f(p3[1]), f(p2[0]), f(p2[1]), f(p2[1]-7))
    return (path(side_d, fill='#12161c', stroke='#9bb0c1', stroke_width='1.3', stroke_linejoin='round') +
            path(top_d, fill='#232a33', stroke='#b4c6d4', stroke_width='1.4', stroke_linejoin='round'))
A['soundTone'] = (path(rpoly(m_top, 10), fill='#10141a', stroke='#9bb0c1', stroke_width='1.8') +
                  g(path(m_bot), 1.8, '#9bb0c1') + g(path(m_tracks), 3.6, '#232932') + g(path(m_tracks), 1.2, '#4a5666') +
                  m_knob(0.22, 0.30) + m_knob(0.22, 0.66) + m_knob(0.48, 0.56) + m_knob(0.74, 0.24) + m_knob(0.74, 0.52) + m_knob(0.74, 0.78))

# 운전자 보조(방패) 호환용 -> soundWarning 별칭
A['soundShield'] = A['soundWarning']

# 라디오 음질 (라디오 + 좌측 주황 노이즈 파형 + 우측 원음/노이즈 파형: D-settings_sound_radionoise.png)
lw = 'M18 148c10-24 22 24 34 0s20-26 30 0'
rw1 = 'M174 148c10-20 20 20 30 0s18-18 28 0'
rw2 = 'M174 148c10 18 22-22 32 0s18 16 26 0'
A['soundRadio'] = (g(path(lw), 1.8, '#d9623b') + g(path(rw1), 1.8, '#d9623b') + g(path(rw2), 2.0, T) +
                   '<rect x="90" y="118" width="76" height="58" rx="6" fill="#0d1117" stroke="%s" stroke-width="1.8"/>' % G +
                   g(path('M98 118l48-22M106 138h24M106 152h24'), 1.8) +
                   '<circle cx="148" cy="145" r="10" fill="none" stroke="%s" stroke-width="1.8"/>' % G)

# 후석 취침(침대)
A['soundRear'] = g('<path d="M64 200v-40a16 16 0 0 1 16-16h96a16 16 0 0 1 16 16v40z"/>'
                    '<path d="M64 176h128M80 200v22M180 200v22"/>', 1.8)

# 일반 › 키보드 (3D 등각투영 키보드: D-settings_general_keyboard.png 정밀 재현)
kb_pts = [(28, 144), (104, 106), (226, 152), (150, 198)]
def kb_pt(u, v):
    x = 28 + (226 - 104) * u + (104 - 28) * v
    y = 144 + (152 - 106) * u + (106 - 144) * v
    return (x, y)
kb_keys = ''
for r_ in range(4):
    cols = 8 if r_ < 3 else 5
    for c_ in range(cols):
        if r_ == 3 and c_ == 2:
            u0, u1 = 0.28, 0.72
        elif r_ == 3 and c_ > 2:
            u0 = 0.75 + (c_ - 3) * 0.10; u1 = u0 + 0.08
        elif r_ == 3:
            u0 = 0.06 + c_ * 0.10; u1 = u0 + 0.08
        else:
            u0 = 0.06 + c_ * 0.112; u1 = u0 + 0.088
        v0 = 0.14 + r_ * 0.21; v1 = v0 + 0.15
        p0 = kb_pt(u0, v0); p1 = kb_pt(u1, v0); p2 = kb_pt(u1, v1); p3 = kb_pt(u0, v1)
        kb_keys += 'M%s %sL%s %sL%s %sL%s %sZ' % (f(p0[0]), f(p0[1]), f(p1[0]), f(p1[1]), f(p2[0]), f(p2[1]), f(p3[0]), f(p3[1]))
A['keyboard'] = (path(rpoly(kb_pts, 8), fill='#11151b', stroke='#9bb0c1', stroke_width='1.8') +
                 g(path('M28 148v7l122 49 76-46v-7'), 1.8, '#9bb0c1') +
                 g(path(kb_keys), 1.1, '#8fa3b5'))

# 디스플레이 › 영상 색상 (모니터 안 슬라이더 3줄: D-settings_display_screencolor.png)
sc_bars = 'M98 128h64M98 144h64M98 160h64'
sc_act = 'M98 128h28M98 144h14M98 160h40'
sc_knobs = ('<circle cx="126" cy="128" r="4" fill="#141414" stroke="#fff" stroke-width="1.5"/>'
            '<circle cx="112" cy="144" r="4" fill="#141414" stroke="#fff" stroke-width="1.5"/>'
            '<circle cx="138" cy="160" r="4" fill="#141414" stroke="#fff" stroke-width="1.5"/>')
A['screenColor'] = screen(g(path(sc_bars), 2.2, '#4a5568') + g(path(sc_act), 2.2, T) + sc_knobs)

# 디스플레이 › 영상 비율
A['videoRatio'] = screen(g('<rect x="96" y="118" width="66" height="46" rx="3"/>'
                           '<path d="M104 126l8 8M154 156l-8-8"/>', 1.8, T))

# 버튼 › 후석 잠금
A['btnRearLock'] = g('<rect x="76" y="114" width="104" height="58" rx="10"/>'
                     '<rect x="114" y="138" width="28" height="22" rx="3"/>'
                     '<path d="M120 138v-7a8 8 0 0 1 16 0v7M128 147v5"/>', 1.8)

# 차량 › 시트 (3D 시트: Lite_d-settings_seat.png 정밀 재현)
seat_defs = ('<defs><linearGradient id="stG" x1="0" y1="0" x2="1" y2="1">'
             '<stop offset="0%" stop-color="#2b313a"/><stop offset="100%" stop-color="#111419"/></linearGradient></defs>')
st_head = 'M140 52c2-12 16-14 24-8 6 4 7 14 4 24l-26-4z'
st_back = 'M116 164c-10-28-6-68 12-94l36-4c14 14 20 34 18 60-2 22-8 42-16 54l-50-16z'
st_bolster = 'M126 74c14 14 18 46 10 82M164 68c8 18 10 46 2 84'
st_base = 'M58 184c-4-12 10-22 30-26l44-8 52 14c12 4 12 18 4 26l-46 24c-20 10-58 8-76-8-5-4-7-12-8-22z'
st_seam = 'M64 186c20 12 52 14 74 2l46-24M104 176l40 12'
A['seat'] = (seat_defs +
             path(st_head, fill='url(#stG)', stroke='#9db0c0', stroke_width='1.8', stroke_linejoin='round') +
             path(st_back, fill='url(#stG)', stroke='#9db0c0', stroke_width='1.8', stroke_linejoin='round') +
             g(path(st_bolster), 1.1, '#5d6b78') +
             path(st_base, fill='url(#stG)', stroke='#9db0c0', stroke_width='1.8', stroke_linejoin='round') +
             g(path(st_seam), 1.3, '#6b7b8a'))

# 차량 › 편의 (톱니바퀴 + 손가락 터치: D-settings_vehicle_convenience.png)
conv_gear = gear(126, 134, 48, 39, 8, 0.42)
hand = ('M138 192v-42a6 6 0 0 1 12 0v22l22 6c6 2 10 8 8 16l-6 22h-36l-14-22c-3-5 1-11 7-9l7 7z')
A['convenience'] = (g(path(conv_gear) + '<circle cx="126" cy="134" r="20"/>', 1.8) +
                    path(hand, fill='#000', stroke=G, stroke_width='1.8', stroke_linejoin='round'))

# 차량 › 디지털 키 (스마트폰 + NFC 카드 키 + 열쇠 아이콘: D-settings_vehicle_digitalkey.png)
dk_phone = rpoly([(126, 68), (192, 92), (146, 212), (80, 188)], 10)
dk_card = rpoly([(62, 124), (124, 146), (98, 212), (38, 190)], 6)
key_ico = '<circle cx="146" cy="126" r="7"/><path d="M142 132l-12 20M136 143l5 3M132 149l5 3"/>'
key_ico2 = '<circle cx="88" cy="164" r="6"/><path d="M84 169l-9 15M80 177l4 2"/>'
A['digitalKey'] = (path(dk_phone, fill='#0d1014', stroke=G, stroke_width='1.8') +
                   g(key_ico, 1.6) +
                   path(dk_card, fill='#0d1014', stroke=G, stroke_width='1.8') +
                   g(key_ico2, 1.5))

# 차량 › 차량 상태
A['vehicleStatus'] = g('<path d="M52 162h152l-14-32c-5-12-18-18-32-18h-62c-14 0-27 6-32 18z"/>'
                       '<circle cx="86" cy="166" r="14"/><circle cx="170" cy="166" r="14"/>'
                       '<path d="M114 82l10 10 20-20" stroke="%s" stroke-width="2.4"/>' % T, 1.8)

# ---------------------------------------------------------------- 클러스터/HUD (ccNC 원본 정밀 재현)
cl_defs = ('<defs>'
           '<radialGradient id="clG" cx="50%" cy="40%" r="55%">'
           '<stop offset="0%" stop-color="#2c3540"/><stop offset="100%" stop-color="#0a0c10"/></radialGradient>'
           '<linearGradient id="clFade" x1="0" y1="0" x2="0" y2="1">'
           '<stop offset="0%" stop-color="#9cb0c2" stop-opacity="1"/><stop offset="78%" stop-color="#9cb0c2" stop-opacity="0.7"/>'
           '<stop offset="100%" stop-color="#000" stop-opacity="0"/></linearGradient>'
           '</defs>')
dial = []
for cx, needle in ((66, (22, -16)), (189, (-20, -18))):
    dial.append('<circle cx="%d" cy="166" r="44" fill="url(#clG)" stroke="url(#clFade)" stroke-width="1.4"/>' % cx)
    for k in range(15):
        a = math.radians(150 + k * 17)
        x0 = cx + 33 * math.cos(a); y0 = 166 + 33 * math.sin(a)
        x1 = cx + 41 * math.cos(a); y1 = 166 + 41 * math.sin(a)
        dial.append('<path d="M%s %sL%s %s" stroke="#7d8f9e" stroke-width="1.3"/>' % (f(x0), f(y0), f(x1), f(y1)))
    dial.append('<path d="M%d 166l%d %d" stroke="#c8d6e2" stroke-width="2" stroke-linecap="round"/>' % (cx, needle[0], needle[1]))
    dial.append('<circle cx="%d" cy="166" r="5" fill="#1b2128" stroke="#9cb0c2" stroke-width="1.3"/>' % cx)
# Steering wheel upper rim arching over the cluster gauges (ccNC_D-settings_cluster_cluster.png)
wheel = (
    '<path d="M12 188C28 112 76 74 128 74s100 38 116 114" fill="none" stroke="url(#clFade)" stroke-width="2.2"/>'
    '<path d="M30 192C44 128 84 96 128 96s84 32 98 96" fill="none" stroke="url(#clFade)" stroke-width="1.6"/>'
    '<path d="M18 194c24-18 64-28 110-28s86 10 110 28" fill="#06080c" stroke="url(#clFade)" stroke-width="1.8"/>'
    '<rect x="108" y="136" width="40" height="22" rx="3" fill="#10141a" stroke="#475563" stroke-width="1"/>'
)
A['cluster'] = cl_defs + ''.join(dial) + wheel

hud_defs = ('<defs>'
            '<linearGradient id="hudBox" x1="0" y1="0" x2="0" y2="1">'
            '<stop offset="0%" stop-color="#4e555e"/><stop offset="100%" stop-color="#191d22"/></linearGradient>'
            '<linearGradient id="hudFade" x1="0" y1="0" x2="0" y2="1">'
            '<stop offset="0%" stop-color="#9cb0c2" stop-opacity="0.95"/><stop offset="100%" stop-color="#000" stop-opacity="0"/></linearGradient>'
            '</defs>')
hud = (hud_defs +
       '<path d="M104 92L24 148M151 92L231 148" stroke="#3c444e" stroke-width="1.4"/>'
       '<rect x="102" y="64" width="51" height="34" rx="10" fill="url(#hudBox)" stroke="#687380" stroke-width="1.3"/>'
       '<text x="115" y="152" font-size="30" font-weight="500" text-anchor="middle" fill="#eef4fa" font-family="sans-serif">100</text>'
       '<text x="159" y="152" font-size="11" text-anchor="middle" fill="#b4c2cf" font-family="sans-serif">km/h</text>'
       '<path d="M16 206h223" stroke="#2f363e" stroke-width="1.2"/>'
       '<path d="M40 248C54 198 88 176 128 176s74 22 88 72" fill="none" stroke="url(#hudFade)" stroke-width="2.2"/>'
       '<path d="M56 248C68 208 94 190 128 190s60 18 72 58" fill="none" stroke="url(#hudFade)" stroke-width="1.6"/>'
       '<path d="M74 236c16-10 34-14 54-14s38 4 54 14" fill="none" stroke="url(#hudFade)" stroke-width="1.6"/>')
A['hud'] = hud

open('ccnc-art.json', 'w', encoding='utf-8').write(json.dumps(A, ensure_ascii=False))
html = '<html><body style="margin:0;background:#000">' + ''.join(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 255 276" width="510" height="552" style="background:#000">%s</svg>' % v
    for v in A.values()) + '</body></html>'
open('preview.html', 'w', encoding='utf-8').write(html)
print(list(A))
