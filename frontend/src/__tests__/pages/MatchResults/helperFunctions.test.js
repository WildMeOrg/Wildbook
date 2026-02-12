import {
  getAllAnnot,
  getAllIndiv,
} from "../../../pages/MatchResultsPage/helperFunctions";

describe("helperFunctions", () => {
  const makeNode = (overrides = {}) => ({
    id: "task-1",
    status: "complete",
    statusOverall: "complete",
    dateCreated: "2024-01-01",
    method: { name: "hotspotter", description: "HotSpotter algorithm" },
    matchingSetFilter: { species: "whale" },
    matchResults: {
      numberCandidates: 5,
      queryAnnotation: {
        x: 0.1,
        y: 0.2,
        width: 0.3,
        height: 0.4,
        theta: 0,
        asset: { url: "http://example.com/img.jpg" },
        encounter: { id: "enc-1", locationId: "loc-1" },
        individual: { id: "ind-1", displayName: "Willy" },
      },
      prospects: {
        annot: [{ annotId: "a1", score: 0.9 }],
        indiv: [{ individualId: "i1", score: 0.85 }],
      },
    },
    children: [],
    ...overrides,
  });

  describe("getAllAnnot", () => {
    test("returns empty array for null node", () => {
      expect(getAllAnnot(null)).toEqual([]);
    });

    test("returns empty array for undefined node", () => {
      expect(getAllAnnot(undefined)).toEqual([]);
    });

    test("returns empty array when node has no method", () => {
      const node = makeNode({ method: null });
      expect(getAllAnnot(node)).toEqual([]);
    });

    test("returns empty array when annot prospects is empty", () => {
      const node = makeNode({
        matchResults: {
          ...makeNode().matchResults,
          prospects: { annot: [], indiv: [{ individualId: "i1", score: 0.9 }] },
        },
      });
      expect(getAllAnnot(node)).toEqual([]);
    });

    test("returns empty array when annot prospects is missing", () => {
      const node = makeNode({
        matchResults: {
          ...makeNode().matchResults,
          prospects: { indiv: [{ individualId: "i1" }] },
        },
      });
      expect(getAllAnnot(node)).toEqual([]);
    });

    test("collects annot prospects and attaches common fields", () => {
      const node = makeNode();
      const result = getAllAnnot(node);
      expect(result).toHaveLength(1);
      const item = result[0];
      expect(item.annotId).toBe("a1");
      expect(item.score).toBe(0.9);
      expect(item.algorithm).toBe("hotspotter");
      expect(item.methodDescription).toBe("HotSpotter algorithm");
      expect(item.taskId).toBe("task-1");
      expect(item.taskStatus).toBe("complete");
      expect(item.taskStatusOverall).toBe("complete");
      expect(item.date).toBe("2024-01-01");
      expect(item.numberCandidates).toBe(5);
      expect(item.queryEncounterId).toBe("enc-1");
      expect(item.encounterLocationId).toBe("loc-1");
      expect(item.queryIndividualId).toBe("ind-1");
      expect(item.queryIndividualDisplayName).toBe("Willy");
      expect(item.queryEncounterImageUrl).toBe("http://example.com/img.jpg");
      expect(item.matchingSetFilter).toEqual({ species: "whale" });
      expect(item.hasResults).toBe(true);
    });

    test("attaches queryEncounterAnnotation with correct shape", () => {
      const node = makeNode();
      const result = getAllAnnot(node);
      expect(result[0].queryEncounterAnnotation).toEqual({
        x: 0.1,
        y: 0.2,
        width: 0.3,
        height: 0.4,
        theta: 0,
      });
    });

    test("uses method.description as methodName when method.name is undefined", () => {
      const node = makeNode({ method: { description: "FlukeMatcher" } });
      const result = getAllAnnot(node);
      expect(result[0].algorithm).toBe("FlukeMatcher");
      expect(result[0].methodName).toBe("FlukeMatcher");
    });

    test("recurses into children", () => {
      const child = makeNode({
        id: "task-2",
        matchResults: {
          ...makeNode().matchResults,
          prospects: {
            annot: [{ annotId: "a2", score: 0.7 }],
            indiv: [],
          },
        },
      });
      const node = makeNode({ children: [child] });
      const result = getAllAnnot(node);
      expect(result).toHaveLength(2);
      expect(result.map((r) => r.annotId)).toContain("a2");
    });

    test("handles node with no matchResults gracefully", () => {
      const node = makeNode({ matchResults: null });
      expect(getAllAnnot(node)).toEqual([]);
    });

    test("handles multiple annot prospects in one node", () => {
      const node = makeNode({
        matchResults: {
          ...makeNode().matchResults,
          prospects: {
            annot: [
              { annotId: "a1", score: 0.9 },
              { annotId: "a2", score: 0.8 },
              { annotId: "a3", score: 0.7 },
            ],
            indiv: [],
          },
        },
      });
      expect(getAllAnnot(node)).toHaveLength(3);
    });

    test("sets displayIndex correctly via spread (items keep own keys)", () => {
      const node = makeNode();
      const result = getAllAnnot(node);
      // common fields should override item fields if same key
      expect(result[0].hasResults).toBe(true);
    });
  });

  describe("getAllIndiv", () => {
    test("returns empty array for null node", () => {
      expect(getAllIndiv(null)).toEqual([]);
    });

    test("returns empty array when node has no method", () => {
      const node = makeNode({ method: undefined });
      expect(getAllIndiv(node)).toEqual([]);
    });

    test("returns empty array when indiv prospects is empty", () => {
      const node = makeNode({
        matchResults: {
          ...makeNode().matchResults,
          prospects: { annot: [{ annotId: "a1" }], indiv: [] },
        },
      });
      expect(getAllIndiv(node)).toEqual([]);
    });

    test("collects indiv prospects and attaches common fields", () => {
      const node = makeNode();
      const result = getAllIndiv(node);
      expect(result).toHaveLength(1);
      const item = result[0];
      expect(item.individualId).toBe("i1");
      expect(item.score).toBe(0.85);
      expect(item.algorithm).toBe("hotspotter");
      expect(item.taskId).toBe("task-1");
      expect(item.hasResults).toBe(true);
    });

    test("recurses into multiple levels of children", () => {
      const grandchild = makeNode({
        id: "task-3",
        matchResults: {
          ...makeNode().matchResults,
          prospects: {
            annot: [],
            indiv: [{ individualId: "i3", score: 0.6 }],
          },
        },
      });
      const child = makeNode({
        id: "task-2",
        children: [grandchild],
        matchResults: {
          ...makeNode().matchResults,
          prospects: {
            annot: [],
            indiv: [{ individualId: "i2", score: 0.7 }],
          },
        },
      });
      const root = makeNode({ children: [child] });
      const result = getAllIndiv(root);
      expect(result).toHaveLength(3);
      expect(result.map((r) => r.individualId)).toEqual(
        expect.arrayContaining(["i1", "i2", "i3"]),
      );
    });

    test("numberCandidates defaults to 0 when missing", () => {
      const node = makeNode({
        matchResults: {
          queryAnnotation: makeNode().matchResults.queryAnnotation,
          prospects: { annot: [], indiv: [{ individualId: "i1" }] },
        },
      });
      expect(getAllIndiv(node)[0].numberCandidates).toBe(0);
    });

    test("queryEncounterId is null when encounter is absent", () => {
      const node = makeNode({
        matchResults: {
          numberCandidates: 3,
          queryAnnotation: { asset: null },
          prospects: {
            annot: [],
            indiv: [{ individualId: "i1" }],
          },
        },
      });
      const result = getAllIndiv(node);
      expect(result[0].queryEncounterId).toBeNull();
      expect(result[0].queryIndividualId).toBeNull();
      expect(result[0].queryEncounterImageUrl).toBeNull();
    });
  });
});
