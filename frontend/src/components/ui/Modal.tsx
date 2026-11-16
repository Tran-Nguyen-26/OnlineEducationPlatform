import { type ReactNode } from 'react'
import { X } from 'lucide-react'

interface ModalProps {
  title: string
  open: boolean
  onClose: () => void
  children: ReactNode
}

export function Modal({ title, open, onClose, children }: ModalProps) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/45 p-4 backdrop-blur-sm">
      <div className="w-full max-w-lg animate-pop rounded-2xl bg-white p-6 shadow-depth ring-1 ring-slate-200/70">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold text-slate-900">{title}</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-slate-700" aria-label="Đóng">
            <X size={20} />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
