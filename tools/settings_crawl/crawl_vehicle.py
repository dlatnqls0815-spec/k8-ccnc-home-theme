"""차량 설정(com.android.vehiclesettings) 수집. 왼쪽 분류 버튼은 모두 누르고,
본문에서 글자가 있는 누를 수 있는 항목은 열어서 기록한 뒤 뒤로 나온다(켬/끔 칸은 누르지 않는다).
열고 돌아온 뒤 본문 글자·선택 상태가 달라지면 멈춘다."""
import os, re, sys, time
sys.argv = [sys.argv[0]] + sys.argv[1:4]
import importlib.util
spec = importlib.util.spec_from_file_location('crawl', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'crawl.py'))
C = importlib.util.module_from_spec(spec)
# crawl.py 의 함수만 쓰려고 맨 아래 실행부 전에 멈춘다
src = open(spec.origin, encoding='utf-8').read().split('\nfor a in ACTS:')[0]
exec(compile(src, spec.origin, 'exec'), C.__dict__)

ACT = 'com.android.vehiclesettings/.VehicleSetting'
SKIP = C.SKIP


def left_buttons(nodes):
    return [n for n in nodes if 'b' in n and n['b'][2] <= 380 and n['b'][1] >= 179 and n.get('clickable') == 'true'
            and n.get('class', '').endswith('Button') and n.get('text')]


def body_items(nodes):
    out = []
    for n in nodes:
        if 'b' not in n or n.get('clickable') != 'true' or n['b'][0] < 375 or n['b'][1] < 180:
            continue
        inner = [m for m in nodes if 'b' in m and n['b'][0] <= m['b'][0] and m['b'][2] <= n['b'][2]
                 and n['b'][1] <= m['b'][1] and m['b'][3] <= n['b'][3]]
        label = ' / '.join(C.unesc(m.get('text', '')) for m in inner if m.get('text'))
        cls = ' '.join(m.get('class', '') for m in inner)
        if not label or re.search(r'CheckBox|Switch|ToggleButton|RadioButton|SeekBar', cls):
            continue
        if any(m.get('checkable') == 'true' for m in inner):
            continue
        out.append({'b': n['b'], 'label': label})
    return out


def body_sig(nodes):
    return [(C.unesc(n.get('text', '')), n.get('selected'), n.get('checked')) for n in nodes
            if 'b' in n and n['b'][0] >= 375 and n['b'][1] >= 180 and (n.get('text') or n.get('checkable') == 'true')]


def open_cat(name):
    for _ in range(5):
        xml, nodes = C.dump()
        if 'vehiclesettings' not in C.focus():
            C.start(ACT); xml, nodes = C.dump()
        for b in left_buttons(nodes):
            if C.unesc(b['text']) == name:
                C.tap(b['b']); return C.dump()
        C.sh('input swipe 186 640 186 300 400'); time.sleep(1)
    for _ in range(5):
        C.sh('input swipe 186 300 186 640 400'); time.sleep(0.5)
    xml, nodes = C.dump()
    for b in left_buttons(nodes):
        if C.unesc(b['text']) == name:
            C.tap(b['b']); return C.dump()
    return xml, nodes


C.log('=== 차량 설정')
C.start(ACT)
xml, nodes = C.dump()
names = []
for _ in range(6):
    add = [C.unesc(b['text']) for b in left_buttons(nodes) if C.unesc(b['text']) not in names]
    names += add
    if not add and names:
        break
    C.sh('input swipe 186 640 186 300 400'); time.sleep(1); xml, nodes = C.dump()
C.log('  분류:', names)
for _ in range(6):
    C.sh('input swipe 186 300 186 640 400'); time.sleep(0.5)
stop = False
for cat in names:
    xml, nodes = open_cat(cat)
    C.save('vehicle-' + cat, xml, nodes)
    for item in body_items(nodes):
        if SKIP.search(item['label']):
            C.log('    건너뜀:', item['label']); continue
        xml, nodes = open_cat(cat)
        cur = [x for x in body_items(nodes) if x['label'] == item['label']]
        if not cur:
            C.log('    못 찾음:', item['label']); continue
        before = body_sig(nodes)
        C.tap(cur[0]['b'])
        xml2, nodes2 = C.dump()
        C.log('    열림:', item['label'], '→', C.focus())
        C.save('vehicle-%s-%s' % (cat, item['label'][:30]), xml2, nodes2)
        C.sh('input swipe 1150 640 1150 260 500'); time.sleep(1.2)
        xml3, nodes3 = C.dump(); C.save('vehicle-%s-%s-아래' % (cat, item['label'][:30]), xml3, nodes3)
        C.sh('input keyevent 4'); time.sleep(1.8)
        xml4, nodes4 = open_cat(cat)
        if body_sig(nodes4) != before:
            C.log('    !!! 상태가 달라짐:', item['label'])
            C.save('vehicle-%s-%s-달라짐' % (cat, item['label'][:30]), xml4, nodes4)
            stop = True; break
    if stop:
        break
C.sh('rm /sdcard/k8ui.xml /sdcard/k8cap.png')
C.log('끝(차량)')
