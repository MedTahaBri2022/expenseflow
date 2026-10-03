import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/client'
import type { ClaimStatus, ClaimSummary, Page } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import StatusBadge from '../components/StatusBadge'
import { STATUS_LABELS, formatDate, formatMoney } from '../format'

const STATUSES = Object.keys(STATUS_LABELS) as ClaimStatus[]

/** Filters live in the URL: a filtered list can be bookmarked or shared. */
export default function ClaimsPage() {
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? ''
  const mine = params.get('mine') === 'true'
  const page = Number(params.get('page') ?? '0')
  const q = params.get('q') ?? ''
  const [search, setSearch] = useState(q)

  const setParam = (key: string, value: string) =>
    setParams((current) => {
      const next = new URLSearchParams(current)
      if (value) next.set(key, value)
      else next.delete(key)
      if (key !== 'page') next.delete('page')
      return next
    })

  // Debounced search: one request when the user stops typing.
  useEffect(() => {
    const timer = setTimeout(() => {
      if (search !== q) setParam('q', search.trim())
    }, 300)
    return () => clearTimeout(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search])

  const query = new URLSearchParams({ page: String(page), size: '15' })
  if (status) query.set('status', status)
  if (q) query.set('q', q)
  if (mine) query.set('mine', 'true')

  const { data, isPending, isError, error } = useQuery({
    queryKey: ['claims', query.toString()],
    queryFn: () => api<Page<ClaimSummary>>(`/api/claims?${query}`),
    placeholderData: keepPreviousData,
  })

  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <h1 className="mr-auto text-2xl font-semibold">Claims</h1>
        <input aria-label="Search titles" placeholder="Search titles" value={search}
          onChange={(e) => setSearch(e.target.value)} className="rounded-md border border-slate-300 px-3 py-1.5" />
        <select aria-label="Status" value={status} onChange={(e) => setParam('status', e.target.value)}
          className="rounded-md border border-slate-300 px-2 py-1.5">
          <option value="">All statuses</option>
          {STATUSES.map((s) => <option key={s} value={s}>{STATUS_LABELS[s]}</option>)}
        </select>
        {user?.role !== 'EMPLOYEE' && (
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={mine} onChange={(e) => setParam('mine', e.target.checked ? 'true' : '')} />
            Only mine
          </label>
        )}
        <Link to="/claims/new" className="rounded-md bg-brand-700 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-900">
          New claim
        </Link>
      </div>

      {isError && <p role="alert" className="text-rose-700">{(error as Error).message}</p>}
      {isPending ? (
        <p className="text-slate-500">Loading…</p>
      ) : data && data.content.length === 0 ? (
        <p className="rounded-lg border border-dashed border-slate-300 p-8 text-center text-slate-500">No claim matches.</p>
      ) : data && (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-600">
              <tr>
                <th className="px-4 py-2">Title</th>
                <th className="px-4 py-2">Owner</th>
                <th className="px-4 py-2">Status</th>
                <th className="px-4 py-2">Created</th>
                <th className="px-4 py-2 text-right">Total</th>
              </tr>
            </thead>
            <tbody>
              {data.content.map((claim) => (
                <tr key={claim.id} className="border-t border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2"><Link className="font-medium text-brand-700 hover:underline" to={`/claims/${claim.id}`}>{claim.title}</Link></td>
                  <td className="px-4 py-2">{claim.owner.fullName}</td>
                  <td className="px-4 py-2"><StatusBadge status={claim.status} /></td>
                  <td className="px-4 py-2">{formatDate(claim.createdAt)}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{formatMoney(claim.total)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {data && data.totalPages > 1 && (
        <div className="flex items-center gap-3 text-sm">
          <button disabled={page === 0} onClick={() => setParam('page', String(page - 1))}
            className="rounded border px-3 py-1 disabled:opacity-40">Previous</button>
          <span>Page {page + 1} of {data.totalPages} · {data.totalElements} claims</span>
          <button disabled={page + 1 >= data.totalPages} onClick={() => setParam('page', String(page + 1))}
            className="rounded border px-3 py-1 disabled:opacity-40">Next</button>
        </div>
      )}
    </section>
  )
}
