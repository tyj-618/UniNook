import { apiClient } from './client.ts'
import type { ApiResponse, ReportTargetType } from '../types/api.ts'

export async function submitContentReport(
  targetType: ReportTargetType,
  targetId: number,
  reason: string,
): Promise<number> {
  const response = await apiClient.post<ApiResponse<number>>('/reports', { targetType, targetId, reason })
  return response.data.data!
}
