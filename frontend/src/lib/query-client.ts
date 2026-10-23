import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './http';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 校内自用、数据变化不频繁，缓存放宽
      staleTime: 60 * 1000,
      gcTime: 5 * 60 * 1000,
      retry: (failureCount, error) => failureCount < 1 && !(error instanceof ApiError && error.status && error.status < 500),
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
});
