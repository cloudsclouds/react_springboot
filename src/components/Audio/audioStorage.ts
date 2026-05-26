const AUDIO_ID = 'workspace-latest-audio';
const PROGRESS_KEY = `workspace:voice:progress:${AUDIO_ID}`;
const RATE_KEY = `workspace:voice:rate:${AUDIO_ID}`;

export function saveProgress(currentTime: number) {
  localStorage.setItem(PROGRESS_KEY, String(currentTime));
}

export function readProgress() {
  const value = Number(localStorage.getItem(PROGRESS_KEY));
  return Number.isFinite(value) && value > 0 ? value : 0;
}

export function saveRate(rate: number) {
  localStorage.setItem(RATE_KEY, String(rate));
}

export function readRate() {
  const value = Number(localStorage.getItem(RATE_KEY));
  return Number.isFinite(value) && value > 0 ? value : 1;
}

export function clearProgress() {
  localStorage.removeItem(PROGRESS_KEY);
}

export function clearRate() {
  localStorage.removeItem(RATE_KEY);
}
