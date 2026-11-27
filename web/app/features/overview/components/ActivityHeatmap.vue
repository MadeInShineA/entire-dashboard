<script setup lang="ts">
/**
 * Punch card: checkpoints per weekday x hour of day, one-hue sequential ramp.
 * Answers "when do we work with AI" without the overplotting of a raw scatter.
 */
import type { EChartsOption } from 'echarts'
import ChartCard from '~/shared/components/ChartCard.vue'
import { VChart } from '~/shared/utils/echarts'
import { punchCard } from '../utils/aggregate'
import type { AnalyticsRow } from '../types/analytics.types'

const props = defineProps<{ rows: AnalyticsRow[] }>()

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
const HOURS = Array.from({ length: 24 }, (_, h) => `${String(h).padStart(2, '0')}:00`)

const { theme, tooltip, xAxisBase } = useChartTheme()

const grid = computed(() => punchCard(props.rows))
const max = computed(() => Math.max(1, ...grid.value.flat()))
const busiest = computed(() => {
  let best = { d: 0, h: 0, v: -1 }
  grid.value.forEach((row, d) => row.forEach((v, h) => { if (v > best.v) best = { d, h, v } }))
  return best.v > 0 ? `${DAYS[best.d]} ${HOURS[best.h]}` : null
})

const option = computed<EChartsOption>(() => {
  const t = theme.value
  const data: [number, number, number][] = []
  const emptyCells: [number, number, number][] = []
  grid.value.forEach((row, d) => row.forEach((v, h) => (v > 0 ? data : emptyCells).push([h, d, v])))
  return {
    grid: { left: 8, right: 8, top: 4, bottom: 44, containLabel: true },
    tooltip: {
      ...tooltip.value,
      trigger: 'item',
      formatter: (p: any) => `<b>${DAYS[p.value[1]]} ${HOURS[p.value[0]]}</b><br/>${p.value[2]} checkpoint${p.value[2] === 1 ? '' : 's'}`,
    },
    xAxis: {
      ...xAxisBase.value,
      type: 'category',
      data: HOURS,
      axisLine: { show: false },
      axisLabel: { ...xAxisBase.value.axisLabel, interval: 2 },
    },
    yAxis: {
      ...xAxisBase.value,
      type: 'category',
      data: DAYS,
      inverse: true,
      axisLine: { show: false },
    },
    visualMap: [{
      min: 1,
      max: max.value,
      calculable: false,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      itemHeight: 160,
      itemWidth: 10,
      text: [String(max.value), '1'],
      textStyle: { color: t.subText, fontSize: 11 },
      inRange: { color: t.sequential },
      // Only the counted cells are color-mapped; empty cells keep a faint neutral
      seriesIndex: 1,
    }, {
      // Empty cells: one faint neutral, no legend
      show: false,
      seriesIndex: 0,
      min: 0,
      max: 1,
      inRange: { color: [t.splitLine, t.splitLine] },
    }],
    series: [{
      type: 'heatmap',
      data: emptyCells,
      silent: true,
      itemStyle: { color: t.splitLine, borderColor: t.surface, borderWidth: 2, borderRadius: 3 },
    }, {
      type: 'heatmap',
      data,
      itemStyle: { borderColor: t.surface, borderWidth: 2, borderRadius: 3 },
      emphasis: { itemStyle: { borderColor: t.text, borderWidth: 1 } },
    }],
  }
})

const table = computed(() => ({
  columns: ['Day', ...HOURS],
  rows: grid.value.map((row, d) => [DAYS[d]!, ...row]),
}))
</script>

<template>
  <ChartCard
    title="When checkpoints happen"
    :subtitle="busiest ? `Checkpoints by weekday and hour (your timezone) · busiest: ${busiest}` : 'Checkpoints by weekday and hour (your timezone)'"
    :summary="`Heatmap of checkpoint counts by weekday and hour of day.${busiest ? ' The busiest slot is ' + busiest + '.' : ''}`"
    :table="table"
    :empty="!rows.length"
  >
    <div class="h-72">
      <VChart :option="option" autoresize />
    </div>
  </ChartCard>
</template>
