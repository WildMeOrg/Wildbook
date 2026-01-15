export function findNodeByValue(treeData, value) {
  for (const node of treeData || []) {
    if (node?.value === value) return node;
    if (node?.children?.length) {
      const found = findNodeByValue(node.children, value);
      if (found) return found;
    }
  }
  return null;
}

export function getAllDescendantValues(node) {
  const res = [];
  (node?.children || []).forEach((child) => {
    if (child?.value != null) res.push(child.value);
    res.push(...getAllDescendantValues(child));
  });
  return res;
}

export function expandIds(locationOptions, values = []) {
  const set = new Set();
  (values || []).forEach((val) => {
    if (!val) return;
    set.add(val);
    const node = findNodeByValue(locationOptions, val); 
    if (node) {
      getAllDescendantValues(node).forEach((cid) => {
        if (cid != null) set.add(cid);
      });
    }
  });
  return Array.from(set);
}