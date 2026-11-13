import { ExerciceForm, serieFormToDto } from './exercise-forms';

function enteteDe(seanceType: any) {
  return {
    id: seanceType.id,
    titre: seanceType.titre,
    note: seanceType.note ?? null,
    weekNumber: seanceType.weekNumber,
    historique: false,
  };
}

export function seanceTypeAvecValeursDe(seanceType: any, exercicesFaits: ExerciceForm[]) {
  const exerciceFaitParId = new Map(exercicesFaits.map((exo) => [String(exo.id), exo]));
  return {
    ...enteteDe(seanceType),
    exercices: (seanceType.exercices ?? []).map((exo: any) => {
      const fait = exerciceFaitParId.get(String(exo.id));
      return {
        nom: exo.nom,
        exerciceDefinitionId: exo.exerciceDefinitionId ?? null,
        blockId: exo.blockId ?? null,
        blockType: exo.blockType ?? null,
        commentaire: (fait ?? exo).commentaire ?? '',
        unilateral: (fait ?? exo).unilateral ?? false,
        series: fait ? fait.series.map(serieSansTrace) : exo.series,
      };
    }),
  };
}

export function seanceTypeAvecExercicesDe(seanceType: any, exercicesFaits: ExerciceForm[]) {
  const exerciceTypeParId = new Map(
    (seanceType.exercices ?? []).map((exo: any) => [String(exo.id), exo]),
  );
  return {
    ...enteteDe(seanceType),
    exercices: exercicesFaits.map((fait) => {
      const origine: any = exerciceTypeParId.get(String(fait.id));
      return {
        nom: fait.nom,
        exerciceDefinitionId: fait.definitionId ?? null,
        blockId: origine?.blockId ?? null,
        blockType: origine?.blockType ?? null,
        commentaire: fait.commentaire ?? '',
        unilateral: fait.unilateral ?? false,
        series: fait.series.map(serieSansTrace),
      };
    }),
  };
}

// WHY: une trace GPS appartient à la sortie du jour, pas à la séance type : l'y reporter
// rattacherait la course d'aujourd'hui à toutes celles de demain.
function serieSansTrace(serie: ExerciceForm['series'][number]) {
  return { ...serieFormToDto(serie), courseTraceId: null };
}
