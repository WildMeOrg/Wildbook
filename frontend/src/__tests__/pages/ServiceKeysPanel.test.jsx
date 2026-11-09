import React from "react";
import { IntlProvider } from "react-intl";
import messages from "../../locale/en.json";
import { render, screen, fireEvent, waitFor, act } from "@testing-library/react";
import ApiAccessPage from "../../pages/ApiAccess/ApiAccessPage";

let mockMe = { data: { username: "alice", roles: ["admin"] } };
jest.mock("../../models/auth/users/useGetMe", () => () => mockMe);
jest.mock("../../models/auth/useMintToken", () => {
  const actual = jest.requireActual("../../models/auth/useMintToken");
  return {
    __esModule: true,
    ...actual,
    default: () => ({ mint: jest.fn(), loading: false }),
  };
});

const KEY = {
  keyId: "k1",
  label: "ingest",
  scopes: ["data:read"],
  createdAt: 1,
  expiresAt: Date.now() + 86400000,
  revokedAt: null,
};

let keysState;
let fetchMock;

function json(status, body) {
  return Promise.resolve({ ok: status < 300, status, json: () => Promise.resolve(body) });
}

function installFetch({
  orgs = [{ id: "o1", name: "Org One" }],
  createStatus = 201,
  createError = "organization already has 2 active keys",
} = {}) {
  keysState = [KEY];
  fetchMock = jest.fn((url, opts) => {
    const m = opts.method;
    if (url === "/api/v3/service-keys/organizations") return json(200, { organizations: orgs });
    if (m === "GET") return json(200, { keys: keysState });
    if (m === "POST") {
      if (createStatus !== 201)
        return json(createStatus, { success: false, error: createError });
      return json(201, { ...KEY, keyId: "k2", label: "new", key: "wbsvc_k2.SECRETVALUE" });
    }
    if (m === "DELETE") {
      keysState = keysState.map((k) => ({ ...k, revokedAt: 5 }));
      return json(200, { keyId: "k1", revoked: true });
    }
    return json(500, {});
  });
  global.fetch = fetchMock;
}

const renderPage = () =>
  render(
    <IntlProvider locale="en" messages={messages}>
      <ApiAccessPage />
    </IntlProvider>,
  );

async function enterPassword(pw = "s3cr3t") {
  fireEvent.change(await screen.findByLabelText("Password"), { target: { value: pw } });
  fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
}

async function openPanel() {
  renderPage();
  fireEvent.click(screen.getByRole("button", { name: "Manage service keys" }));
  await enterPassword();
  await screen.findByText("ingest");
  // the prompt modal fades out; wait until it is really gone
  await waitFor(() => expect(screen.queryByLabelText("Password")).not.toBeInTheDocument());
}

describe("ServiceKeysPanel", () => {
  beforeEach(() => {
    mockMe = { data: { username: "alice", roles: ["admin"] } };
    installFetch();
    window.confirm = jest.fn(() => true);
    localStorage.clear();
    sessionStorage.clear();
  });

  it("entryHiddenWithoutRole", () => {
    mockMe = { data: { username: "alice", roles: ["researcher"] } };
    renderPage();
    expect(screen.queryByRole("button", { name: "Manage service keys" })).not.toBeInTheDocument();
  });

  it("entryShownForOrgAdmin", () => {
    mockMe = { data: { username: "alice", roles: ["orgAdmin"] } };
    renderPage();
    expect(screen.getByRole("button", { name: "Manage service keys" })).toBeInTheDocument();
  });

  it("entryHiddenForOrgSuperOnly", () => {
    mockMe = { data: { username: "alice", roles: ["orgSuper"] } };
    renderPage();
    expect(screen.queryByRole("button", { name: "Manage service keys" })).not.toBeInTheDocument();
  });

  it("listShowsCreatorAndLastUse", async () => {
    const usedAt = Date.UTC(2026, 9, 1, 12, 0, 0);
    keysState = [
      { ...KEY, createdByName: "carol", lastUsedAt: usedAt },
      { ...KEY, keyId: "k3", label: "unused", createdByName: null, lastUsedAt: null },
    ];
    await openPanel();
    expect(screen.getByRole("columnheader", { name: "Created by" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Last used" })).toBeInTheDocument();
    const used = screen.getByText("ingest").closest("tr");
    expect(used).toHaveTextContent("carol");
    expect(used).toHaveTextContent(new Date(usedAt).toLocaleString());
    const unused = screen.getByText("unused").closest("tr");
    expect(unused).toHaveTextContent("\u2014");
    expect(unused).toHaveTextContent("Never");
  });

  it("entryFlow", async () => {
    await openPanel();
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v3/service-keys/organizations");
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Basic " + btoa("alice:s3cr3t"));
    expect(screen.getByLabelText("Organization")).toBeInTheDocument();
    expect(fetchMock.mock.calls[1][0]).toBe("/api/v3/organizations/o1/service-keys");
  });

  it("shows none message when discovery is empty", async () => {
    installFetch({ orgs: [] });
    renderPage();
    fireEvent.click(screen.getByRole("button", { name: "Manage service keys" }));
    await enterPassword();
    expect(await screen.findByText(messages.API_SERVICE_KEYS_NONE)).toBeInTheDocument();
  });

  it("requestsOmitCookies", async () => {
    await openPanel();
    fireEvent.click(screen.getByRole("button", { name: "Revoke" }));
    await enterPassword();
    await waitFor(() => expect(keysState[0].revokedAt).toBe(5));
    expect(fetchMock.mock.calls.length).toBeGreaterThan(2);
    fetchMock.mock.calls.forEach(([, opts]) => {
      expect(opts.credentials).toBe("omit");
      expect(opts.headers.Authorization).toMatch(/^Basic /);
    });
  });

  it("createdKeyShownOnceThenCleared", async () => {
    await openPanel();
    fireEvent.change(screen.getByLabelText("Label"), { target: { value: "new" } });
    fireEvent.click(screen.getByRole("button", { name: "Create service key" }));
    await enterPassword();
    expect(await screen.findByText("wbsvc_k2.SECRETVALUE")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Copy" })).toBeInTheDocument();
    const body = JSON.parse(fetchMock.mock.calls.find(([, o]) => o.method === "POST")[1].body);
    expect(body).toEqual({ label: "new", scopes: ["data:read"], days: 90, readAsCreator: false });
    fireEvent.click(screen.getByRole("button", { name: "Done" }));
    expect(screen.queryByText(/SECRETVALUE/)).not.toBeInTheDocument();
    expect(JSON.stringify({ ...localStorage })).not.toMatch(/SECRETVALUE|s3cr3t/);
    expect(JSON.stringify({ ...sessionStorage })).not.toMatch(/SECRETVALUE|s3cr3t/);
  });

  it("passwordClearedOnCancelAndSuccess", async () => {
    renderPage();
    fireEvent.click(screen.getByRole("button", { name: "Manage service keys" }));
    fireEvent.change(await screen.findByLabelText("Password"), { target: { value: "abc" } });
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    await waitFor(() => expect(screen.queryByLabelText("Password")).not.toBeInTheDocument());
    fireEvent.click(screen.getByRole("button", { name: "Manage service keys" }));
    expect(await screen.findByLabelText("Password")).toHaveValue("");
    await enterPassword("abc");
    await screen.findByText("ingest");
    fireEvent.click(screen.getByRole("button", { name: "Revoke" }));
    expect(await screen.findByLabelText("Password")).toHaveValue("");
  });

  it("daysInputBounded", async () => {
    await openPanel();
    const days = screen.getByLabelText(/Valid for/);
    expect(days).toHaveValue(90);
    fireEvent.change(screen.getByLabelText("Label"), { target: { value: "x" } });
    const create = screen.getByRole("button", { name: "Create service key" });
    expect(create).toBeEnabled();
    fireEvent.change(days, { target: { value: "0" } });
    expect(create).toBeDisabled();
    fireEvent.change(days, { target: { value: "366" } });
    expect(create).toBeDisabled();
    fireEvent.change(days, { target: { value: "365" } });
    expect(create).toBeEnabled();
  });

  it("revokeConfirms", async () => {
    await openPanel();
    window.confirm = jest.fn(() => false);
    fireEvent.click(screen.getByRole("button", { name: "Revoke" }));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining("within 15 minutes"));
    expect(screen.queryByLabelText("Password")).not.toBeInTheDocument();
    window.confirm = jest.fn(() => true);
    fireEvent.click(screen.getByRole("button", { name: "Revoke" }));
    await enterPassword();
    expect(await screen.findByText("Revoked")).toBeInTheDocument();
  });

  it("revokeWarningsSayFifteenMinutesInEveryLocale", () => {
    for (const lang of ["en", "de", "es", "fr", "it"]) {
      const m = require(`../../locale/${lang}.json`);
      for (const id of ["API_SERVICE_KEY_CONFIRM_REVOKE", "API_SERVICE_KEY_CONFIRM_REVOKE_ALL"]) {
        expect(m[id]).toMatch(/15/);
      }
    }
  });

  it("limitErrorShown", async () => {
    installFetch({ createStatus: 409 });
    await openPanel();
    fireEvent.change(screen.getByLabelText("Label"), { target: { value: "third" } });
    fireEvent.click(screen.getByRole("button", { name: "Create service key" }));
    await enterPassword();
    expect(await screen.findByText(messages.API_SERVICE_KEY_LIMIT)).toBeInTheDocument();
  });

  it("otherConflictShowsServiceAccountMessage", async () => {
    installFetch({
      createStatus: 409,
      createError:
        "service account is not eligible for keys; a Wildbook administrator must fix it",
    });
    await openPanel();
    fireEvent.change(screen.getByLabelText("Label"), { target: { value: "third" } });
    fireEvent.click(screen.getByRole("button", { name: "Create service key" }));
    await enterPassword();
    expect(await screen.findByText(messages.API_SERVICE_KEY_ACCOUNT_CONFLICT)).toBeInTheDocument();
    expect(screen.queryByText(messages.API_SERVICE_KEY_LIMIT)).not.toBeInTheDocument();
  });

  it("usernameTakenConflictShowsServiceAccountMessage", async () => {
    installFetch({ createStatus: 409, createError: "service account username is already taken" });
    await openPanel();
    fireEvent.change(screen.getByLabelText("Label"), { target: { value: "third" } });
    fireEvent.click(screen.getByRole("button", { name: "Create service key" }));
    await enterPassword();
    expect(await screen.findByText(messages.API_SERVICE_KEY_ACCOUNT_CONFLICT)).toBeInTheDocument();
  });

  it("staleListResponseIsIgnoredAfterOrgChange", async () => {
    installFetch({
      orgs: [
        { id: "o1", name: "Org One" },
        { id: "o2", name: "Org Two" },
      ],
    });
    const base = fetchMock.getMockImplementation();
    const pending = {};
    renderPage();
    fireEvent.click(screen.getByRole("button", { name: "Manage service keys" }));
    // hold both lists: o1 (initial) and o2 (after the switch)
    pending.hold = true;
    fetchMock.mockImplementation((url, opts) => {
      const m = url.match(/organizations\/(o[12])\/service-keys$/);
      if (opts.method === "GET" && m && pending.hold) {
        return new Promise((resolve) => (pending[m[1]] = resolve));
      }
      return base(url, opts);
    });
    await enterPassword();
    await waitFor(() => expect(pending.o1).toBeDefined());
    await screen.findByLabelText("Organization");
    fireEvent.change(screen.getByLabelText("Organization"), { target: { value: "o2" } });
    await waitFor(() => expect(pending.o2).toBeDefined());
    const keyFor = (label) => ({ ...KEY, keyId: label, label });
    const reply = (body) => ({ ok: true, status: 200, json: () => Promise.resolve(body) });
    pending.o2(reply({ keys: [keyFor("keyOfTwo")] }));
    expect(await screen.findByText("keyOfTwo")).toBeInTheDocument();
    await act(async () => {
      pending.o1(reply({ keys: [keyFor("keyOfOne")] }));
      await new Promise((r) => setTimeout(r, 50));
    });
    expect(screen.getByText("keyOfTwo")).toBeInTheDocument();
    expect(screen.queryByText("keyOfOne")).not.toBeInTheDocument();
  });

  it("shows write-needs-admin hint only with submissions:write", async () => {
    await openPanel();
    expect(screen.queryByText(messages.API_SERVICE_KEY_WRITE_NEEDS_ADMIN)).not.toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("submissions:write"));
    expect(screen.getByText(messages.API_SERVICE_KEY_WRITE_NEEDS_ADMIN)).toBeInTheDocument();
  });

  it("readAsCreatorSentOnlyWithDataRead", async () => {
    await openPanel();
    const box = screen.getByLabelText(messages.API_SERVICE_KEY_READ_AS_CREATOR);
    expect(screen.getByText(messages.API_SERVICE_KEY_READ_AS_CREATOR_HELP)).toBeInTheDocument();
    fireEvent.click(box);
    fireEvent.change(screen.getByLabelText("Label"), { target: { value: "bound" } });
    fireEvent.click(screen.getByRole("button", { name: "Create service key" }));
    await enterPassword();
    await screen.findByText("wbsvc_k2.SECRETVALUE");
    const body = JSON.parse(fetchMock.mock.calls.find(([, o]) => o.method === "POST")[1].body);
    expect(body.readAsCreator).toBe(true);
    // the option disappears without data:read
    fireEvent.click(screen.getByLabelText("data:read"));
    expect(
      screen.queryByLabelText(messages.API_SERVICE_KEY_READ_AS_CREATOR),
    ).not.toBeInTheDocument();
  });

  it("readsAsColumn", async () => {
    keysState = [
      { ...KEY, readAsCreator: true },
      { ...KEY, keyId: "k3", label: "plain", readAsCreator: false },
    ];
    await openPanel();
    expect(screen.getByRole("columnheader", { name: "Reads as" })).toBeInTheDocument();
    expect(screen.getByText("ingest").closest("tr")).toHaveTextContent("Creator");
    expect(screen.getByText("plain").closest("tr")).toHaveTextContent("Service account");
  });

  it("notMemberErrorShown", async () => {
    installFetch({
      createStatus: 403,
      createError: "readAsCreator needs a creator who is a direct member of this organization",
    });
    await openPanel();
    fireEvent.click(screen.getByLabelText(messages.API_SERVICE_KEY_READ_AS_CREATOR));
    fireEvent.change(screen.getByLabelText("Label"), { target: { value: "bound" } });
    fireEvent.click(screen.getByRole("button", { name: "Create service key" }));
    await enterPassword();
    expect(
      await screen.findByText(messages.API_SERVICE_KEY_READ_AS_NOT_MEMBER),
    ).toBeInTheDocument();
  });

  it("refreshFailureAfterCreateDoesNotReprompt", async () => {
    await openPanel();
    const base = fetchMock.getMockImplementation();
    fetchMock.mockImplementation((url, opts) =>
      opts.method === "GET" && url.includes("/service-keys") && !url.endsWith("organizations")
        ? json(500, { success: false, error: "boom" })
        : base(url, opts),
    );
    fireEvent.change(screen.getByLabelText("Label"), { target: { value: "new" } });
    fireEvent.click(screen.getByRole("button", { name: "Create service key" }));
    await enterPassword();
    expect(await screen.findByText("wbsvc_k2.SECRETVALUE")).toBeInTheDocument();
    expect(await screen.findByText(messages.API_SERVICE_KEY_ERROR)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByLabelText("Password")).not.toBeInTheDocument());
    expect(fetchMock.mock.calls.filter(([, o]) => o.method === "POST").length).toBe(1);
  });

  it("refreshFailureAfterRevokeDoesNotReprompt", async () => {
    await openPanel();
    const base = fetchMock.getMockImplementation();
    fetchMock.mockImplementation((url, opts) =>
      opts.method === "GET" && !url.endsWith("organizations")
        ? json(500, { success: false, error: "boom" })
        : base(url, opts),
    );
    fireEvent.click(screen.getByRole("button", { name: "Revoke" }));
    await enterPassword();
    expect(await screen.findByText(messages.API_SERVICE_KEY_ERROR)).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByLabelText("Password")).not.toBeInTheDocument());
    expect(fetchMock.mock.calls.filter(([, o]) => o.method === "DELETE").length).toBe(1);
  });
});

describe("MyReadAsKeysPanel", () => {
  const MINE = {
    ...KEY,
    keyId: "m1",
    label: "gundi",
    readAsCreator: true,
    organizationId: "org 7",
  };
  let mine;

  beforeEach(() => {
    mockMe = { data: { username: "bob", roles: ["researcher"] } };
    mine = [MINE];
    fetchMock = jest.fn((url, opts) => {
      if (url === "/api/v3/service-keys/mine") return json(200, { keys: mine });
      if (opts.method === "DELETE") {
        mine = [];
        return json(200, { keyId: "m1", revoked: true });
      }
      return json(500, {});
    });
    global.fetch = fetchMock;
    window.confirm = jest.fn(() => true);
  });

  const openMine = async () => {
    renderPage();
    fireEvent.click(screen.getByRole("button", { name: messages.API_MY_READ_AS_KEYS }));
    await enterPassword();
    await waitFor(() => expect(screen.queryByLabelText("Password")).not.toBeInTheDocument());
  };

  it("shownToEveryoneSignedIn", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: messages.API_MY_READ_AS_KEYS }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Manage service keys" })).not.toBeInTheDocument();
  });

  it("listsAndRevokesThroughTheKeysOrganization", async () => {
    await openMine();
    expect(await screen.findByText("gundi")).toBeInTheDocument();
    expect(fetchMock.mock.calls[0][1].credentials).toBe("omit");
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe("Basic " + btoa("bob:s3cr3t"));
    fireEvent.click(screen.getByRole("button", { name: "Revoke" }));
    await enterPassword();
    expect(await screen.findByText(messages.API_MY_READ_AS_KEYS_NONE)).toBeInTheDocument();
    const del = fetchMock.mock.calls.find(([, o]) => o.method === "DELETE");
    expect(del[0]).toBe("/api/v3/organizations/org%207/service-keys/m1");
  });

  it("emptyState", async () => {
    mine = [];
    await openMine();
    expect(await screen.findByText(messages.API_MY_READ_AS_KEYS_NONE)).toBeInTheDocument();
  });
});
