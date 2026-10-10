import type { ComponentProps } from 'react'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'

/** 带标签、说明与错误提示的输入框，配合 react-hook-form 的 register 使用。 */
export function FormField({
  id,
  label,
  hint,
  error,
  ...inputProps
}: ComponentProps<typeof Input> & { id: string; label: string; hint?: string; error?: string }) {
  return (
    <div className="grid gap-1.5">
      <Label htmlFor={id} className="font-medium">
        {label}
      </Label>
      <Input id={id} aria-invalid={error ? true : undefined} {...inputProps} />
      {error ? (
        <p className="text-[13px] text-destructive">{error}</p>
      ) : (
        hint && <p className="text-[13px] text-muted-foreground">{hint}</p>
      )}
    </div>
  )
}
