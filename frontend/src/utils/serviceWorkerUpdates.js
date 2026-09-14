/**
 * Service worker update policy (issue #1759).
 *
 * Wildbook used to take every new service worker live the instant it installed
 * (`self.skipWaiting()` in service-worker.js, plus a `SKIP_WAITING` message from the
 * `onUpdate` callback) and reload the page on every `controllerchange`. That produced two
 * forced refreshes users could not avoid:
 *
 *  - On a first visit the document loads with no controller. The worker installs, activates
 *    and `clients.claim()`s the already-loaded page, which fires `controllerchange` -- so the
 *    page reloaded a second or two in, throwing away a half-typed sign-in.
 *  - After a deploy the new worker took over every open tab at once, reloading each of them
 *    mid-work and discarding match-page selections.
 *
 * The policy now: never activate a waiting worker ourselves, and never reload a page just
 * because it became controlled for the first time. A new build goes live once every Wildbook
 * tab has been closed, and until then we only tell the user an update is waiting.
 *
 * The trade-off is deliberate: a tab left open indefinitely keeps running the release it
 * loaded with. That is preferable to destroying work in progress, and it is also the only
 * coherent option -- a page running old JS under a newly activated worker can fail to
 * lazy-import bundles that the new worker's precache no longer holds.
 */

export const SW_UPDATE_AVAILABLE_EVENT = "wildbook:sw-update-available";

let updateAvailable = false;

/** True once a new version has been found and is waiting to take over. */
export function isUpdateAvailable() {
  return updateAvailable;
}

/**
 * Called when a new service worker has finished installing and is waiting.
 *
 * Deliberately does NOT post `SKIP_WAITING` to `registration.waiting`: activating the new
 * worker here swaps the controller of every open tab and reloads all of them.
 */
export function handleUpdateFound() {
  updateAvailable = true;
  // latched above, so a notice that mounts after this still sees it
  if (typeof window !== "undefined" && window.dispatchEvent) {
    window.dispatchEvent(new CustomEvent(SW_UPDATE_AVAILABLE_EVENT));
  }
}

/**
 * Reload only when the controller of an already-controlled page is replaced.
 *
 * Nothing in Wildbook activates a worker mid-session any more, so in practice this fires only
 * if that ever changes. It is kept because that is the one case where reloading is the
 * correct response: the loaded document would otherwise be running against a worker whose
 * precache no longer matches it. A page that simply became controlled for the first time is
 * already running the newest assets and must be left alone.
 *
 * @param {ServiceWorkerContainer} container navigator.serviceWorker, if the browser has it
 * @param {Function} reload how to reload the page
 * @returns {Function} unsubscribe
 */
export function watchForControllerChange(container, reload) {
  if (!container || !container.addEventListener) return () => {};
  const controlledAtLoad = Boolean(container.controller);
  let reloaded = false;
  const onControllerChange = () => {
    if (!controlledAtLoad || reloaded) return;
    reloaded = true;
    reload();
  };

  container.addEventListener("controllerchange", onControllerChange);
  return () => container.removeEventListener("controllerchange",
    onControllerChange);
}
