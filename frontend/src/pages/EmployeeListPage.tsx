import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import {
  deactivateEmployee,
  getEmployeeStatusCounts,
  listEmployees,
} from '../api/employeeApi'
import type {
  Employee,
  EmployeePage,
  EmployeeSortDirection,
  EmployeeSortField,
  EmployeeStatusCounts,
  EmploymentStatus,
} from '../api/employeeApi'
import EmployeeWorkspaceLayout from '../components/EmployeeWorkspaceLayout'
import {
  employeeErrorMessage,
  flashMessage,
  formattingDate,
  hasStatus,
  isAbortError,
  reverifyEmployeeSession,
} from '../utils/employeeUi'

const PAGE_SIZES = [10, 20, 50, 100] as const

const SORT_FIELDS: { value: EmployeeSortField; label: string }[] = [
  { value: 'employeeCode', label: 'Employee code' },
  { value: 'firstName', label: 'First name' },
  { value: 'lastName', label: 'Last name' },
  { value: 'workEmail', label: 'Work email' },
  { value: 'department', label: 'Department' },
  { value: 'designation', label: 'Designation' },
  { value: 'joiningDate', label: 'Joining date' },
  { value: 'createdAt', label: 'Created date' },
  { value: 'updatedAt', label: 'Updated date' },
]

const LOAD_FAILURE_MESSAGE = 'Unable to load employees. Please try again.'
const DEACTIVATE_FAILURE_MESSAGE =
  'Unable to deactivate the employee. Please try again.'
const FORBIDDEN_MESSAGE =
  'This account is not authorized for the organization workspace.'

function EmployeeListPage() {
  const navigate = useNavigate()
  const location = useLocation()

  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<EmploymentStatus | ''>('')
  const [sortBy, setSortBy] = useState<EmployeeSortField>('employeeCode')
  const [sortDirection, setSortDirection] = useState<EmployeeSortDirection>('asc')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)

  const [result, setResult] = useState<EmployeePage | null>(null)
  const [counts, setCounts] = useState<EmployeeStatusCounts | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(flashMessage(location.state))
  const [confirmEmployee, setConfirmEmployee] = useState<Employee | null>(null)
  const [isDeactivating, setIsDeactivating] = useState(false)
  const [reloadToken, setReloadToken] = useState(0)

  const deactivateInProgressRef = useRef(false)
  const authGenerationRef = useRef(0)

  const handleAuthorizationLoss = useCallback(() => {
    authGenerationRef.current += 1
    setResult(null)
    setCounts(null)
    setSuccess('')
    setConfirmEmployee(null)
    setError(FORBIDDEN_MESSAGE)
    void reverifyEmployeeSession(() => navigate('/', { replace: true }))
  }, [navigate])

  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (searchInput.trim() === search.trim()) return

      setSearch(searchInput)
      setPage(0)
    }, 300)

    return () => window.clearTimeout(timer)
  }, [searchInput, search])

  useEffect(() => {
    const controller = new AbortController()
    const generation = authGenerationRef.current
    let active = true

    setIsLoading(true)
    setError('')

    listEmployees(
      {
        page,
        size,
        sortBy,
        sortDirection,
        search,
        status: status || undefined,
      },
      controller.signal,
    )
      .then((employeePage) => {
        if (active && generation === authGenerationRef.current) {
          setResult(employeePage)
        }
      })
      .catch((error: unknown) => {
        if (!active || isAbortError(error)) return

        if (hasStatus(error, 401)) {
          navigate('/', { replace: true })
          return
        }

        if (hasStatus(error, 403)) {
          handleAuthorizationLoss()
          return
        }

        setError(employeeErrorMessage(error, LOAD_FAILURE_MESSAGE))
      })
      .finally(() => {
        if (active) setIsLoading(false)
      })

    return () => {
      active = false
      controller.abort()
    }
  }, [
    page,
    size,
    sortBy,
    sortDirection,
    search,
    status,
    reloadToken,
    navigate,
    handleAuthorizationLoss,
  ])

  useEffect(() => {
    const controller = new AbortController()
    const generation = authGenerationRef.current
    let active = true

    getEmployeeStatusCounts(controller.signal)
      .then((updatedCounts) => {
        if (active && generation === authGenerationRef.current) {
          setCounts(updatedCounts)
        }
      })
      .catch((error: unknown) => {
        if (!active || isAbortError(error)) return

        if (hasStatus(error, 401)) {
          navigate('/', { replace: true })
          return
        }

        if (hasStatus(error, 403)) {
          handleAuthorizationLoss()
        }
        // Other count failures are supplementary; the main list reports them.
      })

    return () => {
      active = false
      controller.abort()
    }
  }, [reloadToken, navigate, handleAuthorizationLoss])

  function handleStatusChange(value: string) {
    setStatus(value === 'ACTIVE' || value === 'INACTIVE' ? value : '')
    setPage(0)
  }

  function handleSortChange(value: EmployeeSortField) {
    setSortBy(value)
    setPage(0)
  }

  function handleSizeChange(value: number) {
    setSize(value)
    setPage(0)
  }

  async function handleDeactivateConfirm() {
    if (!confirmEmployee || deactivateInProgressRef.current) return

    deactivateInProgressRef.current = true
    setIsDeactivating(true)
    setError('')

    try {
      const updated = await deactivateEmployee(confirmEmployee.id)

      setSuccess(`${updated.employeeCode} was deactivated.`)
      setConfirmEmployee(null)
      setReloadToken((current) => current + 1)
    } catch (error) {
      if (hasStatus(error, 401)) {
        navigate('/', { replace: true })
        return
      }

      if (hasStatus(error, 403)) {
        handleAuthorizationLoss()
        return
      }

      setError(employeeErrorMessage(error, DEACTIVATE_FAILURE_MESSAGE))
    } finally {
      deactivateInProgressRef.current = false
      setIsDeactivating(false)
    }
  }

  const hasFilters = search.trim().length > 0 || status !== ''
  const totalElements = result?.totalElements ?? 0
  const start = result && totalElements > 0 ? result.page * result.size + 1 : 0
  const end = result
    ? Math.min((result.page + 1) * result.size, totalElements)
    : 0

  return (
    <EmployeeWorkspaceLayout
      title="Employee management"
      subtitle="Search, review, and maintain employee records for your organization."
      success={success}
    >
      <div className="employee-stats" aria-label="Employee statistics">
        <article className="employee-stat-card">
          <span>Total employees</span>
          <strong>{counts ? counts.total : '—'}</strong>
        </article>
        <article className="employee-stat-card">
          <span>Active employees</span>
          <strong className="employee-stat-active">
            {counts ? counts.active : '—'}
          </strong>
        </article>
        <article className="employee-stat-card">
          <span>Inactive employees</span>
          <strong className="employee-stat-inactive">
            {counts ? counts.inactive : '—'}
          </strong>
        </article>
      </div>

      <div className="employee-toolbar">
        <div className="employee-search-field">
          <label htmlFor="employee-search">
            Search employees
            <small>Code, name, email, department, or designation</small>
          </label>
          <input
            id="employee-search"
            type="search"
            value={searchInput}
            maxLength={100}
            placeholder="Search by name, code, or email"
            onChange={(event) => setSearchInput(event.target.value)}
          />
        </div>

        <div className="employee-filter-field">
          <label htmlFor="employee-status-filter">Status</label>
          <select
            id="employee-status-filter"
            value={status}
            onChange={(event) => handleStatusChange(event.target.value)}
          >
            <option value="">All statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
          </select>
        </div>

        <div className="employee-filter-field">
          <label htmlFor="employee-sort-field">Sort by</label>
          <select
            id="employee-sort-field"
            value={sortBy}
            onChange={(event) =>
              handleSortChange(event.target.value as EmployeeSortField)
            }
          >
            {SORT_FIELDS.map((field) => (
              <option key={field.value} value={field.value}>
                {field.label}
              </option>
            ))}
          </select>
        </div>

        <div className="employee-filter-field">
          <label htmlFor="employee-sort-direction">Direction</label>
          <select
            id="employee-sort-direction"
            value={sortDirection}
            onChange={(event) => {
              setSortDirection(event.target.value as EmployeeSortDirection)
              setPage(0)
            }}
          >
            <option value="asc">Ascending</option>
            <option value="desc">Descending</option>
          </select>
        </div>

        <div className="employee-filter-field">
          <label htmlFor="employee-page-size">Rows per page</label>
          <select
            id="employee-page-size"
            value={size}
            onChange={(event) => handleSizeChange(Number(event.target.value))}
          >
            {PAGE_SIZES.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </div>

        <button
          type="button"
          className="employee-secondary-button"
          onClick={() => setReloadToken((current) => current + 1)}
          disabled={isLoading}
        >
          Refresh
        </button>

        <Link className="employee-primary-button" to="/organization/employees/new">
          Add employee
        </Link>
      </div>

      {error && result && (
        <p className="employee-error" role="alert">
          {error}
        </p>
      )}

      {isLoading ? (
        <div className="employee-loading" role="status">
          Loading employees...
        </div>
      ) : error && !result ? (
        <div className="employee-error-screen">
          <p className="employee-error" role="alert">
            {error}
          </p>
        </div>
      ) : !result || result.content.length === 0 ? (
        <div className="employee-empty-state">
          <h2>{hasFilters ? 'No employees match your filters' : 'No employees yet'}</h2>
          <p>
            {hasFilters
              ? 'Try a different search term or status filter.'
              : 'Add your first employee to start building your organization directory.'}
          </p>
          {!hasFilters && (
            <Link className="employee-primary-button" to="/organization/employees/new">
              Add employee
            </Link>
          )}
        </div>
      ) : (
        <>
          <div className="employee-table-wrap">
            <table className="employee-table">
              <thead>
                <tr>
                  <th scope="col">Employee</th>
                  <th scope="col">Code</th>
                  <th scope="col">Department</th>
                  <th scope="col">Designation</th>
                  <th scope="col">Status</th>
                  <th scope="col">Joined</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((employee) => (
                  <tr key={employee.id}>
                    <td data-label="Employee">
                      <Link
                        className="employee-row-link"
                        to={`/organization/employees/${employee.id}`}
                      >
                        <strong>
                          {employee.firstName} {employee.lastName}
                        </strong>
                        <span>{employee.workEmail}</span>
                      </Link>
                    </td>
                    <td data-label="Code">{employee.employeeCode}</td>
                    <td data-label="Department">{employee.department || '—'}</td>
                    <td data-label="Designation">{employee.designation || '—'}</td>
                    <td data-label="Status">
                      <span
                        className={`employee-badge employee-badge-${employee.employmentStatus.toLowerCase()}`}
                      >
                        {employee.employmentStatus === 'ACTIVE' ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td data-label="Joined">
                      {formattingDate(employee.joiningDate)}
                    </td>
                    <td data-label="Actions">
                      <div className="employee-row-actions">
                        <Link
                          to={`/organization/employees/${employee.id}`}
                          aria-label={`View ${employee.firstName} ${employee.lastName}`}
                        >
                          View
                        </Link>
                        <Link
                          to={`/organization/employees/${employee.id}/edit`}
                          aria-label={`Edit ${employee.firstName} ${employee.lastName}`}
                        >
                          Edit
                        </Link>
                        <button
                          type="button"
                          disabled={employee.employmentStatus === 'INACTIVE'}
                          onClick={() => setConfirmEmployee(employee)}
                          aria-label={`Deactivate ${employee.firstName} ${employee.lastName}`}
                        >
                          Deactivate
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="employee-pagination">
            <span>
              Showing {start}–{end} of {totalElements} employees
            </span>
            <div>
              <button
                type="button"
                onClick={() => setPage((current) => Math.max(0, current - 1))}
                disabled={result.first || isLoading}
              >
                Previous
              </button>
              <span>
                Page {result.page + 1} of {Math.max(result.totalPages, 1)}
              </span>
              <button
                type="button"
                onClick={() => setPage((current) => current + 1)}
                disabled={result.last || isLoading}
              >
                Next
              </button>
            </div>
          </div>
        </>
      )}

      {confirmEmployee && (
        <div className="employee-dialog-backdrop">
          <section
            className="employee-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="deactivate-employee-title"
          >
            <h2 id="deactivate-employee-title">Deactivate employee?</h2>
            <p>
              {confirmEmployee.firstName} {confirmEmployee.lastName} will be marked
              inactive. Their employee history is not permanently deleted.
            </p>
            <div className="employee-dialog-actions">
              <button
                type="button"
                onClick={() => setConfirmEmployee(null)}
                disabled={isDeactivating}
              >
                Cancel
              </button>
              <button
                type="button"
                className="employee-danger-button"
                onClick={() => void handleDeactivateConfirm()}
                disabled={isDeactivating}
              >
                {isDeactivating ? 'Deactivating...' : 'Deactivate employee'}
              </button>
            </div>
          </section>
        </div>
      )}
    </EmployeeWorkspaceLayout>
  )
}

export default EmployeeListPage
