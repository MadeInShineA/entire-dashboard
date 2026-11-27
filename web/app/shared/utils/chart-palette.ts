/**
 * Chart palette shared by every chart (ECharts draws on canvas, so it needs hex values, not CSS vars).
 *
 * The categorical slots are the same validated palette as --session-N in assets/css/main.css
 * (light surface #ffffff, dark surface slate-900 #0f172a). Slots are assigned in fixed order,
 * never cycled: anything past slot 8 folds into the neutral "other".
 */
export type ChartMode = 'light' | 'dark'

export const CATEGORICAL: Record<ChartMode, string[]> = {
  light: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948'],
  dark: ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300', '#9085e9', '#e66767'],
}
export const OTHER: Record<ChartMode, string> = { light: '#94a3b8', dark: '#64748b' }

/** Single-hue sequential ramp (blue), near-zero first. Dark mode uses its own steps so low values recede into the dark surface. */
export const SEQUENTIAL: Record<ChartMode, string[]> = {
  light: ['#cde2fb', '#9ec5f4', '#6da7ec', '#3987e5', '#256abf', '#184f95', '#0d366b'],
  dark: ['#104281', '#184f95', '#256abf', '#2a78d6', '#5598e7', '#86b6ef', '#cde2fb'],
}

export interface ChartTheme {
  mode: ChartMode
  surface: string
  text: string
  subText: string
  axisLine: string
  splitLine: string
  tooltipBg: string
  tooltipBorder: string
  /** Single-series marks (one color for every bar), sparklines */
  accent: string
  /** Neutral for "Other" / unsplit values */
  neutral: string
  /** Lines added / deleted: two opposite hues (warm/cool), not good/bad status colors */
  additions: string
  deletions: string
  /** Token kinds, stacked in palette order */
  input: string
  output: string
  cacheWrite: string
  cacheRead: string
  sequential: string[]
}

export function chartTheme(mode: ChartMode): ChartTheme {
  const dark = mode === 'dark'
  return {
    mode,
    surface: dark ? '#0f172a' : '#ffffff',
    text: dark ? '#e2e8f0' : '#334155',
    subText: dark ? '#94a3b8' : '#64748b',
    axisLine: dark ? '#334155' : '#e2e8f0',
    splitLine: dark ? '#1e293b' : '#f1f5f9',
    tooltipBg: dark ? '#1e293b' : '#ffffff',
    tooltipBorder: dark ? '#334155' : '#e2e8f0',
    accent: CATEGORICAL[mode][0]!,
    neutral: OTHER[mode],
    additions: CATEGORICAL[mode][0]!,
    deletions: CATEGORICAL[mode][1]!,
    input: CATEGORICAL[mode][0]!,
    output: CATEGORICAL[mode][1]!,
    cacheWrite: CATEGORICAL[mode][2]!,
    cacheRead: CATEGORICAL[mode][3]!,
    sequential: SEQUENTIAL[mode],
  }
}

// ---- Agents ----------------------------------------------------------------

/** ECharts symbol per agent: a second channel so agent identity never relies on color alone */
export type AgentSymbol = 'circle' | 'rect' | 'triangle' | 'diamond' | 'pin' | 'arrow' | 'roundRect'

interface AgentStyle {
  /** 0-based categorical slot, or -1 for "other" */
  slot: number
  symbol: AgentSymbol
}

/** Fixed agent -> slot/symbol table (matched by lowercase substring), so an agent keeps its look whatever is filtered */
const AGENT_STYLES: [match: string, style: AgentStyle][] = [
  ['claude', { slot: 1, symbol: 'circle' }],
  ['codex', { slot: 2, symbol: 'rect' }],
  ['gemini', { slot: 0, symbol: 'triangle' }],
  ['copilot', { slot: 3, symbol: 'diamond' }],
  ['opencode', { slot: 4, symbol: 'pin' }],
  ['pi', { slot: 5, symbol: 'arrow' }],
  ['cursor', { slot: 6, symbol: 'roundRect' }],
  ['droid', { slot: 7, symbol: 'triangle' }],
]

function agentStyle(agent?: string | null): AgentStyle {
  const name = (agent || '').toLowerCase()
  if (name === 'pi') return AGENT_STYLES.find(([m]) => m === 'pi')![1]
  for (const [match, style] of AGENT_STYLES) {
    if (match !== 'pi' && name.includes(match)) return style
  }
  return { slot: -1, symbol: 'circle' }
}

export function agentColor(agent: string | null | undefined, mode: ChartMode): string {
  const { slot } = agentStyle(agent)
  return slot >= 0 ? CATEGORICAL[mode][slot]! : OTHER[mode]
}

export function agentSymbol(agent: string | null | undefined): AgentSymbol {
  return agentStyle(agent).symbol
}

/** Inline SVG path (viewBox 0 0 12 12) for legends drawn in HTML, mirroring the ECharts symbol */
export function agentSymbolSvg(agent: string | null | undefined): string {
  switch (agentSymbol(agent)) {
    case 'rect': return 'M2 2h8v8H2z'
    case 'roundRect': return 'M4 2h4a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z'
    case 'triangle': return 'M6 1.5l5 9H1z'
    case 'diamond': return 'M6 1l5 5-5 5-5-5z'
    case 'pin': return 'M6 1a4 4 0 0 1 4 4c0 3-4 6.5-4 6.5S2 8 2 5a4 4 0 0 1 4-4z'
    case 'arrow': return 'M6 1l4.5 10L6 8.5 1.5 11z'
    default: return 'M6 1.5a4.5 4.5 0 1 1 0 9a4.5 4.5 0 1 1 0-9z'
  }
}
