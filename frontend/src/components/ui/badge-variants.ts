import { cva } from 'class-variance-authority'

// Keep non-component styling exports outside Badge to satisfy React Fast
// Refresh while preserving one shared source for badge variants.
export const badgeVariants = cva(
  'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-offset-2',
  {
    variants: {
      variant: {
        default: 'border border-blue-200 bg-blue-100 text-blue-700 hover:bg-blue-200',
        secondary: 'border border-gray-200 bg-gray-100 text-gray-700 hover:bg-gray-200',
        success: 'border border-green-200 bg-green-100 text-green-700 hover:bg-green-200',
        warning: 'border border-amber-200 bg-amber-100 text-amber-700 hover:bg-amber-200',
        destructive: 'border border-red-200 bg-red-100 text-red-700 hover:bg-red-200',
        outline: 'border border-gray-300 bg-transparent text-gray-700',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  }
)
