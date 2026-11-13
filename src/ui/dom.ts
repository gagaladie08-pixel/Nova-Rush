export function esc(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}

export function el<T extends HTMLElement = HTMLElement>(html: string): T {
  const t = document.createElement('template');
  t.innerHTML = html.trim();
  return t.content.firstElementChild as T;
}

export function formatTime(sec: number): string {
  const s = Math.max(0, Math.round(sec));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
}

export function formatScore(n: number): string {
  return n.toLocaleString('fr-FR');
}

let modalRoot: HTMLElement | null = null;
function root(): HTMLElement {
  modalRoot ??= document.getElementById('modals')!;
  return modalRoot;
}

export interface ModalHandle {
  el: HTMLElement;
  close: () => void;
}

export function modal(html: string, opts: { dismissable?: boolean } = {}): ModalHandle {
  const wrap = el(`<div class="modal-backdrop"><div class="modal">${html}</div></div>`);
  const close = () => {
    wrap.classList.add('closing');
    window.setTimeout(() => wrap.remove(), 180);
  };
  if (opts.dismissable) {
    wrap.addEventListener('click', (e) => {
      if (e.target === wrap) close();
    });
  }
  root().appendChild(wrap);
  return { el: wrap.querySelector('.modal')!, close };
}

export function confirmDialog(title: string, body: string, ok: string, danger = false): Promise<boolean> {
  return new Promise((resolve) => {
    const m = modal(`
      <h2>${esc(title)}</h2>
      <p>${body}</p>
      <div class="modal-actions">
        <button class="btn btn-ghost" data-r="0">Annuler</button>
        <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" data-r="1">${esc(ok)}</button>
      </div>`);
    m.el.querySelectorAll<HTMLButtonElement>('[data-r]').forEach((b) =>
      b.addEventListener('click', () => {
        m.close();
        resolve(b.dataset.r === '1');
      }),
    );
  });
}

export function alertDialog(title: string, body: string): Promise<void> {
  return new Promise((resolve) => {
    const m = modal(`
      <h2>${esc(title)}</h2>
      <p>${body}</p>
      <div class="modal-actions"><button class="btn btn-primary">Compris</button></div>`);
    m.el.querySelector('button')!.addEventListener('click', () => {
      m.close();
      resolve();
    });
  });
}

export function toast(msg: string) {
  const t = el(`<div class="toast">${esc(msg)}</div>`);
  root().appendChild(t);
  window.setTimeout(() => t.classList.add('closing'), 2200);
  window.setTimeout(() => t.remove(), 2500);
}
