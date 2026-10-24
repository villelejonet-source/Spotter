import seeded from "@/data/exercises.json";
import { splitEnumList } from "./model";

export interface Exercise {
  id: string;
  name: string;
  primary: string[];
  secondary: string[];
  pattern: string;
  equipment: string[];
  mechanics: string;
  difficulty: string;
  unilateral: boolean;
  instructions: string | null;
  isCustom: boolean;
}

interface SeededExercise {
  id: string;
  name: string;
  primary: string[];
  secondary: string[];
  pattern: string;
  equipment: string[];
  mechanics: string;
  difficulty: string;
  unilateral?: boolean;
  instructions?: string;
}

/** Row of v_custom_exercise. */
export interface CustomExerciseRow {
  id: string;
  name: string;
  primary_muscles: string;
  secondary_muscles: string;
  movement_pattern: string;
  equipment: string;
  mechanics: string;
  difficulty: string;
  unilateral: boolean;
  instructions: string | null;
}

const SEEDED: Exercise[] = (seeded.exercises as SeededExercise[]).map((e) => ({
  id: e.id,
  name: e.name,
  primary: e.primary,
  secondary: e.secondary,
  pattern: e.pattern,
  equipment: e.equipment,
  mechanics: e.mechanics,
  difficulty: e.difficulty,
  unilateral: e.unilateral ?? false,
  instructions: e.instructions ?? null,
  isCustom: false,
}));

/** The seeded catalog plus the user's own custom exercises, by id. */
export function buildCatalog(custom: CustomExerciseRow[]): Map<string, Exercise> {
  const map = new Map(SEEDED.map((e) => [e.id, e]));
  for (const c of custom) {
    map.set(c.id, {
      id: c.id,
      name: c.name,
      primary: splitEnumList(c.primary_muscles),
      secondary: splitEnumList(c.secondary_muscles),
      pattern: c.movement_pattern,
      equipment: splitEnumList(c.equipment),
      mechanics: c.mechanics,
      difficulty: c.difficulty,
      unilateral: c.unilateral,
      instructions: c.instructions,
      isCustom: true,
    });
  }
  return map;
}

/** Display name for an exercise id; falls back to a readable id if it's unknown. */
export function exerciseName(catalog: Map<string, Exercise>, id: string): string {
  return catalog.get(id)?.name ?? id.replace(/-/g, " ");
}
