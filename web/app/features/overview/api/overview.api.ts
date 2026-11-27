/**
 * Overview module - API client
 */
import { $adminApi } from '~/api/admin-api-client'
import type { AnalyticsResponse } from '../types/analytics.types'

export const overviewApi = {
  /** Every checkpoint in [startTime, endTime] with its session aggregates (max 180 days) */
  async getAnalytics(params: { startTime: number; endTime: number }) {
    return $adminApi<AnalyticsResponse>('/overview/analytics', {
      method: 'GET',
      query: params,
    })
  },
}
