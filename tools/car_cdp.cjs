// 차 테마 WebView(Chrome 30) 안에서 JS 를 돌려 결과를 받는 도구(원격 디버깅, 읽기·화면 이동만).
// 먼저: adb forward tcp:9222 localabstract:webview_devtools_remote_<테마 pid>
// 사용: node tools/car_cdp.cjs <js 파일>   (파일 안 마지막 식의 값이 JSON 으로 출력된다. async 함수면 기다린다)
//       node tools/car_cdp.cjs -e "<식>"
// Node 내장 WebSocket 은 이 WebView 와 안 맞아 playwright-core 의 ws 를 쓴다.
'use strict';
const fs = require('fs');
const http = require('http');
const PW = process.env.K8_PLAYWRIGHT ||
  'C:/AI-Work/2026-11-21-k8-ccnc-home-cards/checks/browser-tools/node_modules/playwright-core';
const WS = require(PW + '/lib/utilsBundle').ws;
const PORT = Number(process.env.K8_CDP_PORT || 9222);

function pages() {
  return new Promise((ok, bad) => {
    http.get({ host: '127.0.0.1', port: PORT, path: '/json' }, res => {
      let body = '';
      res.on('data', c => { body += c; });
      res.on('end', () => { try { ok(JSON.parse(body)); } catch (e) { bad(e); } });
    }).on('error', bad);
  });
}

async function evaluate(expression, timeoutMs) {
  const list = await pages();
  const page = list.find(p => /index\.html/.test(p.url)) || list[0];
  if (!page) throw new Error('테마 페이지를 못 찾음(포트 전달 확인)');
  const ws = new WS(page.webSocketDebuggerUrl);
  await new Promise((ok, bad) => { ws.on('open', ok); ws.on('error', bad); });
  let id = 0;
  const call = (method, params) => new Promise((ok, bad) => {
    const my = ++id;
    const timer = setTimeout(() => bad(new Error('시간 초과 ' + method)), timeoutMs || 20000);
    const onMsg = data => {
      const m = JSON.parse(data);
      if (m.id !== my) return;
      clearTimeout(timer); ws.removeListener('message', onMsg);
      m.error ? bad(new Error(JSON.stringify(m.error))) : ok(m.result);
    };
    ws.on('message', onMsg);
    ws.send(JSON.stringify({ id: my, method, params }));
  });
  try {
    // Chrome 30 은 awaitPromise 를 모른다: 결과를 전역에 두고 기다렸다가 읽는다.
    const wrapped = '(function(){window.__cdpDone=false;window.__cdpOut=null;try{var r=(' + expression + ');' +
      'if(r&&typeof r.then==="function"){r.then(function(v){window.__cdpOut=JSON.stringify(v);window.__cdpDone=true;},' +
      'function(e){window.__cdpOut=JSON.stringify({error:String(e)});window.__cdpDone=true;});}' +
      'else{window.__cdpOut=JSON.stringify(r);window.__cdpDone=true;}}catch(e){window.__cdpOut=JSON.stringify({error:String(e&&e.stack||e)});window.__cdpDone=true;}})()';
    await call('Runtime.evaluate', { expression: wrapped });
    const until = Date.now() + (timeoutMs || 20000);
    while (Date.now() < until) {
      const r = await call('Runtime.evaluate', { expression: 'window.__cdpDone?window.__cdpOut:"__wait__"', returnByValue: true });
      const v = r.result && r.result.value;
      if (v !== '__wait__') return v === undefined ? null : JSON.parse(v);
      await new Promise(ok => setTimeout(ok, 200));
    }
    throw new Error('결과 시간 초과');
  } finally { ws.close(); }
}

module.exports = { evaluate };
if (require.main === module) {
  const a = process.argv.slice(2);
  const src = a[0] === '-e' ? a[1] : fs.readFileSync(a[0], 'utf8');
  evaluate(src, 60000).then(v => { console.log(JSON.stringify(v, null, 1)); }, e => { console.error(String(e)); process.exitCode = 1; });
}
