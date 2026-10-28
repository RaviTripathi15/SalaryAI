
import { useState } from 'react'
import { Link, Route, Routes } from 'react-router-dom'
import RegisterPage from './pages/RegisterPage'
import './App.css'

const workforcePhoto =
  'https://images.unsplash.com/photo-1556761175-b413da4baf72?auto=format&fit=crop&w=1400&q=85'

function LoginPage() {
  const [showPassword, setShowPassword] = useState(false)

  return (
    <main className="auth-page">
      <section className="welcome-panel" aria-label="About SalaryAI">
        <header className="brand">
          <div className="brand-icon" aria-hidden="true">
            S
          </div>
          <div>
            <div className="brand-name">SalaryAI</div>
            <p>Workforce · Payroll · AI Insights</p>
          </div>
        </header>

        <div className="global-badge">
          <span className="global-badge-icon" aria-hidden="true">
            ◎
          </span>
          <div>
            <strong>Made for teams everywhere</strong>
            <small>One platform. Every workforce.</small>
          </div>
        </div>

        <div className="welcome-content">
          <p className="eyebrow">
            WORKFORCE MANAGEMENT, REIMAGINED
          </p>
          <h1>
            Better workdays.
            <br />
            <span>Brighter futures.</span>
          </h1>
          <p className="welcome-description">
            Bring your people, time, and payroll together in one
            beautifully simple platform—so your team can focus on
            what matters.
          </p>
        </div>

        <ul className="feature-list" aria-label="SalaryAI features">
          <li>
            <span className="feature-icon" aria-hidden="true">
              ♙
            </span>
            <span>
              <strong>People, together</strong>
              <small>Employee management made easy</small>
            </span>
          </li>
          <li>
            <span className="feature-icon" aria-hidden="true">
              ◷
            </span>
            <span>
              <strong>Time, accounted for</strong>
              <small>Attendance and leave in sync</small>
            </span>
          </li>
          <li>
            <span className="feature-icon" aria-hidden="true">
              ▤
            </span>
            <span>
              <strong>Payroll, simplified</strong>
              <small>Accurate, compliant, on time</small>
            </span>
          </li>
          <li>
            <span className="feature-icon" aria-hidden="true">
              ✧
            </span>
            <span>
              <strong>Insights, powered by AI</strong>
              <small>Make confident decisions</small>
            </span>
          </li>
        </ul>

        <div className="workforce-visual">
          <img
            src={workforcePhoto}
            alt="Colleagues collaborating around a laptop in a modern office"
          />
          <div className="photo-shade" aria-hidden="true" />

          <div className="dashboard-card" aria-hidden="true">
            <span className="dashboard-label">
              TODAY&apos;S ATTENDANCE
            </span>
            <strong>94.1%</strong>
            <span className="dashboard-status">
              <i aria-hidden="true" />
              Looking good across your team
            </span>
            <div className="dashboard-progress" aria-hidden="true">
              <span />
            </div>
          </div>

          <div className="photo-caption">
            <span className="caption-dot" aria-hidden="true" />
            A clearer view of your whole team
          </div>
        </div>

        <footer className="panel-footer">
          <span className="footer-mark" aria-hidden="true">
            ◎
          </span>
          <span>Built for people. Ready for anywhere.</span>
        </footer>
      </section>

      <section className="form-panel" aria-label="Sign in">
        <div className="language-selector">
          <span aria-hidden="true">◎</span>
          <select aria-label="Language" defaultValue="English">
            <option>English</option>
            <option>हिन्दी</option>
          </select>
        </div>

        <div className="login-container">
          <header className="form-brand">
            <div className="brand-icon" aria-hidden="true">
              S
            </div>
            <div>
              <div className="form-brand-name">
                Salary<span>AI</span>
              </div>
              <p>Workforce · Payroll · AI Insights</p>
            </div>
          </header>

          <div className="form-heading">
            <p className="form-eyebrow">
              YOUR WORKSPACE AWAITS
            </p>
            <h2>Welcome back</h2>
            <p className="form-description">
              Sign in to your organization&apos;s secure workspace.
            </p>
          </div>

          <form onSubmit={(event) => event.preventDefault()}>
            <label htmlFor="organization">
              Company or organization
            </label>
            <input
              id="organization"
              name="organization"
              type="text"
              placeholder="e.g. Acme Technologies"
              autoComplete="organization"
              required
            />

            <label htmlFor="email">Work email address</label>
            <input
              id="email"
              name="email"
              type="email"
              placeholder="you@company.com"
              autoComplete="username"
              required
            />

            <div className="password-label-row">
              <label htmlFor="password">Password</label>
              <a href="#forgot-password">
                Forgot password?
              </a>
            </div>

            <div className="password-field">
              <input
                id="password"
                name="password"
                type={showPassword ? 'text' : 'password'}
                placeholder="Enter your password"
                autoComplete="current-password"
                required
              />

              <button
                className="password-toggle"
                type="button"
                aria-label={
                  showPassword ? 'Hide password' : 'Show password'
                }
                aria-pressed={showPassword}
                onClick={() =>
                  setShowPassword((visible) => !visible)
                }
              >
                <svg
                  aria-hidden="true"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.7"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  {showPassword ? (
                    <>
                      <path d="M3 3l18 18" />
                      <path d="M10.6 10.6a2 2 0 002.8 2.8" />
                      <path d="M9.9 5.2A10.8 10.8 0 0112 5c5 0 8.5 4 9.5 7-.4 1.2-1.2 2.4-2.3 3.4" />
                      <path d="M6.2 6.2C3.9 7.5 2.7 9.5 2.5 12c.8 2.6 4.3 7 9.5 7 1 0 2-.2 2.9-.5" />
                    </>
                  ) : (
                    <>
                      <path d="M2.5 12s3.3-7 9.5-7 9.5 7 9.5 7-3.3 7-9.5 7-9.5-7-9.5-7z" />
                      <circle cx="12" cy="12" r="2.5" />
                    </>
                  )}
                </svg>
              </button>
            </div>

            <label className="remember-option">
              <input type="checkbox" name="remember" />
              <span>Remember me</span>
            </label>

            <button className="sign-in-button" type="submit">
              <span>Sign in</span>
              <span className="sign-in-arrow" aria-hidden="true">
                →
              </span>
            </button>
          </form>

          <div className="separator">
            <span>NEW TO SALARYAI?</span>
          </div>

          <Link className="create-organization" to="/register">
            <span className="organization-icon" aria-hidden="true">
              ＋
            </span>
            <span>
              <strong>Create your organization</strong>
              <small>Start your journey with SalaryAI</small>
            </span>
            <span className="organization-arrow" aria-hidden="true">
              ↗
            </span>
          </Link>

          <p className="security-note">
            <span aria-hidden="true">✓</span>
            Your data is protected by access controls.
          </p>
        </div>

        <footer className="form-footer">
          <a href="#privacy">Privacy</a>
          <span aria-hidden="true">·</span>
          <a href="#terms">Terms</a>
          <span className="form-footer-copy">© 2026 SalaryAI</span>
        </footer>
      </section>
    </main>
  )
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
    </Routes>
  )
}

export default App
