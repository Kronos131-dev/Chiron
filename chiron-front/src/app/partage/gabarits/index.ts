import { Zone } from '../mise-en-page';
import { Scene } from '../pinceaux/scene';
import { dessinerBataille } from './bataille';
import { dessinerCourse } from './course';
import { dessinerWod } from './wod';

export function dessinerGabarit(scene: Scene, contenu: Zone): void {
  switch (scene.carte.genre) {
    case 'course':
      dessinerCourse(scene, contenu);
      return;
    case 'wod':
      dessinerWod(scene, contenu);
      return;
    default:
      dessinerBataille(scene, contenu);
  }
}
