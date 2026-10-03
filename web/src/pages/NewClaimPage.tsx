import { useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { api } from '../api/client'
import type { ClaimDetail } from '../api/types'
import ClaimForm from '../components/ClaimForm'

export default function NewClaimPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  return (
    <section className="space-y-4">
      <h1 className="text-2xl font-semibold">New expense claim</h1>
      <p className="text-sm text-slate-500">
        The claim is saved as a draft. You can still change it before submitting it for approval.
      </p>
      <ClaimForm
        submitLabel="Save draft"
        onSubmit={async (values) => {
          const created = await api<ClaimDetail>('/api/claims', { method: 'POST', body: values })
          await queryClient.invalidateQueries({ queryKey: ['claims'] })
          navigate(`/claims/${created.id}`)
        }}
      />
    </section>
  )
}
