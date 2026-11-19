<script setup lang="ts">
import type { ParsedTranscript, ToolUse } from '../utils/transcript-parser'
import { stripProjectRoot } from '../utils/transcript-parser'
import { toolEdit, type ToolEdit } from '../utils/tool-edits'
import ToolEditDiff from './ToolEditDiff.vue'

const props = defineProps<{
  parsed: ParsedTranscript
}>()

const emit = defineEmits<{
  /** Raw tool path of a file to show the full session diff of */
  showFileDiff: [rawPath: string]
  /** Raw tool path of a file to open on the Files page */
  openInFiles: [rawPath: string]
}>()

// Parse each tool once; keyed by object identity
const editCache = new WeakMap<object, ToolEdit | null>()
function editOf(t: ToolUse): ToolEdit | null {
  if (!editCache.has(t)) editCache.set(t, toolEdit(t))
  return editCache.get(t) ?? null
}
function displayPath(e: ToolEdit): string {
  return stripProjectRoot(e.rawPath, props.parsed.projectRoot)
}

function formatTime(ms?: number): string {
  if (ms == null) return ''
  const d = new Date(ms)
  return d.toLocaleTimeString()
}

type ToolLike = { tool: string; title?: string; input?: unknown; output?: unknown; error?: boolean }

const KEY_ARGS = ['command', 'file_path', 'filePath', 'path', 'pattern', 'url', 'query', 'description', 'prompt']

function inputObject(t: ToolLike): Record<string, unknown> | null {
  return t.input && typeof t.input === 'object' && !Array.isArray(t.input)
    ? (t.input as Record<string, unknown>)
    : null
}

/** One-line hint shown next to the tool name: the command, file, pattern, ... */
function toolSummary(t: ToolLike): string {
  if (t.title) return t.title
  const input = inputObject(t)
  if (!input) return ''
  for (const k of KEY_ARGS) {
    const v = input[k]
    if (typeof v === 'string' && v.trim()) return v.trim().split('\n')[0]!
  }
  return ''
}

/** Bash-like tools: show the command as-is instead of a JSON object */
function inputText(t: ToolLike): string {
  const input = inputObject(t)
  if (input && typeof input.command === 'string') {
    const desc = typeof input.description === 'string' ? `# ${input.description}\n` : ''
    return desc + input.command
  }
  if (t.input == null) return ''
  return typeof t.input === 'string' ? t.input : JSON.stringify(t.input, null, 2)
}

function outputText(t: ToolLike): string {
  if (t.output == null) return ''
  return typeof t.output === 'string' ? t.output : JSON.stringify(t.output, null, 2)
}
</script>

<template>
  <div class="space-y-4">
    <div v-for="(msg, i) in (props.parsed.messages ?? [])" :key="msg.id || i" class="space-y-2">
      <!-- User message -->
      <div
        v-if="msg.role === 'user'"
        class="flex gap-3"
      >
        <div class="shrink-0 w-8 h-8 rounded-full bg-primary/20 flex items-center justify-center">
          <UIcon name="i-lucide-user" class="w-4 h-4 text-primary" />
        </div>
        <div class="flex-1 min-w-0">
          <div v-if="msg.taskTitle" class="text-sm font-medium text-default mb-1">
            {{ msg.taskTitle }}
          </div>
          <div class="text-sm text-default whitespace-pre-wrap break-words">
            {{ msg.text }}
          </div>
        </div>
      </div>

      <!-- Assistant message -->
      <div
        v-else
        class="flex gap-3"
      >
        <div class="shrink-0 w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center">
          <UIcon name="i-lucide-bot" class="w-4 h-4 text-blue-600 dark:text-blue-400" />
        </div>
        <div class="flex-1 min-w-0 space-y-2">
          <div v-if="msg.reasoning" class="text-xs text-gray-500 italic">
            {{ msg.reasoning }}
          </div>
          <div v-if="msg.text" class="text-sm text-default whitespace-pre-wrap break-words">
            {{ msg.text }}
          </div>
          <div v-if="(msg.toolsCount ?? 0) > 0" class="flex items-center gap-2">
            <UButton
              size="xs"
              color="neutral"
              variant="soft"
              :icon="'i-lucide-wrench'"
            >
              {{ msg.toolsCount }} tool{{ (msg.toolsCount ?? 0) > 1 ? 's' : '' }} used
            </UButton>
            <div class="flex flex-wrap gap-1">
              <UBadge
                v-for="(t, ti) in (msg.tools ?? [])"
                :key="t.callID || `tool-${ti}`"
                size="xs"
                variant="subtle"
                color="neutral"
              >
                {{ t.tool }}
              </UBadge>
            </div>
          </div>
          <div v-if="(msg.tools ?? []).length > 0" class="text-xs space-y-1 mt-2">
            <details
              v-for="(t, ti) in (msg.tools ?? [])"
              :key="t.callID || `toold-${ti}`"
              class="border rounded px-2 py-1"
              :class="t.error ? 'border-error/50' : 'border-default'"
              :open="!!editOf(t)"
            >
              <summary class="cursor-pointer font-mono text-muted truncate">
                <span class="text-highlighted">{{ t.tool }}</span>
                <span v-if="editOf(t)" class="ml-1.5">{{ displayPath(editOf(t)!) }}</span>
                <span v-else-if="toolSummary(t)" class="ml-1.5">{{ toolSummary(t) }}</span>
                <UBadge v-if="t.error" size="xs" color="error" variant="subtle" class="ml-2 align-middle">error</UBadge>
              </summary>
              <div class="mt-1 space-y-2 pb-1">
                <ToolEditDiff
                  v-if="editOf(t)"
                  :edit="editOf(t)!"
                  :display-path="displayPath(editOf(t)!)"
                  @show-file-diff="emit('showFileDiff', editOf(t)!.rawPath)"
                  @open-in-files="emit('openInFiles', editOf(t)!.rawPath)"
                />
                <div v-else-if="inputText(t)">
                  <div class="text-[11px] uppercase tracking-wider text-muted mb-0.5">Input</div>
                  <pre class="text-xs overflow-auto max-h-64 whitespace-pre-wrap break-words rounded bg-elevated p-2 text-default">{{ inputText(t) }}</pre>
                </div>
                <div v-if="outputText(t)">
                  <div class="text-[11px] uppercase tracking-wider mb-0.5" :class="t.error ? 'text-error' : 'text-muted'">Output</div>
                  <pre class="text-xs overflow-auto max-h-80 whitespace-pre-wrap break-words rounded bg-elevated p-2" :class="t.error ? 'text-error' : 'text-default'">{{ outputText(t) }}</pre>
                </div>
              </div>
            </details>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
