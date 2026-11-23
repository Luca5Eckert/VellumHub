import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Moon, Sun, X } from 'lucide-react'
import type { Book } from './api'
import { Button } from './Button'
import mark from './assets/vellum-mark.png'

const genreLabels: Record<string, string> = {
  'Science Fiction': 'Ficção científica', Fantasy: 'Fantasia', Horror: 'Terror',
  Romance: 'Romance', Thriller: 'Suspense', Biography: 'Biografia', History: 'História',
  'Self-Help': 'Autoajuda', Dystopian: 'Distopia', Adventure: 'Aventura',
}

export const genreLabel = (genre: string) => genreLabels[genre] ?? genre

export function Logo() {
  return <span className="logo"><img src={mark} alt="" width="28" height="28" />VellumHub</span>
}

export function ThemeButton({ dark, toggle }: { dark: boolean; toggle: () => void }) {
  return <button type="button" className="icon-button" aria-label="Tema escuro" aria-pressed={dark} onClick={toggle}>
    {dark ? <Sun size={19} aria-hidden /> : <Moon size={19} aria-hidden />}
  </button>
}

export function Cover({ book, index = 0 }: { book: Book; index?: number }) {
  const [failedUrl, setFailedUrl] = useState('')
  const url = book.coverUrl
  return url && /^https?:\/\//i.test(url) && url !== failedUrl
    ? <img className="cover" src={url} alt="" loading="lazy" referrerPolicy="no-referrer" onError={() => setFailedUrl(url)} />
    : <span className="cover cover-placeholder" data-tone={index % 6} aria-hidden="true" />
}

export function ScreenHeading({ children, className }: { children: ReactNode; className?: string }) {
  const ref = useRef<HTMLHeadingElement>(null)
  useEffect(() => { ref.current?.focus({ preventScroll: true }) }, [])
  return <h1 ref={ref} tabIndex={-1} className={className}>{children}</h1>
}

export function Dialog({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    dialog?.showModal()
    return () => { dialog?.close() }
  }, [])
  return <dialog ref={ref} className="dialog" aria-labelledby="dialog-title" onCancel={event => {
    event.preventDefault()
    onClose()
  }}>
    <header className="dialog-heading">
      <h2 id="dialog-title">{title}</h2>
      <button className="icon-button" aria-label="Fechar" onClick={onClose}><X size={19} aria-hidden /></button>
    </header>
    {children}
  </dialog>
}

export function Retry({ message, retry }: { message: string; retry: () => void }) {
  return <div className="empty"><p role="alert">{message}</p>
    <Button variant="secondary" onClick={retry}>Tentar carregar novamente</Button>
  </div>
}
