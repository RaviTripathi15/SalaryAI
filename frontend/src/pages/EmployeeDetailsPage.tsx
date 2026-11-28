import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import {
  deactivateEmployee,
  getEmployee,
} from '../api/employeeApi'
import type { Employee } from '../api/employeeApi'
import EmployeeWorkspaceLayout from '../components/EmployeeWorkspaceLayout'
import {
  employeeErrorMessage,
  flashMessage,
  formattingDate,
  hasStatus,
  isAbortError,
  reverifyEmployeeSession,
} from '../utils/employeeUi'

const LOAD_FAILURE_MESSAGE = 'Unable to load the employee. Please try again.'
const DEACTIVATE_FAILURE_MESSAGE =
  'Unable to deactivate the employee. Please try again.'
const NOT_FOUND_MESSAGE = 'Employee not found.'
const INVALID_LINK_MESSAGE = 'This employee link is invalid.'
const FORBIDDEN_MESSAGE =
  'This account is not authorized for the organization workspace.'

interface DetailRowProps {
  label: string
  value: string
}

function DetailRow({ label, value }: DetailRowProps) {
  return (
    <div className="employee-detail-row">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

function EmployeeDetailsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const params = useParams()
  const parsedId = Number(params.id)
  const employeeId =
    Number.isInteger(parsedId) && parsedId > 0 ? parsedId : null

  const [employee, setEmployee] = useState<Employee | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [success, setSuccess] = useState(flashMessage(location.state))
  const [actionError, setActionError] = useState('')
  const [showConfirm, setShowConfirm] = useState(false)
  const [isDeactivating, setIsDeactivating] = useState(false)

  const deactivateInProgressRef = useRef(false)

  const loadEmployee = useCallback(
    async (signal?: AbortSignal) => {
      if (employeeId === null) {
        setLoadError(INVALID_LINK_MESSAGE)
        setIsLoading(false)
        return
      }

      setIsLoading(true)
      setLoadError('')

      try {
        const loaded = await getEmployee(employeeId, signal)
        if (signal?.aborted) return
        setEmployee(loaded)
      } catch (error) {
        if (signal?.aborted || isAbortError(error)) return

        if (hasStatus(error, 401)) {
          navigate('/', { replace: true })
          return
        }

        setLoadError(
          hasStatus(error, 404)
            ? NOT_FOUND_MESSAGE
            : employeeErrorMessage(error, LOAD_FAILURE_MESSAGE),
        )
      } finally {
        if (!signal?.aborted) setIsLoading(false)
      }
    },
    [employeeId, navigate],
  )

  useEffect(() => {
    const controller = new AbortController()
    void loadEmployee(controller.signal)

    return () => controller.abort()
  }, [loadEmployee])

  async function handleDeactivate() {
    if (!employee || deactivateInProgressRef.current) return

    deactivateInProgressRef.current = true
    setIsDeactivating(true)
    setActionError('')

    try {
      const updated = await deactivateEmployee(employee.id)
      setEmployee(updated)
      setShowConfirm(false)
      setSuccess(`${updated.employeeCode} was deactivated.`)
    } catch (error) {
      if (hasStatus(error, 401)) {
        navigate('/', { replace: true })
        return
      }

      if (hasStatus(error, 403)) {
        setEmployee(null)
        setSuccess('')
        setShowConfirm(false)
        setActionError('')
        setLoadError(FORBIDDEN_MESSAGE)
        void reverifyEmployeeSession(() => navigate('/', { replace: true }))
        return
      }

      setActionError(employeeErrorMessage(error, DEACTIVATE_FAILURE_MESSAGE))
    } finally {
      deactivateInProgressRef.current = false
      setIsDeactivating(false)
    }
  }

  const fullName = employee
    ? `${employee.firstName} ${employee.lastName}`
    : 'Employee details'

  return (
    <EmployeeWorkspaceLayout
      title={fullName}
      subtitle={employee ? employee.workEmail : 'Review a single employee record.'}
      eyebrow="EMPLOYEE DETAILS"
      success={success}
    >
      {isLoading ? (
        <div className="employee-loading" role="status">
          Loading employee...
        </div>
      ) : loadError || !employee ? (
        <div className="employee-error-screen">
          <p className="employee-error" role="alert">
            {loadError || LOAD_FAILURE_MESSAGE}
          </p>
          <Link className="employee-primary-button" to="/organization/employees">
            Back to employees
          </Link>
        </div>
      ) : (
        <>
          <div className="employee-detail-actions">
            <Link
              className="employee-secondary-button"
              to="/organization/employees"
            >
              Back to employees
            </Link>
            <Link
              className="employee-primary-button"
              to={`/organization/employees/${employee.id}/edit`}
            >
              Edit employee
            </Link>
            <button
              type="button"
              className="employee-danger-button"
              disabled={employee.employmentStatus === 'INACTIVE'}
              onClick={() => setShowConfirm(true)}
            >
              Deactivate
            </button>
          </div>

          {actionError && (
            <p className="employee-error" role="alert">
              {actionError}
            </p>
          )}

          <section className="employee-detail-card">
            <div className="employee-detail-heading">
              <div>
                <span className="employee-detail-avatar" aria-hidden="true">
                  {employee.firstName.charAt(0)}
                  {employee.lastName.charAt(0)}
                </span>
                <div>
                  <h2>{fullName}</h2>
                  <p>{employee.employeeCode}</p>
                </div>
              </div>
              <span
                className={`employee-badge employee-badge-${employee.employmentStatus.toLowerCase()}`}
              >
                {employee.employmentStatus === 'ACTIVE' ? 'Active' : 'Inactive'}
              </span>
            </div>

            <dl className="employee-detail-grid">
              <DetailRow label="Employee code" value={employee.employeeCode} />
              <DetailRow label="Full name" value={fullName} />
              <DetailRow label="Work email" value={employee.workEmail} />
              <DetailRow label="Phone" value={employee.phone || '—'} />
              <DetailRow label="Department" value={employee.department || '—'} />
              <DetailRow label="Designation" value={employee.designation || '—'} />
              <DetailRow
                label="Joining date"
                value={formattingDate(employee.joiningDate)}
              />
              <DetailRow
                label="Employment status"
                value={employee.employmentStatus}
              />
              <DetailRow
                label="Created"
                value={formattingDate(employee.createdAt)}
              />
              <DetailRow
                label="Updated"
                value={formattingDate(employee.updatedAt)}
              />
              <DetailRow
                label="Deactivated"
                value={formattingDate(employee.deactivatedAt)}
              />
              <DetailRow label="Version" value={String(employee.version)} />
            </dl>
          </section>
        </>
      )}

      {showConfirm && employee && (
        <div className="employee-dialog-backdrop">
          <section
            className="employee-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="details-deactivate-title"
          >
            <h2 id="details-deactivate-title">Deactivate this employee?</h2>
            <p>
              {fullName} will be marked inactive. Deactivation does not
              permanently delete employee history.
            </p>
            <div className="employee-dialog-actions">
              <button
                type="button"
                onClick={() => setShowConfirm(false)}
                disabled={isDeactivating}
              >
                Cancel
              </button>
              <button
                type="button"
                className="employee-danger-button"
                onClick={() => void handleDeactivate()}
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

export default EmployeeDetailsPage
