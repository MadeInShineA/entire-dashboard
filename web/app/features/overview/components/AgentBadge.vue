<script setup lang="ts">
/** Agent name with its chart color and shape, so identity never depends on color alone */
import { useAgentColors } from '../composables/useAgentColors'

defineProps<{ agent: string | null | undefined }>()

const { getAgentColor, getAgentBadgeStyle, agentSymbolSvg } = useAgentColors()
</script>

<template>
  <span
    v-if="agent"
    class="inline-flex items-center gap-1.5 rounded-md border px-1.5 py-0.5 text-xs text-default whitespace-nowrap"
    :style="getAgentBadgeStyle(agent)"
  >
    <svg viewBox="0 0 12 12" class="size-2.5 shrink-0" aria-hidden="true">
      <path :d="agentSymbolSvg(agent)" :fill="getAgentColor(agent)" />
    </svg>
    {{ agent }}
  </span>
  <span v-else class="text-dimmed">-</span>
</template>
