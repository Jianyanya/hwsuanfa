import { StrictMode, Suspense } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';

import { router } from '@/router';
import { queryClient } from '@/lib/query-client';
import '@/index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <Suspense fallback={<div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">页面加载中…</div>}><RouterProvider router={router} /></Suspense>
    </QueryClientProvider>
  </StrictMode>,
);
