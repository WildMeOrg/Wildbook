// tests/encounter-search.spec.js
const { test, expect } = require('@playwright/test');

/**
 * Helper function to check if user is logged in
 * Returns true if logged in, false otherwise
 */
async function isLoggedIn(page) {
  try {
    // Check if "My Data" button is visible (only visible when logged in)
    const myDataButton = page.getByRole('button', { name: 'My Data' });
    await myDataButton.waitFor({ state: 'visible', timeout: 2000 });
    return true;
  } catch (error) {
    return false;
  }
}

/**
 * Helper function to perform login
 */
async function login(page) {
  await page.goto('https://qa.wildme.org/react');
  await page.getByRole('textbox', { name: 'Username' }).fill('test123');
  await page.getByRole('textbox', { name: 'Password' }).fill('test123');
  await page.getByRole('button', { name: 'Sign In' }).click();
  
  // Wait for successful login
  await page.waitForURL('**/home');
  await page.waitForLoadState('networkidle');
  
  // Verify login succeeded
  await expect(page.getByRole('button', { name: 'My Data' })).toBeVisible({ timeout: 10000 });
}

test.describe('Encounter Search with Photo Filter', () => {
  test('search for encounters with photos', async ({ page }) => {
    // Step 1: Check if already logged in
    await page.goto('https://qa.wildme.org/react');
    
    const loggedIn = await isLoggedIn(page);
    
    if (!loggedIn) {
      console.log('Not logged in, performing login...');
      await login(page);
    } else {
      console.log('Already logged in, continuing...');
    }
    
    // Step 2: Navigate to Search > Encounters
    // Click on Search button in navigation
    await page.getByRole('button', { name: 'Search' }).click();
    
    // Click on Encounters link in the dropdown
    await page.getByRole('link', { name: 'Encounters' }).click();
    
    // Wait for the encounter search page to load
    await page.waitForURL('**/encounter-search');
    await page.waitForLoadState('networkidle');
    
    // Verify we're on the encounter search page
    await expect(page.getByText('Encounter Search Filters')).toBeVisible();
    
    // Step 3: Find and check the "Has at least one associated photo or video" checkbox
    const photoCheckbox = page.getByRole('checkbox', { 
      name: /Has at least one associated/i 
    });
    
    // Scroll to the checkbox if needed
    await photoCheckbox.scrollIntoViewIfNeeded();
    
    // Check the checkbox
    await photoCheckbox.check();
    
    // Verify checkbox is checked
    await expect(photoCheckbox).toBeChecked();
    
    // Click the Apply button to submit the search
    await page.getByRole('button', { name: 'Apply' }).first().click();
    
    // Wait for results page to load
    await page.waitForURL('**/encounter-search?results=true');
    await page.waitForLoadState('networkidle');
    
    // Wait a bit more for the results to render
    await page.waitForTimeout(2000);
    
    // Step 4: Verify the results page displays correctly
    
    // Verify the page title/heading
    await expect(page.getByRole('heading', { name: 'Encounter Search Results', level: 2 })).toBeVisible();
    
    // Verify table headers are present
    await expect(page.getByRole('columnheader', { name: /Individual ID/ })).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('columnheader', { name: /Sighting ID/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Alternative ID/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Created Date/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Location ID/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Species/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Submitter/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Date Submitted/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Number of Annotations/ })).toBeVisible();
    
    // Verify "Applied Filters" button shows (with the filter count)
    await expect(page.getByRole('button', { name: /Applied Filters/ })).toBeVisible();
    
    // Verify pagination exists
    await expect(page.getByRole('navigation', { name: 'Pagination' })).toBeVisible();
    
    // Verify "Total Items" text is displayed
    await expect(page.getByText(/Total Items:/)).toBeVisible();
    
    // Verify view options are available
    await expect(page.getByRole('button', { name: 'Results Table' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Gallery View' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Map View' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Chart View' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Calendar View' })).toBeVisible();
    
    // Verify Export button is available
    await expect(page.getByRole('button', { name: 'Export' })).toBeVisible();
    
    console.log('✓ Encounter search with photo filter completed successfully!');
  });
});