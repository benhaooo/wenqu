import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'
import { useRegister } from '@/shared/api/generated/auth/auth'
import { setAccessToken } from '@/shared/api/session'
import { safeRedirect } from '@/shared/lib/redirect'
import { Alert, AlertDescription } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { FormField } from '@/shared/ui/form-field'
import { AuthCard } from './AuthCard'

// 与后端 RegisterRequest 的校验规则一致
const schema = z.object({
  email: z.email('邮箱格式不正确'),
  displayName: z.string().trim().min(1, '请输入昵称').max(30, '昵称最多 30 个字符'),
  password: z.string().min(8, '密码长度为 8～72 位').max(72, '密码长度为 8～72 位'),
})

type FormValues = z.infer<typeof schema>

export function RegisterPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const redirect = searchParams.get('redirect')
  const register = useRegister()
  const form = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = form.handleSubmit((values) => {
    register.mutate(
      { data: values },
      {
        onSuccess: (result) => {
          setAccessToken(result.data.accessToken)
          void navigate(safeRedirect(redirect), { replace: true })
        },
      },
    )
  })

  return (
    <AuthCard title="注册问渠" description="注册后即可创建或加入团队空间">
      <form className="grid gap-4" onSubmit={(event) => void onSubmit(event)} noValidate>
        {register.error && (
          <Alert variant="destructive">
            <AlertDescription>{register.error.message}</AlertDescription>
          </Alert>
        )}
        <FormField
          id="email"
          label="邮箱"
          type="email"
          autoComplete="email"
          error={form.formState.errors.email?.message}
          {...form.register('email')}
        />
        <FormField
          id="displayName"
          label="昵称"
          autoComplete="nickname"
          error={form.formState.errors.displayName?.message}
          {...form.register('displayName')}
        />
        <FormField
          id="password"
          label="密码"
          type="password"
          autoComplete="new-password"
          error={form.formState.errors.password?.message}
          {...form.register('password')}
        />
        <Button type="submit" disabled={register.isPending}>
          {register.isPending ? '注册中…' : '注册'}
        </Button>
        <p className="text-center text-sm text-muted-foreground">
          已有账号？
          <Link
            className="ml-1 text-foreground underline underline-offset-4"
            to={redirect ? `/login?redirect=${encodeURIComponent(redirect)}` : '/login'}
          >
            登录
          </Link>
        </p>
      </form>
    </AuthCard>
  )
}
