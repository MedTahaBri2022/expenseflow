import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError, api } from '../api/client'
import { CATEGORIES, type ClaimAction, type ClaimDetail, type ClaimEvent } from '../api/types'
import ClaimForm from '../components/ClaimForm'
import StatusBadge from '../components/StatusBadge'
import { ACTION_LABELS, STATUS_LABELS, formatDate, formatDateTime, formatMoney } from '../format'

export default function ClaimPage() {
  const { id } = useParams()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [comment, setComment] = useState('')
  const [actionError, setActionError] = useState<string | null>(null)

  const claim = useQuery({ queryKey: ['claim', id], queryFn: () => api<ClaimDetail>(`/api/claims/${id}`) })
  const events = useQuery({ queryKey: ['claim', id, 'events'], queryFn: () => api<ClaimEvent[]>(`/api/claims/${id}/events`) })

  const refresh = (updated: ClaimDetail) => {
    queryClient.setQueryData(['claim', id], updated)
    queryClient.invalidateQueries({ queryKey: ['claim', id, 'events'] })
    queryClient.invalidateQueries({ queryKey: ['claims'] })
  }

  const act = useMutation({
    mutationFn: (action: ClaimAction) =>
      api<ClaimDetail>(`/api/claims/${id}/${action.toLowerCase()}`, {
        method: 'POST',
        // The version read on screen: if someone changed the claim since, the API answers 409.
        body: { version: claim.data!.version, comment: comment || undefined },
      }),
    onSuccess: (updated) => {
      setComment('')
      setActionError(null)
      refresh(updated)
    },
    onError: (error) => {
      setActionError(error.message)
      if (error instanceof ApiError && error.status === 409) {
        queryClient.invalidateQueries({ queryKey: ['claim', id] })
      }
    },
  })

  if (claim.isPending) return <p className="text-slate-500">Loading…</p>
  if (claim.isError) {
    const notFound = claim.error instanceof ApiError && claim.error.status === 404
    return <p role="alert" className="text-rose-700">{notFound ? 'This claim does not exist or is not visible to you.' : claim.error.message}</p>
  }
  const data = claim.data
  const label = (value: string) => CATEGORIES.find((c) => c.value === value)?.label ?? value

  return (
    <section className="space-y-6">
      <Link to="/claims" className="text-sm text-brand-700 hover:underline">← All claims</Link>
      <div className="flex flex-wrap items-start gap-4">
        <div className="mr-auto">
          <h1 className="text-2xl font-semibold">{data.title}</h1>
          <p className="text-sm text-slate-500">
            {data.owner.fullName} · created {formatDate(data.createdAt)}
            {data.submittedAt && <> · submitted {formatDate(data.submittedAt)}</>}
          </p>
        </div>
        <StatusBadge status={data.status} />
        <p className="text-2xl font-semibold tabular-nums">{formatMoney(data.total, data.currency)}</p>
      </div>

      {editing ? (
        <ClaimForm
          initial={{ title: data.title, items: data.items }}
          submitLabel="Save changes"
          onSubmit={async (values) => {
            const updated = await api<ClaimDetail>(`/api/claims/${id}`, {
              method: 'PUT',
              body: { ...values, version: data.version },
            })
            refresh(updated)
            setEditing(false)
          }}
        />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-slate-600">
              <tr>
                <th className="px-4 py-2">Date</th>
                <th className="px-4 py-2">Category</th>
                <th className="px-4 py-2">Description</th>
                <th className="px-4 py-2 text-right">Amount</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((item) => (
                <tr key={item.id} className="border-t border-slate-100">
                  <td className="px-4 py-2">{formatDate(item.spentOn)}</td>
                  <td className="px-4 py-2">{label(item.category)}</td>
                  <td className="px-4 py-2">{item.description}</td>
                  <td className="px-4 py-2 text-right tabular-nums">{formatMoney(item.amount, data.currency)}</td>
                </tr>
              ))}
              {data.items.length === 0 && (
                <tr><td colSpan={4} className="px-4 py-6 text-center text-slate-500">No item yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {(data.allowedActions.length > 0 || data.editable) && !editing && (
        <div className="space-y-3 rounded-lg border border-slate-200 bg-white p-4">
          <h2 className="font-medium">What you can do</h2>
          {data.allowedActions.some((a) => a === 'APPROVE' || a === 'REJECT' || a === 'PAY') && (
            <textarea aria-label="Comment" value={comment} onChange={(e) => setComment(e.target.value)} maxLength={500}
              placeholder="Comment (required to reject)" className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm" />
          )}
          <div className="flex flex-wrap gap-2">
            {data.editable && (
              <button onClick={() => setEditing(true)} className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50">
                Edit
              </button>
            )}
            {data.allowedActions.map((action) => (
              <button key={action} disabled={act.isPending} onClick={() => act.mutate(action)}
                className={`rounded-md px-3 py-1.5 text-sm font-medium text-white disabled:opacity-60 ${action === 'REJECT' ? 'bg-rose-700 hover:bg-rose-800' : 'bg-brand-700 hover:bg-brand-900'}`}>
                {ACTION_LABELS[action]}
              </button>
            ))}
          </div>
          {actionError && <p role="alert" className="text-sm text-rose-700">{actionError}</p>}
        </div>
      )}

      <div>
        <h2 className="mb-2 font-medium">History</h2>
        <ol className="space-y-2 border-l-2 border-brand-100 pl-4">
          {events.data?.map((event) => (
            <li key={event.id} className="text-sm">
              <span className="font-medium">{event.actor.fullName}</span>{' '}
              {event.fromStatus ? <>moved it from {STATUS_LABELS[event.fromStatus]} to <b>{STATUS_LABELS[event.toStatus]}</b></> : 'created it'}
              <span className="text-slate-500"> · {formatDateTime(event.occurredAt)}</span>
              {event.comment && <p className="text-slate-600">“{event.comment}”</p>}
            </li>
          ))}
        </ol>
      </div>
    </section>
  )
}
