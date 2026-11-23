// 차에서 테마 화면을 하나씩 열어 상단바·하단바 규칙(①②③)과 위치를 실제 차 WebView 값으로 잰다.
// PC 기준값(tools/bar_baseline.json 의 "차흉내")과 1px 넘게 다른 곳을 적고, 화면마다 adb 사진을 남긴다.
// 차에는 화면 이동만 한다(설정 값 쓰기 없음).
// 먼저: adb forward tcp:9222 localabstract:webview_devtools_remote_<테마 pid>
// 사용: node tools/car_screens.cjs <adb 기기> <결과 폴더>
'use strict';
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { evaluate } = require('./car_cdp.cjs');
const { SCREENS } = require('./c30_preview.cjs');
const { TOP, BOTTOM, EXTRA } = require('./bar_guard.cjs');

const serial = process.argv[2];
const out = process.argv[3];
const adb = path.join(process.env.USERPROFILE, '.local/share/android-sdk/platform-tools/adb.exe');
fs.mkdirSync(out, { recursive: true });
const base = JSON.parse(fs.readFileSync(path.join(__dirname, 'bar_baseline.json'), 'utf8'));
const NAMES = ['home', 'apps1', 'media-bt', 'phone', 'settings', 'screenlayout', 'settings-vehicle', 'settings-sound-tone',
  'settings-sound-balance', 'settings-general', 'screenlayout-theme'];
const ALL = Object.assign({}, SCREENS, EXTRA);

// 차(Chrome 30)에서 돌므로 ES5 로만 쓴다.
const MEASURE = function (sels) {
  var out = {}, bad = [];
  sels.forEach(function (sel) {
    var list = document.querySelectorAll(sel);
    for (var i = 0; i < list.length; i++) {
      var el = list[i], r = el.getBoundingClientRect(), cs = getComputedStyle(el);
      if (!r.width || !r.height || cs.display === 'none' || cs.visibility === 'hidden') continue;
      var key = sel + (list.length > 1 ? '[' + i + ']' : '');
      out[key] = [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)];
      if (/bottom-bar$|bb-nav-keys$|bb-center$|bb-media-box$|^body$|^#top-bar$/.test(sel)) out[key + ' 바탕'] = cs.backgroundColor;
    }
  });
  var bar = document.getElementById('detail-bottom-bar');
  if (bar && bar.getBoundingClientRect().height && getComputedStyle(bar).display !== 'none') {
    var keys = bar.querySelector('.bb-nav-keys').getBoundingClientRect();
    var media = bar.querySelector('.bb-media-box').getBoundingClientRect();
    var view = document.getElementById('view-detail');
    var under = document.getElementById('shell').className.indexOf('under-bar') >= 0;
    var b = bar.getBoundingClientRect();
    if (bar.className.indexOf('no-center') >= 0) {
      var want = view ? getComputedStyle(view).backgroundColor : 'rgb(0, 0, 0)';
      var got = getComputedStyle(bar).backgroundColor;
      if (/^rgba\(.*, 0\)$/.test(got) || got === 'transparent') {
        var vr = view ? view.getBoundingClientRect() : null;
        got = vr && vr.bottom >= b.bottom - 1 ? want : '앱 바탕';
      }
      if (got !== want) bad.push('① 가운데 색 ' + got + ' ≠ ' + want);
    }
    var cols = document.querySelectorAll('.stock-settings-layout .detail-settings-main, .screenlayout-stage > .sl-main');
    for (var j = 0; j < cols.length; j++) {
      var c = cols[j].getBoundingClientRect();
      if (!c.width || !c.height) continue;
      if (Math.abs(c.left - keys.right) > 1) bad.push('② 왼쪽 세로선 ' + Math.round(c.left) + ' ≠ ' + Math.round(keys.right));
      if (Math.abs(c.right - media.left) > 1) bad.push('② 오른쪽 세로선 ' + Math.round(c.right) + ' ≠ ' + Math.round(media.left));
      if (under && c.bottom < b.bottom - 8) bad.push('③ 가운데 칸 아래 끝 ' + Math.round(c.bottom));
    }
    if (under) {
      var side = document.querySelectorAll('.stock-settings-layout .sl-nav, .stock-settings-layout .detail-settings-art, .screenlayout-stage > .sl-nav, .screenlayout-stage > .sl-preview');
      for (var k = 0; k < side.length; k++) {
        var s = side[k].getBoundingClientRect();
        if (s.width && s.height && s.bottom > b.top + 1) bad.push('③ 양쪽 칸 ' + side[k].className + ' 아래 ' + Math.round(s.bottom));
      }
    }
  }
  return { rects: out, bad: bad, shell: document.getElementById('shell').className };
};

// 설정 안쪽 화면은 차(ES5)용으로 따로 적는다.
const OPEN_CARD = "function (K, card, tab) { K.openHome(); K.showPage('settings');" +
  " var cs = document.querySelectorAll('#settings-cards-grid .set-card');" +
  " for (var i = 0; i < cs.length; i++) if (cs[i].textContent.indexOf(card) >= 0) { cs[i].click(); break; }" +
  " if (tab) { var ts = document.querySelectorAll('#theme-settings-detail .sl-tab');" +
  " for (var j = 0; j < ts.length; j++) if (ts[j].textContent.replace(/^\s+|\s+$/g, '') === tab) { ts[j].click(); break; } } }";
const card = (c, t) => 'function (K) { (' + OPEN_CARD + ')(K, ' + JSON.stringify(c) + ', ' + JSON.stringify(t || '') + '); }';
const CAR_NAV = {
  'settings-vehicle': card('차량'),
  'settings-sound-tone': card('사운드', '고음/중음/저음'),
  'settings-sound-balance': card('사운드', '전후/좌우'),
  'settings-general': card('일반'),
  'screenlayout-theme': "function (K) { K.openHome(); K.showPage('screenlayout'); document.querySelector('[data-sl-tab=\"theme\"]').click(); }",
};

function es5(fnSrc) {
  // 화면 이동 함수(화살표 함수)를 ES5 로: K => { ... } / K => expr
  let m = /^\s*K\s*=>\s*\{([\s\S]*)\}\s*$/.exec(fnSrc);
  let body = m ? m[1] : 'return ' + fnSrc.replace(/^\s*K\s*=>\s*/, '') + ';';
  body = body.replace(/\(\s*x\s*\)\s*=>|\bx\s*=>/g, 'function (x) { return ')
    .replace(/function \(x\) \{ return ([^)]*?\.indexOf\([^)]*\) >= 0)\)/g, 'function (x) { return $1; })')
    .replace(/function \(x\) \{ return (x\.textContent\.trim\(\) === '[^']*')\)/g, 'function (x) { return $1; })');
  return 'function (K) {' + body + '}';
}

(async () => {
  const report = [];
  for (const name of NAMES) {
    const nav = CAR_NAV[name] || es5(ALL[name].toString());
    try {
      const navRes = await evaluate('(' + nav + ')(window.K8Home)', 15000);
      if (navRes && navRes.error) throw new Error(navRes.error);
    } catch (e) { report.push(name + ': 화면 열기 실패 ' + e.message); continue; }
    await new Promise(ok => setTimeout(ok, 1500));
    const r = await evaluate('(' + MEASURE.toString() + ')(' + JSON.stringify(TOP.concat(BOTTOM)) + ')', 15000);
    if (!r || r.error) { report.push(name + ': 재기 실패 ' + (r && r.error)); continue; }
    const ref = base[name + ' · 차흉내'] || {};
    const diff = [];
    Object.keys(ref).forEach(k => {
      if (!(k in r.rects)) { diff.push(k + ' 차에 없음'); return; }
      if (typeof ref[k] === 'string') { if (ref[k] !== r.rects[k]) diff.push(k + ' ' + ref[k] + ' → ' + r.rects[k]); return; }
      const d = Math.max.apply(null, ref[k].map((v, i) => Math.abs(v - r.rects[k][i])));
      if (d > 1) diff.push(k + ' PC ' + ref[k].join(',') + ' / 차 ' + r.rects[k].join(','));
    });
    report.push('== ' + name + ' : 규칙 ' + (r.bad.length ? r.bad.length + '곳 어김' : '통과') + ' · PC 흉내와 다른 곳 ' + diff.length);
    r.bad.forEach(x => report.push('   ' + x));
    diff.slice(0, 12).forEach(x => report.push('   ' + x));
    try {
      execFileSync(adb, ['-s', serial, 'shell', 'screencap -p /sdcard/k8s.png']);
      execFileSync(adb, ['-s', serial, 'pull', '/sdcard/k8s.png', path.join(out, 'car-' + name + '.png')]);
    } catch (e) { report.push('   사진 실패'); }
  }
  try { execFileSync(adb, ['-s', serial, 'shell', 'rm /sdcard/k8s.png']); } catch (e) { /* 없음 */ }
  fs.writeFileSync(path.join(out, '70-car-screens.txt'), report.join('\n') + '\n');
  console.log(report.join('\n'));
})();
