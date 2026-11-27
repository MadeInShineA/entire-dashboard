/**
 * Overview state: date range + cross-filters, both mirrored in the URL so a view can be shared
 * and survives a reload. One request fetches the selected period and the one before it; every
 * chart is then derived client-side from the same rows, so filters apply to the whole page at once.
 */
import { overviewApi } from '../api/overview.api'
import { applyFilters } from '../utils/aggregate'
import type { AnalyticsRow, FilterDim, OverviewFilters, RangePreset } from '../types/analytics.types'
import { formatDate, getDateRange } from '~/shared/utils/date'

const PRESET_DAYS: Record<Exclude<RangePreset, 'custom'>, number> = { '7d': 7, '14d': 14, '30d': 30, '90d': 90 }
export const MAX_RANGE_DAYS = 90
const DAY_MS = 24 * 60 * 60 * 1000
const DIMS: FilterDim[] = ['repo', 'author', 'agent', 'model']

function startOfDay(d: Date): number {
  const x = new Date(d)
  x.setHours(0, 0, 0, 0)
  return x.getTime()
}
function endOfDay(d: Date): number {
  const x = new Date(d)
  x.setHours(23, 59, 59, 999)
  return x.getTime()
}
/** YYYY-MM-DD as a local date (new Date('YYYY-MM-DD') would be UTC) */
function parseLocalDate(s: string): Date | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s)
  return m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : null
}
function isPreset(v: unknown): v is RangePreset {
  return v === 'custom' || (typeof v === 'string' && v in PRESET_DAYS)
}

export function useOverviewAnalytics() {
  const route = useRoute()
  const router = useRouter()

  // ---- state from URL ----------------------------------------------------------
  const q = route.query
  const preset = ref<RangePreset>(isPreset(q.range) ? q.range : '14d')
  const customFrom = ref<string>(typeof q.from === 'string' ? q.from : formatDate(new Date(Date.now() - 13 * DAY_MS)))
  const customTo = ref<string>(typeof q.to === 'string' ? q.to : formatDate(new Date()))
  const filters = reactive<OverviewFilters>({
    repo: typeof q.repo === 'string' ? q.repo : null,
    author: typeof q.author === 'string' ? q.author : null,
    agent: typeof q.agent === 'string' ? q.agent : null,
    model: typeof q.model === 'string' ? q.model : null,
  })

  watch([preset, customFrom, customTo, () => ({ ...filters })], () => {
    const query: Record<string, string> = { range: preset.value }
    if (preset.value === 'custom') {
      query.from = customFrom.value
      query.to = customTo.value
    }
    for (const d of DIMS) if (filters[d] != null) query[d] = filters[d]!
    if (JSON.stringify(query) !== JSON.stringify(route.query)) router.replace({ query })
  }, { deep: true })

  // ---- range -------------------------------------------------------------------
  type Span = { start: number; end: number }
  const rangeResult = computed<{ span: Span | null; error: string | null }>(() => {
    if (preset.value !== 'custom') {
      const today = new Date()
      const start = new Date(today)
      start.setDate(start.getDate() - (PRESET_DAYS[preset.value] - 1))
      return { span: { start: startOfDay(start), end: endOfDay(today) }, error: null }
    }
    const from = parseLocalDate(customFrom.value)
    const to = parseLocalDate(customTo.value)
    if (!from || !to) {
      return { span: null, error: 'Pick a start and an end date' }
    }
    if (from > to) {
      return { span: null, error: 'The start date must be before the end date' }
    }
    const start = startOfDay(from)
    const end = endOfDay(to)
    if (end - start > MAX_RANGE_DAYS * DAY_MS) {
      return { span: null, error: `The range can be at most ${MAX_RANGE_DAYS} days` }
    }
    return { span: { start, end }, error: null }
  })
  const range = computed(() => rangeResult.value.span)
  const rangeError = computed(() => rangeResult.value.error)
  /** The period just before, same length, for comparisons */
  const previousRange = computed(() => {
    const r = range.value
    if (!r) return null
    const len = r.end - r.start + 1
    return { start: r.start - len, end: r.start - 1 }
  })
  const dates = computed(() => (range.value ? getDateRange(range.value.start, range.value.end) : []))

  // ---- data --------------------------------------------------------------------
  const rows = ref<AnalyticsRow[]>([])
  const truncated = ref(false)
  const pending = ref(false)
  const loaded = ref(false)
  const error = ref<string | null>(null)
  let seq = 0

  async function fetchRows() {
    const r = range.value
    const p = previousRange.value
    if (!r || !p) return
    const mySeq = ++seq
    pending.value = true
    error.value = null
    try {
      const res = await overviewApi.getAnalytics({ startTime: p.start, endTime: r.end })
      if (mySeq !== seq) return
      rows.value = res.rows ?? []
      truncated.value = !!res.truncated
      loaded.value = true
    } catch (e: any) {
      if (mySeq === seq) error.value = e?.data?.message || e?.message || 'Failed to load analytics'
    } finally {
      if (mySeq === seq) pending.value = false
    }
  }
  watch(range, fetchRows, { immediate: true, deep: true })

  const inRange = (r: AnalyticsRow, span: { start: number; end: number } | null) =>
    !!span && r.commitTime >= span.start && r.commitTime <= span.end

  /** Current period, no filters: the source for filter options */
  const periodRows = computed(() => rows.value.filter((r) => inRange(r, range.value)))
  const previousPeriodRows = computed(() => rows.value.filter((r) => inRange(r, previousRange.value)))
  /** Current period with every filter */
  const current = computed(() => applyFilters(periodRows.value, filters))
  const previous = computed(() => applyFilters(previousPeriodRows.value, filters))
  /** Current period with every filter except one dimension's own (for that dimension's chart / legend) */
  function currentExcept(dim: FilterDim) {
    return applyFilters(periodRows.value, filters, dim)
  }

  // ---- actions -----------------------------------------------------------------
  function setPreset(p: RangePreset) {
    preset.value = p
  }
  /** Set a filter; selecting the active value again clears it */
  function toggleFilter(dim: FilterDim, value: string | null) {
    filters[dim] = value == null || filters[dim] === value ? null : value
  }
  function clearFilters() {
    for (const d of DIMS) filters[d] = null
  }
  const hasFilters = computed(() => DIMS.some((d) => filters[d] != null))

  return {
    preset,
    customFrom,
    customTo,
    setPreset,
    range,
    rangeError,
    dates,
    filters,
    toggleFilter,
    clearFilters,
    hasFilters,
    periodRows,
    current,
    previous,
    currentExcept,
    truncated,
    pending,
    loaded,
    error,
    refresh: fetchRows,
  }
}
