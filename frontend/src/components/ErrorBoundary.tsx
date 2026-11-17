import { Component, type ErrorInfo, type ReactNode } from 'react'

interface State { error: Error | null }

/** Last line of defence: a render crash shows a recoverable screen instead of a blank page. */
export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('UI crash', error, info.componentStack)
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <div className="flex min-h-screen items-center justify-center p-6">
        <div className="surface max-w-md p-8 text-center">
          <h1 className="text-xl font-bold text-slate-900">Đã xảy ra lỗi không mong muốn</h1>
          <p className="mt-2 text-sm text-slate-500">Rất tiếc, trang gặp sự cố. Dữ liệu của bạn vẫn an toàn.</p>
          <div className="mt-5 flex justify-center gap-3">
            <button className="rounded-xl bg-brand-gradient px-4 py-2 text-sm font-semibold text-white" onClick={() => window.location.reload()}>Tải lại trang</button>
            <a className="rounded-xl bg-white px-4 py-2 text-sm font-semibold text-slate-800 ring-1 ring-slate-200" href="/">Về trang chủ</a>
          </div>
        </div>
      </div>
    )
  }
}
