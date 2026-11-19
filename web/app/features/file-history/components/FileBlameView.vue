<script setup lang="ts">
import type { FileLine, FileSession } from '../types/file-history.types'
import { sessionColorVar } from '../utils/session-colors'
import { formatDate } from '~/shared/utils/format'

const props = defineProps<{
  lines: FileLine[]
  lineKey: (line: FileLine) => number | null
  slotByKey: Map<number, number>
  labelByKey: Map<number, string>
  rowByKey: Map<number, FileSession>
  selectedKey: number | null
}>()

const emit = defineEmits<{
  select: [key: number | null]
}>()

interface Row {
  line: FileLine
  key: number | null
  /** First line of a run of lines from the same commit: show the gutter label */
  runStart: boolean
}

const rows = computed<Row[]>(() => {
  let prevSha: string | null | undefined
  let prevKey: number | null | undefined
  return props.lines.map((line) => {
    const key = props.lineKey(line)
    const runStart = line.commitSha !== prevSha || key !== prevKey
    prevSha = line.commitSha
    prevKey = key
    return { line, key, runStart }
  })
})

function gutterLabel(row: Row): string {
  const who = row.line.authorName ? ` · ${row.line.authorName}` : ''
  if (row.key != null) return `${props.labelByKey.get(row.key) ?? 'AI'}${who}`
  return `${row.line.commitSha?.slice(0, 7) ?? '—'}${who}`
}

function hoverText(row: Row): string {
  const when = row.line.commitTime ? formatDate(new Date(row.line.commitTime), 'YYYY-MM-DD HH:mm') : ''
  const base = `${row.line.commitSha?.slice(0, 7) ?? ''} · ${row.line.authorName ?? ''} · ${when}`
  if (row.key == null) return `${base}\nNot linked to an AI checkpoint`
  const s = props.rowByKey.get(row.key)
  const prompt = s?.session?.promptPreview ? `\nPrompt: ${s.session.promptPreview}` : ''
  return `${props.labelByKey.get(row.key) ?? ''} · ${base}${prompt}`
}

function onClick(row: Row) {
  if (row.key == null) return
  emit('select', props.selectedKey === row.key ? null : row.key)
}
</script>

<template>
  <div class="rounded-md border border-default overflow-x-auto bg-default">
    <table class="w-full border-collapse font-mono text-xs leading-5">
      <tbody>
        <tr
          v-for="row in rows"
          :key="row.line.number"
          class="transition-opacity"
          :class="[
            selectedKey != null && row.key !== selectedKey ? 'opacity-35' : '',
            row.runStart && row.line.number > 1 ? 'border-t border-default/60' : '',
          ]"
        >
          <td
            class="w-44 max-w-44 pl-2 pr-3 truncate select-none font-sans"
            :class="row.key != null ? 'text-default cursor-pointer' : 'text-dimmed'"
            :style="{ borderLeft: `4px solid ${row.key != null ? sessionColorVar(slotByKey.get(row.key)) : 'transparent'}` }"
            :title="hoverText(row)"
            @click="onClick(row)"
          >
            <span v-if="row.runStart">{{ gutterLabel(row) }}</span>
          </td>
          <td class="w-10 pr-3 text-right text-dimmed select-none tabular-nums">{{ row.line.number }}</td>
          <td class="pr-4 whitespace-pre text-default">{{ row.line.content || ' ' }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
