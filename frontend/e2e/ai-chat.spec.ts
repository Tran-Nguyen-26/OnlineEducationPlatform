import { test, expect } from '@playwright/test'

/**
 * E2E Flow — Stream L1 (Assessment & Learning Assistant Interactions)
 * Flow: Điều hướng bài kiểm tra/đánh giá → Kiểm tra danh sách câu hỏi & điểm số
 */
test.describe('Assessment & Interactive Learning Flows', () => {
  test('Assessment page displays quizzes, submission statuses, and scores', async ({ page }) => {
    await page.goto('/assessment')

    // 1. Verify assessment page heading
    const pageHeading = page.locator('h1, h2').first()
    await expect(pageHeading).toBeVisible({ timeout: 10000 })

    // 2. Verify quiz list items are visible
    const quizItems = page.locator('text=Kiểm tra, text=Quiz, text=REST API')
    await expect(quizItems.first()).toBeVisible()

    // 3. Verify status badges (graded, pending, submitted)
    const statusBadges = page
      .locator('span, div')
      .filter({ hasText: /graded|pending|submitted|Điểm|score/i })
    expect(await statusBadges.count()).toBeGreaterThan(0)
  })
})
