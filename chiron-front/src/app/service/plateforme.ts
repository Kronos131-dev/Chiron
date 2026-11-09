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

let retourIntercepte: (() => boolean) | null = null;

// WHY: le retour d'Android remonte l'historique sans demander l'avis de l'écran. Une saisie qui
// ne vit qu'en mémoire disparaissait donc sur un simple appui ; l'écran qui en tient une reçoit
// le retour en premier et répond true s'il l'a consommé.
export function intercepterRetourNatif(gestionnaire: () => boolean): () => void {
  retourIntercepte = gestionnaire;
  return () => {
    if (retourIntercepte === gestionnaire) retourIntercepte = null;
  };
}

// WHY: sans ce branchement le bouton retour d'Android ferme l'application depuis n'importe
// quel écran, au lieu de remonter d'une page.
export function preparerCoquilleNative(quitter: () => void): void {
  if (!estNatif()) return;

  StatusBar.setOverlaysWebView({ overlay: true }).catch(() => {});
  StatusBar.setStyle({ style: Style.Dark }).catch(() => {});

  App.addListener('backButton', ({ canGoBack }) => {
    if (retourIntercepte?.()) return;
    if (canGoBack) window.history.back();
    else quitter();
  }).catch(() => {});
}

export function quitterApplication(): void {
  App.exitApp().catch(() => {});
}
