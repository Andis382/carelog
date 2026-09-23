import { describe, expect, it } from 'vitest'
import { formatPhone, initials, parseMoney, centsToInput } from '../format'

describe('format helpers', () => {
  it('groups Albanian mobile numbers', () => {
    expect(formatPhone('355691234567')).toBe('+355 69 123 4567')
  })

  it('reads money the way people type it', () => {
    expect(parseMoney('12,50')).toBe(1250)
    expect(parseMoney('12.5')).toBe(1250)
    expect(parseMoney('')).toBeNull()
    expect(parseMoney('abc')).toBeNull()
  })

  it('round-trips cents to an input value', () => {
    expect(centsToInput(1250)).toBe('12.50')
    expect(centsToInput(1200)).toBe('12')
  })

  it('makes initials', () => {
    expect(initials('Arben Hoxha')).toBe('AH')
    expect(initials('Mira')).toBe('M')
  })
})
