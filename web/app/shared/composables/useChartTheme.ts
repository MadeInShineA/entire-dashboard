/**
 * Reactive chart theme following the color mode, plus the ECharts pieces every chart shares
 * (recessive axes, hairline solid grid, themed tooltip).
 */
import { chartTheme, type ChartTheme } from '~/shared/utils/chart-palette'

export function useChartTheme() {
  const colorMode = useColorMode()
  const theme = computed<ChartTheme>(() => chartTheme(colorMode.value === 'dark' ? 'dark' : 'light'))

  const tooltip = computed(() => ({
    backgroundColor: theme.value.tooltipBg,
    borderColor: theme.value.tooltipBorder,
    borderWidth: 1,
    padding: [8, 10],
    textStyle: { color: theme.value.text, fontSize: 12 },
    extraCssText: 'box-shadow: 0 4px 12px rgb(0 0 0 / 0.12); border-radius: 6px;',
  }))

  /** Category/time axis along the bottom */
  const xAxisBase = computed(() => ({
    axisLine: { lineStyle: { color: theme.value.axisLine } },
    axisTick: { show: false },
    axisLabel: { color: theme.value.subText, fontSize: 11, hideOverlap: true },
    splitLine: { show: false },
  }))

  /** Value axis: no line, solid hairline grid */
  const yAxisBase = computed(() => ({
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: theme.value.subText, fontSize: 11 },
    splitLine: { lineStyle: { color: theme.value.splitLine, type: 'solid' as const } },
    nameTextStyle: { color: theme.value.subText, fontSize: 11 },
  }))

  return { theme, tooltip, xAxisBase, yAxisBase }
}
