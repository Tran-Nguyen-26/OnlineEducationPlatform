import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, getApiErrorMessage } from '../../../lib/apiClient'
import type { BankQuestionResponse } from '../../../types/api'
import { Modal } from '../../../components/ui/Modal'
import { Button } from '../../../components/ui/Button'

/** Pick reusable questions from the course's bank into a quiz; unused questions can be deleted from the bank. */
export function QuestionBankModal({ courseId, quizId, nextPosition, existingIds, onClose }: {
  courseId: string; quizId: string; nextPosition: number; existingIds: string[]; onClose: () => void
}) {
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const bank = useQuery({
    queryKey: ['courses', courseId, 'question-bank'],
    queryFn: () => apiClient.get<BankQuestionResponse[]>(`/api/admin/courses/${courseId}/question-bank`).then((r) => r.data),
  })
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'question-bank'] })
    queryClient.invalidateQueries({ queryKey: ['quizzes', quizId, 'questions'] })
    queryClient.invalidateQueries({ queryKey: ['courses', courseId, 'quizzes'] })
  }
  const add = useMutation({
    mutationFn: (questionId: string) => apiClient.post(`/api/admin/quizzes/${quizId}/questions/from-bank`, { questionId, position: nextPosition, points: 1 }),
    onSuccess: () => { setError(null); refresh() },
    onError: (e) => setError(getApiErrorMessage(e)),
  })
  const del = useMutation({
    mutationFn: (questionId: string) => apiClient.delete(`/api/admin/question-bank/${questionId}`),
    onSuccess: () => { setError(null); refresh() },
    onError: (e) => setError(getApiErrorMessage(e)),
  })
  const fileRef = useRef<HTMLInputElement>(null)
  const [importMsg, setImportMsg] = useState<string | null>(null)

  async function exportBank(format: 'csv' | 'json') {
    try {
      const res = await apiClient.get(`/api/admin/courses/${courseId}/question-bank/export`, { params: { format }, responseType: 'blob' })
      const url = URL.createObjectURL(res.data as Blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `question-bank.${format}`
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) { setError(getApiErrorMessage(e)) }
  }

  async function importFile(file: File) {
    setError(null); setImportMsg(null)
    try {
      const text = await file.text()
      const isJson = file.name.toLowerCase().endsWith('.json')
      const res = await apiClient.post<{ imported: number; skipped: number; errors: string[] }>(
        `/api/admin/courses/${courseId}/question-bank/${isJson ? 'import' : 'import-csv'}`,
        isJson ? JSON.parse(text) : text,
        { headers: { 'Content-Type': isJson ? 'application/json' : 'text/csv' } },
      )
      setImportMsg(`Đã nhập ${res.data.imported} câu, bỏ qua ${res.data.skipped}.` + (res.data.errors.length ? ' ' + res.data.errors.slice(0, 3).join('; ') : ''))
      refresh()
    } catch (e) { setError(getApiErrorMessage(e)) }
    finally { if (fileRef.current) fileRef.current.value = '' }
  }

  const items = (bank.data ?? []).filter((q) => q.questionText.toLowerCase().includes(search.trim().toLowerCase()))

  return (
    <Modal title="Ngân hàng câu hỏi của khóa" open onClose={onClose}>
      <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Tìm câu hỏi..." className="mb-3 h-9 w-full rounded-md border border-slate-300 px-3 text-sm" />
      <div className="mb-3 flex flex-wrap items-center gap-2">
        <Button size="sm" variant="secondary" onClick={() => exportBank('csv')}>Xuất CSV</Button>
        <Button size="sm" variant="secondary" onClick={() => exportBank('json')}>Xuất JSON</Button>
        <Button size="sm" variant="secondary" onClick={() => fileRef.current?.click()}>Nhập từ CSV/JSON</Button>
        <input ref={fileRef} type="file" accept=".csv,.json" className="hidden" onChange={(e) => { const f = e.target.files?.[0]; if (f) void importFile(f) }} />
      </div>
      {importMsg && <p className="mb-2 text-sm text-emerald-700">{importMsg}</p>}
      {error && <p className="mb-2 text-sm text-red-600">{error}</p>}
      <div className="max-h-96 space-y-2 overflow-y-auto">
        {bank.isLoading && <p className="text-slate-400">Đang tải...</p>}
        {items.map((q) => {
          const inQuiz = existingIds.includes(q.questionId)
          return (
            <div key={q.questionId} className="rounded-md border border-slate-100 p-3">
              <p className="text-sm text-slate-800">{q.questionText}</p>
              <p className="mt-0.5 text-xs text-slate-400">{q.questionType} · dùng trong {q.usedInQuizzes} quiz</p>
              <div className="mt-2 flex gap-2">
                <Button size="sm" variant="secondary" disabled={inQuiz || add.isPending} onClick={() => add.mutate(q.questionId)}>{inQuiz ? 'Đã có trong quiz' : 'Thêm vào quiz'}</Button>
                {q.usedInQuizzes === 0 && <Button size="sm" variant="danger" disabled={del.isPending} onClick={() => del.mutate(q.questionId)}>Xóa khỏi ngân hàng</Button>}
              </div>
            </div>
          )
        })}
        {!bank.isLoading && items.length === 0 && <p className="text-sm text-slate-400">Ngân hàng chưa có câu hỏi nào. Câu hỏi bạn tạo trong các quiz sẽ tự động vào đây.</p>}
      </div>
      <div className="mt-4 flex justify-end"><Button onClick={onClose}>Đóng</Button></div>
    </Modal>
  )
}
