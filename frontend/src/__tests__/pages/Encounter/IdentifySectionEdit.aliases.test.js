import React from "react";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { makeAutoObservable } from "mobx";
import { IdentifySectionEdit } from "../../../pages/Encounter/IdentifySectionEdit";

const individual = {
  id: "uuid-213",
  displayName: "00000213",
  names: ["00000213", "00000001", "Old-ID"],
};

function renderIdentifySection(hits = [individual]) {
  const search = jest.fn().mockResolvedValue({ data: { hits } });
  const store = makeAutoObservable(
    {
      identificationRemarksOptions: [],
      individualOptions: [],
      values: {},
      encounterData: {},
      errors: {
        hasSectionError: () => false,
        getSectionErrors: () => [],
      },
      getFieldValue(section, key) {
        return this.values[key] || "";
      },
      setFieldValue(section, key, value) {
        this.values[key] = value;
      },
      setIndividualOptions(options) {
        this.individualOptions = options;
      },
      searchIndividualsByNameAndId: search,
      searchSightingsById: jest.fn().mockResolvedValue({ data: { hits: [] } }),
    },
    { searchIndividualsByNameAndId: false, searchSightingsById: false },
    { autoBind: true },
  );
  const { container } = render(<IdentifySectionEdit store={store} />);
  const input = within(
    container.querySelector("#group-INDIVIDUAL_ID"),
  ).getByRole("combobox");
  return { input, search, store, user: userEvent.setup() };
}

describe("IdentifySectionEdit with the real individual dropdown", () => {
  test.each(["00000001", " old-id "])(
    "selects the existing individual without offering Create for alias %p",
    async (alias) => {
      const { input, store, user } = renderIdentifySection();
      await user.type(input, alias);

      const option = await screen.findByRole("option", { name: "00000213" });
      expect(
        screen.queryByRole("option", { name: /Create/ }),
      ).not.toBeInTheDocument();
      await user.click(option);
      expect(store.values.individualId).toBe("uuid-213");
    },
  );

  test("does not offer Create after clearing and searching for an existing alias again", async () => {
    const { input, search, store, user } = renderIdentifySection();
    await user.type(input, "00000213");
    await user.click(await screen.findByRole("option", { name: "00000213" }));
    await user.click(input);
    await user.keyboard("{Backspace}");
    await waitFor(() => expect(store.values.individualId).toBe(""));

    await user.type(input, "00000001");
    await waitFor(() => expect(search).toHaveBeenLastCalledWith("00000001"));
    await screen.findByRole("option", { name: "00000213" });
    await waitFor(() =>
      expect(
        document.querySelector(".react-select__loading-indicator"),
      ).not.toBeInTheDocument(),
    );
    expect(
      screen.queryByRole("option", { name: /Create/ }),
    ).not.toBeInTheDocument();
  });

  test.each([
    ["Old", [individual]],
    ["New-ID", []],
  ])("still creates a new value for %p", async (name, hits) => {
    const { input, store, user } = renderIdentifySection(hits);
    await user.type(input, name);
    await user.click(
      await screen.findByRole("option", { name: `Create "${name}"` }),
    );
    expect(store.values.individualId).toBe(name);
  });
});
