"""5W 순정 설정 화면을 돌아다니며 화면 구조(글자·상태)와 캡처를 모은다.
- 왼쪽 탭은 모두 누른다.
- 오른쪽 줄은 누르되, 켬/끔·선택이 바뀌면 바로 원래 상태로 되돌린다.
- 되돌릴 수 없는 항목(초기화·삭제·업데이트 등), 차를 움직이는 항목, 슬라이더는 누르지 않는다.
사용: python crawl.py <adb> <serial> <outdir> [activity ...]
"""
import json, os, re, subprocess, sys, time

ADB, SER, OUT = sys.argv[1], sys.argv[2], sys.argv[3]
ACTS = sys.argv[4:]
os.makedirs(OUT, exist_ok=True)
LOG = open(os.path.join(OUT, 'crawl-log.txt'), 'a', encoding='utf-8')
SKIP = re.compile(r'초기|기본값|삭제|리셋|기본값|공장|업데이트|해제|등록|추가|페어링|연결 끊|로그아웃|동기화|백업|불러오기|비밀번호|'
                  r'비우기|포맷|지우기|열기|닫기|이동|작동|실행|측정|보정|저장|전송|다운로드|구매|결제|활성화|가입|약관|동의|'
                  r'Reset|Delete|Update|Format', re.I)
NO_ITEM_TAP = ('userprofile', 'btsettings', 'themesettings')  # 프로필 전환·기기 연결·사진은 누르지 않는다


def log(*a):
    s = ' '.join(str(x) for x in a)
    LOG.write(s + '\n'); LOG.flush(); print(s, flush=True)


def adb(*args, binary=False, timeout=40):
    env = dict(os.environ, MSYS_NO_PATHCONV='1')
    for attempt in range(6):
        try:
            r = subprocess.run([ADB, '-s', SER] + list(args), capture_output=True, timeout=timeout, env=env)
        except subprocess.TimeoutExpired:
            r = None
        err = r.stderr.decode('utf-8', 'replace') if r else 'timeout'
        if r and 'not found' not in err and 'offline' not in err and 'closed' not in err:
            return r.stdout if binary else r.stdout.decode('utf-8', 'replace').replace('\r', '')
        log('  연결 다시 시도', attempt + 1)
        subprocess.run([ADB, 'connect', SER], capture_output=True, timeout=30, env=env)
        time.sleep(3 + attempt * 2)
    raise RuntimeError('차 연결 끊김')


def sh(cmd, **k):
    return adb('shell', cmd, **k)


NODE = re.compile(r'<node ([^>]*?)/?>')
ATTR = re.compile(r'(\S+?)="([^"]*)"')


def dump():
    for _ in range(3):
        sh('uiautomator dump /sdcard/k8ui.xml >/dev/null 2>&1')
        x = sh('cat /sdcard/k8ui.xml')
        if '<hierarchy' in x:
            break
        time.sleep(1)
    nodes = []
    for m in NODE.finditer(x):
        a = dict(ATTR.findall(m.group(1)))
        b = re.findall(r'\d+', a.get('bounds', ''))
        if len(b) == 4:
            a['b'] = list(map(int, b))
        nodes.append(a)
    return x, nodes


def focus():
    w = sh('dumpsys window windows | grep mCurrentFocus')
    m = re.search(r'Window\{\S+ \S+ ([^}]+)\}', w)
    if m:
        return m.group(1).strip()
    w = sh('dumpsys activity activities | grep -m1 mFocusedActivity')
    m = re.search(r'ActivityRecord\{\S+ \S+ (\S+)', w)
    return m.group(1) if m else ''


def center(b):
    return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2


def tap(b):
    x, y = center(b); sh('input tap %d %d' % (x, y)); time.sleep(1.6)


def unesc(t):
    return t.replace('&amp;', '&').replace('&lt;', '<').replace('&gt;', '>').replace('&quot;', '"').replace('&#10;', ' ')


def state(nodes):
    """체크 가능한 것들의 상태(위치 기준)."""
    return {tuple(n['b']): n.get('checked') for n in nodes if n.get('checkable') == 'true' and 'b' in n}


def texts(nodes, region=None):
    out = []
    for n in nodes:
        t = unesc(n.get('text', ''))
        if not t or 'b' not in n:
            continue
        if region and not (region[0] <= n['b'][0] and n['b'][2] <= region[2]):
            continue
        out.append({'t': t, 'id': n.get('resource-id', '').split('/')[-1], 'cls': n.get('class', '').split('.')[-1],
                    'b': n['b'], 'chk': n.get('checked') if n.get('checkable') == 'true' else None,
                    'sel': n.get('selected') == 'true', 'en': n.get('enabled') == 'true'})
    return out


shot_no = [0]


def save(tag, xml, nodes):
    shot_no[0] += 1
    name = '%03d-%s' % (shot_no[0], re.sub(r'[^\w가-힣.-]+', '_', tag)[:80])
    open(os.path.join(OUT, name + '.xml'), 'w', encoding='utf-8').write(xml)
    sh('screencap -p /sdcard/k8cap.png', timeout=60)
    adb('pull', '/sdcard/k8cap.png', os.path.join(OUT, name + '.png'), timeout=90)
    json.dump({'tag': tag, 'focus': focus(), 'texts': texts(nodes)}, open(os.path.join(OUT, name + '.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    return name


def tabs(nodes):
    return [n for n in nodes if n.get('resource-id', '').endswith('tab_main') and 'b' in n]


def tab_title(nodes, tabnode):
    b = tabnode['b']
    for n in nodes:
        if n.get('resource-id', '').endswith('tab_title') and n.get('b') == b:
            return unesc(n.get('text', ''))
    for n in nodes:
        if 'b' in n and n.get('text') and b[0] <= n['b'][0] and n['b'][2] <= b[2] and b[1] <= n['b'][1] and n['b'][3] <= b[3]:
            return unesc(n['text'])
    return ''


def list_rows(nodes):
    """오른쪽 목록의 누를 수 있는 줄(목록 안 x 375~1280)."""
    rows = []
    for n in nodes:
        if 'b' not in n:
            continue
        b = n['b']
        if b[0] < 370 or b[2] > 1290 or b[1] < 180:
            continue
        if n.get('clickable') != 'true' and n.get('checkable') != 'true':
            continue
        if n.get('class', '').endswith('ListView') or n.get('class', '').endswith('ScrollView'):
            continue
        inner = [m for m in nodes if 'b' in m and b[0] <= m['b'][0] and m['b'][2] <= b[2] and b[1] <= m['b'][1] and m['b'][3] <= b[3]]
        label = ' / '.join(unesc(m.get('text', '')) for m in inner if m.get('text'))
        slider = any(re.search(r'SeekBar|ProgressBar|RatingBar', m.get('class', '')) for m in inner)
        ids = [m.get('resource-id', '').split('/')[-1] for m in inner]
        rows.append({'b': b, 'label': label, 'slider': slider, 'cls': n.get('class', '').split('.')[-1],
                     'nav': 'end_icon' in ids, 'check': 'front_icon' in ids})
    # 바깥 줄만 남긴다(안쪽 버튼이 따로 있으면 둘 다)
    return rows


def sig(nodes):
    """목록 상태: 글자와 선택(체크) 표시. 다녀온 뒤 바뀌었는지 본다."""
    out = []
    for n in nodes:
        if 'b' not in n or n['b'][0] < 370 or n['b'][2] > 1290:
            continue
        rid = n.get('resource-id', '').split('/')[-1]
        if rid in ('front_icon', 'sub_text', 'main_text') or n.get('checkable') == 'true':
            out.append((rid, unesc(n.get('text', '')), n.get('selected'), n.get('checked')))
    return out


def start(act):
    sh('am start -W -n ' + act); time.sleep(2.5)


def ensure(act, tabname):
    """설정 화면이 그 탭으로 열려 있게 한다."""
    xml, nodes = dump()
    f = focus()
    if f and act.split('/')[0] not in f:
        log('  다시 열기:', act, '(지금', f, ')')
        start(act); xml, nodes = dump()
    if tabname:
        for _ in range(6):
            for t in tabs(nodes):
                if tab_title(nodes, t) == tabname:
                    if t.get('selected') != 'true':
                        tap(t['b']); xml, nodes = dump()
                    return xml, nodes
            sh('input swipe 187 640 187 300 400'); time.sleep(1); xml, nodes = dump()
        for _ in range(6):
            sh('input swipe 187 300 187 640 400'); time.sleep(0.6)
        xml, nodes = dump()
        for t in tabs(nodes):
            if tab_title(nodes, t) == tabname:
                tap(t['b']); return dump()
    return xml, nodes


def restore(before, act, tabname):
    xml, nodes = dump()
    after = state(nodes)
    changed = [k for k in before if k in after and before[k] != after[k]]
    if not changed:
        return True
    # 원래 켜져 있던 것(라디오)이나 바뀐 스위치를 다시 누른다
    for k in changed:
        if before[k] == 'true':
            tap(list(k)); break
    else:
        tap(list(changed[0]))
    xml, nodes = dump(); after = state(nodes)
    ok = all(after.get(k) == before[k] for k in before if k in after)
    log('    되돌림', 'OK' if ok else '실패!', changed)
    return ok


def crawl(act):
    pkg = act.split('/')[0]
    log('=== ', act)
    start(act)
    xml, nodes = dump()
    save(pkg + '-main', xml, nodes)
    if not tabs(nodes):
        log('  탭 없음')
    # 탭 이름 모으기(스크롤 포함)
    names = []
    for _ in range(6):
        new = [tab_title(nodes, t) for t in tabs(nodes)]
        add = [n for n in new if n and n not in names]
        names += add
        if not add and names:
            break
        sh('input swipe 187 640 187 300 400'); time.sleep(1); xml, nodes = dump()
    log('  탭:', names)
    for _ in range(6):
        sh('input swipe 187 300 187 640 400'); time.sleep(0.5)
    for tn in names or ['']:
        xml, nodes = ensure(act, tn)
        save('%s-%s' % (pkg, tn), xml, nodes)
        seen = set()
        for page in range(6):
            rows = list_rows(nodes)
            fresh = [r for r in rows if r['label'] and r['label'] not in seen]
            for r in fresh:
                seen.add(r['label'])
                if any(k in pkg for k in NO_ITEM_TAP):
                    continue
                if r['cls'].endswith('Button') or not r['nav']:
                    continue  # 체크·버튼·일반 줄은 누르지 않고 기록만
                if r['slider']:
                    log('    건너뜀(슬라이더):', r['label']); continue
                if SKIP.search(r['label']):
                    log('    건너뜀(되돌릴 수 없음):', r['label']); continue
                xml, nodes = ensure(act, tn)
                cur = [x for x in list_rows(nodes) if x['label'] == r['label']]
                if not cur:
                    for _ in range(page):
                        sh('input swipe 830 640 830 260 500'); time.sleep(1)
                    xml, nodes = dump(); cur = [x for x in list_rows(nodes) if x['label'] == r['label']]
                if not cur:
                    log('    못 찾음:', r['label']); continue
                before = sig(nodes)
                tap(cur[0]['b'])
                xml2, nodes2 = dump(); f1 = focus()
                log('    열림:', r['label'], '→', f1)
                save('%s-%s-%s' % (pkg, tn, r['label'][:30]), xml2, nodes2)
                sh('input swipe 830 640 830 260 500'); time.sleep(1.2)
                xml3, nodes3 = dump(); save('%s-%s-%s-아래' % (pkg, tn, r['label'][:30]), xml3, nodes3)
                sh('input keyevent 4'); time.sleep(1.8)
                xml4, nodes4 = ensure(act, tn)
                # 같은 스크롤 위치로 가서 상태 비교
                for _ in range(page):
                    sh('input swipe 830 640 830 260 500'); time.sleep(1)
                xml4, nodes4 = dump()
                if sig(nodes4) != before:
                    log('    !!! 상태가 달라짐:', r['label'])
                    save('%s-%s-%s-달라짐' % (pkg, tn, r['label'][:30]), xml4, nodes4)
                    raise RuntimeError('상태 변화 감지, 멈춤')
            # 목록 아래로
            xml, nodes = ensure(act, tn)
            for _ in range(page + 1):
                sh('input swipe 830 640 830 260 500'); time.sleep(1.1)
            xml, nodes = dump()
            more = [r for r in list_rows(nodes) if r['label'] and r['label'] not in seen]
            if not more:
                break
            save('%s-%s-목록%d' % (pkg, tn, page + 2), xml, nodes)


for a in ACTS:
    try:
        crawl(a)
    except Exception as e:
        log('오류', a, repr(e))
        if '상태 변화' in str(e):
            break
sh('rm /sdcard/k8ui.xml /sdcard/k8cap.png')
log('끝')
