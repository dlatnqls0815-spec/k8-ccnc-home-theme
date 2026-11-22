/* 긴 글자 흐르기. 칸보다 긴 글자는 "…"로 자르지 않고 천천히 왼쪽으로 흘려 끝까지 보여 준다.
   칸 크기는 그대로라 옆 버튼이 밀리지 않는다. 차량 WebView(Chrome 30)에 맞춰 ES5와 -webkit- 전환만 쓴다.
   사용: <script>window.K8_MARQUEE = '선택자, 선택자';</script><script src="marquee.js"></script>
   흐름: 2초 멈춤 → 초당 45px로 끝까지 → 1.5초 멈춤 → 처음으로 → 반복. */
(function () {
  var selector = window.K8_MARQUEE || '';
  if (!selector) return;
  var SPEED = 45, START_PAUSE = 2000, END_PAUSE = 1500;
  var style = document.createElement('style');
  style.textContent =
    '.mq-on { text-overflow: clip !important; }' +
    // !important: page rules such as ".caption span { display:block; position:absolute }" also hit the wrapper.
    '.mq-on > .mq { display: inline-block !important; position: static !important; float: none !important; ' +
    'width: auto !important; max-width: none !important; min-width: 0 !important; margin: 0 !important; ' +
    'padding: 0 !important; overflow: visible !important; white-space: nowrap !important; vertical-align: top; ' +
    // 글자 모양은 원래 글자와 똑같이(굵기 하나). 감싸는 틀이 생겼다고 굵기가 바뀌면 안 된다.
    'font-weight: inherit !important; font-style: inherit !important; font-family: inherit !important; ' +
    'font-size: inherit !important; line-height: inherit !important; letter-spacing: inherit !important; ' +
    '-webkit-transform: translate3d(0,0,0); transform: translate3d(0,0,0); }';
  document.head.appendChild(style);

  function setX(inner, x, seconds) {
    var transition = seconds ? seconds + 's linear' : 'none';
    inner.style.webkitTransition = seconds ? '-webkit-transform ' + transition : 'none';
    inner.style.transition = seconds ? 'transform ' + transition : 'none';
    inner.style.webkitTransform = inner.style.transform = 'translate3d(' + x + 'px,0,0)';
  }

  function stop(el) {
    var state = el.__mq;
    if (!state) return;
    window.clearTimeout(state.timer);
    el.__mq = null;
    el.classList.remove('mq-on');
    var inner = el.firstChild;
    if (inner && inner.nodeType === 1 && inner.className === 'mq' && el.childNodes.length === 1) {
      while (inner.firstChild) el.insertBefore(inner.firstChild, inner);
      el.removeChild(inner);
    }
  }

  // Each cycle: START_PAUSE at 0, move to -distance at SPEED, END_PAUSE, again. The phase is kept by time
  // so that the app rewriting the same text (status refresh) continues the scroll instead of restarting it.
  function place(el) {
    var state = el.__mq;
    if (!state || !document.documentElement.contains(el)) return;
    window.clearTimeout(state.timer);
    var inner = state.inner;
    var distance = inner.offsetWidth - el.clientWidth;
    if (distance <= 1) { stop(el); return; }
    var move = distance / SPEED * 1000;
    var cycle = START_PAUSE + move + END_PAUSE;
    var t = (Date.now() - state.since) % cycle;
    if (t < START_PAUSE) {
      setX(inner, 0, 0);
      state.timer = window.setTimeout(function () { place(el); }, START_PAUSE - t + 5);
    } else if (t < START_PAUSE + move) {
      var done = (t - START_PAUSE) / move;
      setX(inner, -distance * done, 0);
      inner.offsetWidth; // apply the start position before the transition
      setX(inner, -distance, (move - (t - START_PAUSE)) / 1000);
      state.timer = window.setTimeout(function () { place(el); }, START_PAUSE + move - t + 5);
    } else {
      setX(inner, -distance, 0);
      state.timer = window.setTimeout(function () { state.since = Date.now(); place(el); }, cycle - t);
    }
  }

  function update(el) {
    var width = el.clientWidth;
    var key = el.textContent + '|' + width;
    var state = el.__mq;
    var wrapped = el.firstChild && el.firstChild.nodeType === 1 && el.firstChild.className === 'mq' &&
      el.childNodes.length === 1;
    if (state && wrapped && state.key === key) return;
    var since = state && state.key === key ? state.since : Date.now();
    if (state) stop(el);
    else if (wrapped) { el.__mq = { timer: 0 }; stop(el); }
    if (!width || el.scrollWidth <= width + 1) return;
    var inner = document.createElement('span');
    inner.className = 'mq';
    while (el.firstChild) inner.appendChild(el.firstChild);
    el.appendChild(inner);
    el.classList.add('mq-on');
    el.__mq = { key: key, inner: inner, timer: 0, since: since };
    place(el);
  }

  var pending = 0;
  function scan() {
    pending = 0;
    var list = document.querySelectorAll(selector);
    for (var i = 0; i < list.length; i++) update(list[i]);
  }
  function later() {
    if (!pending) pending = window.setTimeout(scan, 250);
  }
  if (window.MutationObserver) {
    new MutationObserver(function (records) {
      for (var i = 0; i < records.length; i++) {
        var target = records[i].target;
        // Our own wrap/unwrap is not a text change.
        if (target.nodeType === 1 && (target.className === 'mq' || /(^|\s)mq-on(\s|$)/.test(target.className))) {
          if (records[i].type === 'childList' && records[i].addedNodes.length === 1 &&
              records[i].addedNodes[0].className === 'mq') continue;
        }
        // 글자가 다시 쓰이면 흐르기 틀이 벗겨진다. 0.25초 뒤가 아니라 그리기 전에(지금) 다시 감싸야
        // 굵기가 바뀌어 보이거나 처음 위치로 튀는 깜빡임이 없다(차 Chrome 30 에서 보임).
        scan();
        return;
      }
    }).observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true,
      attributeFilter: ['hidden', 'class'] });
  }
  window.addEventListener('resize', later);
  window.setInterval(scan, 3000);
  later();
  window.K8Marquee = { scan: scan };
})();
