import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/client'
import { CATEGORIES, type Summary } from '../api/types'
import StatusBadge from '../components/StatusBadge'
import { formatMoney } from '../format'

const year = new Date().getFullYear()

export default function ReportsPage() {
  const [from, setFrom] = useState(`${year}-01-01`)
  const [to, setTo] = useState(new Date().toISOString().slice(0, 10))

  const { data, isError, error } = useQuery({
    queryKey: ['summary', from, to],
    queryFn: () => api<Summary>(`/api/reports/summary?from=${from}&to=${to}`),
    enabled: Boolean(from && to),
  })
  const maxCategory = Math.max(1, ...(data?.byCategory.map((c) => c.amount) ?? [1]))
  const label = (value: string) => CATEGORIES.find((c) => c.value === value)?.label ?? value

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-end gap-3">
        <h1 className="mr-auto text-2xl font-semibold">Finance report</h1>
        <label className="text-sm">From <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} className="ml-1 rounded border px-2 py-1" /></label>
        <label className="text-sm">To <input type="date" value={to} onChange={(e) => setTo(e.target.value)} className="ml-1 rounded border px-2 py-1" /></label>
      </div>
      {isError && <p role="alert" className="text-rose-700">{(error as Error).message}</p>}
      {data && (
        <div className="grid gap-6 md:grid-cols-2">
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <h2 className="mb-3 font-medium">Claims by status (submitted in the period)</h2>
            <ul className="space-y-2 text-sm">
              {data.byStatus.map((row) => (
                <li key={row.status} className="flex items-center gap-3">
                  <StatusBadge status={row.status} />
                  <span className="text-slate-500">{row.claims} claims</span>
                  <span className="ml-auto tabular-nums">{formatMoney(row.amount)}</span>
                </li>
              ))}
              {data.byStatus.length === 0 && <li className="text-slate-500">No claim in this period.</li>}
            </ul>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4">
            <h2 className="mb-3 font-medium">Approved and paid, by category</h2>
            <ul className="space-y-2 text-sm">
              {data.byCategory.map((row) => (
                <li key={row.category}>
                  <div className="flex justify-between"><span>{label(row.category)}</span><span className="tabular-nums">{formatMoney(row.amount)}</span></div>
                  <div className="mt-1 h-2 rounded bg-slate-100">
                    <div className="h-2 rounded bg-brand-600" style={{ width: `${(row.amount / maxCategory) * 100}%` }} />
                  </div>
                </li>
              ))}
              {data.byCategory.length === 0 && <li className="text-slate-500">Nothing approved yet.</li>}
            </ul>
          </div>
          <div className="rounded-lg border border-slate-200 bg-white p-4 md:col-span-2">
            <h2 className="mb-3 font-medium">Approved and paid, by month of submission</h2>
            <table className="w-full text-sm">
              <tbody>
                {data.approvedByMonth.map((row) => (
                  <tr key={row.month} className="border-t border-slate-100">
                    <td className="py-1.5">{row.month}</td>
                    <td className="py-1.5 text-slate-500">{row.claims} claims</td>
                    <td className="py-1.5 text-right tabular-nums">{formatMoney(row.amount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </section>
  )
}
