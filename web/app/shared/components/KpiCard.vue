<script setup lang="ts">
/**
 * Stat tile: the value, its change against the previous period, and a sparkline of the
 * daily series. The change is shown in neutral ink with an arrow: "up" is not always good
 * (more tokens), so it carries no good/bad color.
 */
const props = withDefaults(defineProps<{
  title: string
  value: string
  /** Relative change (0.12 = +12 %) or, with deltaUnit 'pp', percentage points */
  delta?: number | null
  deltaUnit?: '%' | 'pp'
  /** Daily values for the sparkline */
  series?: number[]
  hint?: string
  /** Replaces the comparison line, e.g. to explain why there is no value */
  note?: string
  icon?: string
  color?: string
}>(), {
  delta: null,
  deltaUnit: '%',
  series: () => [],
  hint: undefined,
  note: undefined,
  icon: undefined,
  color: 'currentColor',
})

const deltaText = computed(() => {
  const d = props.delta
  if (d == null || !Number.isFinite(d)) return null
  if (props.deltaUnit === 'pp') {
    const pp = d * 100
    return `${pp >= 0 ? '+' : ''}${pp.toFixed(1)} pp`
  }
  const pct = d * 100
  return `${pct >= 0 ? '+' : ''}${Math.abs(pct) >= 10 ? pct.toFixed(0) : pct.toFixed(1)} %`
})
const deltaIcon = computed(() => {
  const d = props.delta ?? 0
  if (Math.abs(d) < 0.0005) return 'i-lucide-minus'
  return d > 0 ? 'i-lucide-arrow-up-right' : 'i-lucide-arrow-down-right'
})

const W = 200
const H = 32
const path = computed(() => {
  const s = props.series
  if (s.length < 2) return null
  const max = Math.max(...s, 1)
  const step = W / (s.length - 1)
  const pts = s.map((v, i) => [i * step, H - 2 - (v / max) * (H - 4)] as const)
  const line = pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ')
  return { line, area: `${line} L${W} ${H} L0 ${H} Z` }
})
</script>

<template>
  <UCard :ui="{ body: 'p-4' }">
    <div class="flex items-center gap-2 text-sm text-muted">
      <UIcon v-if="icon" :name="icon" class="w-4 h-4" />
      <span class="font-medium">{{ title }}</span>
    </div>
    <div class="mt-2 text-2xl font-semibold text-highlighted">{{ value }}</div>
    <div class="mt-1 flex items-center gap-1 text-xs text-muted whitespace-nowrap" title="Compared with the previous period of the same length">
      <template v-if="note">
        <UIcon name="i-lucide-info" class="w-3.5 h-3.5" />
        <span class="whitespace-normal">{{ note }}</span>
      </template>
      <template v-else-if="deltaText">
        <UIcon :name="deltaIcon" class="w-3.5 h-3.5 text-default" />
        <span class="text-default tabular-nums">{{ deltaText }}</span>
        <span>vs previous period</span>
      </template>
      <span v-else>No previous-period data</span>
    </div>
    <svg
      v-if="path"
      :viewBox="`0 0 ${W} ${H}`"
      preserveAspectRatio="none"
      class="mt-3 w-full h-8 overflow-visible"
      aria-hidden="true"
    >
      <path :d="path.area" :fill="color" fill-opacity="0.12" />
      <path :d="path.line" fill="none" :stroke="color" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke" />
    </svg>
    <div v-else class="mt-3 h-8" />
    <p v-if="hint" class="mt-2 text-xs text-muted">{{ hint }}</p>
  </UCard>
</template>
