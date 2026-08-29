(function(){
  'use strict';
  if (window.__peteV25Installed) return;
  window.__peteV25Installed = true;

  var board = document.getElementById('board');
  var pad = document.getElementById('pad');
  var fastToggle = document.getElementById('fastPencilToggle');
  var numberToggle = document.getElementById('numberFirstToggle');
  var msg = document.getElementById('message');
  var erase = document.getElementById('erase');
  var v24 = window.__sudokuV24;
  if (!board || !pad || !fastToggle || !erase || !v24 || !v24.prefs) return;

  var prefs = v24.prefs;
  var paintPending = false;
  var clearingNotes = false;
  var barActive = 0;

  function say(t){
    if (!msg) return;
    msg.textContent = t;
    clearTimeout(window.__peteV25MsgTimer);
    window.__peteV25MsgTimer = setTimeout(function(){ if (msg.textContent === t) msg.textContent = ''; }, 2300);
  }

  function hapticBulk(v){
    try { if (window.Android && Android.setBulk) Android.setBulk(!!v); } catch(e) {}
  }

  function directCellNumber(cell){
    if (!cell || (!cell.classList.contains('given') && !cell.classList.contains('user'))) return 0;
    for (var i=0; i<cell.childNodes.length; i++) {
      var node = cell.childNodes[i];
      if (node.nodeType === 3) {
        var n = parseInt((node.nodeValue || '').trim(), 10);
        if (!isNaN(n) && n >= 1 && n <= 9) return n;
      }
    }
    var n2 = parseInt(cell.textContent, 10);
    return (!isNaN(n2) && n2 >= 1 && n2 <= 9) ? n2 : 0;
  }

  function activeNumber(){
    if (prefs.numberFirst && prefs.locked >= 1 && prefs.locked <= 9) return prefs.locked;
    if (barActive >= 1 && barActive <= 9) return barActive;
    var selected = board.querySelector('.cell.selected');
    return directCellNumber(selected);
  }

  function repaint(){
    paintPending = false;
    var active = activeNumber();
    var cells = board.querySelectorAll('.cell');
    for (var i=0; i<cells.length; i++) {
      cells[i].classList.toggle('v25-number-match', !!active && directCellNumber(cells[i]) === active);
      var notes = cells[i].querySelectorAll('.note');
      for (var j=0; j<notes.length; j++) {
        var n = parseInt(notes[j].textContent, 10);
        notes[j].classList.toggle('v25-note-match', !!active && !isNaN(n) && n === active);
      }
    }
    var nums = pad.querySelectorAll('.num');
    for (var k=0; k<nums.length; k++) {
      nums[k].classList.toggle('v25-active-number', !!active && k+1 === active);
    }
  }

  function schedulePaint(){
    if (paintPending || clearingNotes) return;
    paintPending = true;
    setTimeout(repaint, 0);
  }

  function clearLiveNotes(){
    if (clearingNotes) return;
    clearingNotes = true;
    hapticBulk(true);
    var cleared = 0;
    try {
      for (var idx=0; idx<81; idx++) {
        var cells = board.querySelectorAll('.cell');
        var cell = cells[idx];
        if (!cell || !cell.querySelector('.notes')) continue;
        cell.click();
        erase.click();
        cleared++;
      }
    } finally {
      hapticBulk(false);
    }

    try {
      sessionStorage.setItem('peteSudokuV25Message', 'Fast Pencil OFF — '+cleared+' note cells cleared');
    } catch(e) {}

    setTimeout(function(){ location.reload(); }, 30);
  }

  var style = document.createElement('style');
  style.textContent = '\n    .cell.v25-number-match{color:#34c759!important;text-shadow:0 0 0 #34c759}\n    .cell .note.v25-note-match{color:#ff453a!important;font-weight:900!important}\n    .num.v25-active-number,.num.v24-locked{background:#34c759!important;color:#07180d!important;outline:2px solid #7ee69a!important;outline-offset:-2px}\n  ';
  document.head.appendChild(style);

  fastToggle.addEventListener('change', function(){
    if (!fastToggle.checked) setTimeout(clearLiveNotes, 0);
    else schedulePaint();
  });

  // Capture number-bar taps before the game's normal input handler runs.
  // This makes color highlighting work whether Number First is ON or OFF.
  pad.addEventListener('click', function(e){
    var btn = e.target.closest('.num');
    if (!btn) return;
    var nums = Array.prototype.slice.call(pad.querySelectorAll('.num'));
    var n = nums.indexOf(btn) + 1;
    if (n >= 1 && n <= 9) barActive = n;
    setTimeout(schedulePaint, 0);
  }, true);

  // A filled board cell becomes the active color number. Empty cells leave the
  // last number-bar selection active, which is useful for repeated entry.
  board.addEventListener('click', function(e){
    var cell = e.target.closest('.cell');
    var n = directCellNumber(cell);
    if (n >= 1 && n <= 9 && !prefs.numberFirst) barActive = n;
    setTimeout(schedulePaint, 0);
  }, false);

  if (numberToggle) numberToggle.addEventListener('change', function(){
    if (!numberToggle.checked && prefs.locked >= 1 && prefs.locked <= 9) barActive = prefs.locked;
    setTimeout(schedulePaint, 0);
  }, false);

  var observer = new MutationObserver(function(){ schedulePaint(); });
  observer.observe(board, {childList:true, subtree:true});

  var pending = null;
  try {
    pending = sessionStorage.getItem('peteSudokuV25Message');
    if (pending) sessionStorage.removeItem('peteSudokuV25Message');
  } catch(e) {}
  if (pending) setTimeout(function(){ say(pending); }, 120);

  repaint();
  window.__sudokuV25 = {
    activeNumber:activeNumber,
    repaint:repaint,
    clearLiveNotes:clearLiveNotes,
    setBarActive:function(n){ barActive=Number(n)||0; schedulePaint(); }
  };
})();
