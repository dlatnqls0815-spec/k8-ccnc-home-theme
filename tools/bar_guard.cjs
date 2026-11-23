// 상단바·하단바 비율 지킴이.
//
// 작업하다 보면 위쪽 줄(상단바)과 아래쪽 줄(하단바)의 크기·자리가 조금씩 틀어진다. 이 도구는
// 화면마다 두 줄의 요소 위치·크기와 바탕색을 재서 tools/bar_baseline.json(기준값)과 비교한다.
// 1px 넘게 달라지거나, 있던 요소가 사라지거나 새로 생기면 실패(종료 코드 1)한다.
//   - 차와 같은 1280x480(배율 1.5), 원본과 Chrome 30 흉내(.legacy) 두 가지로 잰다.
//   - 순정 내비 위 하단바(navbar.html, 1280x63)도 잰다.
//   - 설정처럼 가운데 칸이 없는 화면은 순정 ccNC 처럼 양쪽 칸만 있고 가운데가 비어 있어야 한다(바탕 투명).
//     이 모양도 기준값에 들어 있다.
//   - 시계 글자 폭이 바뀌지 않게 시각을 10:10 으로 고정한다.
//
// 사용:
//   node tools/bar_guard.cjs            기준값과 비교(빌드 전 tools/build_nemesis.py 가 부른다)
//   node tools/bar_guard.cjs --update   두 줄을 일부러 바꿨을 때만 기준값을 새로 쓴다(사용자 확인 뒤)
'use strict';
const fs = require('fs');
const path = require('path');
const { chrome30, SCREENS, VIEW, BAR_VIEW, PW, ASSETS } = require('./c30_preview.cjs');
const { chromium } = require(PW);

const BASE = path.join(__dirname, 'bar_baseline.json');
const TOL = 1;
const TOP = ['#top-bar', '#top-bar .top-left', '#top-bar .top-right', '#profile-btn', '#driver-name',
  '#detail-top-title', '#detail-search-btn', '#detail-heading-text', '#qc-open-btn', '#media-top-tabs',
  '#top-bar .top-right > *'];
const BOTTOM = ['#detail-bottom-bar', '#detail-bottom-bar .bb-nav-keys', '#detail-bottom-bar .bb-key',
  '#detail-bottom-bar .bb-center', '#detail-bottom-bar .bb-media-box', '#detail-bottom-bar .bb-media-thumb',
  '#detail-bottom-bar .bb-media-meta', '.pager-bars'];
const NAVBAR = ['body', '.bb-nav-keys', '.bb-nav-keys > *', '.bb-center', '#bb-route-pill', '#bb-idle-pill',
  '#bb-media-zone', '#bb-media-zone > *'];

function measure(page, selectors) {
  return page.evaluate(sels => {
    const out = {};
    sels.forEach(sel => {
      const list = document.querySelectorAll(sel);
      for (let i = 0; i < list.length; i++) {
        const el = list[i];
        const r = el.getBoundingClientRect();
        const cs = getComputedStyle(el);
        if (!r.width || !r.height || cs.display === 'none' || cs.visibility === 'hidden') continue;
        const key = sel + (list.length > 1 ? '[' + i + ']' : '');
        out[key] = [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)];
        if (/bottom-bar$|bb-nav-keys$|bb-center$|bb-media-box$|^body$|^#top-bar$/.test(sel)) {
          out[key + ' 바탕'] = cs.backgroundColor;
        }
      }
    });
    return out;
  }, selectors);
}

(async () => {
  const update = process.argv.indexOf('--update') >= 0;
  const tmp = path.join(ASSETS, '__guard.html');
  const barTmp = path.join(ASSETS, '__guard_navbar.html');
  fs.writeFileSync(tmp, chrome30(fs.readFileSync(path.join(ASSETS, 'index.html'), 'utf8')));
  fs.writeFileSync(barTmp, chrome30(fs.readFileSync(path.join(ASSETS, 'navbar.html'), 'utf8')));
  const browser = await chromium.launch({ channel: 'msedge' });
  const now = {};
  const errors = [];
  const url = f => 'file:///' + f.replace(/\\/g, '/');
  async function open(file, view, legacy, query) {
    const ctx = await browser.newContext({ viewport: view, deviceScaleFactor: 1.5 });
    await ctx.addInitScript(legacy => {
      const Real = Date, fixed = new Real(2026, 0, 5, 10, 10, 0).getTime();
      // 시계 글자 폭이 시각마다 달라지지 않게 고정.
      window.Date = function (...a) { return a.length ? new Real(...a) : new Real(fixed); };
      window.Date.now = () => fixed; window.Date.UTC = Real.UTC; window.Date.parse = Real.parse;
      window.Date.prototype = Real.prototype;
      window.K8NavBar = window.K8NavBar || { action: function () {} };
      if (legacy) { window.CSS = window.CSS || {}; CSS.supports = () => false; }
    }, legacy);
    const page = await ctx.newPage();
    page.on('pageerror', e => errors.push(path.basename(file) + ': ' + e.message));
    await page.goto(url(file) + (query || ''));
    await page.waitForTimeout(600);
    if (legacy) {
      await page.evaluate(() => {
        const bs = document.querySelectorAll('button');
        for (let i = 0; i < bs.length; i++) {
          const d = getComputedStyle(bs[i]).display;
          if (d === 'flex') bs[i].style.display = 'block';
          else if (d === 'inline-flex') bs[i].style.display = 'inline-block';
        }
      });
    }
    return { ctx, page };
  }
  try {
    for (const name of Object.keys(SCREENS)) {
      for (const [mode, file, legacy] of [['원본', path.join(ASSETS, 'index.html'), false], ['차흉내', tmp, true]]) {
        const s = await open(file, VIEW, legacy, '?vehicle=1&mock=1');
        await s.page.evaluate(src => { const K = window.K8Home; eval('(' + src + ')')(K); }, SCREENS[name].toString());
        await s.page.waitForTimeout(400);
        now[name + ' · ' + mode] = await measure(s.page, TOP.concat(BOTTOM));
        await s.ctx.close();
      }
    }
    for (const [mode, file, legacy] of [['원본', path.join(ASSETS, 'navbar.html'), false], ['차흉내', barTmp, true]]) {
      for (const [state, call] of [['대기', 'V.route("", "", "", false, "", "서울 예시구 부근")'],
        ['길안내', 'V.route("12.4km", "27분", "목적지 예시", true, "3:54 PM", "")']]) {
        const s = await open(file, BAR_VIEW, legacy, '');
        await s.page.evaluate(c => {
          const V = window.K8NavBarView; if (!V) return;
          eval(c);
          if (V.media) V.media('곡 제목 예시', '아티스트', true, 1000, 200000);
        }, call);
        await s.page.waitForTimeout(300);
        now['순정 내비 하단바 ' + state + ' · ' + mode] = await measure(s.page, NAVBAR);
        await s.ctx.close();
      }
    }
  } finally {
    await browser.close();
    fs.unlinkSync(tmp);
    fs.unlinkSync(barTmp);
  }
  if (errors.length) console.log('화면 오류 ' + errors.length + '개\n   ' + errors.slice(0, 5).join('\n   '));
  if (update || !fs.existsSync(BASE)) {
    fs.writeFileSync(BASE, JSON.stringify(now, null, 1) + '\n');
    console.log('bar_guard: 기준값을 새로 썼습니다 (' + Object.keys(now).length + '개 화면) → ' + path.relative(process.cwd(), BASE));
    return;
  }
  const base = JSON.parse(fs.readFileSync(BASE, 'utf8'));
  const rows = [];
  Object.keys(base).forEach(screen => {
    const a = base[screen], b = now[screen] || {};
    Object.keys(a).forEach(k => {
      if (!(k in b)) { rows.push(screen + '  ' + k + '  사라짐(기준 ' + a[k] + ')'); return; }
      if (typeof a[k] === 'string') { if (a[k] !== b[k]) rows.push(screen + '  ' + k + '  ' + a[k] + ' → ' + b[k]); return; }
      const d = Math.max.apply(null, a[k].map((v, i) => Math.abs(v - b[k][i])));
      if (d > TOL) rows.push(screen + '  ' + k + '  ' + a[k].join(',') + ' → ' + b[k].join(','));
    });
    Object.keys(b).forEach(k => { if (!(k in a)) rows.push(screen + '  ' + k + '  새로 생김 ' + b[k]); });
  });
  if (!rows.length) { console.log('bar_guard: 상단바·하단바 기준값과 같음 (' + Object.keys(base).length + '개 화면)'); return; }
  console.log('bar_guard: 상단바·하단바가 기준값과 ' + rows.length + '곳 다릅니다 (좌,위,폭,높이 / CSS px)');
  rows.slice(0, 40).forEach(r => console.log('   ' + r));
  console.log('일부러 바꾼 것이면 사용자 확인 뒤 node tools/bar_guard.cjs --update 로 기준값을 새로 쓰세요.');
  process.exitCode = 1;
})();
