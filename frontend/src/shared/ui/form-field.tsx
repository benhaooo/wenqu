import type { ComponentProps } from 'react'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

/** 带标签与错误提示的输入框，配合 react-hook-form 的 register 使用。 */
export function FormField({
  id,
  label,
  error,
  ...inputProps
}: ComponentProps<typeof Input> & { id: string; label: string; error?: string }) {
  return (
    <div className="grid gap-2">
      <Label htmlFor={id}>{label}</Label>
      <Input id={id} aria-invalid={error ? true : undefined} {...inputProps} />
      {error && <p className="text-sm text-destructive">{error}</p>}
    </div>
  )
}
