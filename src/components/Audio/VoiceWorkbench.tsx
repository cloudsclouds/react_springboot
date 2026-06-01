import { useEffect, useRef, useState } from 'react';
import { clearProgress, clearRate, readProgress, readRate, saveProgress, saveRate } from './audioStorage';
import { clearPlaybackProgress, completeChunkUpload, fetchAudioBlob, transcribeAudioByAudioId, uploadRecordingChunk } from './audioApi';
import { VoicePlayerPanel } from './VoicePlayerPanel';
import { VoiceRecorderPanel } from './VoiceRecorderPanel';
import { VoiceTranscriptPanel } from './VoiceTranscriptPanel';
import type { RecorderStatus, TranscriptSegment } from './types';

const CHUNK_MS = 2000;

export function VoiceWorkbench() {
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<BlobPart[]>([]);
  const chunkIndexRef = useRef(0);
  const sessionIdRef = useRef('');

  const [status, setStatus] = useState<RecorderStatus>('idle');
  const [audioBlob, setAudioBlob] = useState<Blob | null>(null);
  const [audioUrl, setAudioUrl] = useState('');
  const [isAudioLoading, setIsAudioLoading] = useState(false);
  const [audioLoadErrorText, setAudioLoadErrorText] = useState('');
  const [audioId, setAudioId] = useState('workspace-latest-audio');
  const [transcript, setTranscript] = useState('');
  const [segments, setSegments] = useState<TranscriptSegment[]>([]);
  const [errorText, setErrorText] = useState('');
  const [playbackRate, setPlaybackRate] = useState(1);
  const [initialProgress, setInitialProgress] = useState(0);

  const audioUrlCacheRef = useRef<Map<string, string>>(new Map());
  const loadAbortControllerRef = useRef<AbortController | null>(null);

  useEffect(() => {
    setPlaybackRate(readRate());
    setInitialProgress(readProgress());
  }, []);

  useEffect(() => {
    saveRate(playbackRate);
  }, [playbackRate]);

  useEffect(() => {
    return () => {
      loadAbortControllerRef.current?.abort();
      audioUrlCacheRef.current.forEach((url) => URL.revokeObjectURL(url));
      audioUrlCacheRef.current.clear();
      stopTracks();
    };
  }, []);

  const stopTracks = () => {
    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((track) => track.stop());
      mediaStreamRef.current = null;
    }
  };

  const startRecording = async () => {
    try {
      setStatus('idle');
      setErrorText('');
      setTranscript('');
      setSegments([]);
      chunkIndexRef.current = 0;
      sessionIdRef.current = `${Date.now()}`;

      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaStreamRef.current = stream;

      const recorder = new MediaRecorder(stream);
      chunksRef.current = [];

      recorder.ondataavailable = async (event) => {
        if (!event.data || event.data.size === 0) return;
        chunksRef.current.push(event.data);

        setStatus('uploading');
        await uploadRecordingChunk({
          chunk: event.data,
          chunkIndex: chunkIndexRef.current,
          isLastChunk: false,
          sessionId: sessionIdRef.current,
        });
        chunkIndexRef.current += 1;
        setStatus('recording');
      };

      recorder.onstop = async () => {
        try {
          setStatus('uploading');

          await uploadRecordingChunk({
            chunk: new Blob([], { type: 'application/octet-stream' }),
            chunkIndex: chunkIndexRef.current,
            isLastChunk: true,
            sessionId: sessionIdRef.current,
          });

          const uploadInfo = await completeChunkUpload(sessionIdRef.current);
          if (uploadInfo.audioId) {
            setAudioId(uploadInfo.audioId);
          }

          const blob = new Blob(chunksRef.current, { type: 'audio/webm' });
          setAudioBlob(blob);
          setAudioUrl('');

          setStatus('transcribing');
          const result = await transcribeAudioByAudioId(uploadInfo.audioId || audioId);
          setTranscript(result.text);
          setSegments(result.segments ?? []);
          setStatus('done');
        } catch (error) {
          setStatus('error');
          setErrorText('上传或转写失败，请稍后重试。');
        }
      };

      mediaRecorderRef.current = recorder;
      recorder.start(CHUNK_MS);
      setStatus('recording');
    } catch (error) {
      setStatus('error');
      setErrorText('麦克风权限获取失败，请检查浏览器授权。');
    }
  };

  const stopRecording = () => {
    if (!mediaRecorderRef.current || mediaRecorderRef.current.state === 'inactive') return;
    mediaRecorderRef.current.stop();
    stopTracks();
  };

  const handleClearProgress = async () => {
    clearProgress();
    clearRate();
    setInitialProgress(0);
    setPlaybackRate(1);
    try {
      await clearPlaybackProgress({ audioId, source: 'workspace' });
    } catch {
      // ignore server clear failure in MVP
    }
  };

  return (
    <section className="panel voice-workbench">
      <div className="panel-header voice-workbench__header">
        <div>
          <span className="panel-kicker">Voice Studio</span>
          <h3>语音工作区</h3>
        </div>
      </div>

      <div className="voice-workbench__hero">
        <VoiceRecorderPanel status={status} onStart={startRecording} onStop={stopRecording} />
      </div>

      {audioBlob ? (
        <p className="voice-workbench__file-info">录音文件大小：{(audioBlob.size / 1024).toFixed(1)} KB</p>
      ) : null}

      <div className="voice-workbench__content">
        <VoicePlayerPanel
          audioUrl={audioUrl}
          isLoading={isAudioLoading}
          loadErrorText={audioLoadErrorText}
          onRequestLoad={async () => {
            if (isAudioLoading || !audioId) return;

            const cachedUrl = audioUrlCacheRef.current.get(audioId);
            if (cachedUrl) {
              setAudioUrl(cachedUrl);
              setAudioLoadErrorText('');
              return;
            }

            try {
              setIsAudioLoading(true);
              setAudioLoadErrorText('');

              loadAbortControllerRef.current?.abort();
              const controller = new AbortController();
              loadAbortControllerRef.current = controller;

              const blob = await fetchAudioBlob(audioId, controller.signal);
              if (controller.signal.aborted) return;

              setAudioBlob(blob);
              const nextUrl = URL.createObjectURL(blob);
              audioUrlCacheRef.current.set(audioId, nextUrl);
              setAudioUrl(nextUrl);
            } catch (error) {
              const isAbort = error instanceof DOMException
                ? error.name === 'AbortError'
                : (error as { code?: string })?.code === 'ERR_CANCELED';
              if (!isAbort) {
                setAudioLoadErrorText('加载音频失败，请重试。');
              }
            } finally {
              setIsAudioLoading(false);
            }
          }}
          playbackRate={playbackRate}
          onPlaybackRateChange={setPlaybackRate}
          onTimeUpdate={saveProgress}
          initialProgress={initialProgress}
          onClearProgress={handleClearProgress}
        />

        <VoiceTranscriptPanel transcript={transcript} segments={segments} onChange={setTranscript} />
      </div>

      {errorText ? <p className="voice-workbench__error">{errorText}</p> : null}
    </section>
  );
}
