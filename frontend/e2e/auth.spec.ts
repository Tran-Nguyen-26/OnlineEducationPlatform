import { test, expect } from '@playwright/test'

/**
 * E2E Flow — Stream L1 (Auth & Session Flow)
 * Flow: Đăng ký → Đăng nhập → Dashboard Navigation
 */
test.describe('Authentication & Session E2E Flows', () => {
  test('Login page renders required form controls and validates empty inputs', async ({ page }) => {
    await page.goto('/login')

    // 1. Verify page title and header
    await expect(page).toHaveTitle(/Online Education Platform|OEP|Đăng nhập/i)

    const emailInput = page.locator('input[type="email"]')
    const passwordInput = page.locator('input[type="password"]')
    const submitBtn = page.locator('button[type="submit"]')

    await expect(emailInput).toBeVisible()
    await expect(passwordInput).toBeVisible()
    await expect(submitBtn).toBeVisible()

    // 2. Validate empty submission error handling
    await submitBtn.click()
    const errorAlert = page.locator('.error-banner, [role="alert"], text=Vui lòng nhập')
    await expect(errorAlert).toBeVisible()
  })

  test('Navigation between Login, Register, and Choose Role pages', async ({ page }) => {
    await page.goto('/login')

    // Verify registration link navigation
    const registerLink = page.locator('a[href*="register"], a[href*="choose-role"]').first()
    if ((await registerLink.count()) > 0) {
      await registerLink.click()
      await expect(page).toHaveURL(/register|choose-role/)
    }

    // Direct navigation to register
    await page.goto('/register')
    await expect(page).toHaveURL(/\/register/)
  })
})
