import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'
import { useLogin } from '@/shared/api/generated/auth/auth'
import { setAccessToken } from '@/shared/api/session'
import { safeRedirect } from '@/shared/lib/redirect'
import { Alert, AlertDescription } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { FormField } from '@/shared/ui/form-field'
import { AuthCard } from './AuthCard'

const schema = z.object({
  email: z.string().trim().min(1, '请输入邮箱'),
  password: z.string().min(1, '请输入密码'),
})

type FormValues = z.infer<typeof schema>

export function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const redirect = searchParams.get('redirect')
  const login = useLogin()
  const form = useForm<FormValues>({ resolver: zodResolver(schema) })

  const onSubmit = form.handleSubmit((values) => {
    login.mutate(
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
    <AuthCard title="登录问渠" description="使用邮箱和密码登录">
      <form className="grid gap-4" onSubmit={(event) => void onSubmit(event)} noValidate>
        {login.error && (
          <Alert variant="destructive">
            <AlertDescription>{login.error.message}</AlertDescription>
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
          id="password"
          label="密码"
          type="password"
          autoComplete="current-password"
          error={form.formState.errors.password?.message}
          {...form.register('password')}
        />
        <Button type="submit" disabled={login.isPending}>
          {login.isPending ? '登录中…' : '登录'}
        </Button>
        <p className="text-center text-sm text-muted-foreground">
          还没有账号？
          <Link
            className="ml-1 text-foreground underline underline-offset-4"
            to={redirect ? `/register?redirect=${encodeURIComponent(redirect)}` : '/register'}
          >
            注册
          </Link>
        </p>
      </form>
    </AuthCard>
  )
}
