import {
  computeBulkImportPollInterval,
  initialPollClocks,
} from "../../../models/bulkImport/useGetBulkImportTask";

const POLL = 5000;
const BUDGET_MS = 5 * 60 * 1000;

// Fresh clocks object per call so tests don't leak state into one another.
const clocks = () => initialPollClocks();

describe("computeBulkImportPollInterval", () => {
  test("polls on initial load (undefined response from react-query)", () => {
    expect(computeBulkImportPollInterval(undefined, clocks(), 0)).toBe(POLL);
  });

  test("stops when response has no task (deleted/invalid id)", () => {
    expect(computeBulkImportPollInterval({ task: null }, clocks(), 0)).toBe(
      false,
    );
    expect(computeBulkImportPollInterval({}, clocks(), 0)).toBe(false);
  });

  test("stops immediately on terminal-error status, even if iaSummary looks in-flight", () => {
    const response = {
      task: {
        status: "failed",
        iaSummary: { detectionStatus: "sent", pipelineStarted: true },
      },
    };
    expect(computeBulkImportPollInterval(response, clocks(), 0)).toBe(false);
    expect(
      computeBulkImportPollInterval(
        { task: { status: "error", iaSummary: { identificationStatus: "sent" } } },
        clocks(),
        0,
      ),
    ).toBe(false);
  });

  test("polls during early CSV import (processing-background, no encounters)", () => {
    const response = { task: { status: "processing-background", encounters: [] } };
    expect(computeBulkImportPollInterval(response, clocks(), 0)).toBe(POLL);
  });

  // Regression: the reported bug. Import committed ("imported"), encounters
  // persisted, but the IA task hasn't registered a detectionStatus yet. The old
  // !hasEncounters-gated branch dropped polling the instant encounters
  // persisted, stranding the page across the Import->Detection handoff.
  test("polls during the 'imported' pre-IA handoff window (encounters present, no iaSummary)", () => {
    const response = {
      task: {
        status: "imported",
        encounters: [{ id: "e1" }, { id: "e2" }],
        iaSummary: {},
      },
    };
    expect(computeBulkImportPollInterval(response, clocks(), 0)).toBe(POLL);
  });

  test("polls during the 'imported' window even with a missing iaSummary object", () => {
    const response = { task: { status: "imported", encounters: [{ id: "e1" }] } };
    expect(computeBulkImportPollInterval(response, clocks(), 0)).toBe(POLL);
  });

  test("polls while detection is running (detectionStatus=sent)", () => {
    const response = {
      task: {
        status: "processing-detection",
        encounters: [{ id: "e1" }],
        iaSummary: {
          pipelineStarted: true,
          pipelineComplete: false,
          detectionStatus: "sent",
        },
      },
    };
    expect(computeBulkImportPollInterval(response, clocks(), 0)).toBe(POLL);
  });

  // Regression: detection->identification handoff. Detection complete,
  // identification not yet "sent" (status absent / "identification not
  // started"); neither is in the in-flight whitelist, pipeline not complete.
  test("polls across the detection->identification handoff (detection complete, ident not yet started)", () => {
    const detectionDoneNoIdent = {
      task: {
        status: "processing-detection",
        encounters: [{ id: "e1" }],
        iaSummary: {
          pipelineStarted: true,
          pipelineComplete: false,
          detectionStatus: "complete",
          identificationStatus: "identification not started",
        },
      },
    };
    expect(computeBulkImportPollInterval(detectionDoneNoIdent, clocks(), 0)).toBe(
      POLL,
    );

    const detectionDoneAbsentIdent = {
      task: {
        status: "processing-detection",
        encounters: [{ id: "e1" }],
        iaSummary: {
          pipelineStarted: true,
          pipelineComplete: false,
          detectionStatus: "complete",
        },
      },
    };
    expect(
      computeBulkImportPollInterval(detectionDoneAbsentIdent, clocks(), 0),
    ).toBe(POLL);
  });

  test("polls while identification is running (identificationStatus=sent)", () => {
    const response = {
      task: {
        status: "processing-detection",
        encounters: [{ id: "e1" }],
        iaSummary: {
          pipelineStarted: true,
          pipelineComplete: false,
          detectionStatus: "complete",
          identificationStatus: "sent",
        },
      },
    };
    expect(computeBulkImportPollInterval(response, clocks(), 0)).toBe(POLL);
  });

  test("stops when pipeline complete and all encounters have taskInfo", () => {
    const response = {
      task: {
        status: "complete",
        encounters: [{ id: "e1" }],
        iaSummary: {
          pipelineStarted: true,
          pipelineComplete: true,
          identificationStatus: "complete",
          statsAnnotations: { encounterTaskInfo: { e1: [["t1", "x", "y"]] } },
        },
      },
    };
    expect(computeBulkImportPollInterval(response, clocks(), 0)).toBe(false);
  });

  test("polls while pipeline complete but taskInfo lags, within budget; stops past budget", () => {
    const response = {
      task: {
        status: "complete",
        encounters: [{ id: "e1" }],
        iaSummary: {
          pipelineStarted: true,
          pipelineComplete: true,
          identificationStatus: "complete",
          statsAnnotations: { encounterTaskInfo: {} },
        },
      },
    };
    const c = clocks();
    // first observation at t=0 starts the lag clock
    expect(computeBulkImportPollInterval(response, c, 0)).toBe(POLL);
    expect(c.taskInfoLagFirstSeen).toBe(0);
    // still within budget
    expect(computeBulkImportPollInterval(response, c, BUDGET_MS - 1)).toBe(POLL);
    // past budget -> stop
    expect(computeBulkImportPollInterval(response, c, BUDGET_MS + 1)).toBe(false);
  });

  // Codex Major 1: legacy completed task (detection complete, identification
  // "unknown", pipelineComplete false) must NOT poll forever. It is treated as
  // a settled gap state, bounded by the handoff budget.
  test("bounds legacy 'unknown' identification (does not poll forever)", () => {
    const response = {
      task: {
        status: "complete",
        encounters: [{ id: "e1" }],
        iaSummary: {
          pipelineStarted: true,
          pipelineComplete: false,
          detectionStatus: "complete",
          identificationStatus: "unknown",
        },
      },
    };
    const c = clocks();
    expect(computeBulkImportPollInterval(response, c, 0)).toBe(POLL);
    expect(computeBulkImportPollInterval(response, c, BUDGET_MS + 1)).toBe(false);
  });

  // Codex Major 4: detection complete + zero/trivial annotations -> zero match
  // tasks -> "identification not started" is a settled gap, also bounded.
  test("bounds detection-complete + zero annotations (identification not started)", () => {
    const response = {
      task: {
        status: "processing-detection",
        encounters: [{ id: "e1" }],
        iaSummary: {
          pipelineStarted: true,
          pipelineComplete: false,
          detectionStatus: "complete",
          identificationStatus: "identification not started",
        },
      },
    };
    const c = clocks();
    expect(computeBulkImportPollInterval(response, c, 0)).toBe(POLL);
    expect(computeBulkImportPollInterval(response, c, BUDGET_MS + 1)).toBe(false);
  });

  test("fully-skipped import (pipelineComplete via skipped) stops without over-polling", () => {
    const response = {
      task: {
        status: "complete",
        encounters: [{ id: "e1" }],
        iaSummary: {
          pipelineStarted: false,
          pipelineComplete: true,
          detectionStatus: "skipped",
          identificationStatus: "skipped",
        },
      },
    };
    expect(computeBulkImportPollInterval(response, clocks(), 0)).toBe(false);
  });

  // Round-2 Major 1: "processing-pipeline" overlay (re-ID on an already-
  // "complete" task) must NOT poll forever when identification settles
  // incomplete. It is treated as a bounded gap, not unbounded in-flight.
  test("bounds 'processing-pipeline' overlay with settled-incomplete identification", () => {
    const response = {
      task: {
        status: "processing-pipeline",
        encounters: [{ id: "e1" }],
        iaSummary: {
          pipelineStarted: true,
          pipelineComplete: false,
          detectionStatus: "complete",
          identificationStatus: "unknown",
        },
      },
    };
    const c = clocks();
    expect(computeBulkImportPollInterval(response, c, 0)).toBe(POLL);
    expect(computeBulkImportPollInterval(response, c, BUDGET_MS + 1)).toBe(false);
  });

  test("polls unbounded while 'processing-pipeline' re-ID is actively running (ident=sent)", () => {
    const response = {
      task: {
        status: "processing-pipeline",
        encounters: [{ id: "e1" }],
        iaSummary: {
          pipelineStarted: true,
          pipelineComplete: false,
          detectionStatus: "complete",
          identificationStatus: "sent",
        },
      },
    };
    const c = clocks();
    // far past a budget window — still polls because it's genuinely active
    expect(computeBulkImportPollInterval(response, c, 10 * BUDGET_MS)).toBe(POLL);
  });

  // Round-2 Major 2: a legitimately long CSV import keeps polling past the
  // budget as long as importPercent advances (liveness); a wedged one stops.
  test("keeps polling a long CSV import while importPercent advances", () => {
    const c = clocks();
    const at = (pct, t) =>
      computeBulkImportPollInterval(
        { task: { status: "processing-background", encounters: [], importPercent: pct } },
        c,
        t,
      );
    expect(at(0.1, 0)).toBe(POLL);
    // progress at t just under a budget window resets the clock...
    expect(at(0.4, BUDGET_MS - 1)).toBe(POLL);
    // ...so well past the original budget we still poll because pct advanced
    expect(at(0.7, 2 * BUDGET_MS - 2)).toBe(POLL);
  });

  test("stops a wedged CSV import after the budget when importPercent does not advance", () => {
    const c = clocks();
    const stuck = {
      task: { status: "processing-background", encounters: [], importPercent: 0.3 },
    };
    expect(computeBulkImportPollInterval(stuck, c, 0)).toBe(POLL);
    // same pct, past budget -> stop
    expect(computeBulkImportPollInterval(stuck, c, BUDGET_MS + 1)).toBe(false);
  });

  // Round-2 Major 3: a timed-out taskInfo lag must not carry into a later
  // re-run and immediately stop the next aggregation poll. An in-flight
  // observation clears the lag clock.
  test("in-flight observation clears a timed-out taskInfo-lag clock for the next run", () => {
    const c = clocks();
    const lagging = {
      task: {
        status: "complete",
        encounters: [{ id: "e1" }],
        iaSummary: {
          pipelineComplete: true,
          identificationStatus: "complete",
          statsAnnotations: { encounterTaskInfo: {} },
        },
      },
    };
    // lag observed at t=0, then times out past budget -> stop
    expect(computeBulkImportPollInterval(lagging, c, 0)).toBe(POLL);
    expect(computeBulkImportPollInterval(lagging, c, BUDGET_MS + 1)).toBe(false);

    // operator re-runs identification -> ident goes "sent" (in-flight)
    const rerun = {
      task: {
        status: "processing-pipeline",
        encounters: [{ id: "e1" }],
        iaSummary: { identificationStatus: "sent" },
      },
    };
    expect(computeBulkImportPollInterval(rerun, c, BUDGET_MS + 2)).toBe(POLL);
    expect(c.taskInfoLagFirstSeen).toBe(null);

    // ident completes again with taskInfo still lagging -> fresh budget, polls
    expect(computeBulkImportPollInterval(lagging, c, BUDGET_MS + 3)).toBe(POLL);
    expect(c.taskInfoLagFirstSeen).toBe(BUDGET_MS + 3);
  });

  test("importPercent that does not strictly advance (equal/undefined) does not reset budget", () => {
    // equal percent across polls -> no reset -> stops after budget
    const cEq = clocks();
    const eq = { task: { status: "processing-background", encounters: [], importPercent: 0.5 } };
    expect(computeBulkImportPollInterval(eq, cEq, 0)).toBe(POLL);
    expect(computeBulkImportPollInterval(eq, cEq, BUDGET_MS + 1)).toBe(false);

    // undefined percent -> no liveness signal -> bounded by first-observed
    const cU = clocks();
    const noPct = { task: { status: "processing-background", encounters: [] } };
    expect(computeBulkImportPollInterval(noPct, cU, 0)).toBe(POLL);
    expect(computeBulkImportPollInterval(noPct, cU, BUDGET_MS + 1)).toBe(false);
  });

  test("'imported' with constant importPercent=1.0 resets once then stops after budget", () => {
    const c = clocks();
    const imported = {
      task: { status: "imported", encounters: [{ id: "e1" }], importPercent: 1.0 },
    };
    expect(computeBulkImportPollInterval(imported, c, 0)).toBe(POLL);
    expect(c.lastImportPercent).toBe(1.0);
    // constant 1.0 is not a strict advance -> no further reset -> stops
    expect(computeBulkImportPollInterval(imported, c, BUDGET_MS + 1)).toBe(false);
  });

  test("handoff poll stops exactly at the budget boundary (strict <)", () => {
    const c = clocks();
    const response = { task: { status: "imported", encounters: [{ id: "e1" }] } };
    expect(computeBulkImportPollInterval(response, c, 0)).toBe(POLL);
    expect(computeBulkImportPollInterval(response, c, BUDGET_MS)).toBe(false);
  });

  // An active phase must reset the handoff clock so a LATER gap gets a fresh
  // budget rather than inheriting elapsed time from an earlier phase.
  test("in-flight observation resets the handoff clock for a later gap", () => {
    const c = clocks();
    // long CSV import: handoff clock starts at t=0
    const importing = { task: { status: "processing-background", encounters: [] } };
    expect(computeBulkImportPollInterval(importing, c, 0)).toBe(POLL);
    expect(c.handoffFirstSeen).toBe(0);

    // detection goes in-flight near the budget edge -> resets clock
    const detecting = {
      task: {
        status: "processing-detection",
        encounters: [{ id: "e1" }],
        iaSummary: { detectionStatus: "sent" },
      },
    };
    expect(computeBulkImportPollInterval(detecting, c, BUDGET_MS - 1)).toBe(POLL);
    expect(c.handoffFirstSeen).toBe(null);

    // a later handoff gap re-arms the clock fresh and keeps polling
    const handoff = {
      task: {
        status: "processing-detection",
        encounters: [{ id: "e1" }],
        iaSummary: { detectionStatus: "complete" },
      },
    };
    expect(computeBulkImportPollInterval(handoff, c, BUDGET_MS)).toBe(POLL);
    expect(c.handoffFirstSeen).toBe(BUDGET_MS);
  });

  // ---- imports with user-supplied boxes: extraction progress keeps the poll alive ----

  const boxedTask = (ended, total, extra = {}) => ({
    task: {
      status: "processing-detection",
      encounters: [{ id: "e1" }],
      importPercent: 1.0,
      iaSummary: {
        annotationsImported: true,
        detectionStatus: "skipped",
        identificationStatus: "skipped",
        pipelineStarted: true,
        pipelineComplete: ended >= total,
        statsAnnotations: {
          numLatestTasks: total,
          numLatestTask_completed: ended,
          ...extra,
        },
      },
    },
  });

  test("boxed import keeps polling past the budget while more boxes end", () => {
    const c = clocks();
    expect(computeBulkImportPollInterval(boxedTask(0, 30), c, 0)).toBe(POLL);
    // each poll lands just inside a fresh window because the count advanced
    expect(computeBulkImportPollInterval(boxedTask(10, 30), c, BUDGET_MS - 1)).toBe(POLL);
    expect(computeBulkImportPollInterval(boxedTask(20, 30), c, 2 * BUDGET_MS - 2)).toBe(POLL);
    expect(c.lastBoxesEnded).toBe(20);
    // all boxes ended: the pipeline is complete and polling stops
    expect(computeBulkImportPollInterval(boxedTask(30, 30), c, 2 * BUDGET_MS)).toBe(false);
  });

  test("boxed import whose boxes stop ending is bounded by one budget window", () => {
    const c = clocks();
    expect(computeBulkImportPollInterval(boxedTask(5, 30), c, 0)).toBe(POLL);
    expect(computeBulkImportPollInterval(boxedTask(5, 30), c, BUDGET_MS - 1)).toBe(POLL);
    expect(computeBulkImportPollInterval(boxedTask(5, 30), c, BUDGET_MS + 1)).toBe(false);
  });

  test("errored and dropped boxes count as progress for a boxed import", () => {
    const c = clocks();
    expect(computeBulkImportPollInterval(boxedTask(1, 30), c, 0)).toBe(POLL);
    const withError = boxedTask(1, 30, { numLatestTask_error: 1 });
    expect(computeBulkImportPollInterval(withError, c, BUDGET_MS - 1)).toBe(POLL);
    const withDropped = boxedTask(1, 30, {
      numLatestTask_error: 1,
      "numLatestTask_dropped-stale": 1,
    });
    expect(computeBulkImportPollInterval(withDropped, c, 2 * BUDGET_MS - 2)).toBe(POLL);
    expect(c.lastBoxesEnded).toBe(3);
  });

  test("box counts are ignored for an import without supplied boxes", () => {
    const c = clocks();
    const plain = (ended) => {
      const r = boxedTask(ended, 30);
      delete r.task.iaSummary.annotationsImported;
      return r;
    };
    expect(computeBulkImportPollInterval(plain(0), c, 0)).toBe(POLL);
    // the count advances, but this is not a boxed import: no budget reset
    expect(computeBulkImportPollInterval(plain(10), c, BUDGET_MS + 1)).toBe(false);
    expect(c.lastBoxesEnded).toBe(null);
  });

  test("boxed signal first seen after an expired hand-off gets one fresh window, then only real progress re-arms", () => {
    const c = clocks();
    // same task, before the summary carries the boxed flag: bounded and expired
    const before = {
      task: { status: "processing-detection", encounters: [{ id: "e1" }], importPercent: 1.0 },
    };
    expect(computeBulkImportPollInterval(before, c, 0)).toBe(POLL);
    expect(computeBulkImportPollInterval(before, c, BUDGET_MS + 1)).toBe(false);
    // the boxed summary appears with some boxes already ended: one fresh window
    const t0 = BUDGET_MS + 2;
    expect(computeBulkImportPollInterval(boxedTask(4, 30), c, t0)).toBe(POLL);
    expect(c.lastBoxesEnded).toBe(4);
    expect(c.handoffFirstSeen).toBe(t0);
    // unchanged count: no reset, stops after that window
    expect(computeBulkImportPollInterval(boxedTask(4, 30), c, t0 + BUDGET_MS + 1)).toBe(false);
    // a lower count, and a recovery to the previous maximum, never reset the budget
    expect(computeBulkImportPollInterval(boxedTask(2, 30), c, t0 + BUDGET_MS + 2)).toBe(false);
    expect(computeBulkImportPollInterval(boxedTask(4, 30), c, t0 + BUDGET_MS + 3)).toBe(false);
    expect(c.lastBoxesEnded).toBe(4);
    // a later fetch (e.g. on window focus) that shows real progress re-arms polling
    const t1 = t0 + 2 * BUDGET_MS;
    expect(computeBulkImportPollInterval(boxedTask(5, 30), c, t1)).toBe(POLL);
    expect(c.handoffFirstSeen).toBe(t1);
  });
});
