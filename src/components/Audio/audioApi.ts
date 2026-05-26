import axios from 'axios';
import type { TranscriptSegment, UploadedAudioInfo } from './types';

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
}

function getAuthToken() {
  const userStr = localStorage.getItem('paperdesk-user');
  if (!userStr) return '';

  try {
    const user = JSON.parse(userStr) as { token?: string; accessToken?: string };
    return user.token || user.accessToken || '';
  } catch {
    return '';
  }
}

function applyAuthHeader(config: any) {
  const token = getAuthToken();
  if (!token) return config;

  if (config.headers?.set) {
    config.headers.set('Authorization', `Bearer ${token}`);
    return config;
  }

  config.headers = {
    ...(config.headers ?? {}),
    Authorization: `Bearer ${token}`,
  };
  return config;
}

const request = axios.create({
  baseURL: '/api',
  withCredentials: true,
});

request.interceptors.request.use(applyAuthHeader);

const demoAudioRequest = axios.create({
  baseURL: 'http://localhost:8080/api',
});

demoAudioRequest.interceptors.request.use(applyAuthHeader);

function unwrap<T>(payload: ApiResponse<T>): T {
  if (!payload?.success) {
    throw new Error(payload?.message || '请求失败');
  }
  return payload.data;
}

export async function uploadRecordingChunk(params: {
  chunk: Blob;
  chunkIndex: number;
  isLastChunk: boolean;
  sessionId: string;
}): Promise<{ accepted: boolean }> {
  const formData = new FormData();
  formData.append('file', params.chunk, `chunk-${params.chunkIndex}.webm`);
  formData.append('chunkIndex', String(params.chunkIndex));
  formData.append('isLastChunk', String(params.isLastChunk));
  formData.append('sessionId', params.sessionId);
  formData.append('source', 'workspace');

  const { data } = await request.post<ApiResponse<{ accepted?: boolean }>>(
    '/voice/recordings/chunks/upload',
    formData
  );

  const unwrapped = unwrap(data);
  return { accepted: unwrapped?.accepted !== false };
}

export async function completeChunkUpload(sessionId: string): Promise<UploadedAudioInfo> {
  const { data } = await request.post<ApiResponse<UploadedAudioInfo>>(
    '/voice/recordings/chunks/complete',
    { sessionId }
  );

  const unwrapped = unwrap(data);
  if (!unwrapped?.audioId) {
    throw new Error('后端未返回 audioId');
  }
  return unwrapped;
}

export async function fetchAudioBlob(audioId: string): Promise<Blob> {
  const path = audioId === 'open-source-demo'
    ? '/voice/recordings/open-source-demo/blob'
    : `/voice/recordings/${audioId}/blob`;

  const client = audioId === 'open-source-demo' ? demoAudioRequest : request;
  const response = await client.get<Blob>(path, {
    responseType: 'blob',
  });
  return response.data;
}

export async function transcribeAudioByAudioId(audioId: string): Promise<{ text: string; segments: TranscriptSegment[] }> {
  const { data } = await request.post<ApiResponse<{ text?: string; segments?: TranscriptSegment[] }>>(
    '/voice/transcribe',
    { audioId }
  );

  const unwrapped = unwrap(data);
  return {
    text: unwrapped?.text ?? '',
    segments: unwrapped?.segments ?? [],
  };
}

export async function clearPlaybackProgress(params: { audioId: string; source?: string }): Promise<void> {
  const { data } = await request.post<ApiResponse<boolean>>('/voice/playback/clear', params);
  unwrap(data);
}
