import type { RecorderStatus } from './types';

interface VoiceRecorderPanelProps {
  status: RecorderStatus;
  onStart: () => void;
  onStop: () => void;
}

export function VoiceRecorderPanel({ status, onStart, onStop }: VoiceRecorderPanelProps) {
  const isRecording = status === 'recording' || status === 'uploading';

  const statusText =
    status === 'recording'
      ? '录音中...（分片上传）'
      : status === 'uploading'
        ? '上传音频分片中...'
        : status === 'transcribing'
          ? '转写中...'
          : status === 'done'
            ? '已完成'
            : status === 'error'
              ? '失败'
              : '空闲';

  return (
    <section className="voice-control-row" aria-label="录音控制区">
      <div className="voice-control-row__actions">
        {!isRecording ? (
          <button type="button" className="primary-button" onClick={onStart}>
            开始录音
          </button>
        ) : (
          <button type="button" className="secondary-button" onClick={onStop}>
            停止录音
          </button>
        )}
      </div>
      <span className="voice-control-row__status">状态：{statusText}</span>
    </section>
  );
}
