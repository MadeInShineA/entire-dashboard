<script setup lang="ts">
/**
 * Line-by-line attribution. Clicking a line written in an AI session highlights all of that
 * session's lines and expands a session card right under the line (blame "peek"): who/when/prompt,
 * a jump between the session's blocks, and a button opening the transcript + diff sidebar.
 */
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
  open: [row: FileSession]
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
  return `${props.labelByKey.get(row.key) ?? ''} · ${base}\nClick to show the session`
}

// ---- peek ------------------------------------------------------------------
/** Line number the session card is expanded under */
const peekLine = ref<number | null>(null)
const peekKey = computed(() => (peekLine.value != null ? rows.value[peekLine.value - 1]?.key ?? null : null))
const peekSession = computed(() => (peekKey.value != null ? props.rowByKey.get(peekKey.value) ?? null : null))

// Selection changed elsewhere (session list): close a card that no longer matches
watch(() => props.selectedKey, (k) => {
  if (k == null || (peekKey.value != null && k !== peekKey.value)) peekLine.value = null
})
// New file: reset
watch(() => props.lines, () => { peekLine.value = null })

function onLineClick(row: Row) {
  // Selecting code to copy it is not a click on the line
  if (window.getSelection()?.toString()) return
  if (row.key == null) {
    peekLine.value = null
    emit('select', null)
    return
  }
  if (peekLine.value === row.line.number) {
    closePeek()
    return
  }
  peekLine.value = row.line.number
  emit('select', row.key)
}

function closePeek() {
  peekLine.value = null
  emit('select', null)
}

/** First lines of each block of the peeked session, to jump between them */
const blocks = computed(() =>
  peekKey.value == null ? [] : rows.value.filter((r) => r.runStart && r.key === peekKey.value).map((r) => r.line.number)
)
const blockIndex = computed(() => {
  const n = peekLine.value
  if (n == null) return -1
  let idx = -1
  blocks.value.forEach((start, i) => { if (start <= n) idx = i })
  return idx
})

const container = ref<HTMLElement | null>(null)
function goToBlock(delta: number) {
  const target = blocks.value[blockIndex.value + delta]
  if (target == null) return
  peekLine.value = target
  nextTick(() => {
    container.value?.querySelector(`[data-line="${target}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  })
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && peekLine.value != null) closePeek()
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

const peekLinesOwned = computed(() =>
  peekKey.value == null ? 0 : rows.value.filter((r) => r.key === peekKey.value).length
)
</script>

<template>
  <div ref="container" class="rounded-md border border-default overflow-x-auto bg-default">
    <table class="w-full border-collapse font-mono text-xs leading-5">
      <tbody>
        <template v-for="row in rows" :key="row.line.number">
          <tr
            :data-line="row.line.number"
            class="transition-[opacity,background-color]"
            :class="[
              selectedKey != null && row.key !== selectedKey ? 'opacity-35' : '',
              row.runStart && row.line.number > 1 ? 'border-t border-default/60' : '',
              row.key != null ? 'cursor-pointer hover:bg-elevated/60' : '',
              peekLine === row.line.number ? 'bg-elevated' : '',
            ]"
            :title="hoverText(row)"
            @click="onLineClick(row)"
          >
            <td
              class="w-44 max-w-44 pl-2 pr-3 truncate select-none font-sans"
              :class="row.key != null ? 'text-default' : 'text-dimmed'"
              :style="{ borderLeft: `4px solid ${row.key != null ? sessionColorVar(slotByKey.get(row.key)) : 'transparent'}` }"
            >
              <button
                v-if="row.runStart && row.key != null"
                type="button"
                class="truncate max-w-full hover:underline focus-visible:underline outline-none"
                :aria-expanded="peekLine === row.line.number"
                @click.stop="onLineClick(row)"
              >{{ gutterLabel(row) }}</button>
              <span v-else-if="row.runStart">{{ gutterLabel(row) }}</span>
            </td>
            <td class="w-10 pr-3 text-right text-dimmed select-none tabular-nums">{{ row.line.number }}</td>
            <td class="pr-4 whitespace-pre text-default">{{ row.line.content || ' ' }}</td>
          </tr>

          <!-- Session card, expanded under the clicked line -->
          <tr v-if="peekLine === row.line.number && peekSession && peekKey != null">
            <td colspan="3" class="p-0">
              <div
                class="sticky left-0 m-2 ml-11 max-w-3xl rounded-lg border border-default bg-default shadow-md font-sans text-sm"
                :style="{ borderLeft: `4px solid ${sessionColorVar(slotByKey.get(peekKey))}` }"
                role="region"
                :aria-label="`Session ${labelByKey.get(peekKey)}`"
                @click.stop
              >
                <div class="flex items-start gap-3 p-3">
                  <div class="min-w-0 flex-1 space-y-1.5">
                    <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                      <span class="font-semibold text-highlighted">{{ labelByKey.get(peekKey) }}</span>
                      <UBadge v-if="peekSession.session?.agent || peekSession.checkpoint.agent" size="sm" variant="subtle" color="neutral">
                        {{ peekSession.session?.agent || peekSession.checkpoint.agent }}
                      </UBadge>
                      <span class="text-muted">
                        {{ formatDate(new Date(peekSession.checkpoint.commitTime), 'YYYY-MM-DD HH:mm') }}
                        <template v-if="peekSession.checkpoint.commitAuthorName"> · {{ peekSession.checkpoint.commitAuthorName }}</template>
                      </span>
                    </div>
                    <p v-if="peekSession.session?.promptPreview" class="text-default line-clamp-3 whitespace-pre-line">
                      <span class="text-muted">Prompt: </span>{{ peekSession.session.promptPreview }}
                    </p>
                    <p class="text-xs text-muted truncate">
                      <span class="font-mono">{{ peekSession.checkpoint.commitSha?.slice(0, 7) }}</span>
                      {{ peekSession.checkpoint.commitMessage?.split('\n')[0] }}
                      · {{ peekLinesOwned }} line{{ peekLinesOwned === 1 ? '' : 's' }} of this file
                    </p>
                  </div>
                  <UButton
                    icon="i-lucide-x"
                    size="xs"
                    color="neutral"
                    variant="ghost"
                    aria-label="Close (Esc)"
                    @click="closePeek"
                  />
                </div>
                <div class="flex flex-wrap items-center justify-between gap-2 border-t border-default px-3 py-2">
                  <div v-if="blocks.length > 1" class="flex items-center gap-1 text-xs text-muted">
                    <UButton
                      icon="i-lucide-chevron-up"
                      size="xs"
                      color="neutral"
                      variant="ghost"
                      :disabled="blockIndex <= 0"
                      aria-label="Previous block of this session"
                      @click="goToBlock(-1)"
                    />
                    <span class="tabular-nums">Block {{ blockIndex + 1 }} of {{ blocks.length }}</span>
                    <UButton
                      icon="i-lucide-chevron-down"
                      size="xs"
                      color="neutral"
                      variant="ghost"
                      :disabled="blockIndex >= blocks.length - 1"
                      aria-label="Next block of this session"
                      @click="goToBlock(1)"
                    />
                  </div>
                  <span v-else class="text-xs text-muted">Esc to close</span>
                  <UButton
                    icon="i-lucide-messages-square"
                    size="sm"
                    @click="emit('open', peekSession)"
                  >
                    Open conversation
                  </UButton>
                </div>
              </div>
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>
