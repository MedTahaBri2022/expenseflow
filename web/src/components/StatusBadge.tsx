import type { ClaimStatus } from '../api/types'
import { STATUS_LABELS } from '../format'

const COLORS: Record<ClaimStatus, string> = {
  DRAFT: 'bg-slate-100 text-slate-700',
  SUBMITTED: 'bg-sky-100 text-sky-800',
  PENDING_FINANCE: 'bg-amber-100 text-amber-800',
  APPROVED: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-rose-100 text-rose-800',
  PAID: 'bg-brand-100 text-brand-900',
}

export default function StatusBadge({ status }: { status: ClaimStatus }) {
  return (
    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-medium ${COLORS[status]}`}>
      {STATUS_LABELS[status]}
    </span>
  )
}
