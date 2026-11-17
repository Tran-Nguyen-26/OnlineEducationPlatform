import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Activity, Inbox, Layers, RotateCcw, SkullIcon } from 'lucide-react'
import { apiClient, getApiErrorMessage } from '../../lib/apiClient'
import { Button } from '../../components/ui/Button'
import { PageHeader, StatCard } from '../../components/ui/Page'

interface QueueInfo { name: string; messages: number; consumers: number }
interface OpsStatus {
  outbox: { pending: number; processed: number; failed: number; oldestPendingAgeSeconds: number }
  notificationQueue: QueueInfo
  deadLetterQueue: QueueInfo
}

/** Health of the asynchronous pipeline (outbox → RabbitMQ → worker) with the two recovery actions. */
export function OpsPage() {
  const queryClient = useQueryClient()
  const status = useQuery({
    queryKey: ['admin', 'ops'],
    refetchInterval: 5000,
    queryFn: () => apiClient.get<OpsStatus>('/api/admin/ops/status').then((r) => r.data),
  })
  const retry = useMutation({
    mutationFn: () => apiClient.post('/api/admin/ops/outbox/retry-failed'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'ops'] }),
  })
  const replay = useMutation({
    mutationFn: () => apiClient.post('/api/admin/ops/dlq/replay?max=200'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'ops'] }),
  })
  const s = status.data
  const healthy = s && s.outbox.failed === 0 && s.deadLetterQueue.messages === 0 && s.outbox.oldestPendingAgeSeconds < 60

  return (
    <div>
      <PageHeader
        title="Vận hành"
        subtitle="Theo dõi đường truyền thông báo bất đồng bộ: hộp thư outbox → RabbitMQ → worker. Tự làm mới mỗi 5 giây."
      />
      {status.isError && <p className="mb-4 text-red-600">{getApiErrorMessage(status.error)}</p>}
      {s && (
        <div className={`mb-6 rounded-xl p-4 text-sm font-medium ring-1 ${healthy ? 'bg-emerald-50 text-emerald-800 ring-emerald-200' : 'bg-amber-50 text-amber-800 ring-amber-200'}`}>
          {healthy ? 'Đường truyền thông báo đang khỏe mạnh.' : 'Có mục cần chú ý: xem các số liệu bên dưới.'}
        </div>
      )}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatCard label="Outbox đang chờ gửi" value={s?.outbox.pending ?? '—'} icon={Inbox} tone="sky" hint={s ? `cũ nhất ${s.outbox.oldestPendingAgeSeconds}s` : undefined} />
        <StatCard label="Outbox lỗi (đã bỏ cuộc)" value={s?.outbox.failed ?? '—'} icon={Activity} tone={s && s.outbox.failed > 0 ? 'rose' : 'teal'} />
        <StatCard label="Hàng đợi thông báo" value={s?.notificationQueue.messages ?? '—'} icon={Layers} tone="indigo" hint={s ? `${s.notificationQueue.consumers} worker đang nghe` : undefined} />
        <StatCard label="Hàng đợi tin lỗi (DLQ)" value={s?.deadLetterQueue.messages ?? '—'} icon={SkullIcon} tone={s && s.deadLetterQueue.messages > 0 ? 'rose' : 'teal'} />
      </div>

      <div className="mt-6 grid gap-4 lg:grid-cols-2">
        <div className="surface p-5">
          <h2 className="font-bold text-slate-900">Outbox lỗi</h2>
          <p className="mt-1 text-sm text-slate-500">Các sự kiện gửi thất bại quá nhiều lần được "đỗ" lại. Nhấn để đưa chúng vào hàng đợi gửi lại.</p>
          <Button className="mt-4" variant="secondary" disabled={retry.isPending || !s || s.outbox.failed === 0} onClick={() => retry.mutate()}>
            <RotateCcw size={16} /> Gửi lại {s?.outbox.failed ?? 0} sự kiện lỗi
          </Button>
          {retry.isSuccess && <p className="mt-2 text-sm text-green-700">Đã đưa vào hàng đợi gửi lại.</p>}
        </div>
        <div className="surface p-5">
          <h2 className="font-bold text-slate-900">Tin nhắn trong DLQ</h2>
          <p className="mt-1 text-sm text-slate-500">Thông báo worker xử lý lỗi sau 5 lần thử. Sau khi khắc phục nguyên nhân, phát lại để xử lý.</p>
          <Button className="mt-4" variant="secondary" disabled={replay.isPending || !s || s.deadLetterQueue.messages === 0} onClick={() => replay.mutate()}>
            <RotateCcw size={16} /> Phát lại {s?.deadLetterQueue.messages ?? 0} tin
          </Button>
          {replay.isSuccess && <p className="mt-2 text-sm text-green-700">Đã phát lại.</p>}
        </div>
      </div>
    </div>
  )
}
