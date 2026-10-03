export function dessinerTemple(
  ctx: CanvasRenderingContext2D,
  centreX: number,
  centreY: number,
  largeur: number,
  couleur: string,
): void {
  const demi = largeur / 2;
  const hauteurFronton = largeur * 0.2;
  const hauteurFrise = largeur * 0.06;
  const hauteurColonne = largeur * 0.3;
  const hauteurSocle = largeur * 0.06;
  const haut = centreY - (hauteurFronton + hauteurFrise + hauteurColonne + hauteurSocle) / 2;

  ctx.save();
  ctx.fillStyle = couleur;
  ctx.beginPath();
  ctx.moveTo(centreX - demi, haut + hauteurFronton);
  ctx.lineTo(centreX, haut);
  ctx.lineTo(centreX + demi, haut + hauteurFronton);
  ctx.closePath();
  ctx.fill();

  const frise = haut + hauteurFronton + largeur * 0.02;
  ctx.fillRect(centreX - demi * 0.9, frise, largeur * 0.9, hauteurFrise);

  const colonnes = 4;
  const largeurColonne = largeur * 0.08;
  const sommet = frise + hauteurFrise + largeur * 0.02;
  for (let i = 0; i < colonnes; i++) {
    const x = centreX - demi * 0.8 + ((largeur * 0.8 - largeurColonne) * i) / (colonnes - 1);
    ctx.fillRect(x, sommet, largeurColonne, hauteurColonne);
  }

  ctx.fillRect(centreX - demi, sommet + hauteurColonne + largeur * 0.02, largeur, hauteurSocle);
  ctx.restore();
}
