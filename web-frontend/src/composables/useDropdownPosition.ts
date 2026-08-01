/**
 * useDropdownPosition — pure geometry for anchored popup menus.
 *
 * AppSelect teleports its listbox to <body> to escape `overflow` clipping, so
 * the menu must be `position: fixed`. These helpers translate the trigger's
 * `getBoundingClientRect()` into fixed coordinates, clamped to the viewport.
 *
 * Keeping the math in plain functions (no DOM reads) makes it unit-testable.
 */

export interface DropdownPlacement {
  /** Fixed `top` in CSS px. */
  top: number
  /** Fixed `left` in CSS px. */
  left: number
  /** Trigger width in CSS px (menu min-width, never narrower than the trigger). */
  minWidth: number
}

export interface Box {
  x: number
  y: number
  width: number
  height: number
}

export interface ViewportBox {
  width: number
  height: number
}

/** Vertical gap between the trigger's bottom edge and the menu's top edge. */
export const DROPDOWN_GAP = 4

/**
 * Compute a fixed-position placement for the menu under the trigger.
 *
 * - Horizontally: left-aligned with the trigger; clamped so the menu never
 *   overflows the right viewport edge (aligns to the trigger's right edge
 *   instead of scrolling off-screen).
 * - Vertically: below the trigger; when the menu is taller than the space
 *   below, it opens above the trigger instead.
 */
export function computeDropdownPosition(
  trigger: Box,
  menu: { width: number; height: number },
  viewport: ViewportBox,
): DropdownPlacement {
  const minWidth = Math.max(0, trigger.width)
  const menuWidth = Math.max(menu.width, minWidth)

  const rightOverflow = trigger.x + menuWidth - viewport.width
  const left = rightOverflow > 0 ? Math.max(0, trigger.x - rightOverflow) : Math.max(0, trigger.x)

  const below = trigger.y + trigger.height + DROPDOWN_GAP
  const spaceBelow = viewport.height - below
  if (spaceBelow >= menu.height || below > viewport.height) {
    return { top: below, left, minWidth }
  }
  return {
    top: Math.max(0, trigger.y - DROPDOWN_GAP - menu.height),
    left,
    minWidth,
  }
}

/**
 * Close-on-outside-click helper: returns true when the event target is
 * outside the given element (and not inside a teleported popup).
 */
export function isClickOutside(element: Element | null, target: unknown): boolean {
  if (!element || !(target instanceof Node)) return true
  return !element.contains(target)
}
