type Answers = Record<string, string[]>

function database(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const open = indexedDB.open('oep-assessment', 1)
    open.onupgradeneeded = () => open.result.createObjectStore('drafts')
    open.onsuccess = () => resolve(open.result)
    open.onerror = () => reject(open.error)
  })
}

export async function readExamDraft(id: string): Promise<Answers | undefined> {
  const db = await database()
  try {
    return await new Promise((resolve, reject) => {
      const request = db.transaction('drafts').objectStore('drafts').get(id)
      request.onsuccess = () => resolve(request.result)
      request.onerror = () => reject(request.error)
    })
  } finally {
    db.close()
  }
}

export async function writeExamDraft(id: string, answers?: Answers) {
  const db = await database()
  try {
    await new Promise<void>((resolve, reject) => {
      const transaction = db.transaction('drafts', 'readwrite')
      const store = transaction.objectStore('drafts')
      if (answers) store.put(answers, id)
      else store.delete(id)
      transaction.oncomplete = () => resolve()
      transaction.onabort = () => reject(transaction.error)
    })
  } finally {
    db.close()
  }
}
