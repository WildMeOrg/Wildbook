const collectProspects = (node, type, result = []) => {
  if (!node) return result;
  const hasMethod = !!node.method;
  const methodName = node.method?.name ?? node.method?.description;
  const methodDescription = node.method?.description ?? null;
  const prospects = node.matchResults?.prospects?.[type];
  const hasResults = Array.isArray(prospects) && prospects.length > 0;

  if (hasResults && hasMethod) {
    const common = {
      algorithm: methodName,
      date: node.dateCreated,
      numberCandidates: node.matchResults?.numberCandidates || 0,
      queryEncounterId:
        node.matchResults?.queryAnnotation?.encounter?.id || null,
      encounterLocationId:
        node.matchResults?.queryAnnotation?.encounter?.locationId || null,
      matchingSetFilter: node.matchingSetFilter,
      queryIndividualId:
        node.matchResults?.queryAnnotation?.individual?.id || null,
      queryIndividualDisplayName:
        node.matchResults?.queryAnnotation?.individual?.displayName || null,
      queryEncounterImageAsset:
        node.matchResults?.queryAnnotation?.asset || null,
      queryEncounterImageUrl:
        node.matchResults?.queryAnnotation?.asset?.url || null,
      queryEncounterAnnotation: {
        x: node.matchResults?.queryAnnotation?.x,
        y: node.matchResults?.queryAnnotation?.y,
        width: node.matchResults?.queryAnnotation?.width,
        height: node.matchResults?.queryAnnotation?.height,
        theta: node.matchResults?.queryAnnotation?.theta,
      },
      methodName,
      methodDescription,
      method: node.method || null,
      taskId: node.id ?? null,
      taskStatus: node.status ?? null,
      taskStatusOverall: node.statusOverall ?? null,
      hasResults: true,
    };

    const safeProspects = prospects.filter((p) => p && typeof p === "object");
    result.push(
      ...safeProspects.map((item) => ({
        ...item,
        ...common,
      })),
    );
  }

  if (Array.isArray(node.children)) {
    node.children.forEach((child) => collectProspects(child, type, result));
  }

  return result;
};

export const getAllIndiv = (node, result = []) =>
  collectProspects(node, "indiv", result);
export const getAllAnnot = (node, result = []) =>
  collectProspects(node, "annot", result);

const isLeafNode = (node) => {
  if (!node || typeof node !== "object") return false;
  return !Array.isArray(node.children) || node.children.length === 0;
};

const collectLeafNodes = (node, result = []) => {
  if (!node || typeof node !== "object") return result;

  if (isLeafNode(node)) {
    result.push(node);
    return result;
  }

  if (Array.isArray(node.children)) {
    node.children.forEach((child) => collectLeafNodes(child, result));
  }

  return result;
};

const hasOwnStatusOverall = (node) =>
  !!node &&
  typeof node === "object" &&
  Object.prototype.hasOwnProperty.call(node, "statusOverall");

const isTerminalStatus = (status) =>
  status === "completed" || status === "error";

export const isMatchTaskStillRunning = (root) => {
  if (!root || typeof root !== "object") return false;

  const leafNodes = collectLeafNodes(root);
  const nodesToCheck = [root, ...leafNodes].filter(hasOwnStatusOverall);

  if (nodesToCheck.length === 0) return false;

  return nodesToCheck.some((node) => !isTerminalStatus(node.statusOverall));
};

export const hasMatchTaskError = (root) => {
  if (!root || typeof root !== "object") return false;

  const leafNodes = collectLeafNodes(root);
  const nodesToCheck = [root, ...leafNodes].filter(hasOwnStatusOverall);

  return nodesToCheck.some((node) => node.statusOverall === "error");
};

export const isMatchTaskTerminal = (root) => {
  if (!root || typeof root !== "object") return true;

  const leafNodes = collectLeafNodes(root);
  const nodesToCheck = [root, ...leafNodes].filter(hasOwnStatusOverall);

  if (nodesToCheck.length === 0) return true;

  return nodesToCheck.every((node) => isTerminalStatus(node.statusOverall));
};
