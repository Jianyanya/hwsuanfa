import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/** 合并 Tailwind class，后写的规则优先 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}
