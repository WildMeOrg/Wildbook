const { test, expect } = require('@playwright/test');

test.describe('Login Page', () => {
  const testUrl = process.env.TEST_URL || 'https://qa.wildme.org/react';
  const testUsername = process.env.TEST_USERNAME || 'test123';
  const testPassword = process.env.TEST_PASSWORD || 'test123';

  test('should successfully login with valid credentials', async ({ page }) => {
    // Navigate to login page
    await page.goto(testUrl);

    // Wait for login form elements to be visible
    await expect(page.getByRole('textbox', { name: 'Username' })).toBeVisible();

    // Fill in username
    await page.getByRole('textbox', { name: 'Username' }).fill(testUsername);

    // Fill in password
    await page.getByRole('textbox', { name: 'Password' }).fill(testPassword);

    // Check terms checkbox if it exists (required on deployed environment)
    const termsCheckbox = page.getByRole('checkbox', { name: 'I agree to Terms of Use and Privacy Policy' });
    if (await termsCheckbox.isVisible()) {
      await termsCheckbox.check();
    }

    // Click submit button
    await page.getByRole('button', { name: 'Sign In' }).click();

    // Wait for URL to change to home page
    await page.waitForURL('**/home', { timeout: 10000 });

    // Verify post-login marker is visible
    await expect(page.getByRole('heading', { name: 'Latest Data' })).toBeVisible({ timeout: 10000 });
  });

  test('should show error message with invalid credentials', async ({ page }) => {
    // Navigate to login page
    await page.goto(testUrl);

    // Wait for login form elements to be visible
    await expect(page.getByRole('textbox', { name: 'Username' })).toBeVisible();

    // Fill in username with invalid value
    await page.getByRole('textbox', { name: 'Username' }).fill('invalid-user');

    // Fill in password with invalid value
    await page.getByRole('textbox', { name: 'Password' }).fill('invalid-password');

    // Check terms checkbox if it exists (required on deployed environment)
    const termsCheckbox = page.getByRole('checkbox', { name: 'I agree to Terms of Use and Privacy Policy' });
    if (await termsCheckbox.isVisible()) {
      await termsCheckbox.check();
    }

    // Click submit button
    await page.getByRole('button', { name: 'Sign In' }).click();

    // Wait for error alert to be visible
    await expect(page.getByRole('alert')).toBeVisible({ timeout: 10000 });

    // Verify we are still on login page (URL hasn't changed to home)
    await expect(page).not.toHaveURL('**/home');
  });
});
