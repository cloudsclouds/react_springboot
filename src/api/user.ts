// @ts-nocheck
import { postJson } from './http';

export interface LoginPayload {
  email: string;
  password: string;
}

export interface RegisterCodePayload {
  email: string;
}

export interface RegisterPayload {
  email: string;
  nickname: string;
  password: string;
  code: string;
}

export interface LoginResponse {
  success: boolean;
  message: string;
  userId: number | null;
  nickname: string | null;
}

export interface RegisterCodeResponse {
  success: boolean;
  message: string;
  code: string | null;
}

export interface RegisterResponse {
  success: boolean;
  message: string;
  userId: number | null;
}

export async function loginUser(payload: LoginPayload) {
  return postJson<LoginResponse>('/auth/login', payload);
}

export async function getRegisterCode(payload: RegisterCodePayload) {
  return postJson<RegisterCodeResponse>('/auth/register/code', payload);
}

export async function registerUser(payload: RegisterPayload) {
  return postJson<RegisterResponse>('/auth/register', payload);
}

export const userApi = {
  loginUser,
  getRegisterCode,
  registerUser,
};