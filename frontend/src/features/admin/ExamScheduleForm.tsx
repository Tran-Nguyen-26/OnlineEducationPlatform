import { useMutation, useQuery } from '@tanstack/react-query'
import { fetchQuizzes, saveExamSchedule } from '@/api/quiz'
import type { FormEvent } from 'react'

export default function ExamScheduleForm({ courseId }: { courseId: string }) {
  const quizzes = useQuery({ queryKey: ['exam-schedule-quizzes', courseId], queryFn: () => fetchQuizzes(courseId, 1, 100) })
  const save = useMutation({
    mutationFn: ({ quizId, startsAt, endsAt, lateEntryMinutes }: {
      quizId: string; startsAt: string; endsAt: string; lateEntryMinutes: number
    }) => saveExamSchedule(courseId, quizId, { startsAt, endsAt, lateEntryMinutes }),
  })
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    save.mutate({
      quizId: String(form.get('quiz')),
      startsAt: new Date(String(form.get('start'))).toISOString(),
      endsAt: new Date(String(form.get('end'))).toISOString(),
      lateEntryMinutes: Number(form.get('late')),
    })
  }
  const input = 'rounded border border-slate-300 px-2 py-2'
  return (
    <form onSubmit={submit} className="space-y-3 rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="font-semibold">Lên lịch thi đồng loạt</h2>
      <fieldset disabled={save.isPending || !quizzes.data?.items.length} className="flex flex-wrap items-end gap-2">
        <label className="grid gap-1 text-sm">Đề thi
          <select name="quiz" className={input} required defaultValue="">
            <option value="" disabled>Chọn đề đã tạo</option>
            {quizzes.data?.items.map((quiz) => <option key={quiz.id} value={quiz.id}>{quiz.title}</option>)}
          </select>
        </label>
        <label className="grid gap-1 text-sm">Bắt đầu<input name="start" type="datetime-local" className={input} required /></label>
        <label className="grid gap-1 text-sm">Kết thúc<input name="end" type="datetime-local" className={input} required /></label>
        <label className="grid gap-1 text-sm">Vào muộn (phút)<input name="late" type="number" min={0} max={60} defaultValue={15}
          className={input + ' w-28'} required /></label>
        <button className="rounded bg-teal-700 px-3 py-2 text-sm text-white">Lưu lịch</button>
      </fieldset>
      {quizzes.isError && <p role="alert">Không tải được danh sách đề thi.</p>}
      {quizzes.isSuccess && !quizzes.data.items.length && <p className="text-sm text-slate-600">Hãy tạo đề trong phần quản lý bài kiểm tra.</p>}
      {save.isError && <p role="alert" className="text-sm text-red-700">{save.error.message}</p>}
      {save.isSuccess && <p role="status" className="text-sm text-teal-800">Đã lưu lịch thi.</p>}
    </form>
  )
}
