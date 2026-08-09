function dateOf(value: string): Date | null {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

export function formatCompactDateTime(value: string): string {
  const date = dateOf(value)
  if (!date) return value
  const currentYear = new Date().getFullYear()
  const yearPrefix = date.getFullYear() === currentYear ? '' : `${String(date.getFullYear()).slice(-2)}.`
  return `${yearPrefix}${date.getMonth() + 1}.${date.getDate()}  ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function formatCompactDate(value: string): string {
  const date = dateOf(value)
  if (!date) return value
  const currentYear = new Date().getFullYear()
  const yearPrefix = date.getFullYear() === currentYear ? '' : `${String(date.getFullYear()).slice(-2)}.`
  return `${yearPrefix}${date.getMonth() + 1}.${date.getDate()}`
}
