
import { Link } from 'react-router-dom'
import { useState } from 'react'
import { registerOrganization } from '../api/organizationApi'

function RegisterPage() {
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  async function handleSubmit(
    event: React.FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()
    setMessage('')
    setError('')

    const form = event.currentTarget
    const formData = new FormData(form)

    const name = String(formData.get('name') ?? '').trim()
    const email = String(formData.get('email') ?? '').trim()
    const phone = String(formData.get('phone') ?? '').trim()
    const password = String(formData.get('password') ?? '')

    setLoading(true)

    try {
      const result = await registerOrganization({
        name,
        email,
        phone,
        password,
      })

      setMessage(result)
      form.reset()
      setShowPassword(false)
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Registration failed. Please try again.',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="register-page">
      <section className="register-card">
        <Link className="register-back-link" to="/">
          ← Back to sign in
        </Link>

        <div className="register-brand">
          <div className="brand-icon" aria-hidden="true">
            S
          </div>
          <span>Salary<span>AI</span></span>
        </div>

        <div className="register-heading">
          <p className="form-eyebrow">GET STARTED WITH SALARYAI</p>
          <h1>Register your organization</h1>
          <p>
            Create your workspace to manage employees, attendance,
            and payroll in one place.
          </p>
        </div>

        <form className="register-form" onSubmit={handleSubmit}>
          <label htmlFor="register-name">
            Organization name
          </label>
          <input
            id="register-name"
            name="name"
            type="text"
            placeholder="e.g. Acme Technologies"
            autoComplete="organization"
            maxLength={150}
            required
          />

          <label htmlFor="register-email">Work email address</label>
          <input
            id="register-email"
            name="email"
            type="email"
            placeholder="you@company.com"
            autoComplete="email"
            required
          />

          <label htmlFor="register-phone">Phone number</label>
          <input
            id="register-phone"
            name="phone"
            type="tel"
            placeholder="Enter your phone number"
            autoComplete="tel"
            required
          />

          <label htmlFor="register-password">Create password</label>
          <div className="password-field">
            <input
              id="register-password"
              name="password"
              type={showPassword ? 'text' : 'password'}
              placeholder="At least 8 characters"
              autoComplete="new-password"
              minLength={8}
              maxLength={72}
              required
            />
            <button
              className="password-toggle"
              type="button"
              onClick={() => setShowPassword((visible) => !visible)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              aria-pressed={showPassword}
            >
              {showPassword ? 'Hide' : 'Show'}
            </button>
          </div>

          {error && (
            <p className="register-message register-error" role="alert">
              {error}
            </p>
          )}

          {message && (
            <p className="register-message register-success" role="status">
              {message}
            </p>
          )}

          <button
            className="sign-in-button register-submit"
            type="submit"
            disabled={loading}
          >
            <span>
              {loading ? 'Submitting...' : 'Create organization'}
            </span>
            <span aria-hidden="true">→</span>
          </button>

          <p className="register-approval-note">
            Your registration will be reviewed by a platform
            administrator before workspace access is granted.
          </p>
        </form>

        <p className="register-signin">
          Already registered? <Link to="/">Sign in</Link>
        </p>
      </section>
    </main>
  )
}

export default RegisterPage
