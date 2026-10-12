import * as React from 'react'
import { type VariantProps } from 'class-variance-authority'

import { badgeVariants } from '@/components/ui/badge-variants'
import { cn } from '@/lib/utils'

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>, VariantProps<typeof badgeVariants> {}

// Keep Badge as the only component export in this file so React Fast Refresh
// stays predictable when UI primitives are edited during frontend work.
export function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />
}
