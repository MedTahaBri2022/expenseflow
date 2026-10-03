import { Navigate, Route, Routes } from 'react-router'
import { useAuth } from './auth/AuthContext'
import Layout from './components/Layout'
import ClaimPage from './pages/ClaimPage'
import ClaimsPage from './pages/ClaimsPage'
import LoginPage from './pages/LoginPage'
import NewClaimPage from './pages/NewClaimPage'
import ReportsPage from './pages/ReportsPage'

export default function App() {
  const { user, loading } = useAuth()

  if (loading) {
    return <p className="p-8 text-slate-500">Loading…</p>
  }
  if (!user) {
    return (
      <Routes>
        <Route path="*" element={<LoginPage />} />
      </Routes>
    )
  }
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/claims" replace />} />
        <Route path="/claims" element={<ClaimsPage />} />
        <Route path="/claims/new" element={<NewClaimPage />} />
        <Route path="/claims/:id" element={<ClaimPage />} />
        {user.role === 'FINANCE' && <Route path="/reports" element={<ReportsPage />} />}
        <Route path="*" element={<Navigate to="/claims" replace />} />
      </Route>
    </Routes>
  )
}
