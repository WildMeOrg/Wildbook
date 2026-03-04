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

const hasAnyMatchResults = (node) => {
  if (!node?.matchResults?.prospects) return false;

  const annot = node.matchResults.prospects.annot;
  const indiv = node.matchResults.prospects.indiv;

  return (
    (Array.isArray(annot) && annot.length > 0) ||
    (Array.isArray(indiv) && indiv.length > 0)
  );
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

export const isMatchTaskStillRunning = (root) => {
  if (!root || typeof root !== "object") return false;

  // case 1:
  // root itself is a leaf node, has no children and no matchResults,
  // and is not completed => task still running
  if (
    isLeafNode(root) &&
    !hasAnyMatchResults(root) &&
    root.statusOverall !== "completed"
  ) {
    return true;
  }

  // case 2:
  // any leaf node is not completed AND there are still no results anywhere
  const leafNodes = collectLeafNodes(root);
  const hasIncompleteLeaf = leafNodes.some(
    (leaf) => leaf?.statusOverall !== "completed",
  );

  const hasAnyResultsAnywhere =
    getAllAnnot(root).length > 0 || getAllIndiv(root).length > 0;

  return hasIncompleteLeaf && !hasAnyResultsAnywhere;
};
