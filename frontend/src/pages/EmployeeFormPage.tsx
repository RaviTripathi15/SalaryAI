import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { createEmployee, getEmployee, updateEmployee } from '../api/employeeApi'
import EmployeeFormFields from '../components/EmployeeFormFields'
import EmployeeWorkspaceLayout from '../components/EmployeeWorkspaceLayout'
import {
  employeeErrorMessage,
  hasStatus,
  isAbortError,
  reverifyEmployeeSession,
} from '../utils/employeeUi'
import {
  EMPTY_EMPLOYEE_FORM,
  employeeCreatePayload,
  employeeValuesFrom,
  hasEmployeeFormErrors,
  normalizeFormValues,
  validateEmployeeForm,
} from '../utils/employeeForm'
import type {
  EmployeeFormErrors,
  EmployeeFormValues,
} from '../utils/employeeForm'

interface EmployeeFormPageProps {
  mode: 'create' | 'edit'
}

const LOAD_FAILURE_MESSAGE = 'Unable to load the employee. Please try again.'
const SAVE_FAILURE_MESSAGE = 'Unable to save the employee. Please try again.'
const INVALID_LINK_MESSAGE = 'This employee link is invalid.'
const FORBIDDEN_MESSAGE =
  'This account is not authorized for the organization workspace.'

function EmployeeFormPage({ mode }: EmployeeFormPageProps) {
  const navigate = useNavigate()
  const params = useParams()
  const isEdit = mode === 'edit'
  const parsedId = isEdit ? Number(params.id) : null
  const employeeId =
    parsedId !== null && Number.isInteger(parsedId) && parsedId > 0
      ? parsedId
      : null

  const [values, setValues] = useState<EmployeeFormValues>(EMPTY_EMPLOYEE_FORM)
  const [version, setVersion] = useState<number | null>(null)
  const [errors, setErrors] = useState<EmployeeFormErrors>({})
  const [loadError, setLoadError] = useState('')
  const [formError, setFormError] = useState('')
  const [isLoading, setIsLoading] = useState(isEdit)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isDirty, setIsDirty] = useState(false)
  const [showDiscardDialog, setShowDiscardDialog] = useState(false)
  const [needsReload, setNeedsReload] = useState(false)

  const submitInProgressRef = useRef(false)

  const loadEmployee = useCallback(
    async (signal?: AbortSignal) => {
      if (!isEdit) return

      if (employeeId === null) {
        setLoadError(INVALID_LINK_MESSAGE)
        setIsLoading(false)
        return
      }

      setIsLoading(true)
      setLoadError('')

      try {
        const employee = await getEmployee(employeeId, signal)
        if (signal?.aborted) return

        setValues(employeeValuesFrom(employee))
        setVersion(employee.version)
        setIsDirty(false)
        setNeedsReload(false)
      } catch (error) {
        if (signal?.aborted || isAbortError(error)) return

        if (hasStatus(error, 401)) {
          navigate('/', { replace: true })
          return
        }

        setLoadError(employeeErrorMessage(error, LOAD_FAILURE_MESSAGE))
      } finally {
        if (!signal?.aborted) setIsLoading(false)
      }
    },
    [employeeId, isEdit, navigate],
  )

  useEffect(() => {
    const controller = new AbortController()
    void loadEmployee(controller.signal)

    return () => controller.abort()
  }, [loadEmployee])

  useEffect(() => {
    if (!isDirty) return

    function warnBeforeUnload(event: BeforeUnloadEvent) {
      event.preventDefault()
      event.returnValue = ''
    }

    window.addEventListener('beforeunload', warnBeforeUnload)
    return () => window.removeEventListener('beforeunload', warnBeforeUnload)
  }, [isDirty])

  function handleChange(field: keyof EmployeeFormValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: '' }))
    setFormError('')
    setIsDirty(true)
  }

  function handleCancel() {
    if (isDirty) {
      setShowDiscardDialog(true)
      return
    }

    navigate('/organization/employees')
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (submitInProgressRef.current || isLoading) return

    const normalized = normalizeFormValues(values)
    const validationErrors = validateEmployeeForm(normalized)
    setErrors(validationErrors)

    if (hasEmployeeFormErrors(validationErrors)) return
    if (isEdit && version === null) {
      setFormError('Reload the employee before editing.')
      setNeedsReload(true)
      return
    }

    submitInProgressRef.current = true
    setIsSubmitting(true)
    setFormError('')

    try {
      let employeeIdAfterSave: number

      if (isEdit && employeeId !== null && version !== null) {
        const updated = await updateEmployee(employeeId, {
          ...employeeCreatePayload(normalized),
          version,
        })
        employeeIdAfterSave = updated.id
      } else {
        const created = await createEmployee(employeeCreatePayload(normalized))
        employeeIdAfterSave = created.id
      }

      setIsDirty(false)

      navigate(`/organization/employees/${employeeIdAfterSave}`, {
        replace: true,
        state: {
          success: isEdit
            ? 'Employee updated successfully.'
            : 'Employee created successfully.',
        },
      })
    } catch (error) {
      if (hasStatus(error, 401)) {
        navigate('/', { replace: true })
        return
      }

      if (hasStatus(error, 403)) {
        setValues(EMPTY_EMPLOYEE_FORM)
        setVersion(null)
        setErrors({})
        setFormError('')
        setIsDirty(false)
        setNeedsReload(false)
        setLoadError(FORBIDDEN_MESSAGE)
        void reverifyEmployeeSession(() => navigate('/', { replace: true }))
        return
      }

      const message = employeeErrorMessage(error, SAVE_FAILURE_MESSAGE)
      setFormError(message)

      if (hasStatus(error, 409) && isEdit) {
        setNeedsReload(true)
      }
    } finally {
      submitInProgressRef.current = false
      setIsSubmitting(false)
    }
  }

  return (
    <EmployeeWorkspaceLayout
      title={isEdit ? 'Edit employee' : 'Add employee'}
      subtitle={
        isEdit
          ? 'Update the employee record using the latest version from the server.'
          : 'Create a new employee record for your organization.'
      }
      eyebrow={isEdit ? 'EMPLOYEE DETAILS' : 'NEW EMPLOYEE'}
    >
      {isEdit && isLoading ? (
        <div className="employee-loading" role="status">
          Loading employee...
        </div>
      ) : loadError ? (
        <div className="employee-error-screen">
          <p className="employee-error" role="alert">
            {loadError}
          </p>
          <button type="button" onClick={() => void loadEmployee()}>
            Try again
          </button>
        </div>
      ) : (
        <form className="employee-form-card" onSubmit={(event) => void handleSubmit(event)}>
          {isEdit && (
            <p className="employee-version-note">
              Version {version ?? '—'} is preserved from the server and included
              when you save.
            </p>
          )}

          {formError && (
            <p className="employee-error" role="alert">
              {formError}
            </p>
          )}

          <EmployeeFormFields
            values={values}
            errors={errors}
            disabled={isSubmitting || isLoading}
            onChange={handleChange}
          />

          <div className="employee-form-actions">
            <button
              type="submit"
              className="employee-primary-button"
              disabled={isSubmitting || isLoading}
            >
              {isSubmitting
                ? 'Saving...'
                : isEdit
                  ? 'Save changes'
                  : 'Create employee'}
            </button>
            <button
              type="button"
              className="employee-secondary-button"
              onClick={handleCancel}
              disabled={isSubmitting}
            >
              Cancel
            </button>
            {needsReload && isEdit && (
              <button
                type="button"
                className="employee-secondary-button"
                onClick={() => void loadEmployee()}
                disabled={isSubmitting}
              >
                Reload employee
              </button>
            )}
          </div>
        </form>
      )}

      {showDiscardDialog && (
        <div className="employee-dialog-backdrop">
          <section
            className="employee-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="discard-employee-title"
          >
            <h2 id="discard-employee-title">Discard unsaved changes?</h2>
            <p>Your edits have not been saved yet.</p>
            <div className="employee-dialog-actions">
              <button
                type="button"
                onClick={() => setShowDiscardDialog(false)}
              >
                Keep editing
              </button>
              <button
                type="button"
                className="employee-danger-button"
                onClick={() => navigate('/organization/employees')}
              >
                Discard changes
              </button>
            </div>
          </section>
        </div>
      )}
    </EmployeeWorkspaceLayout>
  )
}

export default EmployeeFormPage
