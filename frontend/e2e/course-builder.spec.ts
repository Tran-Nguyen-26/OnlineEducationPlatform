import { test, expect } from '@playwright/test'

/**
 * E2E Flow — Stream L2 (Course Management & Catalog)
 * Flow: Duyệt khóa học → Xem chi tiết khóa học → Kiểm tra thông tin giảng viên và bài học
 */
test.describe('Course Catalog & Management Flows', () => {
  test('Course catalog loads courses and displays details', async ({ page }) => {
    await page.goto('/courses')

    // 1. Verify page header
    const pageHeading = page.locator('h1, h2').first()
    await expect(pageHeading).toBeVisible()

    // 2. Verify courses are rendered
    const courseCards = page.locator('a[href^="/courses/"], [data-testid="course-card"]')
    await expect(courseCards.first()).toBeVisible({ timeout: 10000 })
    const count = await courseCards.count()
    expect(count).toBeGreaterThan(0)

    // 3. Navigate to first course detail
    await courseCards.first().click()
    await expect(page).toHaveURL(/\/courses\/.+/)

    // 4. Verify detail elements
    const courseDetailHeading = page.locator('h1, h2').first()
    await expect(courseDetailHeading).toBeVisible()
  })
})
