import {
  OrganizationApiError,
  getCurrentOrganization,
} from '../api/organizationApi'
import { EmployeeApiError } from '../api/employeeApi'

export function isAbortError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'name' in error &&
    error.name === 'AbortError'
  )
}

export function employeeErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof EmployeeApiError && error.message.trim().length > 0) {
    return error.message
  }

  return fallback
}

export function hasStatus(error: unknown, status: number): boolean {
  return error instanceof EmployeeApiError && error.status === status
}

export async function reverifyEmployeeSession(onInvalid: () => void) {
  try {
    await getCurrentOrganization()
    return true
  } catch (error) {
    if (
      error instanceof OrganizationApiError &&
      (error.status === 401 || error.status === 403)
    ) {
      onInvalid()
      return false
    }

    return true
  }
}

export function flashMessage(locationState: unknown): string {
  if (typeof locationState !== 'object' || locationState === null) return ''

  const candidate = locationState as { success?: unknown }
  return typeof candidate.success === 'string' ? candidate.success : ''
}

export function formattingDate(value: string | null): string {
  if (!value) return '—'

  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split('-').map(Number)
    const localDate = new Date(year, month - 1, day)
    if (!Number.isNaN(localDate.getTime())) {
      return localDate.toLocaleDateString()
    }
  }

  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value

  return parsed.toLocaleString()
}
