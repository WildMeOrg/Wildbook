import { mintToken } from "../../models/auth/useMintToken";

describe("mintToken", () => {
  let fetchMock;
  beforeEach(() => { fetchMock = jest.fn(); global.fetch = fetchMock; }); // jsdom has no fetch by default
  afterEach(() => { jest.resetAllMocks(); });

  it("POSTs with a cookie-less Basic header and returns the token on 200", async () => {
    fetchMock.mockResolvedValue({
      status: 200,
      json: async () => ({ token: "tok123", tokenType: "Bearer", expiresInSeconds: 1800 }),
    });
    const res = await mintToken("alice", "s3cr3t");
    const [url, opts] = fetchMock.mock.calls[0];
    expect(url).toContain("/api/v3/auth/token");
    expect(opts.method).toBe("POST");
    expect(opts.credentials).toBe("omit");                       // no session cookie
    expect(opts.headers.Authorization).toBe("Basic " + btoa("alice:s3cr3t"));
    expect(res.token).toBe("tok123");
    expect(res.expiresInSeconds).toBe(1800);
  });

  it("throws a typed error with the status on non-200", async () => {
    fetchMock.mockResolvedValue({ status: 401, json: async () => ({ error: "invalid credentials" }) });
    await expect(mintToken("alice", "wrong")).rejects.toMatchObject({ status: 401 });
  });
});
