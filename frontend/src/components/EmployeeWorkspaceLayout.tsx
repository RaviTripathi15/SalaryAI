import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
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

interface EmployeeWorkspaceLayoutProps {
  title: string
  subtitle: string
  eyebrow?: string
  success?: string
  children: ReactNode
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof OrganizationApiError && error.status === 401
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

function EmployeeWorkspaceLayout({
  title,
  subtitle,
  eyebrow = 'EMPLOYEE MANAGEMENT',
  success = '',
  children,
}: EmployeeWorkspaceLayoutProps) {
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
        if (active && !redirecting) setIsLoading(false)
      })

    return () => {
      active = false
      isMountedRef.current = false
    }
  }, [navigate])

  async function handleLogout() {
    if (logoutInProgressRef.current) return

    logoutInProgressRef.current = true
    setLogoutError('')
    setIsLoggingOut(true)

    try {
      await logoutOrganization()
      if (!isMountedRef.current) return
      navigate('/', { replace: true })
    } catch (error) {
      if (!isMountedRef.current) return

      if (
        error instanceof OrganizationApiError &&
        (error.status === 401 || error.status === 403)
      ) {
        navigate('/', { replace: true })
        return
      }

      setLogoutError(safeMessage(error, LOGOUT_FAILURE_MESSAGE))
    } finally {
      logoutInProgressRef.current = false
      if (isMountedRef.current) setIsLoggingOut(false)
    }
  }

  if (isLoading) {
    return (
      <main className="employee-shell">
        <p className="employee-initial-loading" role="status">
          Checking your organization session...
        </p>
      </main>
    )
  }

  if (loadError || !organization) {
    return (
      <main className="employee-shell">
        <section className="employee-error-screen">
          <p className="employee-error" role="alert">
            {loadError || SESSION_CHECK_FAILURE_MESSAGE}
          </p>
          <Link to="/">Return to sign in</Link>
        </section>
      </main>
    )
  }

  return (
    <main className="employee-shell">
      <aside className="employee-sidebar" aria-label="Organization workspace navigation">
        <div className="employee-sidebar-brand">
          <div className="brand-icon" aria-hidden="true">
            S
          </div>
          <div>
            <strong>SalaryAI</strong>
            <span>Organization workspace</span>
          </div>
        </div>

        <nav className="employee-nav" aria-label="Employee management">
          <Link to="/organization/dashboard">Overview</Link>
          <Link to="/organization/employees">Employees</Link>
          <Link to="/organization/employees/new">Add employee</Link>
        </nav>

        <p className="employee-sidebar-note">
          Employee records are visible only to your approved organization.
        </p>
      </aside>

      <div className="employee-main">
        <header className="employee-topbar">
          <div>
            <span className="employee-topbar-label">Signed in as</span>
            <strong>{organization.email}</strong>
          </div>
          <button
            type="button"
            onClick={() => void handleLogout()}
            disabled={isLoggingOut}
          >
            {isLoggingOut ? 'Signing out...' : 'Sign out'}
          </button>
        </header>

        <section className="employee-content">
          <nav className="employee-breadcrumb" aria-label="Breadcrumb">
            <Link to="/organization/dashboard">Overview</Link>
            <span aria-hidden="true">/</span>
            <span aria-current="page">{title}</span>
          </nav>

          <div className="employee-page-heading">
            <div>
              <p className="form-eyebrow">{eyebrow}</p>
              <h1>{title}</h1>
              <p>{subtitle}</p>
            </div>
          </div>

          {logoutError && (
            <p className="employee-error" role="alert">
              {logoutError}
            </p>
          )}

          {success && (
            <p className="employee-success" role="status">
              {success}
            </p>
          )}

          {children}
        </section>
      </div>
    </main>
  )
}

export default EmployeeWorkspaceLayout
