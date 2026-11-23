import type { ComponentProps } from 'react'

export function Button({ variant = 'primary', className = '', ...props }: ComponentProps<'button'> & {
  variant?: 'primary' | 'secondary'
}) {
  return <button type="button" className={`button ${variant} ${className}`} {...props} />
}
