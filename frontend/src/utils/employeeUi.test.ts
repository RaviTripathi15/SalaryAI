import { describe, expect, it } from 'vitest'
import { EmployeeApiError } from '../api/employeeApi'
import {
  employeeErrorMessage,
  flashMessage,
  formattingDate,
  hasStatus,
  isAbortError,
} from './employeeUi'

describe('employeeUi', () => {
  it('formats date-only values without a timezone shift', () => {
    expect(formattingDate('2024-01-15')).toBe(
      new Date(2024, 0, 15).toLocaleDateString(),
    )
    expect(formattingDate(null)).toBe('—')
  })

  it('uses only safe API messages and fixed fallbacks', () => {
    const apiError = new EmployeeApiError(409, 'The employee record changed.')
    expect(employeeErrorMessage(apiError, 'Fallback')).toBe(
      'The employee record changed.',
    )
    expect(
      employeeErrorMessage(new Error('jdbc:postgresql://internal'), 'Fallback'),
    ).toBe('Fallback')
    expect(hasStatus(apiError, 409)).toBe(true)
    expect(hasStatus(apiError, 400)).toBe(false)
    expect(isAbortError(new DOMException('Aborted', 'AbortError'))).toBe(true)
  })

  it('reads success notifications only from structured router state', () => {
    expect(flashMessage({ success: 'Saved.' })).toBe('Saved.')
    expect(flashMessage({ success: 42 })).toBe('')
    expect(flashMessage(null)).toBe('')
  })
})
