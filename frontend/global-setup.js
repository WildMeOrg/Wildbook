import { chromium } from "@playwright/test";

async function globalSetup() {
  const baseURL = process.env.TEST_URL || "https://qa.wildme.org/react";
  const username = process.env.TEST_USERNAME || "test";
  const password = process.env.TEST_PASSWORD || "test";

  const browser = await chromium.launch();
  const page = await browser.newPage();

  await page.goto(baseURL);

  await page.getByRole("textbox", { name: "Username" }).fill(username);
  await page.getByRole("textbox", { name: "Password" }).fill(password);

  const termsCheckbox = page.getByRole("checkbox", {
    name: "I agree to Terms of Use and Privacy Policy",
  });

  if (await termsCheckbox.isVisible().catch(() => false)) {
    await termsCheckbox.check();
  }

  await page.getByRole("button", { name: "Sign In" }).click();

  await page.waitForURL("**/home", { timeout: 15000 });

  await page.context().storageState({ path: ".auth/state.json" });

  await browser.close();
}

export default globalSetup;
