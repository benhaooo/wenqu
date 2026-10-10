import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  getAskQuestionUrl,
  getConversation,
  getGetConversationQueryKey,
  getListConversationsQueryKey,
  getRegenerateAnswerUrl,
} from '@/shared/api/generated/chat/chat'
import type {
  AskRequest,
  ChatStreamEvent,
  CitationResponse,
  SourceDocumentResponse,
} from '@/shared/api/generated/model'
import { httpStream } from '@/shared/api/http'

export interface PendingAnswer {
  conversationId: number
  /** 新提问时显示在回答上方；重新生成时为 null（问题已在列表中） */
  question: string | null
  documents: SourceDocumentResponse[] | null
  text: string
  citations: CitationResponse[]
  status: 'streaming' | 'stopped' | 'failed'
  error: string | null
}

/** 停止后服务端在下一次写出失败时才保存回答，刷新会话时稍等再确认。 */
const SAVE_CHECK_ATTEMPTS = 5
const SAVE_CHECK_INTERVAL = 400

/**
 * 流式回答：提问或重新生成，逐字显示；停止时中止请求，服务端随之停止生成并保存已生成的部分。
 * 回答保存后刷新会话，确认列表中已有该回答再移除临时显示，避免闪烁。
 */
export function useAnswerStream(workspaceId: number) {
  const queryClient = useQueryClient()
  const [pending, setPending] = useState<PendingAnswer | null>(null)
  const controller = useRef<AbortController | null>(null)

  useEffect(() => () => controller.current?.abort(), [])

  const settle = useCallback(
    async (conversationId: number, messageId: number | null) => {
      const key = getGetConversationQueryKey(workspaceId, conversationId)
      for (let attempt = 0; attempt < SAVE_CHECK_ATTEMPTS; attempt++) {
        const result = await queryClient.query({
          queryKey: key,
          queryFn: () => getConversation(workspaceId, conversationId),
          staleTime: 0,
        })
        const last = result.data.messages.at(-1)
        if (last?.role === 'ASSISTANT' && (messageId === null || last.id >= messageId)) break
        await new Promise((resolve) => setTimeout(resolve, SAVE_CHECK_INTERVAL))
      }
      setPending(null)
      void queryClient.invalidateQueries({ queryKey: getListConversationsQueryKey(workspaceId) })
    },
    [queryClient, workspaceId],
  )

  const run = useCallback(
    async (
      conversationId: number,
      url: string,
      body: AskRequest | null,
      question: string | null,
    ) => {
      const abort = new AbortController()
      controller.current = abort
      setPending({
        conversationId,
        question,
        documents: null,
        text: '',
        citations: [],
        status: 'streaming',
        error: null,
      })
      const update = (change: Partial<PendingAnswer>) => {
        setPending((current) => (current ? { ...current, ...change } : current))
      }
      let messageId: number | null = null
      try {
        await httpStream(
          url,
          {
            method: 'POST',
            headers: body ? { 'Content-Type': 'application/json' } : undefined,
            body: body ? JSON.stringify(body) : undefined,
            signal: abort.signal,
          },
          (data) => {
            const event = JSON.parse(data) as ChatStreamEvent
            switch (event.type) {
              case 'RETRIEVAL':
                // 先用全部资料显示角标，生成结束后换成实际引用的资料；此时提问已保存，会话标题已更新
                update({ documents: event.documents ?? [], citations: event.citations ?? [] })
                void queryClient.invalidateQueries({
                  queryKey: getListConversationsQueryKey(workspaceId),
                })
                break
              case 'TOKEN':
                setPending((current) =>
                  current ? { ...current, text: current.text + (event.text ?? '') } : current,
                )
                break
              case 'CITATIONS':
                update({ citations: event.citations ?? [] })
                break
              case 'ERROR':
                update({ status: 'failed', error: event.message ?? '回答生成失败' })
                break
              case 'DONE':
                messageId = event.messageId ?? null
                break
            }
          },
        )
      } catch (error) {
        if (abort.signal.aborted) {
          update({ status: 'stopped' })
        } else {
          // 开始输出前的错误（如 AI 服务不可用）：提问可能未保存，显示错误并保留输入
          update({
            status: 'failed',
            error: error instanceof Error ? error.message : '回答生成失败',
          })
          void queryClient.invalidateQueries({
            queryKey: getGetConversationQueryKey(workspaceId, conversationId),
          })
          return
        }
      } finally {
        if (controller.current === abort) controller.current = null
      }
      await settle(conversationId, messageId)
    },
    [queryClient, settle, workspaceId],
  )

  const ask = useCallback(
    (conversationId: number, request: AskRequest) =>
      run(
        conversationId,
        getAskQuestionUrl(workspaceId, conversationId),
        request,
        request.question,
      ),
    [run, workspaceId],
  )

  const regenerate = useCallback(
    (conversationId: number) =>
      run(conversationId, getRegenerateAnswerUrl(workspaceId, conversationId), null, null),
    [run, workspaceId],
  )

  const stop = useCallback(() => {
    controller.current?.abort()
  }, [])

  const dismiss = useCallback(() => {
    setPending(null)
  }, [])

  return { pending, ask, regenerate, stop, dismiss }
}
