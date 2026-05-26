import type { TranscriptSegment } from './types';

interface VoiceTranscriptPanelProps {
  transcript: string;
  segments: TranscriptSegment[];
  onChange: (value: string) => void;
}

function formatSeconds(sec: number) {
  const m = Math.floor(sec / 60)
    .toString()
    .padStart(2, '0');
  const s = Math.floor(sec % 60)
    .toString()
    .padStart(2, '0');
  return `${m}:${s}`;
}

export function VoiceTranscriptPanel({ transcript, segments, onChange }: VoiceTranscriptPanelProps) {
  return (
    <section className="voice-transcript" aria-label="转写结果区">
      <label htmlFor="voice-transcript-input" className="voice-transcript__label">
        转写结果（textarea 展示）
      </label>
      <textarea
        id="voice-transcript-input"
        value={transcript}
        onChange={(e) => onChange(e.target.value)}
        placeholder="录音完成后将显示转写文本"
        className="voice-transcript__input"
      />

      {segments.length > 0 ? (
        <div className="voice-transcript__segments">
          <p className="voice-transcript__segments-title">分段结果（带时间戳）</p>
          <ul className="voice-transcript__list">
            {segments.map((seg, idx) => (
              <li key={`${idx}-${seg.startSec}`} className="voice-transcript__item">
                <strong>
                  [{formatSeconds(seg.startSec)} - {formatSeconds(seg.endSec)}]
                </strong>{' '}
                <span>{seg.text}</span>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </section>
  );
}
