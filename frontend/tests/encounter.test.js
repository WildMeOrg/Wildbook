// tests/encounter-detail.spec.js
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
  await page.getByRole('textbox', { name: 'Username' }).fill(process.env.TEST_USERNAME || 'test123');
  await page.getByRole('textbox', { name: 'Password' }).fill(process.env.TEST_PASSWORD || 'test123');
  await page.getByRole('button', { name: 'Sign In' }).click();
  
  // Wait for successful login
  await page.waitForURL('**/home');
  await page.waitForLoadState('networkidle');
  
  // Verify login succeeded
  await expect(page.getByRole('button', { name: 'My Data' })).toBeVisible({ timeout: 10000 });
}

test.describe('Encounter Detail Page', () => {
  test('view encounter detail page and verify content', async ({ page }) => {
    // Step 1: Check if already logged in
    await page.goto(process.env.TEST_URL || 'https://qa.wildme.org/react');
    
    const loggedIn = await isLoggedIn(page);
    
    if (!loggedIn) {
      console.log('Not logged in, performing login...');
      await login(page);
    } else {
      console.log('Already logged in, continuing...');
    }
    
    // Step 2: Navigate directly to the encounter page
    await page.goto(`${process.env.TEST_URL}/encounter?number=${process.env.TEST_ENCOUNTER_ID || '20dde5ff-bd62-4648-ae0c-9e2515444a85'}`);
    
    // Wait for the page to load
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);
    
    // Step 3: Verify the encounter page displays correctly
    
    // Verify the main heading with encounter title
    await expect(page.getByRole('heading', { name: /Encounter Unassigned/i, level: 2 })).toBeVisible();
    
    // Verify the encounter ID is displayed
    await expect(page.getByText(/Encounter ID: 20dde5ff-bd62-4648-ae0c-9e2515444a85/i)).toBeVisible();
    
    // Verify the status dropdown exists
    const statusDropdown = page.getByRole('combobox');
    await expect(statusDropdown).toBeVisible();
    
    // Verify tab navigation exists
    await expect(page.getByText('Overview')).toBeVisible();
    await expect(page.getByText('More Details')).toBeVisible();
    
    // Verify Identity section
    await expect(page.getByRole('heading', { name: 'Identified as:', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Matched by:', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Alternate ID:', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Sighting ID:', level: 6 })).toBeVisible();
    
    // Verify Metadata section
    await expect(page.getByRole('heading', { name: 'Encounter ID', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Date Created', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Last Edit', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Assigned User:', level: 6 })).toBeVisible();
    await expect(page.getByText('test123')).toBeVisible();
    
    // Verify Location section
    await expect(page.getByRole('heading', { name: 'Location:', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Location ID:', level: 6 })).toBeVisible();
    await expect(page.getByText('Germany')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Country:', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Coordinates', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Map', level: 6 })).toBeVisible();
    
    // Verify Attributes section
    await expect(page.getByRole('heading', { name: 'Taxonomy:', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Status:', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Sex:', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Distinguishing Scar:', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Behavior:', level: 6 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Life Stage:', level: 6 })).toBeVisible();
    
    // Verify Images section
    await expect(page.getByRole('img', { name: 'encounter image' })).toBeVisible();
    
    // Verify image action buttons
    await expect(page.getByText('Match Results')).toBeVisible();
    await expect(page.getByText('Visual Matcher')).toBeVisible();
    await expect(page.getByText('New Match')).toBeVisible();
    await expect(page.getByText('Add Annotation')).toBeVisible();
    await expect(page.getByText('Add Image')).toBeVisible();
    
    // Verify Danger Zone section exists
    await expect(page.getByText('Danger Zone')).toBeVisible();
    await expect(page.getByText('Delete Encounter?')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Delete Encounter' })).toBeVisible();
    
    console.log('✓ Encounter detail page verification completed successfully!');
  });
});