// @ts-nocheck
import { postJson } from './http';

export interface LoginPayload {
  email: string;
  password: string;
}

export interface LoginResponse {
  success: boolean;
  message: string;
  userId: number | null;
  nickname: string | null;
}

export async function loginUser(payload: LoginPayload) {
  return postJson<LoginResponse>('/auth/login', payload);
}

export const userApi = {
  loginUser,
};