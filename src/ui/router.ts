export interface Screen {
  /** Nom utilisé pour les transitions et le style. */
  name: string;
  render(root: HTMLElement): void;
  dispose?(): void;
  /** Retour Android : renvoie true si l'écran a géré l'action. */
  onBack?(): boolean;
}

let current: Screen | null = null;
let rootEl: HTMLElement;

export function initRouter(el: HTMLElement) {
  rootEl = el;
}

export function show(screen: Screen) {
  current?.dispose?.();
  current = screen;
  const view = document.createElement('div');
  view.className = `screen screen-${screen.name} screen-enter`;
  rootEl.replaceChildren(view);
  screen.render(view);
  requestAnimationFrame(() => view.classList.remove('screen-enter'));
  window.scrollTo(0, 0);
}

export function currentScreen(): Screen | null {
  return current;
}
