// WHY: Safari n'accorde l'écriture dans le presse-papiers qu'à l'appel qui suit directement le
// geste de l'utilisateur ; attendre la réponse du serveur avant d'écrire lui ferait perdre ce droit.
// `ClipboardItem` accepte une promesse : l'écriture part au clic et se termine quand le texte arrive.
export async function copierTexte(texte: Promise<string>): Promise<void> {
  if (typeof ClipboardItem !== 'undefined' && navigator.clipboard?.write) {
    const contenu = texte.then((valeur) => new Blob([valeur], { type: 'text/plain' }));
    await navigator.clipboard.write([new ClipboardItem({ 'text/plain': contenu })]);
    return;
  }
  await navigator.clipboard.writeText(await texte);
}
