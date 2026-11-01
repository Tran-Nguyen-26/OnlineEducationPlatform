import { describe, it, expect } from 'vitest'
import { cn } from './utils'

describe('cn utility', () => {
  it('merges static class names', () => {
    expect(cn('font-bold', 'text-sm')).toBe('font-bold text-sm')
  })

  it('handles conditional expressions correctly', () => {
    const isActive = true
    const isHidden = false
    expect(cn('base', isActive && 'is-active', isHidden && 'is-hidden')).toBe('base is-active')
  })

  it('resolves conflicting tailwind utilities using tailwind-merge', () => {
    expect(cn('p-4', 'p-2')).toBe('p-2')
    expect(cn('text-red-500', 'text-blue-500')).toBe('text-blue-500')
  })
})
