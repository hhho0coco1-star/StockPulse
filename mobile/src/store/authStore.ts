import { create } from 'zustand';
import { storage } from '@/utils/storage';

interface User {
  userId: string;
  email: string;
  nickname: string;
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: User | null;
  isAuthenticated: boolean;

  setTokens: (accessToken: string, refreshToken: string) => Promise<void>;
  setUser: (user: User) => void;
  logout: () => Promise<void>;
  loadFromStorage: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  refreshToken: null,
  user: null,
  isAuthenticated: false,

  setTokens: async (accessToken, refreshToken) => {
    await storage.setAccessToken(accessToken);
    await storage.setRefreshToken(refreshToken);
    set({ accessToken, refreshToken, isAuthenticated: true });
  },

  setUser: (user) => {
    set({ user });
    storage.setUserId(user.userId);
  },

  logout: async () => {
    await storage.clearAll();
    set({ accessToken: null, refreshToken: null, user: null, isAuthenticated: false });
  },

  loadFromStorage: async () => {
    const accessToken = await storage.getAccessToken();
    const refreshToken = await storage.getRefreshToken();
    const userId = await storage.getUserId();
    if (accessToken && refreshToken && userId) {
      set({ accessToken, refreshToken, isAuthenticated: true });
    }
  },
}));
