import { describe, it, expect } from 'vitest'
import {
  computeDropdownPosition,
  isClickOutside,
  DROPDOWN_GAP,
} from '@/composables/useDropdownPosition'

describe('computeDropdownPosition', () => {
  const viewport = { width: 1000, height: 800 }
  const trigger = { x: 100, y: 200, width: 200, height: 48 }

  it('opens below the trigger, left-aligned, with trigger min-width', () => {
    const result = computeDropdownPosition(trigger, { width: 120, height: 150 }, viewport)
    expect(result.top).toBe(200 + 48 + DROPDOWN_GAP)
    expect(result.left).toBe(100)
    expect(result.minWidth).toBe(200)
  })

  it('clamps the menu into the right viewport edge', () => {
    const nearRight = { x: 900, y: 200, width: 200, height: 48 }
    const result = computeDropdownPosition(nearRight, { width: 500, height: 150 }, viewport)
    expect(result.left).toBe(900 - 400) // 900 + 500 = 1400 → overflow 400
    expect(result.top).toBe(200 + 48 + DROPDOWN_GAP)
  })

  it('opens above the trigger when there is no room below', () => {
    const low = { x: 100, y: 700, width: 200, height: 48 }
    const result = computeDropdownPosition(low, { width: 200, height: 200 }, viewport)
    expect(result.top).toBe(700 - DROPDOWN_GAP - 200)
    expect(result.left).toBe(100)
  })

  it('never returns negative coordinates', () => {
    const topLeft = { x: 0, y: 0, width: 10, height: 10 }
    const above = computeDropdownPosition(topLeft, { width: 500, height: 500 }, { width: 400, height: 300 })
    expect(above.top).toBeGreaterThanOrEqual(0)
    expect(above.left).toBeGreaterThanOrEqual(0)
  })

  it('handles zero-size rects without NaN', () => {
    const result = computeDropdownPosition(
      { x: 0, y: 0, width: 0, height: 0 },
      { width: 0, height: 0 },
      { width: 0, height: 0 },
    )
    expect(Number.isNaN(result.top)).toBe(false)
    expect(Number.isNaN(result.left)).toBe(false)
    expect(result.minWidth).toBe(0)
  })
})

describe('isClickOutside', () => {
  it('returns true when there is no reference element', () => {
    expect(isClickOutside(null, document.createElement('div'))).toBe(true)
  })

  it('returns true for a target outside the element', () => {
    const element = document.createElement('div')
    document.body.appendChild(element)
    const outside = document.createElement('span')
    document.body.appendChild(outside)
    expect(isClickOutside(element, outside)).toBe(true)
    element.remove()
    outside.remove()
  })

  it('returns false for a target inside the element', () => {
    const element = document.createElement('div')
    const inside = document.createElement('span')
    element.appendChild(inside)
    document.body.appendChild(element)
    expect(isClickOutside(element, inside)).toBe(false)
    element.remove()
  })

  it('returns true for a non-Node target', () => {
    const element = document.createElement('div')
    expect(isClickOutside(element, 'foo')).toBe(true)
  })
})
