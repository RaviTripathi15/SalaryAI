
import { useState } from 'react'
import { Link } from 'react-router-dom'

function PlatformAdminLogin() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  return (
    <main className="admin-login-page">
      <section className="admin-login-card">
        <div className="admin-login-brand">
          <div className="brand-icon">S</div>
          <div>
            <h1>SalaryAI</h1>
            <p>Platform Administration</p>
          </div>
        </div>

        <div className="admin-login-heading">
          <p className="form-eyebrow">SECURE ADMIN ACCESS</p>
          <h2>Administrator sign in</h2>
          <p>Sign in to manage the SalaryAI platform.</p>
        </div>

        <form onSubmit={(event) => event.preventDefault()}>
          <label htmlFor="admin-email">Administrator email</label>
          <input
            id="admin-email"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="admin@salaryai.com"
            autoComplete="username"
            required
          />

          <label htmlFor="admin-password">Password</label>
          <div className="password-field">
            <input
              id="admin-password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Enter your password"
              autoComplete="current-password"
              required
            />

            <button
              type="button"
              className="password-toggle"
              onClick={() => setShowPassword(!showPassword)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? 'Hide' : 'Show'}
            </button>
          </div>

          <button className="sign-in-button" type="submit">
            Sign in to Platform Admin
          </button>
        </form>

        <p className="admin-login-note">
          Authorized SalaryAI platform administrators only.
        </p>

        <Link to="/" className="admin-back-link">
          Back to organization login
        </Link>
      </section>
    </main>
  )
}

export default PlatformAdminLogin
