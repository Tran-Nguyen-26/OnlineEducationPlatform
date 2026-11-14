import type { CreateQuestionPayload, QuestionItem, QuestionType } from '@/api/quiz'

export type TransferFormat = 'json' | 'csv'

const questionTypes: QuestionType[] = ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'KEYWORD_FILL']
const csvColumns = ['question_type', 'question_text', 'keyword_answer', 'points', 'options_json', 'learning_outcome_id', 'bloom_level'] as const

function quoteCsv(value: string) {
  return `"${value.replaceAll('"', '""')}"`
}

function parseCsv(source: string): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let cell = ''
  let quoted = false
  for (let index = 0; index < source.length; index++) {
    const character = source[index]
    if (quoted) {
      if (character === '"' && source[index + 1] === '"') {
        cell += '"'
        index++
      } else if (character === '"') quoted = false
      else cell += character
    } else if (character === '"') quoted = true
    else if (character === ',') {
      row.push(cell)
      cell = ''
    } else if (character === '\n' || character === '\r') {
      if (character === '\r' && source[index + 1] === '\n') index++
      row.push(cell)
      cell = ''
      if (row.some((value) => value.length > 0)) rows.push(row)
      row = []
    } else cell += character
  }
  if (quoted) throw new Error('CSV chưa đóng dấu ngoặc kép.')
  if (cell.length > 0 || row.length > 0) {
    row.push(cell)
    if (row.some((value) => value.length > 0)) rows.push(row)
  }
  return rows
}

function normalizeQuestion(value: unknown, index: number): CreateQuestionPayload {
  if (!value || typeof value !== 'object') throw new Error(`Câu hỏi ${index + 1} không hợp lệ.`)
  const item = value as Record<string, unknown>
  const type = item.question_type
  const questionText = typeof item.question_text === 'string' ? item.question_text.trim() : ''
  const points = Number(item.points)
  if (!questionTypes.includes(type as QuestionType) || !questionText || !Number.isFinite(points) || points <= 0) {
    throw new Error(`Câu hỏi ${index + 1} thiếu loại, nội dung hoặc điểm hợp lệ.`)
  }
  const rawOptions = item.options
  if (!Array.isArray(rawOptions)) throw new Error(`Câu hỏi ${index + 1} thiếu danh sách phương án.`)
  const options = rawOptions.map((option, optionIndex) => {
    if (!option || typeof option !== 'object') throw new Error(`Phương án ${optionIndex + 1} của câu ${index + 1} không hợp lệ.`)
    const record = option as Record<string, unknown>
    if (typeof record.option_text !== 'string' || !record.option_text.trim() || typeof record.is_correct !== 'boolean') {
      throw new Error(`Phương án ${optionIndex + 1} của câu ${index + 1} cần có nội dung và trạng thái đúng/sai.`)
    }
    return { option_text: record.option_text.trim(), is_correct: record.is_correct, position: optionIndex }
  })
  const correctCount = options.filter((option) => option.is_correct).length
  if (type === 'TRUE_FALSE' && (options.length !== 2 || correctCount !== 1)) {
    throw new Error(`Câu Đúng/Sai ${index + 1} cần đúng 2 phương án và 1 đáp án đúng.`)
  }
  if (type === 'SINGLE_CHOICE' && (options.length < 2 || correctCount !== 1)) {
    throw new Error(`Câu chọn một ${index + 1} cần ít nhất 2 phương án và đúng 1 đáp án đúng.`)
  }
  if (type === 'MULTIPLE_CHOICE' && (options.length < 2 || correctCount < 1)) {
    throw new Error(`Câu chọn nhiều ${index + 1} cần ít nhất 2 phương án và một đáp án đúng.`)
  }
  if (type === 'KEYWORD_FILL' && (options.length !== 0 || typeof item.keyword_answer !== 'string' || !item.keyword_answer.trim())) {
    throw new Error(`Câu điền từ ${index + 1} cần đáp án từ khóa và không có phương án lựa chọn.`)
  }
  return {
    question_type: type as QuestionType,
    question_text: questionText,
    keyword_answer: typeof item.keyword_answer === 'string' ? item.keyword_answer : undefined,
    learning_outcome_id: typeof item.learning_outcome_id === 'string' ? item.learning_outcome_id || undefined : undefined,
    bloom_level: item.bloom_level === 'REMEMBER' || item.bloom_level === 'UNDERSTAND' || item.bloom_level === 'APPLY' ? item.bloom_level : undefined,
    points,
    options,
  }
}

export function exportQuestionBank(questions: QuestionItem[], format: TransferFormat) {
  const rows = questions.map((question) => ({
    question_type: question.question_type,
    question_text: question.question_text,
    keyword_answer: question.keyword_answer ?? '',
    learning_outcome_id: question.learning_outcome_id ?? '',
    bloom_level: question.bloom_level ?? '',
    points: Number(question.points),
    options: question.options.map(({ option_text, is_correct }, position) => ({ option_text, is_correct, position })),
  }))
  if (format === 'json') return JSON.stringify({ schema_version: 1, questions: rows }, null, 2)
  const lines = [csvColumns.map(quoteCsv).join(',')]
  for (const row of rows) {
    lines.push([
      row.question_type,
      row.question_text,
      row.keyword_answer,
      String(row.points),
      JSON.stringify(row.options),
      row.learning_outcome_id,
      row.bloom_level,
    ].map(quoteCsv).join(','))
  }
  return `\uFEFF${lines.join('\r\n')}`
}

export function importQuestionBank(source: string, format: TransferFormat): CreateQuestionPayload[] {
  let raw: unknown[]
  if (format === 'json') {
    const parsed: unknown = JSON.parse(source)
    const items = Array.isArray(parsed) ? parsed : (parsed as { questions?: unknown[] } | null)?.questions
    if (!Array.isArray(items)) throw new Error('JSON cần là danh sách câu hỏi hoặc có trường questions.')
    raw = items
  } else {
    const rows = parseCsv(source)
    const headers = rows[0]?.map((header) => header.trim().toLowerCase()) ?? []
    const requiredColumns = csvColumns.slice(0, 5)
    if (requiredColumns.some((column) => !headers.includes(column))) {
      throw new Error(`CSV cần các cột: ${requiredColumns.join(', ')}.`)
    }
    const indexes = Object.fromEntries(csvColumns.map((column) => [column, headers.indexOf(column)])) as Record<(typeof csvColumns)[number], number>
    raw = rows.slice(1).map((row) => {
      let options: unknown
      try { options = JSON.parse(row[indexes.options_json] ?? '') } catch { throw new Error('Cột options_json có JSON không hợp lệ.') }
      return {
        question_type: row[indexes.question_type],
        question_text: row[indexes.question_text],
        keyword_answer: row[indexes.keyword_answer],
        points: row[indexes.points],
        options,
        learning_outcome_id: indexes.learning_outcome_id >= 0 ? row[indexes.learning_outcome_id] : '',
        bloom_level: indexes.bloom_level >= 0 ? row[indexes.bloom_level] : '',
      }
    })
  }
  if (raw.length === 0 || raw.length > 500) throw new Error('Tệp phải có từ 1 đến 500 câu hỏi.')
  return raw.map(normalizeQuestion)
}
