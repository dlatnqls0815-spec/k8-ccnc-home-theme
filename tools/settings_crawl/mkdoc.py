import json, sys, os, glob, re
O, DOC = sys.argv[1], sys.argv[2]
APPS = [('com.android.soundsettings', '사운드'), ('com.android.displaysettings', '디스플레이'), ('com.android.generalsettings', '일반'),
        ('com.android.buttonsettings', '버튼'), ('vehicle-', '차량'), ('com.android.btsettings', '기기 연결'),
        ('com.android.themesettings', '화면구성'), ('com.android.settings-main', '설정 첫 화면')]
screens = {}
for d in ['crawl', 'crawl-vehicle']:
    for f in sorted(glob.glob(os.path.join(O, d, '*.json'))):
        name = os.path.basename(f)[4:-5]
        j = json.load(open(f, encoding='utf-8'))
        items = []
        for t in j['texts']:
            if t['b'][1] < 180 or t['id'] == 'title_bar_text' or not t['t'].strip():
                continue
            if t['b'][2] <= 380 and d == 'crawl':
                continue
            mark = '(설명) ' if t['b'][0] >= 1280 else ''
            on = ' ✔' if (t['sel'] or t['chk'] == 'true') and t['b'][0] >= 370 and not mark else ''
            s = mark + t['t'].replace('\n', ' ') + on
            if s not in items:
                items.append(s)
        key = re.sub(r'-(아래|목록\d)$', '', name)
        lst = screens.setdefault(key, [])
        for s in items:
            if s not in lst:
                lst.append(s)

vals = {}
for line in open(os.path.join(O, 'after-settings-system.txt'), encoding='utf-8'):
    m = re.search(r'name=([^,]+), value=(.*)', line)
    if m:
        vals[m.group(1)] = m.group(2).strip()

out = [open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'doc-head.md'), encoding='utf-8').read()]
keys = [('내비게이션 안내 일시 정지', 'sound_mute_nav_guide'), ('웰컴 사운드', 'use_welcome_message'),
        ('음성 변경(밝은 여성/부드러운 남성)', 'voice_type_information'), ('카카오 프렌즈 음성', 'kakao_friends_voice_information'),
        ('호출어(음성인식)', 'vr_enable_wakeup'), ('안내 중 말하기(끼어들기)', 'vr_enable_bargein'),
        ('후석 음성인식 잠금', 'rear_voice_recognition_lock'), ('블루라이트 차단', 'bluelight_enable'),
        ('블루라이트 시간(0 자동)', 'bluelight_mode'), ('블루라이트 색감 단계', 'bluelight_level'),
        ('블루라이트 시작', 'bluelight_starttime'), ('블루라이트 종료', 'bluelight_endtime'),
        ('미디어 마지막 상태 유지', 'keep_last_mode'), ('미디어 알림', 'media_notification'), ('시간 표기(12/24)', 'time_12_24'),
        ('GPS 시간 자동', 'gps_auto_time'), ('서머타임', 'summer_time'), ('VOL/TUNE 위치 바꾸기', 'vol_tuneknob_swap'),
        ('TUNE 노브 기능', 'tune_knob_function'), ('[∧]/[∨] 버튼', 'button_settings_seek'),
        ('사운드 위치 모드', 'sound_position_mode'), ('사운드 위치 좌석', 'sound_position_seat'), ('후석 취침 모드', 'rear_sleep_mode'),
        ('주간 밝기', 'daylight_bright'), ('야간 밝기', 'night_bright'), ('분할 화면', 'split_screen_on'),
        ('대기 화면 종류', 'waiting_display_type'), ('시계 위젯 종류', 'clock_widget_type'), ('아날로그 시계 종류', 'analog_clock_type'),
        ('언어', 'language_type'), ('후방 카메라 옵션', 'rear_camera_option'), ('카메라 밝기(주간)', 'camera_bright_setting_day'),
        ('폴더 반복', 'media_repeat_folder'), ('Android Auto 분할 표시', 'androidauto_ss_osd'), ('CarPlay 분할 표시', 'carplay_ss_osd'),
        ('영상 비율', 'video_aspect_ratio'), ('노면 소음 저감(RNC)', 'sound_rnc'), ('적응형 소음 저감', 'sound_adp_anc'),
        ('드라이브 모드 변경 알림', 'drivemode_notification'), ('시트 위치 알림', 'seat_position_alert'),
        ('화면 밝기', 'screen_brightness'), ('자동 밝기', 'screen_brightness_mode')]
for label, k in keys:
    has = (k + '_dr1') in vals
    v = vals.get(k + '_dr1', vals.get(k, ''))
    out.append('| %s | `%s`%s | %s |\n' % (label, k, '(`_dr1`)' if has else '', v))
out.append('| 고음/중음/저음 | `hkmc_sndset_treble/middle/bass_USER_1` (10=0) | %s / %s / %s |\n' % (
    vals.get('hkmc_sndset_treble_USER_1'), vals.get('hkmc_sndset_middle_USER_1'), vals.get('hkmc_sndset_bass_USER_1')))
out.append('| 오디오 음량 자동 줄이기 | `hkmc_sndset_auto_adjusting_vol_USER_1` | %s |\n' % vals.get('hkmc_sndset_auto_adjusting_vol_USER_1'))
out.append('\n키와 화면 항목의 짝은 이름·값·화면 상태로 맞춘 것이고, 번호의 뜻은 일부 추정이다. 차에서 한 항목씩 바꿔 보면 확정된다.\n')
out.append('\n## 화면별 항목 (✔ = 지금 선택·켜짐, (설명) = 오른쪽 안내 글)\n')
for prefix, title in APPS:
    ks = [k for k in screens if k.startswith(prefix)]
    if not ks:
        continue
    out.append('\n### %s\n' % title)
    for k in ks:
        nm = k.replace(prefix, '').strip('-_').replace('_', ' ') or '첫 화면'
        out.append('- **%s**: %s\n' % (nm, ' · '.join(screens[k])[:1500]))
open(DOC, 'w', encoding='utf-8').write(''.join(out))
print('ok')
