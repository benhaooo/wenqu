import type { ScopeType } from '@/shared/api/generated/model'
import { cn } from '@/shared/lib/cn'

export interface ScopeOption {
  type: ScopeType
  label: string
  /** 范围对应的文件夹或文档，整个空间为 undefined */
  nodeId?: number
  /** 不可选时的说明 */
  disabledReason?: string
}

/** 提问范围：整个空间、当前文件夹、当前文档（从文档页进入问答时可选后两项）。 */
export function ScopePicker({
  options,
  value,
  onChange,
}: {
  options: ScopeOption[]
  value: ScopeType
  onChange: (type: ScopeType) => void
}) {
  return (
    <div className="flex items-center gap-3">
      <span className="text-[13px] text-muted-foreground">范围</span>
      <div
        role="radiogroup"
        aria-label="提问范围"
        className="flex gap-1 rounded-md bg-secondary p-[3px]"
      >
        {options.map((option) => {
          const checked = option.type === value
          return (
            <button
              key={option.type}
              type="button"
              role="radio"
              aria-checked={checked}
              disabled={option.disabledReason !== undefined}
              title={option.disabledReason ?? option.label}
              onClick={() => {
                onChange(option.type)
              }}
              className={cn(
                'max-w-[180px] min-h-8 truncate rounded-sm px-3 text-[13px] text-muted-foreground disabled:cursor-not-allowed disabled:opacity-50',
                checked && 'bg-background font-semibold text-primary-strong shadow-xs',
              )}
            >
              {option.label}
            </button>
          )
        })}
      </div>
    </div>
  )
}
