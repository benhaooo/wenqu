import { useQueryClient } from '@tanstack/react-query'
import {
  ArrowUpIcon,
  CheckIcon,
  LoaderCircleIcon,
  RotateCwIcon,
  SearchXIcon,
  SquareIcon,
  ThumbsDownIcon,
  ThumbsUpIcon,
} from 'lucide-react'
import { useEffect, useRef, useState, type SyntheticEvent } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import {
  getGetConversationQueryKey,
  getListConversationsQueryKey,
  useCreateConversation,
  useGetConversation,
  useRateAnswer,
} from '@/shared/api/generated/chat/chat'
import type {
  CitationResponse,
  MessageResponse,
  Rating,
  ScopeType,
  SourceDocumentResponse,
} from '@/shared/api/generated/model'
import { useWorkspaceAccess } from '@/shared/api/useWorkspaceAccess'
import { cn } from '@/shared/lib/cn'
import { Button } from '@/shared/ui/button'
import { useAnswerStream, type PendingAnswer } from '../hooks/useAnswerStream'
import { useScopeOptions } from '../hooks/useScopeOptions'
import { AnswerContent } from './AnswerContent'
import { CitationCard } from './CitationCard'
import { ScopePicker } from './ScopePicker'

/** AI 问答：会话中的问答记录、流式回答、引用角标与原文跳转、停止与重新生成、反馈。 */
export function ChatPage() {
  const { workspaceId } = useWorkspaceAccess()
  const routeId = useParams().conversationId
  const conversationId = routeId ? Number(routeId) : null
  const [searchParams] = useSearchParams()
  const contextDocumentId = Number(searchParams.get('doc')) || null
  const location = useLocation()
  const navigate = useNavigate()
  const stream = useAnswerStream(workspaceId)
  const createConversation = useCreateConversation()
  const queryClient = useQueryClient()
  const scopeOptions = useScopeOptions(workspaceId, contextDocumentId)
  const [scope, setScope] = useState<ScopeType>(contextDocumentId ? 'DOCUMENT' : 'WORKSPACE')
  const [input, setInput] = useState('')
  const bottomRef = useRef<HTMLDivElement>(null)
  const { data: conversation } = useGetConversation(workspaceId, conversationId ?? 0, {
    query: { enabled: conversationId !== null, select: (result) => result.data },
  })

  const pending = stream.pending?.conversationId === conversationId ? stream.pending : null
  const streaming = pending?.status === 'streaming'
  const selectedScope = scopeOptions.find(
    (option) => option.type === scope && !option.disabledReason,
  )
  const messages = visibleMessages(conversation?.messages ?? [], pending)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [pending?.text, pending?.documents, messages.length])

  async function send(event?: SyntheticEvent) {
    event?.preventDefault()
    const question = input.trim()
    if (!question || streaming || createConversation.isPending) return
    let id = conversationId
    if (id === null) {
      try {
        id = (await createConversation.mutateAsync({ workspaceId })).data.id
        void queryClient.invalidateQueries({ queryKey: getListConversationsQueryKey(workspaceId) })
      } catch (error) {
        toast.error(error instanceof Error ? error.message : '新建会话失败')
        return
      }
      void navigate(`/w/${String(workspaceId)}/chat/${String(id)}${location.search}`, {
        replace: true,
      })
    }
    setInput('')
    await stream.ask(id, {
      question,
      scopeType: selectedScope?.type ?? 'WORKSPACE',
      scopeNodeId: selectedScope?.nodeId,
    })
  }

  const lastAnswerId = messages[lastIndexOf(messages, 'ASSISTANT')]?.id

  return (
    <section aria-label="问答" className="mx-auto flex min-h-full w-full max-w-[820px] flex-col">
      <header className="sticky top-0 z-10 flex flex-wrap items-center gap-3 border-b border-border-subtle bg-background px-8 py-3.5 max-sm:px-4">
        <h1 className="grow truncate text-base font-semibold">{conversation?.title ?? '新会话'}</h1>
        <ScopePicker
          options={scopeOptions}
          value={selectedScope?.type ?? 'WORKSPACE'}
          onChange={setScope}
        />
      </header>

      <div className="flex grow flex-col gap-6 px-8 py-7 max-sm:px-4">
        {messages.length === 0 && !pending && (
          <div className="m-auto flex max-w-md flex-col items-center gap-2 text-center">
            <h2 className="text-lg font-semibold">向团队知识库提问</h2>
            <p className="text-muted-foreground">
              回答只依据空间中的文档，并标注来源；点击角标可以查看原文。
            </p>
          </div>
        )}
        {messages.map((message) =>
          message.role === 'USER' ? (
            <QuestionBubble key={message.id} text={message.content} />
          ) : (
            <StoredAnswer
              key={message.id}
              workspaceId={workspaceId}
              conversationId={conversationId ?? 0}
              message={message}
              canRegenerate={message.id === lastAnswerId && !pending}
              onRegenerate={() => {
                if (conversationId !== null) void stream.regenerate(conversationId)
              }}
            />
          ),
        )}
        {pending && (
          <PendingView
            workspaceId={workspaceId}
            pending={pending}
            onStop={stream.stop}
            onEditQuestion={(question) => {
              setInput(question)
              stream.dismiss()
            }}
          />
        )}
        <div ref={bottomRef} />
      </div>

      <form
        onSubmit={(event) => void send(event)}
        className="sticky bottom-0 bg-background px-8 pt-4 pb-6 max-sm:px-4"
      >
        <label htmlFor="ask" className="sr-only">
          输入问题
        </label>
        <div className="flex items-end gap-2.5 rounded-xl border border-input bg-background py-2.5 pr-2.5 pl-4 shadow-sm focus-within:border-primary focus-within:ring-3 focus-within:ring-primary-soft">
          <textarea
            id="ask"
            rows={2}
            value={input}
            maxLength={2000}
            placeholder={
              messages.length > 0
                ? '继续追问，Enter 发送，Shift + Enter 换行'
                : '输入问题，Enter 发送，Shift + Enter 换行'
            }
            onChange={(event) => {
              setInput(event.target.value)
            }}
            onKeyDown={(event) => {
              if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
                event.preventDefault()
                void send()
              }
            }}
            className="grow resize-none bg-transparent outline-none placeholder:text-muted-foreground"
          />
          <Button
            type="submit"
            size="icon"
            aria-label="发送"
            disabled={!input.trim() || streaming}
            className="size-10 shrink-0 rounded-[10px]"
          >
            <ArrowUpIcon />
          </Button>
        </div>
      </form>
    </section>
  )
}

/**
 * 回答生成期间，列表中与临时显示重复的部分不显示：提问时为服务端已保存的同一个问题，
 * 重新生成时为将被替换的旧回答。
 */
function visibleMessages(
  messages: MessageResponse[],
  pending: PendingAnswer | null,
): MessageResponse[] {
  if (!pending) return messages
  const lastQuestion = lastIndexOf(messages, 'USER')
  if (pending.question === null) return messages.slice(0, lastQuestion + 1)
  const last = messages.at(-1)
  return last?.role === 'USER' && last.content === pending.question
    ? messages.slice(0, -1)
    : messages
}

function lastIndexOf(messages: MessageResponse[], role: MessageResponse['role']): number {
  for (let index = messages.length - 1; index >= 0; index--) {
    if (messages[index]?.role === role) return index
  }
  return -1
}

function QuestionBubble({ text }: { text: string }) {
  return (
    <div className="max-w-[80%] self-end rounded-[14px_14px_4px_14px] bg-secondary px-4 py-2.5 whitespace-pre-wrap">
      {text}
    </div>
  )
}

function SourceChips({
  workspaceId,
  documents,
  label,
}: {
  workspaceId: number
  documents: SourceDocumentResponse[]
  label: string
}) {
  return (
    <div className="flex flex-wrap items-center gap-2 text-[13px] text-muted-foreground">
      <CheckIcon className="size-4 text-primary" aria-hidden="true" />
      {label}
      {documents.map((document) => (
        <Link
          key={document.documentId}
          to={`/w/${String(workspaceId)}/d/${String(document.documentId)}`}
          className="rounded-full border border-border px-2.5 py-0.5 text-foreground hover:bg-secondary"
        >
          {document.title}
        </Link>
      ))}
    </div>
  )
}

/** 回答引用的文档（去重），用于历史回答的来源标签。 */
function citedDocuments(citations: CitationResponse[]): SourceDocumentResponse[] {
  const documents = new Map<number, SourceDocumentResponse>()
  for (const citation of citations) {
    if (!documents.has(citation.documentId)) {
      documents.set(citation.documentId, {
        documentId: citation.documentId,
        title: citation.documentTitle,
      })
    }
  }
  return [...documents.values()]
}

function NoSourceAnswer({ text }: { text: string }) {
  return (
    <div className="flex gap-2.5 rounded-[10px] border border-dashed border-input px-4 py-3 text-text-secondary">
      <SearchXIcon className="mt-[3px] size-[18px] shrink-0" aria-hidden="true" />
      <span className="whitespace-pre-wrap">{text}</span>
    </div>
  )
}

function AnswerBody({
  workspaceId,
  text,
  citations,
  cursor,
}: {
  workspaceId: number
  text: string
  citations: CitationResponse[]
  cursor: boolean
}) {
  const [activeMarker, setActiveMarker] = useState<number | null>(null)
  const active = citations.find((citation) => citation.marker === activeMarker)
  return (
    <>
      <AnswerContent
        text={text}
        citations={citations}
        activeMarker={activeMarker}
        onCitationClick={(marker) => {
          setActiveMarker((current) => (current === marker ? null : marker))
        }}
        trailing={
          cursor && (
            <span
              aria-hidden="true"
              className="ml-0.5 inline-block h-4 w-2 animate-pulse bg-primary align-text-bottom"
            />
          )
        }
      />
      {active && <CitationCard workspaceId={workspaceId} citation={active} />}
    </>
  )
}

function StoredAnswer({
  workspaceId,
  conversationId,
  message,
  canRegenerate,
  onRegenerate,
}: {
  workspaceId: number
  conversationId: number
  message: MessageResponse
  canRegenerate: boolean
  onRegenerate: () => void
}) {
  const documents = citedDocuments(message.citations)
  const noSource = message.citations.length === 0 && message.status === 'COMPLETED'
  return (
    <div className="flex flex-col gap-3">
      {documents.length > 0 && (
        <SourceChips
          workspaceId={workspaceId}
          documents={documents}
          label={`引用了 ${String(documents.length)} 篇文档`}
        />
      )}
      {noSource ? (
        <NoSourceAnswer text={message.content} />
      ) : (
        <AnswerBody
          workspaceId={workspaceId}
          text={message.content}
          citations={message.citations}
          cursor={false}
        />
      )}
      {message.status === 'STOPPED' && (
        <p className="text-[13px] text-muted-foreground">已停止生成</p>
      )}
      {message.status === 'FAILED' && (
        <p className="text-[13px] text-destructive">回答生成失败，可以重新生成</p>
      )}
      <div className="flex flex-wrap gap-2">
        {canRegenerate && (
          <Button variant="outline" onClick={onRegenerate}>
            <RotateCwIcon />
            重新生成
          </Button>
        )}
        <FeedbackButtons
          workspaceId={workspaceId}
          conversationId={conversationId}
          message={message}
        />
      </div>
    </div>
  )
}

function FeedbackButtons({
  workspaceId,
  conversationId,
  message,
}: {
  workspaceId: number
  conversationId: number
  message: MessageResponse
}) {
  const queryClient = useQueryClient()
  const rate = useRateAnswer({
    mutation: {
      onSuccess: () =>
        queryClient.invalidateQueries({
          queryKey: getGetConversationQueryKey(workspaceId, conversationId),
        }),
      onError: (error) => toast.error(error.message),
    },
  })
  const toggle = (rating: Rating) => {
    rate.mutate({
      workspaceId,
      messageId: message.id,
      data: { rating: message.rating === rating ? undefined : rating },
    })
  }
  return (
    <>
      <Button
        variant="outline"
        size="icon"
        aria-label="有帮助"
        aria-pressed={message.rating === 'UP'}
        disabled={rate.isPending}
        onClick={() => {
          toggle('UP')
        }}
        className={cn('text-muted-foreground', message.rating === 'UP' && 'bg-accent text-primary')}
      >
        <ThumbsUpIcon />
      </Button>
      <Button
        variant="outline"
        size="icon"
        aria-label="没有帮助"
        aria-pressed={message.rating === 'DOWN'}
        disabled={rate.isPending}
        onClick={() => {
          toggle('DOWN')
        }}
        className={cn(
          'text-muted-foreground',
          message.rating === 'DOWN' && 'bg-destructive-soft text-destructive',
        )}
      >
        <ThumbsDownIcon />
      </Button>
    </>
  )
}

function PendingView({
  workspaceId,
  pending,
  onStop,
  onEditQuestion,
}: {
  workspaceId: number
  pending: PendingAnswer
  onStop: () => void
  onEditQuestion: (question: string) => void
}) {
  const streaming = pending.status === 'streaming'
  const noSource = pending.documents?.length === 0
  return (
    <>
      {pending.question !== null && <QuestionBubble text={pending.question} />}
      <div className="flex flex-col gap-3" aria-live="polite" aria-busy={streaming}>
        {pending.documents === null
          ? pending.status === 'streaming' && (
              <p className="flex items-center gap-2 text-[13px] text-muted-foreground">
                <LoaderCircleIcon className="size-4 animate-spin" aria-hidden="true" />
                正在检索相关文档…
              </p>
            )
          : pending.documents.length > 0 && (
              <SourceChips
                workspaceId={workspaceId}
                documents={pending.documents}
                label={`已检索到 ${String(pending.documents.length)} 篇相关文档`}
              />
            )}
        {noSource && pending.text ? (
          <NoSourceAnswer text={pending.text} />
        ) : (
          pending.text && (
            <AnswerBody
              workspaceId={workspaceId}
              text={pending.text}
              citations={pending.citations}
              cursor={streaming}
            />
          )
        )}
        {pending.status === 'stopped' && (
          <p className="text-[13px] text-muted-foreground">已停止生成</p>
        )}
        {pending.status === 'failed' && (
          <div className="flex flex-wrap items-center gap-3 text-[13px] text-destructive">
            {pending.error}
            {pending.question !== null && pending.documents === null && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => {
                  onEditQuestion(pending.question ?? '')
                }}
              >
                重新提问
              </Button>
            )}
          </div>
        )}
        {streaming && (
          <Button variant="outline" className="self-start" onClick={onStop}>
            <SquareIcon className="fill-current" />
            停止生成
          </Button>
        )}
      </div>
    </>
  )
}
