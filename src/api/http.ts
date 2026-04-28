const JAVA_API_BASE_URL = process.env.REACT_APP_JAVA_API_BASE_URL || 'http://localhost:8080/api';

export interface ApiResult<T> {
  ok: boolean;
  status: number;
  data: T;
}

function getAuthHeaders(): HeadersInit {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  
  try {
    const userStr = localStorage.getItem('paperdesk-user');
    if (userStr) {
      const user = JSON.parse(userStr);
      const token = user.token || user.accessToken;
      if (token) {
        headers['Authorization'] = `Bearer ${token}`;
      }
    }
  } catch (e) {
    // Ignore parse errors
  }
  
  return headers;
}

async function handleResponse<TResponse>(response: Response): Promise<ApiResult<TResponse>> {
  if (response.status === 401) {
    console.warn('Unauthorized (401). Please login again.');
  }
  
  let data;
  try {
    data = await response.json();
  } catch (e) {
    // If response is not JSON
    data = null;
  }
  
  return {
    ok: response.ok,
    status: response.status,
    data: data as TResponse,
  };
}

export async function getJson<TResponse>(path: string): Promise<ApiResult<TResponse>> {
  const response = await fetch(`${JAVA_API_BASE_URL}${path}`, {
    method: 'GET',
    headers: getAuthHeaders(),
  });
  return handleResponse<TResponse>(response);
}

export async function postJson<TResponse>(path: string, payload: unknown): Promise<ApiResult<TResponse>> {
  const response = await fetch(`${JAVA_API_BASE_URL}${path}`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify(payload),
  });
  return handleResponse<TResponse>(response);
}

export async function putJson<TResponse>(path: string, payload: unknown): Promise<ApiResult<TResponse>> {
  const response = await fetch(`${JAVA_API_BASE_URL}${path}`, {
    method: 'PUT',
    headers: getAuthHeaders(),
    body: JSON.stringify(payload),
  });
  return handleResponse<TResponse>(response);
}

export async function deleteJson<TResponse>(path: string): Promise<ApiResult<TResponse>> {
  const response = await fetch(`${JAVA_API_BASE_URL}${path}`, {
    method: 'DELETE',
    headers: getAuthHeaders(),
  });
  return handleResponse<TResponse>(response);
}