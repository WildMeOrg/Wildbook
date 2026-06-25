import React from "react";
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
    render(<ApiAccessPage />);
    fireEvent.click(screen.getByRole("button", { name: /generate/i }));
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "s3cr3t" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm/i }));
    await waitFor(() => expect(screen.getByText(/tok-xyz/)).toBeInTheDocument());
    expect(mockMint).toHaveBeenCalledWith("alice", "s3cr3t");
  });

  it("shows an inline error on 401", async () => {
    mockMint.mockRejectedValue(Object.assign(new Error("invalid credentials"), { status: 401 }));
    render(<ApiAccessPage />);
    fireEvent.click(screen.getByRole("button", { name: /generate/i }));
    fireEvent.change(screen.getByLabelText(/password/i), { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: /confirm/i }));
    await waitFor(() => expect(screen.getByText(/incorrect password/i)).toBeInTheDocument());
    expect(screen.queryByText(/tok-/)).not.toBeInTheDocument();
  });
});
