
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  getCurrentOrganization,
  logoutOrganization,
  OrganizationApiError,
} from '../api/organizationApi'
import type { OrganizationLoginResponse } from '../api/organizationApi'

const SESSION_CHECK_FAILURE_MESSAGE =
  'Unable to verify your organization session. Please try again.'
const LOGOUT_FAILURE_MESSAGE = 'Unable to sign out. Please try again.'
const WRONG_PRINCIPAL_MESSAGE =
  'This account is not authorized for the organization workspace.'

function isUnauthorized(error: unknown): boolean {
  return error instanceof OrganizationApiError && error.status === 401
}

function isUnauthorizedLogout(error: unknown): boolean {
  return (
    error instanceof OrganizationApiError &&
    (error.status === 401 || error.status === 403)
  )
}

function safeMessage(error: unknown, fallback: string): string {
  if (
    error instanceof OrganizationApiError &&
    error.message.trim().length > 0
  ) {
    return error.message
  }

  return fallback
}

function OrganizationDashboard() {
  const navigate = useNavigate()

  const [organization, setOrganization] =
    useState<OrganizationLoginResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [logoutError, setLogoutError] = useState('')
  const [isLoggingOut, setIsLoggingOut] = useState(false)

  const isMountedRef = useRef(true)
  const logoutInProgressRef = useRef(false)

  useEffect(() => {
    let active = true
    let redirecting = false

    isMountedRef.current = true

    getCurrentOrganization()
      .then((currentOrganization) => {
        if (!active) return

        if (currentOrganization.role !== 'ORGANIZATION_ADMIN') {
          setLoadError(WRONG_PRINCIPAL_MESSAGE)
          return
        }

        setOrganization(currentOrganization)
      })
      .catch((error: unknown) => {
        if (!active) return

        if (isUnauthorized(error)) {
          redirecting = true
          navigate('/', { replace: true })
          return
        }

        setLoadError(safeMessage(error, SESSION_CHECK_FAILURE_MESSAGE))
      })
      .finally(() => {
        if (active && !redirecting) {
          setIsLoading(false)
        }
      })

    return () => {
      active = false
      isMountedRef.current = false
    }
  }, [navigate])

  async function handleLogout() {
    if (logoutInProgressRef.current) {
      return
    }

    logoutInProgressRef.current = true
    setLogoutError('')
    setIsLoggingOut(true)

    try {
      await logoutOrganization()

      if (!isMountedRef.current) return

      navigate('/', { replace: true })
    } catch (error) {
      if (!isMountedRef.current) return

      if (isUnauthorizedLogout(error)) {
        navigate('/', { replace: true })
        return
      }

      setLogoutError(safeMessage(error, LOGOUT_FAILURE_MESSAGE))
    } finally {
      logoutInProgressRef.current = false

      if (isMountedRef.current) {
        setIsLoggingOut(false)
      }
    }
  }

  if (isLoading) {
    return (
      <main className="admin-dashboard-page">
        <p role="status">Checking your organization session...</p>
      </main>
    )
  }

  if (loadError || !organization) {
    return (
      <main className="admin-dashboard-page">
        <section className="admin-dashboard-content">
          <p className="form-error" role="alert">
            {loadError || SESSION_CHECK_FAILURE_MESSAGE}
          </p>
        </section>
      </main>
    )
  }

  const { name, email, role } = organization

  return (
    <main className="admin-dashboard-page">
      <header className="admin-dashboard-header">
        <div className="admin-dashboard-brand">
          <div className="brand-icon" aria-hidden="true">
            S
          </div>
          <div>
            <h1>SalaryAI</h1>
            <p>Organization Workspace</p>
          </div>
        </div>

        <div className="admin-dashboard-account">
          <span>{email}</span>
          <button
            type="button"
            onClick={() => void handleLogout()}
            disabled={isLoggingOut}
          >
            {isLoggingOut ? 'Signing out...' : 'Sign out'}
          </button>
        </div>
      </header>

      <section className="admin-dashboard-content">
        <div className="admin-dashboard-title">
          <div>
            <p className="form-eyebrow">ORGANIZATION OVERVIEW</p>
            <h2>{name}</h2>
            <p>
              Review your organization details while the full workspace is
              being prepared.
            </p>
          </div>
        </div>

        {logoutError && (
          <p className="form-error" role="alert">
            {logoutError}
          </p>
        )}

        <div className="admin-request-summary">
          <span>Current role</span>
          <strong>{role}</strong>
        </div>

        <div className="admin-organization-card">
          <div className="admin-organization-details">
            <div className="admin-organization-heading">
              <h3>Organization details</h3>
            </div>

            <p>
              <strong>Name:</strong> {name}
            </p>
            <p>
              <strong>Email:</strong> {email}
            </p>
            <p>
              <strong>Role:</strong> {role}
            </p>
          </div>
        </div>

        <section className="admin-empty-state">
          <h3>Your organization dashboard is coming soon.</h3>
          <p>
            Employee, attendance and payroll tools will appear here in a
            future stage.
          </p>
        </section>
      </section>
    </main>
  )
}

export default OrganizationDashboard
