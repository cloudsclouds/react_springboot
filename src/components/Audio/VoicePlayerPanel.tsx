import { useEffect, useRef } from 'react';
import WaveSurfer from 'wavesurfer.js';

interface VoicePlayerPanelProps {
  audioUrl: string;
  isLoading: boolean;
  loadErrorText: string;
  onRequestLoad: () => void;
  playbackRate: number;
  onPlaybackRateChange: (value: number) => void;
  onTimeUpdate: (time: number) => void;
  initialProgress: number;
  onClearProgress: () => void;
}

const WAVE_COLOR = '#a5b4fc';
const PROGRESS_COLOR = '#4f46e5';

export function VoicePlayerPanel({
  audioUrl,
  isLoading,
  loadErrorText,
  onRequestLoad,
  playbackRate,
  onPlaybackRateChange,
  onTimeUpdate,
  initialProgress,
  onClearProgress,
}: VoicePlayerPanelProps) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const waveformContainerRef = useRef<HTMLDivElement | null>(null);
  const waveSurferRef = useRef<WaveSurfer | null>(null);

  useEffect(() => {
    if (!audioUrl || !waveformContainerRef.current) return;

    let disposed = false;
    waveSurferRef.current?.destroy();

    // 创建波形可视化组件
    const ws = WaveSurfer.create({
      container: waveformContainerRef.current,
      waveColor: WAVE_COLOR,
      progressColor: PROGRESS_COLOR,
      cursorColor: PROGRESS_COLOR,
      barWidth: 2,
      barGap: 1,
      barRadius: 2,
      height: 64,
      normalize: true,
      dragToSeek: true,
      media: audioRef.current ?? undefined,
    });

    waveSurferRef.current = ws;

    Promise.resolve(ws.load(audioUrl)).catch((error: unknown) => {
      if (disposed) return;
      if (error instanceof DOMException && error.name === 'AbortError') return;
      // eslint-disable-next-line no-console
      console.error('WaveSurfer 加载音频失败:', error);
    });

    ws.on('ready', () => {
      const duration = ws.getDuration();
      if (initialProgress > 0 && duration > 0 && initialProgress < duration) {
        ws.setTime(initialProgress);
      }
      ws.setPlaybackRate(playbackRate);
    });

    ws.on('timeupdate', (currentTime) => {
      onTimeUpdate(currentTime);
    });

    return () => {
      disposed = true;
      ws.destroy();
      waveSurferRef.current = null;
    };
  }, [audioUrl, initialProgress, onTimeUpdate]);

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.playbackRate = playbackRate;
    }
    waveSurferRef.current?.setPlaybackRate(playbackRate);
  }, [playbackRate]);

  const handleLoadedMetadata = () => {
    if (!audioRef.current) return;
    const duration = audioRef.current.duration || 0;
    if (initialProgress > 0 && duration > 0 && initialProgress < duration) {
      audioRef.current.currentTime = initialProgress;
    }
  };

  if (!audioUrl) {
    return (
      <section className="voice-player" aria-label="音频播放区">
        <div className="voice-player__toolbar">
          <button type="button" className="primary-button" onClick={onRequestLoad} disabled={isLoading}>
            {isLoading ? '音频加载中...' : '开始播放'}
          </button>
        </div>
        <p className="voice-player-empty">{loadErrorText || '点击开始播放后加载音频。'}</p>
      </section>
    );
  }

  return (
    <section className="voice-player" aria-label="音频播放区">
      <div className="voice-player__toolbar">
        <label className="voice-player__rate-control">
          <span>倍速</span>
          <select value={playbackRate} onChange={(e) => onPlaybackRateChange(Number(e.target.value))}>
            <option value={0.5}>0.5x</option>
            <option value={1}>1.0x</option>
            <option value={1.25}>1.25x</option>
            <option value={1.5}>1.5x</option>
            <option value={2}>2.0x</option>
          </select>
        </label>
        <button type="button" className="secondary-button" onClick={onClearProgress}>
          清空进度
        </button>
      </div>

    {/* 音频播放器，使用 audio 标签，并设置 controls，onLoadedMetadata 和 onTimeUpdate 事件 */}
      <audio
        ref={audioRef}
        className="voice-player__audio"
        src={audioUrl}
        controls
        onLoadedMetadata={handleLoadedMetadata}
        onTimeUpdate={() => audioRef.current && onTimeUpdate(audioRef.current.currentTime)}
      />

      <div ref={waveformContainerRef} aria-label="waveform" className="voice-player__waveform" />
    </section>
  );
}
