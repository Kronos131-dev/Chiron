import { CoursePointDto } from '../../service/chiron-api';
import { projeterPointsDans } from '../../util/trace-svg';
import { Zone } from '../mise-en-page';
import { Palier } from '../paliers';
import { melanger } from './metal';
import { BRONZE } from './scene';

const POINTS_MAX = 1200;
const MARGE_TRACE = 40;
const CLAIR = '#fff7c0';

function allegerPoints(points: CoursePointDto[]): CoursePointDto[] {
  if (points.length <= POINTS_MAX) return points;
  const pas = Math.ceil(points.length / POINTS_MAX);
  const gardes = points.filter((point, index) => index % pas === 0 || point.coupure);
  const dernier = points[points.length - 1];
  return gardes[gardes.length - 1] === dernier ? gardes : [...gardes, dernier];
}

function couleurSelonAllure(palier: Palier, ratio: number): string {
  if (ratio < 0.5) return melanger(palier.accent, BRONZE, ratio * 2);
  return melanger(BRONZE, CLAIR, (ratio - 0.5) * 2);
}

export function dessinerTrace(
  ctx: CanvasRenderingContext2D,
  points: CoursePointDto[],
  zone: Zone,
  palier: Palier,
): boolean {
  const trace = projeterPointsDans(allegerPoints(points), zone.largeur, zone.hauteur, MARGE_TRACE);
  if (!trace.segments.length) return false;

  const ecart = trace.allureMaxKmh - trace.allureMinKmh;
  ctx.save();
  ctx.translate(zone.x, zone.y);
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';

  ctx.strokeStyle = 'rgba(255, 183, 121, 0.16)';
  ctx.lineWidth = 26;
  ctx.beginPath();
  for (const segment of trace.segments) {
    ctx.moveTo(segment.de.x, segment.de.y);
    ctx.lineTo(segment.vers.x, segment.vers.y);
  }
  ctx.stroke();

  ctx.lineWidth = 9;
  for (const segment of trace.segments) {
    const ratio = ecart > 0 ? (segment.allureKmh - trace.allureMinKmh) / ecart : 0.5;
    ctx.strokeStyle = couleurSelonAllure(palier, ratio);
    ctx.beginPath();
    ctx.moveTo(segment.de.x, segment.de.y);
    ctx.lineTo(segment.vers.x, segment.vers.y);
    ctx.stroke();
  }

  if (trace.depart) {
    ctx.strokeStyle = BRONZE;
    ctx.fillStyle = '#020617';
    ctx.lineWidth = 7;
    ctx.beginPath();
    ctx.arc(trace.depart.x, trace.depart.y, 15, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
  }
  if (trace.arrivee) {
    ctx.fillStyle = CLAIR;
    ctx.shadowColor = BRONZE;
    ctx.shadowBlur = 24;
    ctx.beginPath();
    ctx.arc(trace.arrivee.x, trace.arrivee.y, 17, 0, Math.PI * 2);
    ctx.fill();
  }
  ctx.restore();
  return true;
}
