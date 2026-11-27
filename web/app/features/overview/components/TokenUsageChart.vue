<script setup lang="ts">
/**
 * Tokens per day by kind: input, output, prompt-cache writes and prompt-cache reads (Entire CLI 0.13.2+).
 * Cache reads usually dominate; the split shows how much of the volume the cache absorbs.
 */
import type { EChartsOption } from 'echarts'
import ChartCard from '~/shared/components/ChartCard.vue'
import { VChart } from '~/shared/utils/echarts'
import { shortDayLabel } from '~/shared/utils/date'
import { formatCompact, formatPercent } from '~/shared/utils/format'
import type { DayBucket, Totals } from '../utils/aggregate'

const props = defineProps<{
  days: DayBucket[]
  totals: Totals
}>()

const { theme, tooltip, xAxisBase, yAxisBase } = useChartTheme()

type Kind = 'inputTokens' | 'outputTokens' | 'cacheWriteTokens' | 'cacheReadTokens' | 'otherTokens'
const kinds = computed(() => {
  const t = theme.value
  const list: { key: Kind; name: string; color: string; total: number }[] = [
    { key: 'inputTokens', name: 'Input', color: t.input, total: props.totals.inputTokens },
    { key: 'outputTokens', name: 'Output', color: t.output, total: props.totals.outputTokens },
    { key: 'cacheWriteTokens', name: 'Cache write', color: t.cacheWrite, total: props.totals.cacheWriteTokens },
    { key: 'cacheReadTokens', name: 'Cache read', color: t.cacheRead, total: props.totals.cacheReadTokens },
  ]
  // Older checkpoints without a per-kind split
  if (props.totals.otherTokens > 0) list.push({ key: 'otherTokens', name: 'Unsplit', color: t.neutral, total: props.totals.otherTokens })
  return list
})

const empty = computed(() => props.totals.tokens === 0)

const option = computed<EChartsOption>(() => {
  const t = theme.value
  const series = kinds.value.map((k, i) => ({
    type: 'bar' as const,
    stack: 'tokens',
    barMaxWidth: 28,
    name: k.name,
    data: props.days.map((d) => d[k.key]),
    // 2px surface gap between stacked segments; round only the top of the stack
    itemStyle: { color: k.color, borderColor: t.surface, borderWidth: 1, borderRadius: i === kinds.value.length - 1 ? [4, 4, 0, 0] : 0 },
  }))
  return {
    grid: { left: 8, right: 8, top: 12, bottom: 4, containLabel: true },
    tooltip: {
      ...tooltip.value,
      trigger: 'axis',
      axisPointer: { type: 'shadow', shadowStyle: { color: t.splitLine, opacity: 0.6 } },
      formatter: (params: any) => {
        const d = props.days[params[0].dataIndex]!
        return `<b>${shortDayLabel(d.date)}</b>` + [...kinds.value].reverse()
          .map((k) => `<br/>${k.name}: <b>${d[k.key].toLocaleString()}</b>`).join('')
      },
    },
    xAxis: { ...xAxisBase.value, type: 'category', data: props.days.map((d) => shortDayLabel(d.date)) },
    yAxis: { ...yAxisBase.value, type: 'value', axisLabel: { ...yAxisBase.value.axisLabel, formatter: (v: number) => formatCompact(v) } },
    series,
  }
})

const table = computed(() => ({
  columns: ['Day', ...kinds.value.map((k) => `${k.name} tokens`)],
  rows: props.days.map((d) => [d.date, ...kinds.value.map((k) => d[k.key])]),
}))

const subtitle = computed(() => {
  const parts = ['Tokens per day by kind']
  if (props.totals.cacheReadShare != null) parts.push(`${formatPercent(props.totals.cacheReadShare)} served from cache`)
  if (props.totals.tokensPerLine != null) parts.push(`${formatCompact(props.totals.tokensPerLine)} tokens per changed line`)
  return parts.join(' · ')
})
</script>

<template>
  <ChartCard
    title="Token usage"
    :subtitle="subtitle"
    :summary="`Stacked bars of tokens per day by kind; ${totals.tokens} tokens in total.`"
    :table="table"
    :empty="empty"
    empty-text="No token usage recorded for this selection"
  >
    <div class="h-72">
      <VChart :option="option" autoresize />
    </div>
    <template #footer>
      <div class="flex flex-wrap gap-x-6 gap-y-1 text-sm">
        <span v-for="k in kinds" :key="k.key" class="inline-flex items-center gap-2">
          <span class="size-3 rounded-sm" :style="{ background: k.color }" />
          <span class="text-default">{{ k.name }}</span>
          <span class="text-muted tabular-nums">{{ formatCompact(k.total) }}</span>
        </span>
      </div>
    </template>
  </ChartCard>
</template>
