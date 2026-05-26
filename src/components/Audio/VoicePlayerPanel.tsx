import { useEffect, useRef } from 'react';

interface VoicePlayerPanelProps {
  audioUrl: string;
  playbackRate: number;
  onPlaybackRateChange: (value: number) => void;
  onTimeUpdate: (time: number) => void;
  initialProgress: number;
  onClearProgress: () => void;
}

export function VoicePlayerPanel({
  audioUrl,
  playbackRate,
  onPlaybackRateChange,
  onTimeUpdate,
  initialProgress,
  onClearProgress,
}: VoicePlayerPanelProps) {
  const audioRef = useRef<HTMLAudioElement | null>(null);

  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.playbackRate = playbackRate;
    }
  }, [playbackRate]);

  const handleLoadedMetadata = () => {
    if (!audioRef.current) return;
    const duration = audioRef.current.duration || 0;
    if (initialProgress > 0 && duration > 0 && initialProgress < duration) {
      audioRef.current.currentTime = initialProgress;
    }
  };

  if (!audioUrl) {
    return <p className="voice-player-empty">暂无可播放音频，请先录音。</p>;
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

      <audio
        ref={audioRef}
        className="voice-player__audio"
        src={audioUrl}
        controls
        onLoadedMetadata={handleLoadedMetadata}
        onTimeUpdate={() => audioRef.current && onTimeUpdate(audioRef.current.currentTime)}
      />

      <p className="voice-player__note">
        当前使用原生音频控件，波形区域为 MVP 占位，后续可接入 wavesurfer.js。
      </p>

      <div aria-label="waveform-placeholder" className="voice-player__waveform" />
    </section>
  );
}
