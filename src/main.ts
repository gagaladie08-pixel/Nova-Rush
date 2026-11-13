import '@fontsource/orbitron/latin-500.css';
import '@fontsource/orbitron/latin-800.css';
import '@fontsource/rajdhani/latin-500.css';
import '@fontsource/rajdhani/latin-700.css';
import '@fontsource/righteous/latin-400.css';
import '@fontsource/nunito/latin-400.css';
import '@fontsource/nunito/latin-800.css';
import '@fontsource/fraunces/latin-600.css';
import '@fontsource/fraunces/latin-800.css';
import '@fontsource/ubuntu/latin-400.css';
import '@fontsource/ubuntu/latin-700.css';
import './styles/base.css';
import './styles/themes.css';

import { Capacitor } from '@capacitor/core';
import { settings, type Settings } from './storage/settings';
import { db } from './storage/db';
import { currentScreen, initRouter } from './ui/router';
import { nav } from './ui/nav';

function applySettings(s: Settings) {
  const html = document.documentElement;
  html.dataset.theme = s.menuTheme;
  html.dataset.reduceMotion = String(s.reduceMotion);
  const color = { cyber: '#050510', survivor: '#080808', african: '#120800' }[s.menuTheme];
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', color);
}

async function setupAndroid() {
  if (!Capacitor.isNativePlatform()) return;
  const { App } = await import('@capacitor/app');
  await App.addListener('backButton', () => {
    const screen = currentScreen();
    if (screen?.name === 'library') {
      void App.exitApp();
      return;
    }
    if (!screen?.onBack?.()) void nav.library();
  });
}

applySettings(settings.get());
settings.subscribe(applySettings);
initRouter(document.getElementById('app')!);
void db.requestPersistence();
void setupAndroid();
void nav.library();

// Empêche le menu contextuel et la sélection lors des appuis longs.
document.addEventListener('contextmenu', (e) => e.preventDefault());
