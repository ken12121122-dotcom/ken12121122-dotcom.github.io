/* 念動圖書館音樂播放候選 v0.1：純本機瀏覽器音訊，不上傳、不修改 Android 服務。 */
(function (root) {
  'use strict';
  if (root.LivingMusicPlayer) return;
  const audio = new Audio();
  audio.preload = 'metadata';
  let volumePref = null; try { volumePref = root.localStorage?.getItem('living-library-music-volume-v1') ?? null; } catch (_) {}
  const savedVolume = volumePref === null ? NaN : Number(volumePref);
  audio.volume = Number.isFinite(savedVolume) && savedVolume >= 0 && savedVolume <= 1 ? savedVolume : 0.7;
  let tracks = [];
  let position = -1;
  let repeat = true;
  let shuffle = false;
  let message = '選擇手機或電腦上的音樂檔案';
  const listeners = new Set();
  const supported = /\.(mp3|m4a|aac|wav|ogg|opus|flac|webm)$/i;
  function valid(file) {
    return !!file && typeof file.name === 'string' && typeof file.size === 'number' &&
      typeof file.slice === 'function' && file.size > 0 && file.size <= 250 * 1024 * 1024 &&
      ((file.type && file.type.startsWith('audio/')) || supported.test(file.name));
  }
  function snapshot() {
    return Object.freeze({tracks: tracks.map((x) => x.name), position,
      playing: !audio.paused && !audio.ended, repeat, shuffle,
      volume: audio.volume, message});
  }
  function emit() {
    const state = snapshot();
    listeners.forEach((fn) => { try { fn(state); } catch (_) {} });
  }
  function mediaSession() {
    if (!('mediaSession' in navigator)) return;
    try {
      const current = tracks[position];
      navigator.mediaSession.metadata = current ? new MediaMetadata({title:current.name, artist:'念動圖書館 · 本機音樂'}) : null;
      navigator.mediaSession.playbackState = audio.paused ? 'paused' : 'playing';
    } catch (_) {}
  }
  function load(index) {
    if (!Number.isInteger(index) || index < 0 || index >= tracks.length) return false;
    position = index;
    audio.pause();
    audio.src = tracks[index].url;
    audio.load();
    message = '已選擇：' + tracks[index].name;
    mediaSession();emit();return true;
  }
  async function play() {
    if (!tracks.length || position < 0) {message = '請先選擇音樂檔案';emit();return false;}
    try {await audio.play();message = '正在播放';mediaSession();emit();return true;}
    catch (_) {message = '無法播放：請點擊播放鍵，或更換支援的音訊格式';emit();return false;}
  }
  function pause() {audio.pause();message = '已暫停';mediaSession();emit();}
  function selectFiles(files) {
    const incoming = Array.from(files || []).filter(valid).slice(0, 100);
    if (!incoming.length) {message = '沒有可用的音訊檔（單檔限制 250 MB）';emit();return false;}
    audio.pause();audio.removeAttribute('src');audio.load();
    tracks.forEach((track) => URL.revokeObjectURL(track.url));
    tracks = incoming.map((file) => ({name: file.name, url: URL.createObjectURL(file)}));
    position = -1;load(0);message = '已載入 ' + tracks.length + ' 首，點 ▶ 播放';emit();return true;
  }
  async function move(delta) {
    if (!tracks.length) return false;
    const next = shuffle && tracks.length > 1
      ? (position + 1 + Math.floor(Math.random() * (tracks.length - 1))) % tracks.length
      : (position + delta + tracks.length) % tracks.length;
    const wasPlaying = !audio.paused && !audio.ended;
    load(next);
    if (wasPlaying) return play();
    return true;
  }
  async function moveTo(index) {
    if (!Number.isInteger(index) || index < 0 || index >= tracks.length) return false;
    const wasPlaying = !audio.paused && !audio.ended;
    load(index);
    return wasPlaying ? play() : true;
  }
  function stop() {audio.pause();audio.currentTime = 0;message = '已停止';mediaSession();emit();}
  function setVolume(value) {
    const v = Number(value);if (!Number.isFinite(v)) return;
    audio.volume = Math.max(0, Math.min(1, v));
    try {root.localStorage?.setItem('living-library-music-volume-v1', String(audio.volume));} catch (_) {}
    emit();
  }
  function setRepeat(value) {repeat = !!value;emit();}
  function setShuffle(value) {shuffle = !!value;emit();}
  audio.addEventListener('play', () => {mediaSession();emit();});
  audio.addEventListener('pause', () => {mediaSession();emit();});
  audio.addEventListener('error', () => {message = '音樂格式不支援或檔案無法讀取';emit();});
  audio.addEventListener('ended', () => {
    if (!tracks.length) return;
    if (position < tracks.length - 1 || repeat) {
      const next = shuffle && tracks.length > 1
        ? (position + 1 + Math.floor(Math.random() * (tracks.length - 1))) % tracks.length
        : (position + 1) % tracks.length;
      load(next);play();
    } else {message = '播放完成';emit();}
  });
  if ('mediaSession' in navigator) {
    try {
      navigator.mediaSession.setActionHandler('play', play);
      navigator.mediaSession.setActionHandler('pause', pause);
      navigator.mediaSession.setActionHandler('stop', stop);
      navigator.mediaSession.setActionHandler('previoustrack', () => move(-1));
      navigator.mediaSession.setActionHandler('nexttrack', () => move(1));
    } catch (_) {}
  }
  root.LivingMusicPlayer = Object.freeze({
    snapshot, subscribe(fn) {listeners.add(fn);fn(snapshot());return () => listeners.delete(fn);},
    selectFiles, play, pause, stop, move, moveTo, setVolume, setRepeat, setShuffle
  });
})(window);