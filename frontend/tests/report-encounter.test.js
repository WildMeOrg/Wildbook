// tests/report-encounter.spec.js
const { test, expect } = require('@playwright/test');

// Increase timeout for these tests
test.setTimeout(60000);

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
  await page.waitForURL('**/home', { timeout: 15000 });
  
  // Verify login succeeded
  await expect(page.getByRole('button', { name: 'My Data' })).toBeVisible({ timeout: 10000 });
}

test.describe('Report an Encounter Form', () => {
  test('navigate to report encounter form and verify all sections', async ({ page }) => {
    // Step 1: Check if already logged in
    await page.goto('https://qa.wildme.org/react');
    
    const loggedIn = await isLoggedIn(page);
    
    if (!loggedIn) {
      console.log('Not logged in, performing login...');
      await login(page);
    } else {
      console.log('Already logged in, continuing...');
    }
    
    // Step 2: Click on Submit button in navigation
    await page.getByRole('button', { name: 'Submit' }).click();
    
    // Step 3: Click on "Report an Encounter" link
    await page.getByRole('link', { name: 'Report an Encounter' }).click();
    
    // Wait for the page to load
    await page.waitForURL('**/report', { timeout: 15000 });
    await page.waitForTimeout(2000);
    
    // Step 4: Verify the Report an Encounter page displays correctly
    
    // Verify page heading
    await expect(page.getByRole('heading', { name: 'Report an Encounter', level: 3 })).toBeVisible();
    
    // Verify introductory text
    await expect(page.getByText(/Tell us about the animal you saw/i)).toBeVisible();
    
    // Verify required fields indicator
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByText(/Required fields/i)).toBeVisible();
    
    // Verify left sidebar navigation exists with all sections
    await expect(page.getByRole('heading', { name: 'Photos', level: 5 }).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Date and Time', level: 5 }).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Place', level: 5 }).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Species', level: 5 }).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Additional Comments', level: 5 }).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Followup Information', level: 5 }).first()).toBeVisible();
    
    // Verify Submit Encounter button in sidebar
    await expect(page.getByRole('button', { name: /Submit Encounter/i })).toBeVisible();
    
    // Verify Photos section
    await expect(page.getByText(/We support .jpg, .jpeg, .png, and bmp/i)).toBeVisible();
    await expect(page.getByText(/Drag and drop your files here or browse/i)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Browse' })).toBeVisible();
    
    // Verify Date and Time section
    await expect(page.getByRole('heading', { name: /Date and Time\*/i, level: 5 })).toBeVisible();
    await expect(page.getByText(/Type or select the date of the encounter/i)).toBeVisible();
    await expect(page.getByPlaceholder('YYYY-MM-DD')).toBeVisible();
    await expect(page.getByRole('combobox', { name: 'Date & Time from EXIF' })).toBeVisible();
    
    // Verify Place section (required)
    await expect(page.getByRole('heading', { name: /Place\*/i, level: 5 })).toBeVisible();
    await expect(page.getByText(/Location ID is required to establish match sets/i)).toBeVisible();
    await expect(page.getByText('Location ID*')).toBeVisible();
    
    // Verify GPS Coordinates fields
    await expect(page.getByText('GPS Coordinates')).toBeVisible();
    const spinbuttons = page.getByRole('spinbutton');
    expect(await spinbuttons.count()).toBeGreaterThanOrEqual(2);
    
    // Verify Species section
    await expect(page.getByRole('heading', { name: 'Species', level: 5 })).toBeVisible();
    await expect(page.getByText(/Species must be set for the detection model to run/i)).toBeVisible();
    
    // Verify Species dropdown
    const speciesDropdown = page.getByRole('combobox').filter({ hasText: /Select Species/ });
    await expect(speciesDropdown).toBeVisible();
    
    // Verify some species options are available
    await expect(page.getByRole('option', { name: /Giraffa tippelskirchi/i })).toBeVisible();
    await expect(page.getByRole('option', { name: /Panthera onca/i })).toBeVisible();
    await expect(page.getByRole('option', { name: /Panthera pardus/i })).toBeVisible();
    
    // Verify Additional Comments section
    await expect(page.getByRole('heading', { name: 'Additional Comments', level: 5 })).toBeVisible();
    const commentsTextbox = page.getByRole('textbox', { name: 'Type here' }).first();
    await expect(commentsTextbox).toBeVisible();
    
    // Verify Followup Information section
    await expect(page.getByRole('heading', { name: 'Followup Information', level: 5 })).toBeVisible();
    await expect(page.getByText(/Provide email if you want these people to get email updates/i)).toBeVisible();
    
    // Verify Submitter's information fields
    await expect(page.getByRole('heading', { name: "Submitter's information", level: 6 })).toBeVisible();
    await expect(page.getByText('Name').first()).toBeVisible();
    await expect(page.getByText('Email').first()).toBeVisible();
    
    // Verify Photographer's information fields
    await expect(page.getByRole('heading', { name: "Photographer's information", level: 6 })).toBeVisible();
    
    // Verify Additional Emails field
    await expect(page.getByRole('heading', { name: 'Additional Emails', level: 6 })).toBeVisible();
    await expect(page.getByPlaceholder(/name@email.com/i)).toBeVisible();
    
    console.log('✓ Report an Encounter form verification completed successfully!');
  });

  test('verify form validation for required fields', async ({ page }) => {
    // Login
    await page.goto('https://qa.wildme.org/react');
    
    const loggedIn = await isLoggedIn(page);
    if (!loggedIn) {
      await login(page);
    }
    
    // Navigate to Report an Encounter
    await page.getByRole('button', { name: 'Submit' }).click();
    await page.getByRole('link', { name: 'Report an Encounter' }).click();
    await page.waitForURL('**/report', { timeout: 15000 });
    await page.waitForTimeout(2000);
    
    // Try to submit without filling required fields
    await page.getByRole('button', { name: /Submit Encounter/i }).click();
    
    // Wait a moment for validation messages
    await page.waitForTimeout(1000);
    
    // Verify we're still on the report page (form didn't submit)
    expect(page.url()).toContain('/report');
    
    console.log('✓ Form validation test completed!');
  });

  test('fill out partial form data', async ({ page }) => {
    // Login
    await page.goto('https://qa.wildme.org/react');
    
    const loggedIn = await isLoggedIn(page);
    if (!loggedIn) {
      await login(page);
    }
    
    // Navigate to Report an Encounter
    await page.getByRole('button', { name: 'Submit' }).click();
    await page.getByRole('link', { name: 'Report an Encounter' }).click();
    await page.waitForURL('**/report', { timeout: 15000 });
    await page.waitForTimeout(2000);
    
    // Fill in Date and Time
    await page.getByPlaceholder('YYYY-MM-DD').fill('2025-12-03');
    
    // Select a species
    const speciesDropdown = page.getByRole('combobox').filter({ hasText: /Select Species/ });
    await speciesDropdown.click();
    await page.getByRole('option', { name: /Giraffa tippelskirchi/i }).first().click();
    
    // Fill in Additional Comments
    await page.getByRole('textbox', { name: 'Type here' }).first().fill('Test encounter report');
    
    // Fill in Submitter's Name
    const nameFields = page.getByText('Name').locator('..').locator('input');
    await nameFields.first().fill('Test User');
    
    // Fill in Submitter's Email
    const emailFields = page.getByText('Email').locator('..').locator('input');
    await emailFields.first().fill('test@example.com');
    
    // Verify fields are filled
    await expect(page.getByPlaceholder('YYYY-MM-DD')).toHaveValue('2025-12-03');
    await expect(page.getByRole('textbox', { name: 'Type here' }).first()).toHaveValue('Test encounter report');
    
    console.log('✓ Partial form fill test completed!');
  });
});