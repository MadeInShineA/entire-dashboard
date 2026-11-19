/**
 * File history module - API client
 */
import { $adminApi } from '~/api/admin-api-client'
import type { PagerPayload } from '~/shared/types/api'
import type { RepoDTO } from '~/features/repository/types/repoDTO'
import type { FileHistory, FileList } from '../types/file-history.types'

export const fileHistoryApi = {
  /** All configured repositories (first 200) */
  async listRepos(): Promise<RepoDTO[]> {
    const res = await $adminApi<PagerPayload<RepoDTO>>('/repo/search', {
      method: 'GET',
      query: { page: 0, size: 200 },
    })
    return res.data ?? []
  },

  /** Files of a branch with AI session counts */
  listFiles(repoId: number, branch?: string | null): Promise<FileList> {
    return $adminApi<FileList>('/file/list', {
      method: 'GET',
      query: { repoId, ...(branch ? { branch } : {}) },
    })
  },

  /** Sessions that touched a file + line-by-line attribution */
  getHistory(repoId: number, path: string, branch?: string | null): Promise<FileHistory> {
    return $adminApi<FileHistory>('/file/history', {
      method: 'GET',
      query: { repoId, path, ...(branch ? { branch } : {}) },
    })
  },
}
