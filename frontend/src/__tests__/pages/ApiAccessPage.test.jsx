import React from "react";
import { IntlProvider } from "react-intl";
import messages from "../../locale/en.json";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import ApiAccessPage from "../../pages/ApiAccess/ApiAccessPage";

jest.mock("../../models/auth/users/useGetMe", () => () => ({
  data: { username: "alice" },
}));
const mockMint = jest.fn();
jest.mock("../../models/auth/useMintToken", () => () => ({ mint: mockMint, loading: false }));

describe("ApiAccessPage", () => {
  beforeEach(() => { mockMint.mockReset(); });

  it("mints and shows the token on success", async () => {
    mockMint.mockResolvedValue({ token: "tok-xyz", expiresInSeconds: 1800 });
    render(<IntlProvider locale="en" messages={messages}><ApiAccessPage /></IntlProvider>);
    fireEvent.click(screen.getByRole("button", { name: /generate/i }));
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "s3cr3t" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm/i }));
    await waitFor(() => expect(screen.getByText(/tok-xyz/)).toBeInTheDocument());
    expect(mockMint).toHaveBeenCalledWith("alice", "s3cr3t");
  });

  it("shows an inline error on 401", async () => {
    mockMint.mockRejectedValue(Object.assign(new Error("invalid credentials"), { status: 401 }));
    render(<IntlProvider locale="en" messages={messages}><ApiAccessPage /></IntlProvider>);
    fireEvent.click(screen.getByRole("button", { name: /generate/i }));
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm/i }));
    await waitFor(() => expect(screen.getByText(/incorrect password/i)).toBeInTheDocument());
    expect(screen.queryByText(/tok-/)).not.toBeInTheDocument();
  });
  it("explicitly requests import scope and clears the token when purpose changes", async () => {
    mockMint.mockResolvedValue({ token: "import-token", expiresInSeconds: 1800 });
    render(<IntlProvider locale="en" messages={messages}><ApiAccessPage /></IntlProvider>);
    fireEvent.change(screen.getByLabelText("Token purpose"), { target: { value: "import" } });
    fireEvent.click(screen.getByRole("button", { name: /generate/i }));
    expect(screen.getByLabelText("Token purpose")).toBeDisabled();
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm/i }));
    await waitFor(() => expect(screen.getByText("import-token")).toBeInTheDocument());
    expect(mockMint).toHaveBeenCalledWith("alice", "secret", "submissions:write");
    fireEvent.change(screen.getByLabelText("Token purpose"), { target: { value: "read" } });
    expect(screen.queryByText("import-token")).not.toBeInTheDocument();
  });

  it("explains missing enrollment on 403", async () => {
    mockMint.mockRejectedValue(Object.assign(new Error("denied"), { status: 403 }));
    render(<IntlProvider locale="en" messages={messages}><ApiAccessPage /></IntlProvider>);
    fireEvent.change(screen.getByLabelText("Token purpose"), { target: { value: "import" } });
    fireEvent.click(screen.getByRole("button", { name: /generate/i }));
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "secret" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm/i }));
    await waitFor(() => expect(screen.getByText(/Ask a site administrator/)).toBeInTheDocument());
  });
});
