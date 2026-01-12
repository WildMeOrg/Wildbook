const collectProspects = (node, type, result = []) => {
  const hasMethod = !!node.method;
  const methodName = node.method?.name ?? node.method?.description;
  const methodDescription = node.method?.description ?? null;

  const prospects = node.matchResults?.prospects?.[type];

  const hasResults = Array.isArray(prospects) && prospects.length > 0;

  if (hasResults && hasMethod) {
    const common = {
      algorithm: methodName,
      date: node.matchResults.created,
      numberCandidates: node.matchResults.numberCandidates || 0,
      queryEncounterId:
        node.matchResults.queryAnnotation?.encounter?.id || null,
      queryIndividualId:
        node.matchResults.queryAnnotation?.individual?.id || null,
      queryIndividualDisplayName:
        node.matchResults.queryAnnotation?.individual?.displayName || null,
      queryEncounterImageAsset:
        node.matchResults.queryAnnotation?.asset || null,
      queryEncounterImageUrl:
        node.matchResults.queryAnnotation?.asset?.url || null,

      methodName,
      methodDescription,
      method: node.method || null,
      taskId: node.id ?? null,
      taskStatus: node.status ?? null,
      taskStatusOverall: node.statusOverall ?? null,

      hasResults: true,
    };

    result.push(
      ...prospects.map((item) => ({
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

export const getAllIndiv = (node, result) =>
  collectProspects(node, "indiv", result);
export const getAllAnnot = (node, result) =>
  collectProspects(node, "annot", result);
