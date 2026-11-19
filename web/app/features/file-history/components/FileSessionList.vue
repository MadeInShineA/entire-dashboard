<script setup lang="ts">
import type { CheckpointDTO } from '~/features/checkpoint/types/checkpoint.types'
import type { FileSession } from '../types/file-history.types'
import { sessionColorVar } from '../utils/session-colors'
import { formatDate } from '~/shared/utils/format'

const props = defineProps<{
  rows: FileSession[]
  keyOf: (row: FileSession) => number
  slotByKey: Map<number, number>
  labelByKey: Map<number, string>
  selectedKey: number | null
}>()

const emit = defineEmits<{
  select: [key: number | null]
  open: [checkpoint: CheckpointDTO]
}>()

function toggle(row: FileSession) {
  const key = props.keyOf(row)
  emit('select', props.selectedKey === key ? null : key)
}
</script>

<template>
  <div v-if="!rows.length" class="text-sm text-muted py-6 text-center">
    No AI session touched this file.
  </div>
  <ul v-else class="divide-y divide-default rounded-md border border-default">
    <li
      v-for="row in rows"
      :key="keyOf(row)"
      class="flex items-start gap-3 px-3 py-2.5 cursor-pointer transition-colors"
      :class="selectedKey === keyOf(row) ? 'bg-elevated' : 'hover:bg-elevated/50'"
      @click="toggle(row)"
    >
      <span
        class="mt-1 size-3 shrink-0 rounded-sm"
        :style="slotByKey.has(keyOf(row))
          ? { background: sessionColorVar(slotByKey.get(keyOf(row))) }
          : { border: '1px dashed var(--ui-border-accented)' }"
        aria-hidden="true"
      />
      <div class="min-w-0 flex-1 space-y-1">
        <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
          <span class="font-semibold text-highlighted">{{ labelByKey.get(keyOf(row)) }}</span>
          <span class="text-muted">{{ formatDate(new Date(row.checkpoint.commitTime), 'YYYY-MM-DD HH:mm') }}</span>
          <span v-if="row.checkpoint.commitAuthorName" class="text-muted">· {{ row.checkpoint.commitAuthorName }}</span>
          <UBadge v-if="row.session?.agent || row.checkpoint.agent" size="sm" variant="subtle" color="neutral">
            {{ row.session?.agent || row.checkpoint.agent }}
          </UBadge>
          <UBadge v-if="row.currentLines > 0" size="sm" variant="soft" color="primary">
            {{ row.currentLines }} current line{{ row.currentLines === 1 ? '' : 's' }}
          </UBadge>
          <UBadge v-else size="sm" variant="outline" color="neutral">no current lines</UBadge>
          <UTooltip v-if="!row.touchedFile" text="The session's file list doesn't include this file; lines are attributed through the commit">
            <UBadge size="sm" variant="outline" color="neutral">via commit</UBadge>
          </UTooltip>
        </div>
        <p class="text-sm text-default truncate" :title="row.session?.promptPreview || ''">
          {{ row.session?.promptPreview || row.checkpoint.commitMessage?.split('\n')[0] }}
        </p>
        <p class="text-xs text-muted truncate">
          <span class="font-mono">{{ row.checkpoint.commitSha?.slice(0, 7) }}</span>
          {{ row.checkpoint.commitMessage?.split('\n')[0] }}
        </p>
      </div>
      <UButton
        icon="i-lucide-panel-right-open"
        size="xs"
        color="neutral"
        variant="ghost"
        aria-label="Open checkpoint details"
        @click.stop="emit('open', row.checkpoint)"
      />
    </li>
  </ul>
</template>
