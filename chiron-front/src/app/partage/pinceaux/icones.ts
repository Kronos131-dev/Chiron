export function dessinerCoeur(
  ctx: CanvasRenderingContext2D,
  centreX: number,
  centreY: number,
  taille: number,
  couleur: string,
): void {
  const demi = taille / 2;
  ctx.save();
  ctx.fillStyle = couleur;
  ctx.beginPath();
  ctx.moveTo(centreX, centreY + demi * 0.9);
  ctx.bezierCurveTo(
    centreX - demi * 1.5,
    centreY - demi * 0.1,
    centreX - demi * 0.7,
    centreY - demi * 1.2,
    centreX,
    centreY - demi * 0.45,
  );
  ctx.bezierCurveTo(
    centreX + demi * 0.7,
    centreY - demi * 1.2,
    centreX + demi * 1.5,
    centreY - demi * 0.1,
    centreX,
    centreY + demi * 0.9,
  );
  ctx.closePath();
  ctx.fill();
  ctx.restore();
}
