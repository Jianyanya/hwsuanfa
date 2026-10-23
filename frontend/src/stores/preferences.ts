import { create } from 'zustand';

type Theme = 'system' | 'light' | 'dark';
const themeKey = 'hwsuanfa.theme';
const storedTheme = localStorage.getItem(themeKey);
const initialTheme: Theme = storedTheme === 'light' || storedTheme === 'dark' ? storedTheme : 'system';
const media = window.matchMedia('(prefers-color-scheme: dark)');

function applyTheme(theme: Theme) {
  const dark = theme === 'dark' || (theme === 'system' && media.matches);
  document.documentElement.classList.toggle('dark', dark);
  document.documentElement.classList.toggle('light', !dark);
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
}

export const usePreferences = create<{
  theme: Theme;
  sidebarCollapsed: boolean;
  cycleTheme: () => void;
  toggleSidebar: () => void;
}>((set, get) => ({
  theme: initialTheme,
  sidebarCollapsed: localStorage.getItem('hwsuanfa.sidebar_collapsed') === 'true',
  cycleTheme() {
    const theme: Theme = get().theme === 'system' ? 'light' : get().theme === 'light' ? 'dark' : 'system';
    localStorage.setItem(themeKey, theme);
    applyTheme(theme);
    set({ theme });
  },
  toggleSidebar() {
    const sidebarCollapsed = !get().sidebarCollapsed;
    localStorage.setItem('hwsuanfa.sidebar_collapsed', String(sidebarCollapsed));
    set({ sidebarCollapsed });
  },
}));

applyTheme(initialTheme);
media.addEventListener('change', () => applyTheme(usePreferences.getState().theme));

