// 차 WebView(Chrome 30) 흉내 비교 도구.
//
// 차는 1920x720 화면을 CSS 1280x480(배율 1.5)으로 그리고, 다음을 버린다:
//   clamp()/min()/max()·calc() 안의 vw 가 든 선언, flex gap, display:grid,
//   접두어 없는 transform/filter/clip-path/animation(-webkit- 쪽만 안다), button 안의 flex.
// 이 도구는 그 선언을 지운 HTML 을 assets/__c30*.html 로 잠깐 써서 Edge 로 열고(file:// 은 가로채기가 안 된다),
// 원본(지우지 않은 것)과 나란히 찍어 captures/c30/ 에 둔다(커밋하지 않는 폴더).
// 원본과 흉내에서 위치·크기가 4px(CSS) 넘게 다른 요소를 화면마다 적는다.
// 하단바(1280x63)와 본문 아래 끝이 맞는지도 적는다.
//
// 사용: node tools/c30_preview.cjs [화면이름 ...]
//   화면: home standby apps1 media-bt media-usb phone settings screenlayout navi weather hybrid
// 필요: playwright-core(아래 PW 경로 또는 환경변수 K8_PLAYWRIGHT), Microsoft Edge.
'use strict';
const fs = require('fs');
const path = require('path');
const PW = process.env.K8_PLAYWRIGHT ||
  'C:/AI-Work/2026-11-21-k8-ccnc-home-cards/checks/browser-tools/node_modules/playwright-core';
const { chromium } = require(PW);

const ROOT = path.resolve(__dirname, '..');
const ASSETS = path.join(ROOT, 'assets');
const OUT = path.join(ROOT, 'captures', 'c30');
const VIEW = { width: 1280, height: 480 };
const BAR_VIEW = { width: 1280, height: 63 };

const SCREENS = {
  home: K => K.openHome(),
  standby: K => { K.openHome(); K.showPage('standby'); },
  apps1: K => { K.openHome(); K.showPage('apps1'); },
  'media-bt': K => { K.setMediaSource('bt'); K.openHome(); K.showPage('media'); },
  'media-usb': K => { K.setMediaSource('usb'); K.openHome(); K.showPage('media'); },
  phone: K => { K.openHome(); K.showPage('phone'); },
  settings: K => { K.openHome(); K.showPage('settings'); },
  screenlayout: K => { K.openHome(); K.showPage('screenlayout'); },
  navi: K => { K.openHome(); K.showPage('navi'); },
  weather: K => { K.openHome(); K.showPage('weather'); },
  hybrid: K => { K.openHome(); K.showPage('hybrid'); },
};

// CSS 선언 하나를 Chrome 30 이 버리는지.
function dropped(prop, value, block) {
  if (/(^|[^\w-])(clamp|min|max)\(/.test(value)) return true;
  if (/calc\([^;]*\d(vw|vh)/.test(value)) return true;
  if (/^(gap|row-gap|column-gap|grid-gap|aspect-ratio|inset|place-items|place-content)$/.test(prop)) return true;
  if (prop === 'display' && /^(inline-)?grid\b/.test(value.trim())) return true;
  if (/^(transform|transform-origin|filter|clip-path|animation[\w-]*)$/.test(prop)) return true; // -webkit- 쪽만
  return false;
}

function stripDecls(body) {
  return body.replace(/(^|[;{\s])([a-zA-Z-]+)\s*:\s*([^;{}]*)(;?)/g, (m, lead, prop, value, semi) =>
    dropped(prop.toLowerCase(), value, body) ? lead : m);
}

function chrome30(html) {
  html = html.replace(/(<style[^>]*>)([\s\S]*?)(<\/style>)/gi, (m, a, css, b) => {
    css = css.replace(/\/\*[\s\S]*?\*\//g, '');
    css = css.replace(/\{([^{}]*)\}/g, (mm, body) => '{' + stripDecls(body) + '}');
    return a + css + b;
  });
  return html.replace(/(\sstyle=")([^"]*)(")/g, (m, a, body, b) => a + stripDecls(body) + b);
}

async function boxMap(page) {
  return page.evaluate(() => {
    const out = {};
    const list = document.querySelectorAll('body *');
    for (let i = 0; i < list.length; i++) {
      const el = list[i];
      const r = el.getBoundingClientRect();
      if (!r.width || !r.height || r.top >= innerHeight || r.bottom <= 0) continue;
      const cs = getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden') continue;
      let key = el.id ? '#' + el.id : el.tagName.toLowerCase() + (typeof el.className === 'string' && el.className
        ? '.' + el.className.trim().split(/\s+/).join('.') : '');
      let n = 1;
      while (out[key + (n > 1 ? '~' + n : '')]) n++;
      out[key + (n > 1 ? '~' + n : '')] = [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)];
    }
    return out;
  });
}

function compare(a, b) {
  const rows = [];
  for (const key of Object.keys(a)) {
    if (!b[key]) continue;
    const d = Math.max.apply(null, a[key].map((v, i) => Math.abs(v - b[key][i])));
    if (d > 4) rows.push({ key, d, a: a[key], b: b[key] });
  }
  return rows.sort((x, y) => y.d - x.d);
}

async function bottomGap(page) {
  return page.evaluate(() => {
    const bar = document.querySelector('.bottom-bar');
    if (!bar || !bar.getBoundingClientRect().height) return null;
    const barTop = Math.round(bar.getBoundingClientRect().top);
    let lowest = 0;
    const list = document.querySelectorAll('.page:not([hidden]) *, [id^="stage-"]:not([hidden]) *');
    for (let i = 0; i < list.length; i++) {
      const r = list[i].getBoundingClientRect();
      const cs = getComputedStyle(list[i]);
      if (!r.height || cs.position === 'fixed' || cs.visibility === 'hidden') continue;
      if (r.bottom <= innerHeight && r.bottom > lowest) lowest = r.bottom;
    }
    return { barTop, barHeight: Math.round(bar.getBoundingClientRect().height), contentBottom: Math.round(lowest) };
  });
}

module.exports = { chrome30, SCREENS, VIEW, BAR_VIEW, PW, ASSETS };
if (require.main === module) (async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const want = process.argv.slice(2);
  const names = want.length ? want : Object.keys(SCREENS);
  const src = fs.readFileSync(path.join(ASSETS, 'index.html'), 'utf8');
  const tmp = path.join(ASSETS, '__c30.html');
  fs.writeFileSync(tmp, chrome30(src));
  const barSrc = fs.readFileSync(path.join(ASSETS, 'navbar.html'), 'utf8');
  const barTmp = path.join(ASSETS, '__c30_navbar.html');
  fs.writeFileSync(barTmp, chrome30(barSrc));
  const browser = await chromium.launch({ channel: 'msedge' });
  const errors = [];
  const url = f => 'file:///' + f.replace(/\\/g, '/');

  async function open(file, view, legacy, query) {
    const ctx = await browser.newContext({ viewport: view, deviceScaleFactor: 1.5 });
    if (legacy) {
      await ctx.addInitScript(() => {
        window.CSS = window.CSS || {};
        CSS.supports = () => false; // 차와 같이 .legacy 규칙을 켠다
      });
    }
    await ctx.addInitScript(() => { window.K8NavBar = window.K8NavBar || { action: function () {} }; });
    const page = await ctx.newPage();
    page.on('pageerror', e => errors.push(path.basename(file) + ': ' + e.message));
    await page.goto(url(file) + (query || ''));
    await page.waitForTimeout(700);
    if (legacy) {
      // button 안의 flex 는 차에서 무너진다: block 으로 본다.
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

  const report = [];
  try {
    for (const name of names) {
      const go = SCREENS[name];
      if (!go) { report.push('?? 모르는 화면: ' + name); continue; }
      const shots = {};
      for (const [label, file, legacy] of [['original', path.join(ASSETS, 'index.html'), false], ['c30', tmp, true]]) {
        const s = await open(file, VIEW, legacy, '?vehicle=1&mock=1');
        await s.page.evaluate('(' + go.toString() + ')(window.K8Home)');
        await s.page.waitForTimeout(900);
        await s.page.screenshot({ path: path.join(OUT, name + '-' + label + '.png') });
        shots[label] = { boxes: await boxMap(s.page), bottom: await bottomGap(s.page) };
        await s.ctx.close();
      }
      const rows = compare(shots.original.boxes, shots.c30.boxes);
      report.push('== ' + name + ': 4px 넘게 다른 요소 ' + rows.length + '개');
      rows.slice(0, 10).forEach(r => report.push('   ' + r.key + '  원본 ' + r.a.join(',') + ' → 차 흉내 ' + r.b.join(',')));
      const b = shots.c30.bottom;
      if (b) {
        const gap = b.barTop - b.contentBottom;
        report.push('   하단바 위 ' + b.barTop + '(높이 ' + b.barHeight + '), 본문 아래 끝 ' + b.contentBottom +
          (gap < 0 ? '  ← 본문이 하단바 밑으로 ' + (-gap) + 'px 들어감' : '  (빈틈 ' + gap + 'px)'));
      }
    }

    // 하단바: 길안내 중, 긴 목적지·긴 곡 제목.
    const bars = {};
    for (const [label, file, legacy] of [['original', path.join(ASSETS, 'navbar.html'), false], ['c30', barTmp, true]]) {
      const s = await open(file, BAR_VIEW, legacy, '');
      await s.page.evaluate(() => {
        const V = window.K8NavBarView;
        if (!V) return;
        if (V.route) V.route('12.4km', '27분', '목적지 예시 아주 긴 이름 주차장 정문', true, '3:54 PM', '');
        if (V.media) V.media('아주 긴 곡 제목 예시가 미디어 칸보다 길 때 흐르는지 확인', '아티스트', true, 1000, 200000);
      });
      await s.page.waitForTimeout(600);
      await s.page.screenshot({ path: path.join(OUT, 'navbar-' + label + '.png') });
      bars[label] = await boxMap(s.page);
      await s.ctx.close();
    }
    const rows = compare(bars.original, bars.c30);
    report.push('== navbar: 4px 넘게 다른 요소 ' + rows.length + '개');
    rows.slice(0, 10).forEach(r => report.push('   ' + r.key + '  원본 ' + r.a.join(',') + ' → 차 흉내 ' + r.b.join(',')));
  } finally {
    await browser.close();
    fs.unlinkSync(tmp);
    fs.unlinkSync(barTmp);
  }
  report.push('화면 오류 ' + errors.length + '개' + (errors.length ? '\n   ' + errors.slice(0, 8).join('\n   ') : ''));
  report.push('사진: ' + OUT);
  console.log(report.join('\n'));
})();
