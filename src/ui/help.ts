import { nav } from './nav';
import type { Screen } from './router';

export function helpScreen(): Screen {
  return {
    name: 'help',
    onBack() {
      void nav.library();
      return true;
    },
    render(root) {
      root.innerHTML = `
        <header class="topbar">
          <button class="icon-btn" data-back aria-label="Retour">←</button>
          <h2 class="topbar-title">Règles du jeu</h2>
          <span></span>
        </header>
        <div class="help">
          <section class="card">
            <h3>Le principe</h3>
            <p>Choisis une démo ou importe ta musique (MP3, M4A, WAV). Nova Rush l’analyse et crée un parcours de tuiles sur quatre voies.
            Touche chaque tuile au moment où elle atteint la ligne lumineuse.</p>
          </section>
          <section class="card">
            <h3>Les tuiles</h3>
            <ul>
              <li><strong>Courte</strong> : touche une fois quand elle atteint la ligne.</li>
              <li><strong>Longue</strong> : touche au début, maintiens, puis relâche à la fin. Les points dépendent de la durée.
                Relâchée trop tôt, elle compte comme un Great (50 points) et le combo repart à zéro, mais la partie continue.</li>
              <li><strong>Accord</strong> : plusieurs tuiles reliées à toucher ensemble, avec plusieurs doigts.</li>
            </ul>
          </section>
          <section class="card">
            <h3>Jugements</h3>
            <table class="rules">
              <tr><td class="j-pp">Perfect+</td><td>à ±25 ms de la ligne</td><td>150 × multiplicateur</td></tr>
              <tr><td class="j-p">Perfect</td><td>jusqu’à ±70 ms</td><td>100</td></tr>
              <tr><td class="j-g">Great</td><td>moins précis</td><td>50</td></tr>
              <tr><td class="j-m">Raté</td><td>la tuile disparaît</td><td>fin de partie</td></tr>
            </table>
          </section>
          <section class="card">
            <h3>Combo Perfect+</h3>
            <p>Seuls les Perfect+ font monter le combo : le premier vaut 150 × 1, le deuxième 150 × 2, le troisième 150 × 3…
            Un Perfect ou un Great remet le combo à zéro. Dans un accord, la remise à zéro a lieu après le traitement de tout l’accord.</p>
          </section>
          <section class="card">
            <h3>Difficulté</h3>
            <p>Chaque morceau est classé automatiquement : Facile, Moyen, Difficile ou Expert, selon la densité des gestes, leur espacement,
            les accords, les changements de rythme et les tuiles longues — pas selon le genre musical. La difficulté règle la densité et la vitesse des tuiles.</p>
          </section>
          <section class="card">
            <h3>Pendant la partie</h3>
            <p>Les cinq étoiles indiquent le temps écoulé dans le morceau (une par cinquième), pas ta performance.
            La pause est disponible à tout moment ; la reprise se fait au même instant après un compte à rebours.
            Si le son te semble décalé, lance le test de synchronisation dans les paramètres.</p>
          </section>
          <section class="card">
            <h3>Confidentialité</h3>
            <p>Aucun compte. Ta musique, ses analyses et tes scores restent sur l’appareil. Supprimer un morceau retire son audio mais conserve son titre et tes records.</p>
          </section>
        </div>`;
      root.querySelector('[data-back]')!.addEventListener('click', () => void nav.library());
    },
  };
}
