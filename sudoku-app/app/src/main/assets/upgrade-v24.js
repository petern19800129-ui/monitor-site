(function(){
  'use strict';
  if (window.__peteV24Installed) return;
  window.__peteV24Installed = true;

  var $ = function(id){ return document.getElementById(id); };
  var board = $('board');
  var wrap = board && board.parentElement;
  var pad = $('pad');
  var tools = document.querySelector('.tools');
  var pencil = $('pencil');
  var erase = $('erase');
  var undo = $('undo');
  var hint = $('hint');
  var msg = $('message');
  if (!board || !wrap || !pad || !tools || !pencil || !erase || !undo || !hint) return;

  var prefs = {fast:false, numberFirst:false, locked:0};
  try {
    var saved = JSON.parse(localStorage.getItem('peteSudokuV24Prefs') || 'null');
    if (saved) {
      prefs.fast = !!saved.fast;
      prefs.numberFirst = !!saved.numberFirst;
      prefs.locked = Number(saved.locked) || 0;
    }
  } catch(e) {}
  var bypass = false;
  var filling = false;
  var refillTimer = null;

  function savePrefs(){
    try { localStorage.setItem('peteSudokuV24Prefs', JSON.stringify(prefs)); } catch(e) {}
  }
  function haptic(){ try { if (window.Android && Android.haptic) Android.haptic(); } catch(e) {} }
  function setBulk(v){ try { if (window.Android && Android.setBulk) Android.setBulk(!!v); } catch(e) {} }
  function say(t){
    if (!msg) return;
    msg.textContent = t;
    clearTimeout(window.__peteV24Msg);
    window.__peteV24Msg = setTimeout(function(){ if (msg.textContent === t) msg.textContent = ''; }, 2200);
  }
  function boardValues(){
    return Array.prototype.map.call(document.querySelectorAll('.cell'), function(c){
      if (c.classList.contains('given') || c.classList.contains('user')) {
        var n = parseInt(c.textContent, 10);
        return isNaN(n) ? 0 : n;
      }
      return 0;
    });
  }
  function candidates(v, idx){
    var r = Math.floor(idx/9), c = idx%9, used = {};
    for (var x=0; x<9; x++) {
      if (v[r*9+x]) used[v[r*9+x]] = 1;
      if (v[x*9+c]) used[v[x*9+c]] = 1;
    }
    var br = Math.floor(r/3)*3, bc = Math.floor(c/3)*3;
    for (var rr=br; rr<br+3; rr++) for (var cc=bc; cc<bc+3; cc++) if (v[rr*9+cc]) used[v[rr*9+cc]] = 1;
    var out=[]; for (var n=1; n<=9; n++) if (!used[n]) out.push(n); return out;
  }
  function currentNotes(cell){
    var out={};
    var ns=cell.querySelectorAll('.note');
    for (var i=0;i<ns.length;i++) {
      var n=parseInt(ns[i].textContent,10); if (!isNaN(n)) out[n]=1;
    }
    return out;
  }
  function pencilActive(){ return pencil.classList.contains('active'); }
  function nativeClick(el){
    bypass = true;
    try { el.click(); } finally { bypass = false; }
  }
  function ensurePencilOff(){ if (pencilActive()) nativeClick(pencil); }
  function scheduleRefill(){
    if (!prefs.fast || filling) return;
    clearTimeout(refillTimer);
    refillTimer = setTimeout(function(){ fillAllCandidates(false); }, 20);
  }

  function fillAllCandidates(announce){
    if (!prefs.fast || filling) return;
    var cells = document.querySelectorAll('.cell');
    if (!cells.length) return;
    filling = true;
    setBulk(true);
    ensurePencilOff();
    nativeClick(pencil);
    var values = boardValues();
    var changed = 0, empty = 0, total = 0;
    try {
      for (var idx=0; idx<81; idx++) {
        cells = document.querySelectorAll('.cell');
        var cell = cells[idx];
        if (!cell || cell.classList.contains('given') || cell.classList.contains('user')) continue;
        empty++;
        var want = candidates(values, idx), wantSet={};
        for (var w=0; w<want.length; w++) { wantSet[want[w]]=1; total++; }
        var have = currentNotes(cell);
        var diff=[];
        for (var n=1;n<=9;n++) if (!!wantSet[n] !== !!have[n]) diff.push(n);
        if (!diff.length) continue;
        nativeClick(cell);
        for (var d=0; d<diff.length; d++) {
          var buttons=document.querySelectorAll('.num');
          nativeClick(buttons[diff[d]-1]);
          changed++;
        }
      }
    } finally {
      ensurePencilOff();
      setBulk(false);
      filling = false;
    }
    if (announce) say('⚡ Fast Pencil: '+total+' candidates in '+empty+' cells');
    updateUi();
  }

  var style=document.createElement('style');
  style.textContent='    .v24-modebar{display:grid;grid-template-columns:1fr 1fr;gap:6px;margin:0 0 8px}.v24-mode{display:flex;align-items:center;justify-content:space-between;gap:8px;background:var(--panel);border-radius:12px;padding:9px 10px;min-width:0}.v24-copy{min-width:0}.v24-copy b{display:block;font-size:12px}.v24-copy small{display:block;color:var(--muted);font-size:9px;line-height:1.25;margin-top:2px}.v24-switch{position:relative;width:42px;height:24px;flex:0 0 auto}.v24-switch input{position:absolute;opacity:0;pointer-events:none}.v24-slider{position:absolute;inset:0;border-radius:999px;background:var(--panel3);box-shadow:inset 0 0 0 1px #ffffff12;transition:.16s}.v24-slider:before{content:"";position:absolute;width:18px;height:18px;left:3px;top:3px;border-radius:50%;background:var(--muted);transition:.16s}.v24-switch input:checked+.v24-slider{background:var(--accentStrong)}.v24-switch input:checked+.v24-slider:before{transform:translateX(18px);background:#fff}.num.v24-locked{background:var(--accentStrong)!important;color:#fff!important;outline:2px solid var(--accent);outline-offset:-2px}.v24-zoom{width:52px!important;font-size:10px!important;font-weight:850!important}@media(max-width:380px){.v24-modebar{grid-template-columns:1fr}}';
  document.head.appendChild(style);

  var oldFast=$('fastPencil'); if (oldFast) oldFast.remove();
  var oldZoom=$('zoomBadgeV23'); if (oldZoom) oldZoom.remove();

  var modebar=document.createElement('div');
  modebar.className='v24-modebar';
  modebar.innerHTML='<div class="v24-mode"><div class="v24-copy"><b>Fast pencil</b><small>Auto-fill and update candidates</small></div><label class="v24-switch"><input type="checkbox" id="fastPencilToggle"><span class="v24-slider"></span></label></div><div class="v24-mode"><div class="v24-copy"><b>Number first</b><small>Pick a number, then tap cells</small></div><label class="v24-switch"><input type="checkbox" id="numberFirstToggle"><span class="v24-slider"></span></label></div>';
  tools.parentNode.insertBefore(modebar, tools);
  var fastToggle=$('fastPencilToggle'), numberToggle=$('numberFirstToggle');

  var header=document.querySelector('.header-actions') || document.querySelector('.headbtns');
  var zoomBtn=document.createElement('button');
  zoomBtn.id='zoomBtnV24';
  zoomBtn.className=(header && header.querySelector('.icon-btn'))?'icon-btn v24-zoom':'icon v24-zoom';
  zoomBtn.textContent='100%';
  if (header) header.insertBefore(zoomBtn, header.firstChild);

  function updateUi(){
    fastToggle.checked=prefs.fast;
    numberToggle.checked=prefs.numberFirst;
    var nums=document.querySelectorAll('.num');
    for (var i=0;i<nums.length;i++) nums[i].classList.toggle('v24-locked', prefs.numberFirst && prefs.locked===i+1);
  }

  fastToggle.addEventListener('change',function(){
    prefs.fast=fastToggle.checked;
    savePrefs();
    if (prefs.fast) {
      ensurePencilOff();
      say('⚡ Fast Pencil ON');
      setTimeout(function(){ fillAllCandidates(true); }, 10);
    } else {
      say('Fast Pencil OFF');
    }
    haptic(); updateUi();
  });
  numberToggle.addEventListener('change',function(){
    prefs.numberFirst=numberToggle.checked;
    if (!prefs.numberFirst) prefs.locked=0;
    savePrefs(); haptic(); updateUi();
    say(prefs.numberFirst?'Number first ON — choose a number':'Number first OFF');
  });

  pad.addEventListener('click',function(e){
    if (bypass) return;
    var b=e.target.closest('.num'); if (!b) return;
    if (prefs.numberFirst) {
      e.preventDefault(); e.stopImmediatePropagation();
      var nums=Array.prototype.slice.call(document.querySelectorAll('.num'));
      var n=nums.indexOf(b)+1;
      if (b.classList.contains('done')) { say('All '+n+'s are already placed'); return; }
      prefs.locked=(prefs.locked===n)?0:n;
      savePrefs(); haptic(); updateUi();
      if (prefs.locked) say('Number '+prefs.locked+' selected — tap the board');
      else say('Number selection cleared');
      return;
    }
    if (prefs.fast) setTimeout(scheduleRefill, 0);
  }, true);

  board.addEventListener('click',function(e){
    if (bypass) return;
    var cell=e.target.closest('.cell'); if (!cell) return;
    if (prefs.numberFirst && prefs.locked && !cell.classList.contains('given')) {
      e.preventDefault(); e.stopImmediatePropagation();
      nativeClick(cell);
      var nums=document.querySelectorAll('.num'), target=nums[prefs.locked-1];
      if (target && !target.classList.contains('done')) nativeClick(target);
      if (prefs.fast) scheduleRefill();
      updateUi();
    }
  }, true);

  pencil.addEventListener('click',function(e){
    if (bypass) return;
    if (prefs.fast) { e.preventDefault(); e.stopImmediatePropagation(); say('Turn Fast Pencil off for manual notes'); }
  }, true);
  erase.addEventListener('click',function(){ if (!bypass && prefs.fast) setTimeout(scheduleRefill,0); }, true);
  hint.addEventListener('click',function(){ if (!bypass && prefs.fast) setTimeout(scheduleRefill,0); }, true);
  undo.addEventListener('click',function(e){
    if (bypass || !prefs.fast) return;
    e.preventDefault(); e.stopImmediatePropagation();
    var before=boardValues().join(','), after=before, loops=0;
    setBulk(true);
    try {
      while (after===before && loops<100) {
        nativeClick(undo);
        after=boardValues().join(',');
        loops++;
      }
    } finally { setBulk(false); }
    scheduleRefill();
  }, true);

  var zoom=1,px=0,py=0,g=null,drag=false,lastTap=0;
  wrap.style.position='relative'; wrap.style.overflow='hidden'; wrap.style.touchAction='none';
  board.style.touchAction='none'; board.style.transformOrigin='0 0'; board.style.willChange='transform';
  function clamp(){var mx=Math.max(0,(zoom-1)*board.offsetWidth),my=Math.max(0,(zoom-1)*board.offsetHeight);px=Math.min(0,Math.max(-mx,px));py=Math.min(0,Math.max(-my,py));}
  function applyZoom(){clamp();board.style.transform='translate('+px+'px,'+py+'px) scale('+zoom+')';zoomBtn.textContent=Math.round(zoom*100)+'%';}
  function resetZoom(){zoom=1;px=0;py=0;g=null;applyZoom();}
  function dist(a,b){return Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY);}
  function mid(a,b){var r=wrap.getBoundingClientRect();return{x:(a.clientX+b.clientX)/2-r.left,y:(a.clientY+b.clientY)/2-r.top};}
  wrap.addEventListener('touchstart',function(e){drag=false;if(e.touches.length===2){e.preventDefault();var m=mid(e.touches[0],e.touches[1]);g={t:'pinch',d:dist(e.touches[0],e.touches[1]),z:zoom,x:px,y:py,mx:m.x,my:m.y};}else if(e.touches.length===1&&zoom>1){var t=e.touches[0];g={t:'pan',cx:t.clientX,cy:t.clientY,x:px,y:py};}},{passive:false});
  wrap.addEventListener('touchmove',function(e){if(!g)return;if(g.t==='pinch'&&e.touches.length>=2){e.preventDefault();drag=true;var nz=Math.min(3,Math.max(1,g.z*dist(e.touches[0],e.touches[1])/g.d)),f=nz/g.z;zoom=nz;px=g.mx-(g.mx-g.x)*f;py=g.my-(g.my-g.y)*f;applyZoom();}else if(g.t==='pan'&&e.touches.length===1){e.preventDefault();var t=e.touches[0],dx=t.clientX-g.cx,dy=t.clientY-g.cy;if(Math.abs(dx)+Math.abs(dy)>5)drag=true;px=g.x+dx;py=g.y+dy;applyZoom();}},{passive:false});
  wrap.addEventListener('touchend',function(e){if(!e.touches.length){g=null;if(!drag){var n=Date.now();if(n-lastTap<320){resetZoom();haptic();lastTap=0;}else lastTap=n;}}});
  zoomBtn.addEventListener('click',function(){resetZoom();haptic();});
  window.addEventListener('resize',applyZoom);

  updateUi(); applyZoom();
  if (prefs.fast) setTimeout(function(){fillAllCandidates(false);}, 120);
  window.__sudokuV24={fillAllCandidates:function(){fillAllCandidates(true);},prefs:prefs,boardValues:boardValues};
})();
