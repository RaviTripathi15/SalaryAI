import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { EmployeeApiError } from '../api/employeeApi'
import EmployeeDetailsPage from './EmployeeDetailsPage'
import {
  click,
  deferred,
  pageText,
  renderEmployeeRoute,
  sampleEmployee,
  settle,
} from '../test/employeeTestUtils'

const apiMocks = vi.hoisted(() => ({
  getEmployee: vi.fn(),
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
    getEmployee: apiMocks.getEmployee,
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

function renderDetails(path: string | { pathname: string; state?: unknown } = '/organization/employees/11') {
  return renderEmployeeRoute(
    <EmployeeDetailsPage />,
    path,
    '/organization/employees/:id',
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
  apiMocks.getEmployee.mockResolvedValue(sampleEmployee)
  apiMocks.deactivateEmployee.mockResolvedValue({
    ...sampleEmployee,
    employmentStatus: 'INACTIVE',
    deactivatedAt: '2024-02-01T10:00:00',
  })
})

afterEach(() => {
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

describe('EmployeeDetailsPage', () => {
  it('shows a loading state while details are pending', async () => {
    const pending = deferred<typeof sampleEmployee>()
    apiMocks.getEmployee.mockReturnValueOnce(pending.promise)

    const view = renderDetails()
    await settle()

    expect(pageText(view.container)).toContain('Loading employee...')

    view.unmount()
    pending.resolve(sampleEmployee)
  })

  it('renders only safe employee fields and displays a success notification', async () => {
    const view = renderDetails({
      pathname: '/organization/employees/11',
      state: { success: 'Employee updated successfully.' },
    })
    await settle()

    expect(apiMocks.getEmployee).toHaveBeenCalledWith(11, expect.any(AbortSignal))
    const text = pageText(view.container)
    expect(text).toContain('Ada Lovelace')
    expect(text).toContain('ada@example.com')
    expect(text).toContain('Engineering')
    expect(text).toContain('Employee updated successfully.')
    expect(text).not.toContain('organizationId')
    expect(text).not.toContain('password')
    expect(text).not.toContain('SECRET')

    view.unmount()
  })

  it('handles invalid IDs, not-found records, and safe loading failures', async () => {
    const invalidView = renderDetails('/organization/employees/not-a-number')
    await settle()
    expect(pageText(invalidView.container)).toContain(
      'This employee link is invalid.',
    )
    expect(apiMocks.getEmployee).not.toHaveBeenCalled()
    invalidView.unmount()

    apiMocks.getEmployee.mockRejectedValueOnce(
      new EmployeeApiError(404, 'Employee not found.'),
    )
    const missingView = renderDetails()
    await settle()
    expect(pageText(missingView.container)).toContain('Employee not found.')
    missingView.unmount()

    apiMocks.getEmployee.mockRejectedValueOnce(
      new Error('jdbc:postgresql://internal-db/salaryai'),
    )
    const failedView = renderDetails()
    await settle()
    expect(pageText(failedView.container)).toContain(
      'Unable to load the employee. Please try again.',
    )
    expect(pageText(failedView.container)).not.toContain('internal-db')
    failedView.unmount()
  })

  it('redirects on 401 and shows a safe message on 403', async () => {
    apiMocks.getEmployee.mockRejectedValueOnce(
      new EmployeeApiError(
        401,
        'Your organization session is not active. Please sign in again.',
      ),
    )

    const unauthorizedView = renderDetails()
    await settle()
    expect(pageText(unauthorizedView.container)).toContain('Sign in page')
    unauthorizedView.unmount()

    apiMocks.getEmployee.mockRejectedValueOnce(
      new EmployeeApiError(
        403,
        'This account is not authorized for the organization workspace.',
      ),
    )

    const forbiddenView = renderDetails()
    await settle()
    expect(pageText(forbiddenView.container)).toContain(
      'This account is not authorized for the organization workspace.',
    )
    expect(forbiddenView.container.querySelector('.employee-detail-card')).toBeNull()

    forbiddenView.unmount()
  })

  it('clears employee details when deactivation reports 403', async () => {
    apiMocks.deactivateEmployee.mockRejectedValueOnce(
      new EmployeeApiError(
        403,
        'This account is not authorized for the organization workspace.',
      ),
    )

    const view = renderDetails()
    await settle()

    click(view.container, '.employee-detail-actions .employee-danger-button')
    click(view.container, '.employee-dialog .employee-danger-button')
    await settle()

    expect(pageText(view.container)).toContain(
      'This account is not authorized for the organization workspace.',
    )
    expect(pageText(view.container)).not.toContain('Ada Lovelace')
    expect(view.container.querySelector('.employee-detail-card')).toBeNull()

    view.unmount()
  })

  it('requires confirmation for deactivation and refreshes displayed status', async () => {
    const view = renderDetails()
    await settle()

    click(view.container, '.employee-detail-actions .employee-danger-button')
    expect(pageText(view.container)).toContain('Deactivate this employee?')
    expect(pageText(view.container)).toContain(
      'does not permanently delete employee history',
    )

    click(view.container, '.employee-dialog .employee-danger-button')
    await settle()

    expect(apiMocks.deactivateEmployee).toHaveBeenCalledWith(11)
    expect(pageText(view.container)).toContain('EMP-001 was deactivated.')
    expect(pageText(view.container)).toContain('Inactive')
    expect(
      view.container.querySelector<HTMLButtonElement>(
        '.employee-detail-actions .employee-danger-button',
      )?.disabled,
    ).toBe(true)

    view.unmount()
  })
})
