import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

// Keep class composition centralized so UI primitives can merge Tailwind
// utilities without duplicating conflict rules in each component.
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
