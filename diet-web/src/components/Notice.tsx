import { CircleAlert, CircleCheck } from 'lucide-react'

export function Notice({ message, tone = 'success' }: { message: string; tone?: 'success' | 'error' }) {
  if (!message) return null
  return <div className={`notice ${tone}`}>{tone === 'success' ? <CircleCheck size={17} /> : <CircleAlert size={17} />}{message}</div>
}

