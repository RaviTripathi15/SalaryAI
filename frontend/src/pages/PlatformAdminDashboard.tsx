
import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  getCurrentPlatformAdmin,
  getOrganizationStatusCounts,
  getOrganizationsByStatus,
  logoutPlatformAdmin,
  reviewOrganization,
} from '../api/platformAdminApi'
import type {
  AdminLoginResponse,
  OrganizationReview,
  OrganizationStatus,
  OrganizationStatusCounts,
} from '../api/platformAdminApi'

const STATUS_LABELS: Record<OrganizationStatus, string> = {
  PENDING: 'Pending',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
}

function PlatformAdminDashboard() {
  const navigate = useNavigate()

  const [admin, setAdmin] = useState<AdminLoginResponse | null>(null)
  const [organizations, setOrganizations] = useState<OrganizationReview[]>([])
  const [counts, setCounts] = useState<OrganizationStatusCounts>({
    PENDING: 0,
    APPROVED: 0,
    REJECTED: 0,
  })
  const [selectedStatus, setSelectedStatus] =
    useState<OrganizationStatus>('PENDING')
  const [isLoading, setIsLoading] = useState(true)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

  // Load counts and the organizations for the currently selected card.
  const loadOrganizationData = useCallback(async () => {
    const [updatedCounts, updatedOrganizations] = await Promise.all([
      getOrganizationStatusCounts(),
      getOrganizationsByStatus(selectedStatus),
    ])

    setCounts(updatedCounts)
    setOrganizations(updatedOrganizations)
  }, [selectedStatus])

  // Verify the administrator session before loading protected data.
  const loadDashboard = useCallback(async () => {
    setError('')
    setIsLoading(true)

    try {
      const currentAdmin = await getCurrentPlatformAdmin()

      if (currentAdmin.role !== 'PLATFORM_ADMIN') {
        navigate('/platform-admin/login', { replace: true })
        return
      }

      setAdmin(currentAdmin)

      const [updatedCounts, updatedOrganizations] = await Promise.all([
        getOrganizationStatusCounts(),
        getOrganizationsByStatus(selectedStatus),
      ])

      setCounts(updatedCounts)
      setOrganizations(updatedOrganizations)
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : 'Unable to load the dashboard.'

      if (errorMessage.includes('session is not active')) {
        navigate('/platform-admin/login', { replace: true })
        return
      }

      setError(errorMessage)
    } finally {
      setIsLoading(false)
    }
  }, [navigate, selectedStatus])

  useEffect(() => {
    void loadDashboard()
  }, [loadDashboard])

  // Fetch the list associated with the card the administrator clicks.
  async function handleStatusSelect(status: OrganizationStatus) {
    setSelectedStatus(status)
    setError('')
    setMessage('')
    setIsLoading(true)

    try {
      const [updatedCounts, updatedOrganizations] = await Promise.all([
        getOrganizationStatusCounts(),
        getOrganizationsByStatus(status),
      ])

      setCounts(updatedCounts)
      setOrganizations(updatedOrganizations)
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to load organizations.',
      )
    } finally {
      setIsLoading(false)
    }
  }

  // Approve or reject a pending organization.
  async function handleReview(
    id: number,
    action: 'approve' | 'reject',
  ) {
    const organization = organizations.find((item) => item.id === id)

    if (!organization || organization.status !== 'PENDING') return

    const confirmed = window.confirm(
      `Are you sure you want to ${action} ${organization.name}?`,
    )

    if (!confirmed) return

    setBusyId(id)
    setError('')
    setMessage('')

    try {
      const updatedOrganization = await reviewOrganization(id, action)

      setMessage(
        `${updatedOrganization.name} was ${
          action === 'approve' ? 'approved' : 'rejected'
        } successfully.`,
      )

      // Refresh the counts and current list from the backend.
      await loadOrganizationData()
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to review this organization.',
      )
    } finally {
      setBusyId(null)
    }
  }

  async function handleLogout() {
    setError('')

    try {
      await logoutPlatformAdmin()
      navigate('/platform-admin/login', { replace: true })
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Unable to log out. Please try again.',
      )
    }
  }

  if (isLoading && !admin) {
    return (
      <main className="admin-dashboard-page">
        <p role="status">Loading administrator dashboard...</p>
      </main>
    )
  }

  return (
    <main className="admin-dashboard-page">
      <header className="admin-dashboard-header">
        <div className="admin-dashboard-brand">
          <div className="brand-icon" aria-hidden="true">
            S
          </div>
          <div>
            <h1>SalaryAI</h1>
            <p>Platform Administration</p>
          </div>
        </div>

        <div className="admin-dashboard-account">
          <span>{admin?.email}</span>
          <button type="button" onClick={handleLogout}>
            Sign out
          </button>
        </div>
      </header>

      <section className="admin-dashboard-content">
        <div className="admin-dashboard-title">
          <div>
            <p className="form-eyebrow">PLATFORM OVERVIEW</p>
            <h2>Organization management</h2>
            <p>
              Review registrations and manage organization access to SalaryAI.
            </p>
          </div>

          <button
            type="button"
            className="admin-refresh-button"
            onClick={() => void loadDashboard()}
            disabled={busyId !== null || isLoading}
          >
            Refresh data
          </button>
        </div>

        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}

        {message && (
          <p className="admin-success-message" role="status">
            {message}
          </p>
        )}

        <div className="admin-status-cards" aria-label="Organization status filters">
          {(['PENDING', 'APPROVED', 'REJECTED'] as const).map((status) => (
            <button
              key={status}
              type="button"
              className={`admin-status-card admin-status-${status.toLowerCase()} ${
                selectedStatus === status ? 'is-selected' : ''
              }`}
              aria-pressed={selectedStatus === status}
              onClick={() => void handleStatusSelect(status)}
              disabled={isLoading || busyId !== null}
            >
              <span className="admin-status-card-label">
                {STATUS_LABELS[status]} organizations
              </span>
              <strong className="admin-status-card-count">
                {counts[status]}
              </strong>
              <span className="admin-status-card-hint">
                View {STATUS_LABELS[status].toLowerCase()} organizations
              </span>
            </button>
          ))}
        </div>

        <div className="admin-list-heading">
          <div>
            <h2>{STATUS_LABELS[selectedStatus]} organizations</h2>
            <p>
              {selectedStatus === 'PENDING'
                ? 'Review new organization registrations.'
                : selectedStatus === 'APPROVED'
                  ? 'Organizations approved to access SalaryAI.'
                  : 'Organizations whose registrations were rejected.'}
            </p>
          </div>

          <span className="admin-list-count">
            {counts[selectedStatus]} total
          </span>
        </div>

        {isLoading ? (
          <p role="status" className="admin-loading-message">
            Loading organizations...
          </p>
        ) : organizations.length === 0 ? (
          <section className="admin-empty-state">
            <h3>
              No {STATUS_LABELS[selectedStatus].toLowerCase()} organizations
            </h3>
            <p>
              Organizations matching this status will appear here when available.
            </p>
          </section>
        ) : (
          <div className="admin-organization-list">
            {organizations.map((organization) => (
              <article
                className="admin-organization-card"
                key={organization.id}
              >
                <div className="admin-organization-details">
                  <div className="admin-organization-heading">
                    <h3>{organization.name}</h3>
                    <span
                      className={`admin-status-badge admin-badge-${organization.status.toLowerCase()}`}
                    >
                      {STATUS_LABELS[
                        organization.status as OrganizationStatus
                      ] ?? organization.status}
                    </span>
                  </div>

                  <p>
                    <strong>Email:</strong> {organization.email}
                  </p>
                  <p>
                    <strong>Phone:</strong> {organization.phone}
                  </p>
                  <p>
                    <strong>Submitted:</strong>{' '}
                    {new Date(organization.createdAt).toLocaleString()}
                  </p>
                </div>

                {organization.status === 'PENDING' && (
                  <div className="admin-organization-actions">
                    <button
                      type="button"
                      className="admin-approve-button"
                      onClick={() =>
                        void handleReview(organization.id, 'approve')
                      }
                      disabled={busyId !== null}
                    >
                      {busyId === organization.id
                        ? 'Processing...'
                        : 'Approve'}
                    </button>

                    <button
                      type="button"
                      className="admin-reject-button"
                      onClick={() =>
                        void handleReview(organization.id, 'reject')
                      }
                      disabled={busyId !== null}
                    >
                      {busyId === organization.id
                        ? 'Processing...'
                        : 'Reject'}
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
        )}
      </section>
    </main>
  )
}

export default PlatformAdminDashboard
