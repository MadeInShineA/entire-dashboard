<script setup lang="tsx">
/**
 * Checkpoints of the current selection, newest first, paginated client-side.
 * Clicking a row opens the checkpoint's sessions, transcript and diffs.
 */
import type { Row } from '@tanstack/vue-table'
import { UIcon } from '#components'
import AgentBadge from './AgentBadge.vue'
import { formatDateTime } from '~/shared/utils/date'
import { formatCompact } from '~/shared/utils/format'
import { rowTokens } from '../utils/aggregate'
import type { AnalyticsRow } from '../types/analytics.types'

const props = defineProps<{ rows: AnalyticsRow[] }>()
const emit = defineEmits<{ open: [row: AnalyticsRow] }>()

const pageSizeOptions = [10, 20, 50, 100].map((n) => ({ label: `${n} per page`, value: n }))
const page = ref(1)
const size = ref(20)
watch(() => props.rows, () => { page.value = 1 })
watch(size, () => { page.value = 1 })

const sorted = computed(() => [...props.rows].sort((a, b) => b.commitTime - a.commitTime))
const pageRows = computed(() => sorted.value.slice((page.value - 1) * size.value, page.value * size.value))

const columns = [
  {
    accessorKey: 'commitMessage',
    header: 'Commit',
    cell: ({ row }: { row: Row<AnalyticsRow> }) => (
      <button
        type="button"
        class="group max-w-[280px] w-full text-left inline-flex items-center gap-1.5 rounded px-1.5 py-0.5 -mx-1.5 hover:bg-primary/10 transition-colors"
        title="Open checkpoint details"
        onClick={() => emit('open', row.original)}
      >
        <span class="truncate text-highlighted group-hover:text-primary">{row.original.commitMessage || row.original.checkpointId}</span>
        <UIcon name="i-lucide-chevron-right" class="size-3.5 shrink-0 text-muted opacity-0 group-hover:opacity-100" />
      </button>
    ),
  },
  {
    accessorKey: 'commitTime',
    header: 'Committed',
    cell: ({ row }: { row: Row<AnalyticsRow> }) => <span class="whitespace-nowrap tabular-nums">{formatDateTime(row.original.commitTime)}</span>,
  },
  {
    accessorKey: 'repoName',
    header: 'Repository',
    cell: ({ row }: { row: Row<AnalyticsRow> }) => (
      <span>
        {row.original.repoName || '-'}
        {row.original.branch ? <span class="text-muted"> · {row.original.branch}</span> : null}
      </span>
    ),
  },
  {
    accessorKey: 'commitAuthorName',
    header: 'Author',
    cell: ({ row }: { row: Row<AnalyticsRow> }) => row.original.commitAuthorName || '-',
  },
  {
    accessorKey: 'agent',
    header: 'Agent',
    cell: ({ row }: { row: Row<AnalyticsRow> }) => <AgentBadge agent={row.original.agent} />,
  },
  {
    accessorKey: 'model',
    header: 'Model',
    cell: ({ row }: { row: Row<AnalyticsRow> }) => (
      <span class="whitespace-nowrap">
        {row.original.model || '-'}
        {row.original.turnCount ? <span class="text-muted"> · {row.original.turnCount} turn{row.original.turnCount === 1 ? '' : 's'}</span> : null}
      </span>
    ),
  },
  {
    id: 'changes',
    header: 'Changes',
    cell: ({ row }: { row: Row<AnalyticsRow> }) => (
      <span class="whitespace-nowrap tabular-nums" title={`${row.original.filesTouched ?? 0} files touched`}>
        <span class="text-success">+{row.original.additions ?? 0}</span>
        <span class="text-muted mx-1">/</span>
        <span class="text-error">-{row.original.deletions ?? 0}</span>
      </span>
    ),
  },
  {
    id: 'tokens',
    header: 'Tokens',
    cell: ({ row }: { row: Row<AnalyticsRow> }) => (
      <span class="tabular-nums" title={rowTokens(row.original).toLocaleString()}>{formatCompact(rowTokens(row.original))}</span>
    ),
  },
]
</script>

<template>
  <UCard :ui="{ body: 'p-4 sm:p-5' }">
    <div class="flex items-baseline justify-between gap-4 mb-3">
      <h3 class="text-base font-semibold text-highlighted">Checkpoints</h3>
      <span class="text-sm text-muted">{{ rows.length.toLocaleString() }} in this selection</span>
    </div>

    <div v-if="!rows.length" class="text-center py-12 text-sm text-muted">
      <UIcon name="i-lucide-git-commit" class="w-8 h-8 mx-auto mb-2 opacity-50" />
      No checkpoints in this selection
    </div>
    <template v-else>
      <div class="overflow-x-auto">
        <UTable :data="pageRows" :columns="columns" />
      </div>
      <div class="flex flex-wrap justify-end items-center gap-3 pt-4 mt-2 border-t border-default">
        <UPagination v-model:page="page" :items-per-page="size" :total="rows.length" :max="7" />
        <USelect v-model="size" :items="pageSizeOptions" class="w-36" />
      </div>
    </template>
  </UCard>
</template>
