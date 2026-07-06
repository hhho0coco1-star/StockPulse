import { apiClient } from './client';
import type { ApiResponse } from '@/types/api';

interface LoginRequest {
  email: string;
  password: string;
}

interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  userId: string;
  nickname: string;
}

interface SignupRequest {
  email: string;
  password: string;
  nickname: string;
}

interface UserProfile {
  userId: string;
  email: string;
  nickname: string;
  createdAt: string;
}

export const authApi = {
  async login(req: LoginRequest): Promise<AuthTokens> {
    const { data } = await apiClient.post<ApiResponse<AuthTokens>>('/auth/login', req);
    if (!data.success || !data.data) throw new Error(data.error?.message ?? '로그인 실패');
    return data.data;
  },

  async signup(req: SignupRequest): Promise<AuthTokens> {
    const { data } = await apiClient.post<ApiResponse<AuthTokens>>('/auth/signup', req);
    if (!data.success || !data.data) throw new Error(data.error?.message ?? '회원가입 실패');
    return data.data;
  },

  async logout(): Promise<void> {
    await apiClient.post('/auth/logout');
  },

  async getProfile(): Promise<UserProfile> {
    const { data } = await apiClient.get<ApiResponse<UserProfile>>('/users/me');
    if (!data.success || !data.data) throw new Error('프로필 조회 실패');
    return data.data;
  },
};
