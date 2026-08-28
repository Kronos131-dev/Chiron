export type TypeSessionAudio = 'auto' | 'ambient' | 'transient' | 'playback';

function sessionAudio(): any | null {
  const nav = navigator as any;
  return nav?.audioSession ?? null;
}

export function sessionAudioDisponible(): boolean {
  return sessionAudio() !== null;
}

// WHY: c'est la seule poignée que le web donne sur le focus audio d'Android. « ambient » se
// mélange à Spotify au lieu de le couper ; « transient » le fait baisser le temps d'une phrase
// puis lui rend le volume. Sans cette API — Chrome ne l'a pas encore partout — la boucle de
// survie met la musique en pause, et rien dans le standard ne permet de l'éviter.
export function reglerSessionAudio(type: TypeSessionAudio): boolean {
  const session = sessionAudio();
  if (!session) return false;
  try {
    session.type = type;
    return true;
  } catch {
    return false;
  }
}

export function baisserLaMusiquePendant(parle: boolean): void {
  reglerSessionAudio(parle ? 'transient' : 'ambient');
}
