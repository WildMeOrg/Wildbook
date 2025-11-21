// @ts-check
const { test, expect } = require("@playwright/test");

/**
 * Test: Encounter search with photo filter shows image on encounter detail page
 *
 * This test verifies the workflow:
 * 1. Log in to the Wildbook application
 * 2. Navigate to Encounter Search via the Search menu
 * 3. Enable the "Has at least one associated photo or video" filter
 * 4. Submit the search and verify results load
 * 5. Open an encounter detail page from the results
 * 6. Verify that a photo is displayed correctly (not broken)
 */
test("encounter search shows image for encounters with photos", async ({
  page,
}) => {
  // Increase default timeout for slower operations
  test.setTimeout(60000);

  // ============================================
  // Step 1: Navigate to the homepage
  // ============================================
  await page.goto("http://frontend.scribble.com");

  // ============================================
  // Step 2: Click on "Log in" link to go to login page
  // The homepage has two versions - check which one we're on
  // ============================================
  const loginLink = page.getByRole("link", { name: "Log in" });
  if (await loginLink.isVisible({ timeout: 3000 }).catch(() => false)) {
    await loginLink.click();

    // ============================================
    // Step 3: Fill in login credentials
    // ============================================
    await page.getByRole("textbox", { name: "Username" }).fill("tomcat");
    await page.getByRole("textbox", { name: "Password" }).fill("tomcat123");
    await page.getByRole("button", { name: "Sign In" }).click();

    // Wait for navigation to complete after login
    await page.waitForURL(/\/react\/home/);
  }
  // If already logged in (no Log in link visible), continue with the flow

  // ============================================
  // Step 4: Open the Search dropdown menu
  // ============================================
  await page.getByRole("button", { name: "Search" }).click();

  // ============================================
  // Step 5: Click on "Encounters" in the dropdown
  // ============================================
  await page.getByRole("link", { name: "Encounters", exact: true }).click();

  // ============================================
  // Step 6: Wait for Encounter Search page to load
  // ============================================
  await expect(page).toHaveURL(/\/react\/encounter-search/);
  await expect(page.getByText("Encounter Search Filters")).toBeVisible();

  // ============================================
  // Step 7: Enable the "Has at least one associated photo or video" filter
  // ============================================
  await page
    .getByRole("checkbox", { name: "Has at least one associated" })
    .check();
  await expect(
    page.getByRole("checkbox", { name: "Has at least one associated" }),
  ).toBeChecked();

  // ============================================
  // Step 8: Submit the search by clicking the "Apply" button
  // ============================================
  await page.getByRole("button", { name: "Apply" }).first().click();

  // ============================================
  // Step 9: Wait for search results to load
  // ============================================
  await expect(page).toHaveURL(/results=true/);
  await expect(
    page.getByRole("heading", { name: "Encounter Search Results" }),
  ).toBeVisible();

  // Verify the table is visible (this is a div with role="table", not a native <table>)
  await expect(page.getByRole("table")).toBeVisible();

  // Wait for data rows to load - the first row (index 0) is the header row
  // Data rows start from index 1
  await expect(page.getByRole("row").nth(1)).toBeVisible();

  // ============================================
  // Step 10: Click on an Encounter row to open its detail page
  // ============================================
  // Listen for the new page (popup/new tab) BEFORE clicking
  const encounterPagePromise = page.context().waitForEvent("page");

  // Click on the first data row (skip header row at index 0)
  await page.getByRole("row").nth(1).click();

  // ============================================
  // Step 11: Wait for the new tab and switch to it
  // ============================================
  const encounterPage = await encounterPagePromise;

  // Wait for the page to fully load
  await encounterPage.waitForLoadState("domcontentloaded");
  await encounterPage.waitForLoadState("networkidle");

  // ============================================
  // Step 12: Verify we're on the Encounter detail page
  // ============================================
  // Check URL contains encounter page pattern
  await expect(encounterPage).toHaveURL(/\/react\/encounter\?number=/);

  // The heading text is "Encounter Unassigned" or "Encounter [ID]"
  // Use a more specific selector that matches the actual heading
  await expect(
    encounterPage
      .getByRole("heading", { level: 2 })
      .filter({ hasText: "Encounter" }),
  ).toBeVisible();

  // ============================================
  // Step 13: Verify that a photo is displayed correctly in the Images section
  // ============================================
  // Check that the "Images" section label is visible
  await expect(encounterPage.getByText("Images")).toBeVisible();

  // Verify that an encounter image element exists and is visible
  const encounterImage = encounterPage.getByRole("img", {
    name: "encounter image",
  });
  await expect(encounterImage).toBeVisible();

  // Verify the image has loaded correctly (naturalWidth > 0 indicates no broken image)
  const imageLoaded = await encounterImage.evaluate((img) => {
    return img.complete && img.naturalWidth > 0;
  });
  expect(imageLoaded).toBe(true);

  // ============================================
  // Cleanup: Close the encounter detail page tab
  // ============================================
  await encounterPage.close();
});
