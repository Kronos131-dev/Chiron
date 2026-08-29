// WHY: la reconnaissance vocale rend « crêpes » pour « reps » et « la poule donne » pour « lat
// pulldown ». Ces substitutions sont la différence entre une série loggée à la voix et une
// phrase que le coach ne comprend pas.
const CORRECTIONS: [RegExp, string][] = [
  [/\b(crêpe|crêpes|crepe|crepes|rêve|rêves|bref|brefs)\b/g, 'reps'],
  [/\b(pêche|pèche|peche|puch|bouche|touche)\b/g, 'push'],
  [/\b(poule|poules|pull|pool)\b/g, 'pull'],
  [/\b(lex|l'ex|l'est|lait)\b/g, 'legs'],
  [/\b(chest press|juste presse|geste presse|chaise presse)\b/g, 'chest press'],
  [/\b(bench|bench press|banche|bain tu presses|bain de presse)\b/g, 'développé couché'],
  [/\b(incline bench|incline|un clean|un clin)\b/g, 'développé incliné'],
  [/\b(pec deck|pack deck|bec dec|pique nique)\b/g, 'pec deck'],
  [/\b(dips|chips|dix|gips)\b/g, 'dips'],
  [/\b(deadlift|dès de lift|tête lift)\b/g, 'soulevé de terre'],
  [/\b(rowing|héroïne|ruine|robin)\b/g, 'rowing'],
  [/\b(lat pulldown|la poule donne|la poule down)\b/g, 'tirage vertical'],
  [/\b(pull up|pull ups|poule up|poulpe)\b/g, 'tractions'],
  [/\b(squat|squatt|scoot|scot)\b/g, 'squat'],
  [/\b(leg press|l'express|l'ex presse|lait presse)\b/g, 'presse à cuisses'],
  [/\b(leg extension|l'ex tension|les extensions)\b/g, 'leg extension'],
  [/\b(leg curl|le girl|les girls)\b/g, 'leg curl'],
  [/\b(ohp|o a h p|eau h p)\b/g, 'développé militaire'],
  [/\b(élévations latérales|la téral|latérales)\b/g, 'élévations latérales'],
  [/\b(curl|coeur le|girl|gueule)\b/g, 'curl'],
  [/\b(triceps|tricepse)\b/g, 'triceps'],
];

export function corrigerVocabulaire(transcript: string): string {
  let texte = transcript.toLowerCase();
  for (const [motif, remplacement] of CORRECTIONS) texte = texte.replace(motif, remplacement);
  return texte;
}
