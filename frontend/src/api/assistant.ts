import { apiClient, refreshAccessToken } from './client.ts'
import { readAccessToken } from '../auth/session.ts'
import type { AiAssistantResponse, ApiResponse, CampusScope, CreatePostResponse } from '../types/api.ts'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api'

export interface AssistantStreamHandlers {
  onChunk: (chunk: string) => void
  onDone: (response: AiAssistantResponse) => void
}

export async function askAssistant(question: string, scope: CampusScope): Promise<AiAssistantResponse> {
  const response = await apiClient.post<ApiResponse<AiAssistantResponse>>('/ai/assistant/ask', { question, scope })
  return response.data.data
}

export async function confirmPendingPost(actionId: string, categoryId: number): Promise<CreatePostResponse> {
  const response = await apiClient.post<ApiResponse<CreatePostResponse>>(`/ai/pending-actions/${actionId}/confirm`, { categoryId })
  return response.data.data!
}

export async function cancelPendingAction(actionId: string): Promise<void> {
  await apiClient.delete<ApiResponse<boolean>>(`/ai/pending-actions/${actionId}`)
}

export async function streamAssistant(
  question: string,
  scope: CampusScope,
  sessionId: string,
  handlers: AssistantStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  let response = await openStream(question, scope, sessionId, readAccessToken(), signal)
  if (response.status === 401) {
    response = await openStream(question, scope, sessionId, await refreshAccessToken(), signal)
  }
  if (!response.ok) throw new Error(await responseErrorMessage(response))
  if (!response.body) throw new Error('浏览器不支持流式响应。')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      buffer = consumeSseEvents(buffer, handlers)
    }
    consumeSseEvents(`${buffer}${decoder.decode()}\n\n`, handlers)
  } finally {
    reader.releaseLock()
  }
}

async function openStream(
  question: string,
  scope: CampusScope,
  sessionId: string,
  token: string | null,
  signal?: AbortSignal,
): Promise<Response> {
  return fetch(`${apiBaseUrl}/ai/assistant/stream`, {
    method: 'POST', credentials: 'include',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify({ question, scope, sessionId }),
    signal,
  })
}

function consumeSseEvents(buffer: string, handlers: AssistantStreamHandlers): string {
  const events = buffer.replace(/\r\n/g, '\n').split('\n\n')
  const remainder = events.pop() ?? ''
  for (const text of events) {
    const name = text.split('\n').find((line) => line.startsWith('event:'))?.slice(6).trim() ?? 'message'
    const data = text.split('\n').filter((line) => line.startsWith('data:')).map((line) => line.slice(5).trimStart()).join('\n')
    if (!data) continue
    if (name === 'message') handlers.onChunk(data)
    else if (name === 'done') handlers.onDone(JSON.parse(data) as AiAssistantResponse)
    else if (name === 'error') throw new Error((JSON.parse(data) as { message?: string }).message || '智能问答流已中断，请稍后重试。')
  }
  return remainder
}

async function responseErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json() as ApiResponse<unknown>
    return body.message || '智能问答服务暂时不可用，请稍后重试。'
  } catch {
    return '智能问答服务暂时不可用，请稍后重试。'
  }
}
