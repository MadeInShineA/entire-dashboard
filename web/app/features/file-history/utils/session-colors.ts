/**
 * Fixed-order categorical palette for sessions, declared as --session-N in assets/css/main.css
 * (validated for the white and slate-900 surfaces). Never cycled: sessions beyond slot 8
 * share the neutral --session-other and rely on their text label.
 */
export const SESSION_SLOT_COUNT = 8

/** CSS color for a 0-based slot; transparent when the line has no session */
export function sessionColorVar(slot: number | undefined): string {
  if (slot == null || slot < 0) return 'transparent'
  return slot < SESSION_SLOT_COUNT ? `var(--session-${slot + 1})` : 'var(--session-other)'
}
