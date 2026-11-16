import type { OutlineLesson } from '../../../types/api'

export interface FlatLesson extends OutlineLesson {
  moduleId: string
  moduleTitle: string
}

/** Minimal shape of a student assignment as returned by /api/assignments (full type lives in types/api.ts). */
export interface AssignmentResponseLike { id: string; lessonId: string | null; title: string; deadline: string | null; graded: boolean; canSubmit: boolean; submissionCount: number }
