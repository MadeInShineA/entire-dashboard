<script setup lang="ts">
import type { ToolEdit } from '../utils/tool-edits'

const props = defineProps<{
  edit: ToolEdit
  /** Display path (project root stripped) */
  displayPath: string
}>()

const emit = defineEmits<{
  showFileDiff: []
  openInFiles: []
}>()

/** Long changes start collapsed to this many lines */
const PREVIEW_LINES = 24

const expanded = ref(false)
const totalLines = computed(() => props.edit.blocks.reduce((n, b) => n + b.length, 0))
const visibleBlocks = computed(() => {
  if (expanded.value || totalLines.value <= PREVIEW_LINES) return props.edit.blocks
  const out: typeof props.edit.blocks = []
  let left = PREVIEW_LINES
  for (const b of props.edit.blocks) {
    if (left <= 0) break
    out.push(b.slice(0, left))
    left -= b.length
  }
  return out
})

const LINE_CLASS = {
  ctx: 'text-muted',
  del: 'bg-red-500/10 text-red-700 dark:text-red-300',
  add: 'bg-green-500/10 text-green-700 dark:text-green-300',
} as const
const SIGN = { ctx: ' ', del: '-', add: '+' } as const
</script>

<template>
  <div class="rounded border border-default overflow-hidden">
    <div class="flex items-center gap-2 px-2 py-1 bg-elevated text-xs">
      <UIcon :name="edit.isWrite ? 'i-lucide-file-plus' : 'i-lucide-file-pen'" class="w-3.5 h-3.5 shrink-0 text-muted" />
      <span class="font-mono truncate text-default" :title="edit.rawPath">{{ displayPath }}</span>
      <span class="shrink-0 tabular-nums">
        <span class="text-green-700 dark:text-green-400">+{{ edit.additions }}</span>
        <span class="ml-1 text-red-700 dark:text-red-400">-{{ edit.deletions }}</span>
      </span>
      <span class="flex-1" />
      <UTooltip text="Full diff of this file in the session">
        <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-git-compare" aria-label="Show file diff" @click="emit('showFileDiff')" />
      </UTooltip>
      <UTooltip text="Open in Files: sessions and line-by-line history">
        <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-file-search" aria-label="Open in Files" @click="emit('openInFiles')" />
      </UTooltip>
    </div>
    <div class="font-mono text-xs leading-5 overflow-x-auto">
      <template v-for="(block, bi) in visibleBlocks" :key="bi">
        <div v-if="bi > 0" class="border-t border-dashed border-default text-center text-[10px] text-muted leading-4">⋯</div>
        <div v-for="(line, li) in block" :key="li" class="flex whitespace-pre" :class="LINE_CLASS[line.kind]">
          <span class="w-5 shrink-0 text-center select-none opacity-70">{{ SIGN[line.kind] }}</span>
          <span class="pr-3">{{ line.text || ' ' }}</span>
        </div>
      </template>
      <button
        v-if="totalLines > PREVIEW_LINES"
        type="button"
        class="w-full py-1 text-[11px] text-muted hover:text-default bg-elevated/60 border-t border-default"
        @click="expanded = !expanded"
      >
        {{ expanded ? 'Show less' : `Show all ${totalLines} lines` }}
      </button>
    </div>
  </div>
</template>
