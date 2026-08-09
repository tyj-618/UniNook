import { apiClient } from './client.ts'
import type { AiAssistantResponse, ApiResponse, CampusScope } from '../types/api.ts'

export async function askAssistant(question: string, scope: CampusScope): Promise<AiAssistantResponse> {
  const response = await apiClient.post<ApiResponse<AiAssistantResponse>>('/ai/assistant/ask', { question, scope })
  return response.data.data
}
