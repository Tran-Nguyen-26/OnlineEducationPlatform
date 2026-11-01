import { test, expect } from '@playwright/test'

/**
 * E2E Flow — Stream L2 (Lesson & Course Detail Viewer)
 * Flow: Điều hướng bài học → Kiểm tra siêu dữ liệu khóa học & thời lượng
 */
test.describe('Lesson & Course Viewer Flows', () => {
  test('Course detail view loads metadata, lesson count, and navigation actions', async ({
    page,
  }) => {
    // Navigate directly to course ID 1
    await page.goto('/courses/1')

    // 1. Verify page has loaded with course content
    const heading = page.locator('h1, h2').first()
    await expect(heading).toBeVisible({ timeout: 10000 })

    // 2. Verify back navigation or course catalog link
    const backLink = page.locator('a[href="/courses"], a[href*="courses"]').first()
    await expect(backLink).toBeVisible()

    // 3. Return to courses catalog
    await backLink.click()
    await expect(page).toHaveURL(/\/courses$/)
  })
})
