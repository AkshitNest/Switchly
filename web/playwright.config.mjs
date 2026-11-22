import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./e2e",
  workers: 1,
  fullyParallel: false,
  timeout: 45000,
  retries: 0,
  use: {
    baseURL: process.env.CONSOLE_URL || "http://127.0.0.1:13086",
    launchOptions: process.env.TEST_CERT_SPKI ? {
      args: ["--ignore-certificate-errors-spki-list=" + process.env.TEST_CERT_SPKI],
    } : {},
    channel: process.env.PLAYWRIGHT_CHANNEL || "chrome",
    viewport: { width: 1440, height: 960 },
    trace: "off",
    video: "off",
    screenshot: "off",
  },
  reporter: "list",
});
