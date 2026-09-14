/**
 * Issue #1759: /react/login and /react/match-results refreshed themselves a moment after you
 * navigated to them, losing half-typed credentials and match selections.
 *
 * Cause: the service worker called skipWaiting() + clientsClaim(), and index.js reloaded the
 * page on every `controllerchange`. A page that loads uncontrolled becomes controlled as soon
 * as the freshly installed worker claims it, so the very first visit in any browser reloaded
 * itself; and a worker that takes over mid-session reloaded every open tab.
 */

const loadModule = () => {
  let mod;

  jest.isolateModules(() => {
    mod = require("../../utils/serviceWorkerUpdates");
  });
  return mod;
};

// Minimal stand-in for navigator.serviceWorker: only `controller` and the event plumbing
// matter here.
const makeContainer = (controller) => {
  const listeners = {};

  return {
    controller,
    addEventListener: (type, fn) => {
      listeners[type] = listeners[type] || [];
      listeners[type].push(fn);
    },
    removeEventListener: (type, fn) => {
      listeners[type] = (listeners[type] || []).filter((l) => l !== fn);
    },
    // pretend a worker took over
    emitControllerChange: () =>
      (listeners.controllerchange || []).forEach((fn) => fn()),
  };
};

describe("watchForControllerChange", () => {
  // The regression. On a first visit the page is uncontrolled, clients.claim() controls it,
  // and reloading at that point destroys whatever the user has already typed for nothing --
  // a first load is already running the newest assets.
  it("does not reload a page that was not controlled when it loaded", () => {
    const { watchForControllerChange } = loadModule();
    const container = makeContainer(null);
    const reload = jest.fn();

    watchForControllerChange(container, reload);
    container.emitControllerChange();
    expect(reload).not.toHaveBeenCalled();
  });

  // The other half: if a page WAS controlled and its controller is swapped underneath it, the
  // document is now running old JS against a new worker whose activation may have pruned the
  // bundles it still lazy-imports. Reloading is the safe response there.
  it("reloads a page whose controller is replaced mid-session", () => {
    const { watchForControllerChange } = loadModule();
    const container = makeContainer({});
    const reload = jest.fn();

    watchForControllerChange(container, reload);
    container.emitControllerChange();
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("reloads at most once", () => {
    const { watchForControllerChange } = loadModule();
    const container = makeContainer({});
    const reload = jest.fn();

    watchForControllerChange(container, reload);
    container.emitControllerChange();
    container.emitControllerChange();
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it("does nothing when the browser has no service worker support", () => {
    const { watchForControllerChange } = loadModule();

    expect(() => watchForControllerChange(undefined, jest.fn())).not.toThrow();
  });
});

describe("handleUpdateFound", () => {
  // Posting SKIP_WAITING activates the new worker immediately, which changes the controller of
  // every open tab and reloads them all mid-work. The waiting worker must be left alone; it
  // takes over once every Wildbook tab is closed.
  it("never activates the waiting worker", () => {
    const { handleUpdateFound } = loadModule();
    const waiting = { postMessage: jest.fn() };

    handleUpdateFound({ waiting });
    expect(waiting.postMessage).not.toHaveBeenCalled();
  });

  it("announces the available update to the app", () => {
    const { handleUpdateFound, SW_UPDATE_AVAILABLE_EVENT } = loadModule();
    const onAnnounce = jest.fn();

    window.addEventListener(SW_UPDATE_AVAILABLE_EVENT, onAnnounce);
    handleUpdateFound({ waiting: { postMessage: jest.fn() } });
    window.removeEventListener(SW_UPDATE_AVAILABLE_EVENT, onAnnounce);
    expect(onAnnounce).toHaveBeenCalledTimes(1);
  });

  // The notice component can mount after the update was found, so the announcement latches.
  it("latches so a late-mounting listener can still see the update", () => {
    const { handleUpdateFound, isUpdateAvailable } = loadModule();

    expect(isUpdateAvailable()).toBe(false);
    handleUpdateFound({ waiting: { postMessage: jest.fn() } });
    expect(isUpdateAvailable()).toBe(true);
  });

  it("survives a registration with no waiting worker", () => {
    const { handleUpdateFound } = loadModule();

    expect(() => handleUpdateFound({})).not.toThrow();
    expect(() => handleUpdateFound(null)).not.toThrow();
  });
});
