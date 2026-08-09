import { apiClient } from './client.ts'
import type { ApiResponse, CandidateAnswerAiReview, MyQuestionRole, PageResponse, QuestionAnswer, QuestionSourceSummary, QuestionSourceType, QuestionSubscriptionResponse, QuestionTracking } from '../types/api.ts'

export async function getQuestionBySource(sourceType: QuestionSourceType, sourceId: number): Promise<QuestionTracking | null> {
  const response = await apiClient.get<ApiResponse<QuestionTracking | null>>('/questions/by-source', {
    params: { sourceType, sourceId },
  })
  return response.data.data
}

export async function getQuestionsBySources(sourceType: QuestionSourceType, sourceIds: number[]): Promise<Record<number, QuestionSourceSummary>> {
  if (sourceIds.length === 0) return {}
  const response = await apiClient.get<ApiResponse<Record<number, QuestionSourceSummary>>>('/questions/by-sources', {
    params: { sourceType, sourceIds },
    paramsSerializer: { indexes: null },
  })
  return response.data.data
}

export async function getQuestion(questionId: number): Promise<QuestionTracking> {
  const response = await apiClient.get<ApiResponse<QuestionTracking>>(`/questions/${questionId}`)
  return response.data.data
}

export async function createQuestion(sourceType: QuestionSourceType, sourceId: number, questionText: string): Promise<QuestionTracking> {
  const response = await apiClient.post<ApiResponse<QuestionTracking>>('/questions', {
    sourceType,
    sourceId,
    questionText,
  })
  return response.data.data
}

export async function subscribeQuestion(questionId: number): Promise<QuestionSubscriptionResponse> {
  const response = await apiClient.post<ApiResponse<QuestionSubscriptionResponse>>(`/questions/${questionId}/subscriptions`)
  return response.data.data
}

export async function unsubscribeQuestion(questionId: number): Promise<QuestionSubscriptionResponse> {
  const response = await apiClient.delete<ApiResponse<QuestionSubscriptionResponse>>(`/questions/${questionId}/subscriptions`)
  return response.data.data
}

export async function getMyQuestions(role: MyQuestionRole, page = 1, size = 20): Promise<PageResponse<QuestionTracking>> {
  const response = await apiClient.get<ApiResponse<PageResponse<QuestionTracking>>>('/users/me/questions', {
    params: { role, page, size },
  })
  return response.data.data
}

export async function getQuestionAnswers(questionId: number): Promise<QuestionAnswer[]> {
  const response = await apiClient.get<ApiResponse<QuestionAnswer[]>>(`/questions/${questionId}/answers`)
  return response.data.data
}

export async function reviewQuestionAnswerWithAi(questionId: number, answerId: number): Promise<CandidateAnswerAiReview> {
  const response = await apiClient.post<ApiResponse<CandidateAnswerAiReview>>(`/questions/${questionId}/answers/${answerId}/ai-review`)
  return response.data.data
}

export async function acceptQuestionAnswer(questionId: number, answerId: number): Promise<QuestionTracking> {
  const response = await apiClient.post<ApiResponse<QuestionTracking>>(`/questions/${questionId}/answers/${answerId}/accept`)
  return response.data.data
}

export async function rejectQuestionAnswer(questionId: number, answerId: number): Promise<QuestionAnswer> {
  const response = await apiClient.post<ApiResponse<QuestionAnswer>>(`/questions/${questionId}/answers/${answerId}/reject`)
  return response.data.data
}

export async function completeQuestion(questionId: number): Promise<QuestionTracking> {
  const response = await apiClient.post<ApiResponse<QuestionTracking>>(`/questions/${questionId}/complete`)
  return response.data.data
}

export async function reopenQuestion(questionId: number): Promise<QuestionTracking> {
  const response = await apiClient.post<ApiResponse<QuestionTracking>>(`/questions/${questionId}/reopen`)
  return response.data.data
}

export async function deleteQuestion(questionId: number): Promise<void> {
  await apiClient.delete<ApiResponse<null>>(`/questions/${questionId}`)
}
