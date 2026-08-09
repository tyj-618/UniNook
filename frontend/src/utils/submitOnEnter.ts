/**
 * Message-style editors submit with Enter and keep Shift + Enter for a newline.
 * IME composition must finish before Enter can trigger submission.
 */
export function submitOnEnter(event: KeyboardEvent, submit: () => void | Promise<void>): void {
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return
  event.preventDefault()
  void submit()
}
