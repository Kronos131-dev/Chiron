import { App } from '@capacitor/app';
import { Browser } from '@capacitor/browser';
import { Capacitor } from '@capacitor/core';
import { StatusBar, Style } from '@capacitor/status-bar';

export function estNatif(): boolean {
  return Capacitor.isNativePlatform();
}

// WHY: dans la WebView, un window.location.href vers Olympus ou Fitbit remplace l'application
// par un site dont on ne revient pas — il n'y a ni barre d'adresse ni bouton retour. Le
// navigateur système, lui, se referme sur Chiron.
export function ouvrirALExterieur(url: string): void {
  if (!estNatif()) {
    window.location.href = url;
    return;
  }
  Browser.open({ url }).catch(() => {
    window.location.href = url;
  });
}

export function ouvrirDansUnOnglet(url: string): void {
  if (!estNatif()) {
    window.open(url, '_blank');
    return;
  }
  Browser.open({ url }).catch(() => {});
}

// WHY: sans ce branchement le bouton retour d'Android ferme l'application depuis n'importe
// quel écran, au lieu de remonter d'une page.
export function preparerCoquilleNative(quitter: () => void): void {
  if (!estNatif()) return;

  StatusBar.setOverlaysWebView({ overlay: true }).catch(() => {});
  StatusBar.setStyle({ style: Style.Dark }).catch(() => {});

  App.addListener('backButton', ({ canGoBack }) => {
    if (canGoBack) window.history.back();
    else quitter();
  }).catch(() => {});
}

export function quitterApplication(): void {
  App.exitApp().catch(() => {});
}
