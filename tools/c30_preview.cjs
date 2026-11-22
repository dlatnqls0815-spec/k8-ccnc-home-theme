// 0.5.0-test18: PC 흉내 비교 도구.
// 차 WebView(Chrome 30)는 clamp()/min()/max() 와 flex gap 을 무시한다.
// 이 도구는 그 상태를 흉내 내어(선언을 지우고) 원본과 나란히 찍고,
// 원본과 4px 넘게 다른 요소를 목록으로 낸다.
// 사용: node tools/c30_preview.cjs
const { chromium } = require('C:/AI-Work/2026-11-21-k8-ccnc-home-cards/checks/browser-tools/node_modules/playwright-core');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const OUT = path.join(ROOT, 'captures', 'c30');
const PAGE = 'file:///' + path.join(ROOT, 'assets', 'index.html').replace(/\\/g, '/').replace(/ /g, '%20');
const BAR = 'file:///' + path.join(ROOT, 'assets', 'navbar.html').replace(/\\/g, '/').replace(/ /g, '%20');
const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';

// Chrome 30 이 무시하는 선언을 지운다.
function stripChrome30(css) {
  return css
    .replace(/[-a-zA-Z]+\s*:\s*[^;{}]*?(?:clamp|min|max)\([^;{}]*\)\s*;/g, '')
    .replace(/[-a-zA-Z]+\s*:\s*[^;{}]*?calc\([^;{}]*vw[^;{}]*\)\s*;/g, '')
    .replace(/(^|[\s;{])(row-gap|column-gap|grid-gap|gap)\s*:\s*[^;}]*;?/g, '$1');
}

const SCREENS = [
  ['home', null], ['split', 'split'], ['media-bt', 'media'], ['media-usb', 'media'],
  ['media-radio', 'media'], ['settings', 'settings']
];

async function boxMap(page) {
  return await page.evaluate(() => {
    var out = [];
    var list = document.querySelectorAll('body *');
    for (var i = 0; i < list.length; i++) {
      var el = list[i], r = el.getBoundingClientRect();
      if (!r.width || !r.height || r.top > 720 || r.bottom < 0) continue;
      var cs = window.getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden') continue;
      out.push({
        key: el.tagName + '.' + (el.className || '') + '#' + (el.id || ''),
        x: Math.round(r.left), y: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height)
      });
    }
    return out;
  });
}

function diff(a, b) {
  const map = new Map();
  for (const el of a) map.set(el.key, el);
  const out = [];
  for (const el of b) {
    const old = map.get(el.key);
    if (!old) continue;
    const d = Math.max(Math.abs(old.x - el.x), Math.abs(old.y - el.y), Math.abs(old.w - el.w), Math.abs(old.h - el.h));
    if (d > 4) out.push({ key: el.key, d: d, a: old, b: el });
  }
  return out;
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: EDGE });
  const errors = [];

  async function open(url, viewport, legacy) {
    const ctx = await browser.newContext({ viewport: viewport, deviceScaleFactor: 1 });
    if (legacy) await ctx.addInitScript(() => { CSS.supports = () => false; });
    const page = await ctx.newPage();
    page.on('pageerror', e => errors.push('PAGEERROR: ' + e.message));
    if (legacy) {
      await page.route(url, async route => {
        const res = await route.fetch();
        let body = await res.text();
        body = body.replace(/<style[^>]*>([\s\S]*?)<\/style>/gi, (m, css) => m.replace(css, stripChrome30(css)));
        await route.fulfill({ response: res, body: body, headers: res.headers() });
      });
    }
    await page.goto(url);
    await page.waitForTimeout(500);
    if (legacy) {
      await page.addStyleTag({ content: '*{gap:0!important}' });
      await page.evaluate(() => {
        var bs = document.querySelectorAll('button');
        for (var i = 0; i < bs.length; i++) {
          var d = window.getComputedStyle(bs[i]).display;
          if (d === 'flex') bs[i].style.display = 'block';
          else if (d === 'inline-flex') bs[i].style.display = 'inline-block';
        }
      });
      await page.waitForTimeout(250);
    }
    return { ctx, page };
  }

  for (const [name, page] of SCREENS) {
    const plain = await open(PAGE + '?vehicle=1&mock=1', { width: 1920, height: 720 }, false);
    const mock = await open(PAGE + '?vehicle=1&mock=1', { width: 1920, height: 720 }, true);
    for (const s of [plain, mock]) {
      if (s === mock) {
        await s.page.evaluate(() => {
          if (window.K8Home) { window.K8Home.openHome(); window.K8Home.showPage('apps1'); }
        });
        await s.page.waitForTimeout(300);
      }
    }
    // 본체: 원본·흉내 각각 찍기
    await plain.page.screenshot({ path: path.join(OUT, name + '-original.png') });
    await mock.page.screenshot({ path: path.join(OUT, name + '-mock.png') });
    const d = diff(await boxMap(plain.page), await boxMap(mock.page));
    if (d.length) {
      console.log('== ' + name + ' : 4px 넘게 다른 요소 ' + d.length + '개');
      d.slice(0, 12).forEach(x => console.log('   ' + x.key + '  원본 ' + JSON.stringify(x.a) + ' → 흉내 ' + JSON.stringify(x.b)));
    } else {
      console.log('== ' + name + ' : 4px 넘게 다른 요소 없음');
    }
    await plain.ctx.close();
    await mock.ctx.close();
  }

  // 하단바
  const barPlain = await open(BAR, { width: 1920, height: 95 }, false);
  const barMock = await open(BAR, { width: 1920, height: 95 }, true);
  await barPlain.page.evaluate(() => window.K8NavBarView.route('9.6km', '23분', '아리랑리조텔 속초점 정문 주차장', true, '3:54 PM', '서울'));
  await barMock.page.evaluate(() => window.K8NavBarView.route('9.6km', '23분', '아리랑리조텔 속초점 정문 주차장', true, '3:54 PM', '서울'));
  await barPlain.page.waitForTimeout(200);
  await barMock.page.waitForTimeout(200);
  await barPlain.page.screenshot({ path: path.join(OUT, 'navbar-original.png') });
  await barMock.page.screenshot({ path: path.join(OUT, 'navbar-mock.png') });
  const bd = diff(await boxMap(barPlain.page), await boxMap(barMock.page));
  console.log('== navbar : 4px 넘게 다른 요소 ' + bd.length + '개');
  bd.slice(0, 12).forEach(x => console.log('   ' + x.key + '  원본 ' + JSON.stringify(x.a) + ' → 흉내 ' + JSON.stringify(x.b)));
  await barPlain.ctx.close();
  await barMock.ctx.close();

  console.log('화면 오류: ' + errors.length + (errors.length ? '\n' + errors.slice(0, 10).join('\n') : ''));
  console.log('저장 위치: ' + OUT);
  await browser.close();
})();
