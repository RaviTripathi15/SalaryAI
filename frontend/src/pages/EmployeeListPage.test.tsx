import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { EmployeeApiError } from '../api/employeeApi'
import EmployeeListPage from './EmployeeListPage'
import {
  advanceDebounce,
  click,
  deferred,
  employeePage,
  pageText,
  renderEmployeeRoute,
  sampleEmployee,
  setInput,
  setSelect,
  settle,
} from '../test/employeeTestUtils'

const apiMocks = vi.hoisted(() => ({
  listEmployees: vi.fn(),
  getEmployeeStatusCounts: vi.fn(),
  deactivateEmployee: vi.fn(),
  getCurrentOrganization: vi.fn(),
  logoutOrganization: vi.fn(),
}))

vi.mock('../api/employeeApi', async () => {
  const actual = await vi.importActual<typeof import('../api/employeeApi')>(
    '../api/employeeApi',
  )

  return {
    ...actual,
    listEmployees: apiMocks.listEmployees,
    getEmployeeStatusCounts: apiMocks.getEmployeeStatusCounts,
    deactivateEmployee: apiMocks.deactivateEmployee,
  }
})

vi.mock('../api/organizationApi', async () => {
  const actual = await vi.importActual<typeof import('../api/organizationApi')>(
    '../api/organizationApi',
  )

  return {
    ...actual,
    getCurrentOrganization: apiMocks.getCurrentOrganization,
    logoutOrganization: apiMocks.logoutOrganization,
  }
})

function renderList() {
  return renderEmployeeRoute(
    <EmployeeListPage />,
    '/organization/employees',
    '/organization/employees',
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  apiMocks.getCurrentOrganization.mockResolvedValue({
    id: 7,
    name: 'Acme',
    email: 'acme@example.com',
    role: 'ORGANIZATION_ADMIN',
  })
  apiMocks.logoutOrganization.mockResolvedValue(undefined)
  apiMocks.listEmployees.mockResolvedValue(employeePage())
  apiMocks.getEmployeeStatusCounts.mockResolvedValue({
    total: 1,
    active: 1,
    inactive: 0,
  })
  apiMocks.deactivateEmployee.mockResolvedValue({
    ...sampleEmployee,
    employmentStatus: 'INACTIVE',
  })
})

afterEach(() => {
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

describe('EmployeeListPage', () => {
  it('shows a loading state while the employee page is pending', async () => {
    const pending = deferred<ReturnType<typeof employeePage>>()
    apiMocks.listEmployees.mockReturnValueOnce(pending.promise)

    const view = renderList()
    await settle()

    expect(pageText(view.container)).toContain('Loading employees...')
    expect(apiMocks.deactivateEmployee).not.toHaveBeenCalled()

    view.unmount()
    pending.resolve(employeePage())
  })

  it('renders employees, accurate counts, and empty state correctly', async () => {
    apiMocks.listEmployees.mockResolvedValue(
      employeePage({ content: [], totalElements: 0, totalPages: 0 }),
    )
    apiMocks.getEmployeeStatusCounts.mockResolvedValue({
      total: 0,
      active: 0,
      inactive: 0,
    })

    const view = renderList()
    await settle()

    expect(pageText(view.container)).toContain('No employees yet')
    expect(pageText(view.container)).toContain('Total employees')
    expect(apiMocks.listEmployees).toHaveBeenCalledWith(
      expect.objectContaining({
        page: 0,
        size: 20,
        sortBy: 'employeeCode',
        sortDirection: 'asc',
        search: '',
        status: undefined,
      }),
      expect.any(AbortSignal),
    )

    view.unmount()
  })

  it('renders employee rows and pagination from the backend page', async () => {
    apiMocks.listEmployees.mockResolvedValue(
      employeePage({
        totalElements: 30,
        totalPages: 2,
        first: true,
        last: false,
      }),
    )

    const view = renderList()
    await settle()

    expect(pageText(view.container)).toContain('Ada Lovelace')
    expect(pageText(view.container)).toContain('EMP-001')
    expect(pageText(view.container)).toContain('Showing 1–20 of 30 employees')
    expect(pageText(view.container)).toContain('Page 1 of 2')

    view.unmount()
  })

  it('applies debounced search, status filtering, sorting, and pagination', async () => {
    apiMocks.listEmployees.mockResolvedValue(
      employeePage({ totalElements: 30, totalPages: 2, last: false }),
    )

    const view = renderList()
    await settle()

    setSelect(view.container, 'employee-status-filter', 'ACTIVE')
    await settle()
    expect(apiMocks.listEmployees).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'ACTIVE', page: 0 }),
      expect.any(AbortSignal),
    )

    setInput(view.container, 'employee-search', 'ada')
    await advanceDebounce()
    expect(apiMocks.listEmployees).toHaveBeenLastCalledWith(
      expect.objectContaining({ search: 'ada', status: 'ACTIVE', page: 0 }),
      expect.any(AbortSignal),
    )

    setSelect(view.container, 'employee-sort-direction', 'desc')
    await settle()
    expect(apiMocks.listEmployees).toHaveBeenLastCalledWith(
      expect.objectContaining({ sortDirection: 'desc', page: 0 }),
      expect.any(AbortSignal),
    )

    click(view.container, '.employee-pagination button:last-child')
    await settle()
    expect(apiMocks.listEmployees).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, sortDirection: 'desc' }),
      expect.any(AbortSignal),
    )

    view.unmount()
  })

  it('requires confirmation before deactivation and refreshes after success', async () => {
    const view = renderList()
    await settle()

    click(view.container, '[aria-label="Deactivate Ada Lovelace"]')
    expect(pageText(view.container)).toContain('Deactivate employee?')
    expect(pageText(view.container)).toContain('history is not permanently deleted')

    click(view.container, '.employee-dialog .employee-danger-button')
    await settle()

    expect(apiMocks.deactivateEmployee).toHaveBeenCalledWith(11)
    expect(pageText(view.container)).toContain('EMP-001 was deactivated.')
    expect(apiMocks.listEmployees).toHaveBeenCalledTimes(2)
    expect(apiMocks.getEmployeeStatusCounts).toHaveBeenCalledTimes(2)

    view.unmount()
  })

  it('redirects to sign-in on 401 and shows a safe message on 403', async () => {
    apiMocks.listEmployees.mockRejectedValueOnce(
      new EmployeeApiError(
        401,
        'Your organization session is not active. Please sign in again.',
      ),
    )

    const unauthorizedView = renderList()
    await settle()
    expect(pageText(unauthorizedView.container)).toContain('Sign in page')
    unauthorizedView.unmount()

    apiMocks.listEmployees.mockRejectedValueOnce(
      new EmployeeApiError(
        403,
        'This account is not authorized for the organization workspace.',
      ),
    )

    const forbiddenView = renderList()
    await settle()
    expect(pageText(forbiddenView.container)).toContain(
      'This account is not authorized for the organization workspace.',
    )
    expect(pageText(forbiddenView.container)).not.toContain('Ada Lovelace')
    expect(forbiddenView.container.querySelector('.employee-table')).toBeNull()

    forbiddenView.unmount()
  })

  it('ignores an in-flight list response after an authorization loss', async () => {
    const pending = deferred<ReturnType<typeof employeePage>>()
    apiMocks.listEmployees.mockReturnValueOnce(pending.promise)
    apiMocks.getEmployeeStatusCounts.mockRejectedValueOnce(
      new EmployeeApiError(
        403,
        'This account is not authorized for the organization workspace.',
      ),
    )

    const view = renderList()
    await settle()

    pending.resolve(employeePage())
    await settle()

    expect(pageText(view.container)).toContain(
      'This account is not authorized for the organization workspace.',
    )
    expect(pageText(view.container)).not.toContain('Ada Lovelace')
    expect(view.container.querySelector('.employee-table')).toBeNull()

    view.unmount()
  })

  it('does not expose backend details on list API failure', async () => {
    apiMocks.listEmployees.mockRejectedValueOnce(
      new Error('POST http://localhost:8080/api/v1/organizations/employees 500'),
    )

    const view = renderList()
    await settle()

    expect(pageText(view.container)).toContain(
      'Unable to load employees. Please try again.',
    )
    expect(pageText(view.container)).not.toContain('localhost:8080')

    view.unmount()
  })
})
