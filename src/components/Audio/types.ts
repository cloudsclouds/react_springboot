export type RecorderStatus = 'idle' | 'recording' | 'uploading' | 'transcribing' | 'done' | 'error';

export interface UploadedAudioInfo {
  audioId: string;
  fileName?: string;
  audioUrl?: string;
}

export interface TranscriptSegment {
  startSec: number;
  endSec: number;
  text: string;
}
