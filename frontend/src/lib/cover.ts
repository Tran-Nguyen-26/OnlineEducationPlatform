/** Deterministic gradient per subject so a subject always looks the same across pages. */
const COVERS = [
  'from-indigo-500 via-violet-500 to-fuchsia-400',
  'from-sky-500 via-indigo-500 to-violet-500',
  'from-teal-500 via-emerald-500 to-sky-500',
  'from-amber-400 via-orange-400 to-rose-400',
  'from-violet-500 via-purple-500 to-pink-400',
]

export const coverFor = (subject: string) => COVERS[[...subject].reduce((a, c) => a + c.charCodeAt(0), 0) % COVERS.length]
