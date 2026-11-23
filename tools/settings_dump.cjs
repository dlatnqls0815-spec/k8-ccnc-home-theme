// 테마 설정 화면 덤프: 설정 첫 화면의 칸을 하나씩 열고 왼쪽 탭을 모두 눌러
// 탭 이름·오른쪽 항목 글자를 captures/settings/tree.json 에, 화면 사진을 captures/settings/*.png 에 둔다.
// 차와 같은 1280x480(배율 1.5), .legacy 규칙을 켠 상태로 본다.
// 사용: node tools/settings_dump.cjs
'use strict';
const fs = require('fs');
const path = require('path');
const PW = process.env.K8_PLAYWRIGHT ||
  'C:/AI-Work/2026-11-21-k8-ccnc-home-cards/checks/browser-tools/node_modules/playwright-core';
const { chromium } = require(PW);
const ROOT = path.resolve(__dirname, '..');
const OUT = path.join(ROOT, 'captures', 'settings');

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ channel: 'msedge' });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 480 }, deviceScaleFactor: 1.5 });
  await ctx.addInitScript(() => { window.CSS = window.CSS || {}; CSS.supports = () => false; });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  await page.goto('file:///' + path.join(ROOT, 'assets', 'index.html').replace(/\\/g, '/') + '?vehicle=1&mock=1');
  await page.waitForTimeout(800);

  const cards = await page.evaluate(() => { K8Home.openHome(); K8Home.showPage('settings');
    return [].map.call(document.querySelectorAll('#settings-cards-grid .set-card'), b => b.textContent.trim()); });
  const tree = {};
  for (let ci = 0; ci < cards.length; ci++) {
    const name = cards[ci];
    await page.evaluate(i => { K8Home.openHome(); K8Home.showPage('settings');
      document.querySelectorAll('#settings-cards-grid .set-card')[i].click(); }, ci);
    await page.waitForTimeout(400);
    const tabs = await page.evaluate(() => [].map.call(document.querySelectorAll('#theme-settings-detail .sl-nav button, #theme-settings-detail .sl-tab'),
      b => b.textContent.trim()).filter((v, i, a) => v && a.indexOf(v) === i));
    tree[name] = {};
    const list = tabs.length ? tabs : ['(탭 없음)'];
    for (let ti = 0; ti < list.length; ti++) {
      if (tabs.length) {
        await page.evaluate(label => {
          const bs = document.querySelectorAll('#theme-settings-detail .sl-nav button, #theme-settings-detail .sl-tab');
          for (let i = 0; i < bs.length; i++) if (bs[i].textContent.trim() === label) { bs[i].click(); break; }
        }, list[ti]);
        await page.waitForTimeout(300);
      }
      const body = await page.evaluate(() => {
        const pane = document.querySelector('#theme-settings-detail');
        if (!pane || pane.hidden) return { text: [], other: document.querySelector('.shell').className };
        const nav = pane.querySelector('.sl-nav');
        const out = [];
        const walk = el => {
          if (nav && (el === nav || nav.contains(el))) return;
          const cs = getComputedStyle(el);
          if (cs.display === 'none' || cs.visibility === 'hidden') return;
          for (const n of el.childNodes) {
            if (n.nodeType === 3) { const t = n.textContent.replace(/\s+/g, ' ').trim(); if (t) out.push(t); }
            else if (n.nodeType === 1) walk(n);
          }
        };
        walk(pane);
        return { text: out };
      });
      tree[name][list[ti]] = body.text;
      await page.screenshot({ path: path.join(OUT, String(ci).padStart(2, '0') + '-' + ti + '.png') });
    }
  }
  fs.writeFileSync(path.join(OUT, 'tree.json'), JSON.stringify(tree, null, 1));
  console.log('cards', cards.join(' / '));
  for (const [k, v] of Object.entries(tree)) console.log('## ' + k + ' : ' + Object.keys(v).join(' | '));
  console.log('errors', errors.length, errors.slice(0, 3));
  await browser.close();
})();
