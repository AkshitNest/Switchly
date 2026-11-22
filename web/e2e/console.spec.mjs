import { test, expect } from "@playwright/test";
import { randomUUID, randomBytes } from "node:crypto";

const base = process.env.TEST_API_ORIGIN || "http://127.0.0.1:18086";
const accounts = () => ({
  fullName: "Release Engineer",
  email: randomUUID() + "@example.test",
  password: randomBytes(18).toString("hex"),
  organizationName: "Platform Studio",
});
async function backend(path, method = "GET", body, authorization) {
  const r = await fetch(base + "/api/v1" + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(authorization ? { Authorization: authorization } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!r.ok) throw Error(method + " " + path + " returned " + r.status);
  return r.status === 204 ? null : r.json();
}
async function fixture() {
  const info = accounts(),
    auth = await backend("/auth/register", "POST", info),
    bearer = "Bearer " + auth.accessToken;
  const [org] = await backend("/orgs", "GET", undefined, bearer);
  const project = await backend(
    "/orgs/" + org.id + "/projects",
    "POST",
    { name: "Release console", key: "release-console" },
    bearer,
  );
  return { info, bearer, org, project };
}
const button = (scope, name) =>
  scope.getByRole("button", { name, exact: true });
const dialog = (page) => page.getByRole("dialog");
async function login(page, info, url = "/") {
  await page.goto(url);
  await page.getByLabel("Email address").fill(info.email);
  await page.getByPlaceholder("Enter your password").fill(info.password);
  await button(page, "Sign in").click();
  await expect(
    page.getByRole("heading", { name: "Welcome back" }),
  ).toBeHidden();
}
async function navigate(page, name) {
  if (await button(page, "Open menu").isVisible())
    await button(page, "Open menu").click();
  await button(page.locator("nav"), name).click();
}
async function createFlag(page, name, type, value) {
  await button(page, "New flag").click();
  const d = dialog(page);
  await d.getByLabel("Flag name").fill(name);
  await d.getByLabel("Flag key").fill(name.toLowerCase().replaceAll(" ", "-"));
  await d.getByLabel("Value type").selectOption(type);
  if (type === "BOOLEAN")
    await d.getByLabel("Logical default value").selectOption(value);
  else await d.getByLabel("Logical default value").fill(value);
  await button(d, "Create flag").click();
  await expect(page.getByRole("heading", { name, exact: true })).toBeVisible();
}
test("registration, organization and project selection, reload, logout and login", async ({
  page,
}) => {
  const info = accounts();
  await page.goto("/");
  await button(page, "Create an account").click();
  await page.getByLabel("Full name").fill(info.fullName);
  await page.getByLabel("Email address").fill(info.email);
  await page.getByPlaceholder("At least 8 characters").fill(info.password);
  await page
    .getByLabel("Organization name (optional)")
    .fill(info.organizationName);
  await button(page, "Create account").click();
  await button(page, "New project").click();
  await dialog(page).getByLabel("Project name").fill("Checkout");
  await button(dialog(page), "Create project").click();
  await expect(
    page.getByRole("heading", { name: "Project overview" }),
  ).toBeVisible();
  await page.reload();
  await expect(page.getByLabel("Project", { exact: true })).toContainText(
    "Checkout",
  );
  await navigate(page, "Team access");
  const signIn = await page.getByLabel("Organization sign-in link").inputValue();
  expect(signIn).toContain("#login/");
  await button(page, "Sign out").click();
  await expect(
    page.getByRole("heading", { name: "Welcome back" }),
  ).toBeVisible();
  await login(page, info, signIn);
});
test("all four logical types, precise JSON, environment configuration and deletion", async ({
  page,
}) => {
  const { info } = await fixture();
  await login(page, info);
  for (const [name, type, value] of [
    ["Rate", "NUMBER", "0.123456789012345678901"],
    ["Banner", "STRING", "Welcome"],
    ["Layout", "JSON", '{"count":9007199254740993123}'],
    ["Checkout", "BOOLEAN", "false"],
  ]) {
    await navigate(page, "Feature flags");
    await createFlag(page, name, type, value);
    if (type === "NUMBER" || type === "JSON")
      await expect(page.locator(".definition .literal")).toContainText(value);
  }
  await button(page, "Edit logical flag").click();
  await dialog(page).getByLabel("Flag name").fill("Checkout V2");
  await dialog(page).getByLabel("Description").fill("New checkout flow");
  await button(dialog(page), "Save changes").click();
  await expect(
    page.getByRole("heading", { name: "Checkout V2" }),
  ).toBeVisible();
  for (const label of ["Development", "Staging", "Production"]) {
    await page.getByLabel("Configuration environment").selectOption({ label });
    const toggle = page.getByRole("switch", {
      name: "Enable flag in " + label,
    });
    await expect(toggle).not.toBeChecked();
    if (label === "Production") {
      await toggle.click();
      await expect(toggle).toBeChecked();
      await button(page, "Edit value").click();
      await dialog(page).getByLabel("Environment value").selectOption("true");
      await button(dialog(page), "Save changes").click();
      await expect(page.locator(".value-row pre")).toHaveText("true");
    }
  }
  await page
    .getByLabel("Configuration environment")
    .selectOption({ label: "Development" });
  await expect(page.getByRole("switch")).not.toBeChecked();
  await button(page, "Delete flag").click();
  await button(dialog(page), "Cancel").click();
  await expect(
    page.getByRole("heading", { name: "Checkout V2" }),
  ).toBeVisible();
  await button(page, "Delete flag").click();
  await button(dialog(page), "Delete flag").click();
  await expect(
    page.getByRole("heading", { name: "Feature flags" }),
  ).toBeVisible();
  await expect(button(page, "Checkout V2")).toHaveCount(0);
});
test("segments, preview, ordered targeting, rollout and SDK credential lifecycle", async ({
  page,
}) => {
  const { info } = await fixture();
  await login(page, info);
  await navigate(page, "Segments");
  await button(page, "New segment").click();
  let d = dialog(page);
  await d.getByLabel("Name", { exact: true }).fill("Pilot customers");
  await d.getByLabel("Segment key").fill("pilot");
  await d.getByLabel("Operand", { exact: true }).fill("IN");
  await button(d, "Create segment").click();
  await button(page, "Preview").click();
  d = dialog(page);
  await d.getByLabel("Evaluation context").fill('{"country":"IN"}');
  await button(d, "Run preview").click();
  await expect(d.getByText("Matches segment")).toBeVisible();
  await d.getByLabel("Evaluation context").fill("{}");
  await button(d, "Run preview").click();
  await expect(d.getByText("Outside segment")).toBeVisible();
  await button(d, "Close dialog").click();
  await navigate(page, "Feature flags");
  await createFlag(page, "Beta", "BOOLEAN", "false");
  await page.getByRole("switch").click();
  await expect(page.getByRole("switch")).toBeChecked();
  await button(page, "Edit value").click();
  await dialog(page).getByLabel("Environment value").selectOption("true");
  await button(dialog(page), "Save changes").click();
  await button(page, "Edit rules").click();
  d = dialog(page);
  await button(d, "Add rule").click();
  await d
    .getByRole("combobox", { name: "Action", exact: true })
    .selectOption("DEFAULT");
  await d.getByLabel("Operand", { exact: true }).fill("CA");
  await button(d, "Add rule").click();
  await d.getByLabel("Match using").nth(1).selectOption("segment");
  await d
    .getByRole("combobox", { name: "Segment", exact: true })
    .selectOption("pilot");
  await button(d, "Move rule 2 up").click();
  await button(d, "Save rules").click();
  await expect(page.locator(".rule-list li").first()).toContainText("pilot");
  await button(page, "Edit rules").click();
  await button(dialog(page), "Remove rule 2").click();
  await button(dialog(page), "Save rules").click();
  await expect(page.locator(".rule-list li")).toHaveCount(1);
  for (const pct of ["0", "100", "", "100"]) {
    await button(page, "Edit rollout").click();
    await dialog(page).getByLabel("Percentage").fill(pct);
    await button(dialog(page), "Save changes").click();
    await expect(page.locator(".metric-small")).toHaveText(
      pct ? pct + "%" : "Unset",
    );
  }
  await page.getByLabel("Evaluation context").fill('{"userId":"stable-user","country":"IN"}');
  await button(page,"Evaluate flag").click();
  await expect(page.locator(".preview-result")).toContainText("rollout included");
  await expect(page.locator(".preview-result strong")).toHaveText("true");
  await navigate(page, "SDK keys");
  await button(page, "Issue SDK key").click();
  await dialog(page).getByLabel("Key name").fill("Checkout server");
  await dialog(page).getByLabel("Development").check();
  await button(dialog(page), "Issue key").click();
  const secret = await dialog(page)
    .getByLabel("One-time SDK secret")
    .inputValue();
  expect(secret.startsWith("swk_")).toBe(true);
  await button(dialog(page), "Done").click();
  const payload = {
    projectKey: "release-console",
    environmentKey: "development",
    flagKey: "beta",
    context: { userId: "stable-user", country: "IN" },
  };
  const decision = await backend("/evaluate", "POST", payload, "SDK " + secret);
  expect(decision.reason).toBe("ROLLOUT_INCLUDED");
  expect(decision.value).toBe(true);
  expect(
    await page.evaluate(
      () =>
        Object.keys(localStorage).length + Object.keys(sessionStorage).length,
    ),
  ).toBe(0);
  await page.reload();
  await expect(page.getByText("Checkout server")).toBeVisible();
  expect((await page.locator("body").innerText()).includes(secret)).toBe(false);
  await button(page, "Revoke").click();
  await button(dialog(page), "Revoke key").click();
  await expect(page.getByText("Revoked", { exact: true })).toBeVisible();
  const revoked = await fetch(base + "/api/v1/evaluate", {
    method: "POST",
    headers: {
      Authorization: "SDK " + secret,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
  expect(revoked.status).toBe(401);
  await navigate(page, "Segments");
  await button(page, "Delete Pilot customers").click();
  await button(dialog(page), "Delete segment").click();
  await expect(dialog(page).getByRole("alert")).toContainText("reference");
  await button(dialog(page), "Cancel").click();
  await button(page, "Edit").click();
  await dialog(page).getByLabel("Name", { exact: true }).fill("Paid pilot");
  await button(dialog(page), "Save changes").click();
  await navigate(page, "Feature flags");
  await button(page, "Beta").click();
  await button(page, "Edit rules").click();
  await button(dialog(page), "Remove rule 1").click();
  await button(dialog(page), "Save rules").click();
  await navigate(page, "Segments");
  await button(page, "Delete Paid pilot").click();
  await button(dialog(page), "Delete segment").click();
  await expect(
    page.getByRole("heading", { name: "No segments yet" }),
  ).toBeVisible();
});
test("environment CRUD and mobile navigation", async ({ page }) => {
  const { info } = await fixture();
  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, info);
  await expect(
    page.getByRole("heading", { name: "Project overview" }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
  await navigate(page, "Environments");
  await button(page, "New environment").click();
  let d = dialog(page);
  await d.getByLabel("Name", { exact: true }).fill("Preview");
  await d.getByLabel("Environment key").fill("preview");
  await button(d, "Create environment").click();
  const card = page
    .getByRole("article")
    .filter({
      has: page.getByRole("heading", { name: "Preview", exact: true }),
    });
  await button(card, "Edit").click();
  d = dialog(page);
  await d.getByLabel("Name", { exact: true }).fill("Canary");
  await button(d, "Save changes").click();
  await button(page, "Delete Canary").click();
  await button(dialog(page), "Delete environment").click();
  await expect(page.getByRole("heading", { name: "Canary" })).toHaveCount(0);
  await expect(button(page, "Delete Production")).toHaveCount(0);
});
for (const role of ["ADMIN", "EMPLOYEE"]) {
  test(role + " gets matching controls and server authorization", async ({ page }) => {
      const seed = await fixture();
      const sibling = await backend("/orgs/" + seed.org.id + "/projects", "POST", { name: "Restricted project" }, seed.bearer);
      let account = seed.info;
      if (role === "EMPLOYEE") {
        account = accounts();
        await login(page, seed.info);
        await navigate(page, "Team access");
        await button(page, "Invite member").click();
        await dialog(page).getByLabel("Email address").fill(account.email);
        await dialog(page).getByLabel("Assigned project").selectOption(seed.project.id);
        await button(dialog(page), "Create invitation").click();
        const inviteLink = await dialog(page).getByLabel("Invitation link").inputValue();
        await button(dialog(page), "Close dialog").click();
        await button(page, "Sign out").click();
        await expect(page.getByRole("heading",{name:"Welcome back"})).toBeVisible();
        await page.goto(inviteLink);
        await page.getByLabel("Full name").fill(account.fullName);
        await page.getByLabel("Email address").fill(account.email);
        await page.getByLabel("Password", {exact:true}).fill(account.password);
        await button(page, "Join organization").click();
      } else await login(page, account);
      await expect(
        page.getByRole("heading", { name: "Project overview" }),
      ).toBeVisible();
      const admin = role === "ADMIN", editor = true;
      await expect(page.getByLabel("Project", { exact: true }).locator('option[value="'+sibling.id+'"]')).toHaveCount(admin ? 1 : 0);
      const denied = await page.evaluate(async id => {
        return await Promise.all(["flags","segments","flag-drafts"].map(async tail => {
          const draft = tail === "flag-drafts";
          return (await fetch("/api/v1/projects/"+id+"/"+tail, draft
            ? {method:"POST",headers:{"X-Switchly-Request":"console","Content-Type":"application/json"},body:'{"brief":""}'} : {})).status;
        }));
      }, sibling.id);
      expect(denied).toEqual(admin ? [200,200,400] : [403,403,403]);
      await expect(button(page, "New flag")).toHaveCount(editor ? 1 : 0);
      await expect(button(page.locator("nav"), "SDK keys")).toHaveCount(
        admin ? 1 : 0,
      );
      await navigate(page, "Environments");
      await expect(button(page, "New environment")).toHaveCount(admin ? 1 : 0);
      await navigate(page, "Segments");
      await expect(button(page, "New segment")).toHaveCount(editor ? 1 : 0);
      const statuses = await page.evaluate(async ({ id }) => {
        const path = "/api/v1/projects/" + id;
        const env = await fetch(path + "/environments", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Switchly-Request": "console",
          },
          body: JSON.stringify({ name: "Canary", key: "canary" }),
        });
        const flag = await fetch(path + "/flags", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Switchly-Request": "console",
          },
          body: JSON.stringify({ name: "Release", key: "release" }),
        });
        const keys = await fetch(path + "/sdk-keys");
        return [env.status, flag.status, keys.status];
      }, seed.project);
      expect(statuses).toEqual([
        admin ? 201 : 403,
        editor ? 201 : 403,
        admin ? 200 : 403,
      ]);
      const other = await fixture();
      expect(
        await page.evaluate(
          async (id) =>
            (await fetch("/api/v1/projects/" + id + "/flags")).status,
          other.project.id,
        ),
      ).toBe(403);
    },
  );
}

test("a real expired JWT is refreshed across concurrent reads", async ({
  page,
}) => {
  test.setTimeout(95000);
  const { info } = await fixture();
  await login(page, info);
  await expect(
    page.getByRole("heading", { name: "Project overview" }),
  ).toBeVisible();
  // The isolated test stack issues one-minute access tokens.
  await new Promise((resolve) => setTimeout(resolve, 62000));
  const statuses = await page.evaluate(() =>
    Promise.all(
      ["/api/v1/orgs", "/api/v1/auth/me", "/api/v1/orgs"].map(
        async (p) => (await fetch(p)).status,
      ),
    ),
  );
  expect(statuses).toEqual([200, 200, 200]);
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Project overview" }),
  ).toBeVisible();
});

test("dashboard, project switching and responsive layouts", async ({
  page,
}, testInfo) => {
  const { info, bearer, org, project } = await fixture();
  const path = "/projects/" + project.id;
  const envs = await backend(path + "/environments", "GET", undefined, bearer);
  const dev = envs.find((e) => e.key === "development");
  for (const [name, key, type, fallback] of [
    ["Checkout redesign", "checkout-redesign", "BOOLEAN", false],
    ["Welcome message", "welcome-message", "STRING", "Welcome"],
    ["Retry limit", "retry-limit", "NUMBER", 3],
  ]) {
    const f = await backend(
      path + "/flags",
      "POST",
      { name, key, valueType: type, defaultValue: fallback },
      bearer,
    );
    await backend(
      "/environments/" + dev.id + "/flags/" + f.id + "/state",
      "PUT",
      { enabled: true },
      bearer,
    );
  }
  await backend(
    path + "/segments",
    "POST",
    {
      name: "Early access",
      key: "early-access",
      conditions: {
        match: "AND",
        conditions: [
          { attribute: "platform", operator: "EQUALS", value: "web" },
        ],
      },
    },
    bearer,
  );
  const other = await backend(
    "/orgs/" + org.id + "/projects",
    "POST",
    { name: "Mobile releases" },
    bearer,
  );
  await login(page, info);
  await page.getByLabel("Project", { exact: true }).selectOption(project.id);
  await expect(page.getByText("3 of 3 enabled")).toBeVisible();
  const badge = await page.locator(".org-name .scope-icon").boundingBox();
  const label = await page.locator(".org-name strong").boundingBox();
  expect(label.x).toBeGreaterThanOrEqual(badge.x + badge.width);
  await page.screenshot({ path: testInfo.outputPath("dashboard-desktop.png") });
  await page.getByLabel("Project", { exact: true }).selectOption(other.id);
  await expect(
    page.getByRole("heading", { name: "No feature flags yet" }),
  ).toBeVisible();
  await page.getByLabel("Project", { exact: true }).selectOption(project.id);
  for (const width of [768, 390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    await expect(page.getByText("3 of 3 enabled")).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    if (width === 390)
      await page.screenshot({
        path: testInfo.outputPath("dashboard-mobile.png"),
      });
  }
  await button(page, "Open menu").click();
  await page.keyboard.press("Escape");
  await expect(button(page, "Open menu")).toBeFocused();
});

test("local model drafts an editable flag before explicit creation", async ({ page }) => {
  test.setTimeout(125000);
  const { info, project, bearer } = await fixture();
  await login(page, info);
  await button(page,"New flag").click();
  const form = dialog(page);
  await form.getByText("Draft with AI",{exact:true}).click();
  await form.getByLabel("Release brief").fill("A boolean flag named Express checkout. Disable it by default.");
  await button(form,"Generate draft").click();
  await expect(form.getByRole("textbox", {name:"Flag name",exact:true})).not.toHaveValue("",{timeout:95000});
  await expect(form.getByLabel("Value type")).toHaveValue("BOOLEAN");
  await expect(form.getByLabel("Logical default value")).toHaveValue("false");
  expect(await backend("/projects/"+project.id+"/flags","GET",undefined,bearer)).toHaveLength(0);
  await form.getByRole("textbox", {name:"Flag name",exact:true}).fill("Reviewed checkout");
  await button(form,"Create flag").click();
  await expect(page.getByRole("heading",{name:"Reviewed checkout",exact:true})).toBeVisible();
});

test("team invitation cancellation, workspace selection, reassignment and removal", async ({page,browser}) => {
  const team = await fixture(), second = await fixture();
  const other = await backend("/orgs/"+team.org.id+"/projects","POST",{name:"Payments"},team.bearer);
  await login(page,team.info);
  await navigate(page,"Team access");
  async function invite() {
    await button(page,"Invite member").click();
    const d = dialog(page);
    await d.getByLabel("Email address").fill(second.info.email);
    await d.getByLabel("Assigned project").selectOption(team.project.id);
    await button(d,"Create invitation").click();
    const link = await dialog(page).getByLabel("Invitation link").inputValue();
    await button(dialog(page),"Close dialog").click();
    return link;
  }
  await invite();
  await button(page,"Cancel invitation").click();
  await button(dialog(page),"Cancel invitation").click();
  await expect(page.getByRole("heading",{name:"Pending invitations"})).toHaveCount(0);
  const link = await invite();
  const userContext = await browser.newContext();
  try {
    const member = await userContext.newPage();
    await member.goto(link);
    await member.getByLabel("Full name").fill(second.info.fullName);
    await member.getByLabel("Email address").fill(second.info.email);
    await member.locator("input[name=password]").fill(second.info.password);
    await button(member,"Join organization").click();
    await expect(member.getByRole("heading",{name:"Project overview"})).toBeVisible();
    await expect(member.getByLabel("Project",{exact:true})).toHaveValue(team.project.id);
    await button(member,"Sign out").click();
    await expect(member.getByRole("heading",{name:"Welcome back"})).toBeVisible();
    await member.getByLabel("Email address").fill(second.info.email);
    await member.locator("input[name=password]").fill(second.info.password);
    await button(member,"Sign in").click();
    await member.getByLabel("Select organization").selectOption(team.org.id);
    await button(member,"Sign in").click();
    await expect(member.getByRole("heading",{name:"Project overview"})).toBeVisible();
    await page.reload();
    const row = page.locator(".member-row").filter({hasText:second.info.email});
    await row.getByRole("button",{name:/Edit access/}).click();
    await dialog(page).getByLabel("Assigned project").selectOption(other.id);
    await button(dialog(page),"Save access").click();
    await expect(row).toContainText("Payments");
    await member.reload();
    await expect(member.getByLabel("Project",{exact:true})).toHaveValue(other.id);
    await row.getByRole("button",{name:/Remove /}).click();
    await button(dialog(page),"Remove member").click();
    await expect(row).toHaveCount(0);
    await member.reload();
    await expect(member.getByRole("heading",{name:"Welcome back"})).toBeVisible();
  } finally {await userContext.close();}
});
