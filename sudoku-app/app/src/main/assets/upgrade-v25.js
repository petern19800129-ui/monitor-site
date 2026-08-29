(function(){
  'use strict';
  if (window.__peteV25Installed) return;
  window.__peteV25Installed = true;

  var board = document.getElementById('board');
  var pad = document.getElementById('pad');
  var fastToggle = document.getElementById('fastPencilToggle');
  var numberToggle = document.getElementById('numberFirstToggle');
  var msg = document.getElementById('message');
  var v24 = window.__sudokuV24;
  if (!board || !pad || !fastToggle || !v24 || !v24.prefs) return;

  var prefs = v24.prefs;
  var paintPending = false;

  function say(t){
    if (!msg) return;
    msg.textContent = t;
    clearTimeout(window.__peteV25MsgTimer);
    window.__peteV25MsgTimer = setTimeout(function(){ if (msg.textContent === t) msg.textContent = ''; }, 2300);
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
    if (paintPending) return;
    paintPending = true;
    setTimeout(repaint, 0);
  }

  function clearStoredNotesAndReload(){
    try {
      var raw = localStorage.getItem('peteSudokuV2');
      if (raw) {
        var state = JSON.parse(raw);
        state.notes = Array.from({length:81}, function(){ return []; });
        localStorage.setItem('peteSudokuV2', JSON.stringify(state));
      }
      sessionStorage.setItem('peteSudokuV25Message', 'Fast Pencil OFF — notes cleared');
    } catch(e) {}
    location.reload();
  }

  var style = document.createElement('style');
  style.textContent = '\n    .cell.v25-number-match{color:#34c759!important;text-shadow:0 0 0 #34c759}\n    .cell .note.v25-note-match{color:#ff453a!important;font-weight:900!important}\n    .num.v25-active-number,.num.v24-locked{background:#34c759!important;color:#07180d!important;outline:2px solid #7ee69a!important;outline-offset:-2px}\n  ';
  document.head.appendChild(style);

  fastToggle.addEventListener('change', function(){
    if (!fastToggle.checked) {
      setTimeout(clearStoredNotesAndReload, 0);
    } else {
      schedulePaint();
    }
  });

  board.addEventListener('click', function(){ setTimeout(schedulePaint, 0); }, false);
  pad.addEventListener('click', function(){ setTimeout(schedulePaint, 0); }, false);
  if (numberToggle) numberToggle.addEventListener('change', function(){ setTimeout(schedulePaint, 0); }, false);

  var observer = new MutationObserver(function(){ schedulePaint(); });
  observer.observe(board, {childList:true, subtree:true});

  var pending = null;
  try {
    pending = sessionStorage.getItem('peteSudokuV25Message');
    if (pending) sessionStorage.removeItem('peteSudokuV25Message');
  } catch(e) {}
  if (pending) setTimeout(function(){ say(pending); }, 120);

  repaint();
  window.__sudokuV25 = {activeNumber:activeNumber, repaint:repaint};
})();
