import { useEffect } from "react";
import { useIntl } from "react-intl";
import { toast } from "react-toastify";
import {
  SW_UPDATE_AVAILABLE_EVENT,
  isUpdateAvailable,
} from "../utils/serviceWorkerUpdates";

/**
 * Tells the user a new Wildbook version is waiting, without disturbing what they are doing.
 *
 * Wildbook no longer activates a new service worker under an open page, so the update only
 * takes effect once every Wildbook tab has been closed (issue #1759). This notice is the only
 * thing that makes that visible; it renders nothing itself.
 */
export default function ServiceWorkerUpdateNotice() {
  const intl = useIntl();

  useEffect(() => {
    const announce = () => {
      toast.info(
        intl.formatMessage({
          id: "APP_UPDATE_AVAILABLE",
          defaultMessage:
            "A new version of Wildbook is available. Close all Wildbook tabs when convenient to start using it.",
        }),
        // the update stays pending until every tab closes, so this must not time out --
        // and toastId keeps a re-announcement from stacking duplicates
        { toastId: "wildbook-sw-update", autoClose: false },
      );
    };

    // the update can be found before this mounts, hence the latch
    if (isUpdateAvailable()) announce();
    window.addEventListener(SW_UPDATE_AVAILABLE_EVENT, announce);
    return () => window.removeEventListener(SW_UPDATE_AVAILABLE_EVENT, announce);
  }, [intl]);

  return null;
}
