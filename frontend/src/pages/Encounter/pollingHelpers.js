// Shared detection-status predicates used by Encounter.jsx polling loop and
// ImageCard.jsx "Detection in progress" indicator. Keeping them here avoids
// the two files drifting out of sync.

const POLL_INTERVAL_MS = 3000;
const MAX_POLL_CYCLES = 100; // 100 cycles * 3s = ~5 minutes

const isAnnotationTrivial = (a) => a?.isTrivial === true || a?.trivial === true;

export const isTerminalDetectionStatus = (status) =>
  !status ||
  status === "complete" ||
  status === "error" ||
  status === "pending";

// True when an asset is from a bulk import and detection has been queued
// to WBIA but the callback hasn't returned yet. In this state the API
// returns detectionStatus=null and only a trivial placeholder annotation.
// Scoped to encounters that have an importTaskId so we don't poll forever
// on legacy/manual encounters that intentionally never run detection.
export const isAwaitingBulkImportDetection = (asset, encounterData) => {
  if (!encounterData?.importTaskId) return false;
  if (asset?.detectionStatus !== null && asset?.detectionStatus !== undefined)
    return false;
  const anns = Array.isArray(asset?.annotations) ? asset.annotations : [];
  return anns.length > 0 && anns.every(isAnnotationTrivial);
};

export const isAssetActivelyAwaitingDetection = (asset, encounterData) =>
  !isTerminalDetectionStatus(asset?.detectionStatus) ||
  isAwaitingBulkImportDetection(asset, encounterData);

export const shouldContinuePollingEncounter = (encounterData) => {
  const mediaAssets = Array.isArray(encounterData?.mediaAssets)
    ? encounterData.mediaAssets
    : [];
  if (mediaAssets.length === 0) return false;
  return mediaAssets.some((asset) =>
    isAssetActivelyAwaitingDetection(asset, encounterData),
  );
};

export { POLL_INTERVAL_MS, MAX_POLL_CYCLES };
