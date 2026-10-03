import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/AuthContext'

const ROLE_LABELS = { EMPLOYEE: 'Employee', MANAGER: 'Manager', FINANCE: 'Finance' } as const

export default function Layout() {
  const { user, logout } = useAuth()
  const link = ({ isActive }: { isActive: boolean }) =>
    `rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-brand-700 text-white' : 'text-brand-50 hover:bg-brand-700/60'}`

  return (
    <div className="min-h-screen">
      <header className="bg-brand-900 text-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-4 px-4 py-3">
          <span className="text-lg font-semibold tracking-tight">ExpenseFlow</span>
          <nav className="flex gap-1">
            <NavLink to="/claims" end className={link}>Claims</NavLink>
            <NavLink to="/claims/new" className={link}>New claim</NavLink>
            {user?.role === 'FINANCE' && <NavLink to="/reports" className={link}>Reports</NavLink>}
          </nav>
          <div className="ml-auto flex items-center gap-3 text-sm">
            <span>
              {user?.fullName} <span className="rounded bg-white/15 px-2 py-0.5 text-xs">{user && ROLE_LABELS[user.role]}</span>
            </span>
            <button onClick={logout} className="rounded-md border border-white/30 px-3 py-1 hover:bg-white/10">
              Sign out
            </button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
