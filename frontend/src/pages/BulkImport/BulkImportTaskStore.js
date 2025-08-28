import { makeAutoObservable } from "mobx";

export class BulkImportTaskStore {
  locationOptions = [];
  locationID = [];

  constructor() {
    makeAutoObservable(this, {}, { autoBind: true });
  }

  setOptions(options) {
    this.locationOptions = Array.isArray(options) ? options : [];
  }

  setLocationID(ids) {
    const uniq = Array.from(new Set((ids || []).filter(Boolean)));
    this.locationID = uniq;
  }

  clear() {
    this.locationOptions = [];
    this.locationID = [];
  }

  get locationIDOptions() {
    return this.locationOptions;
  }
  setLocationIDOptions(options) {
    this.setOptions(options);
  }

  findNodeByValue(treeData, value) {
    for (const node of treeData || []) {
      if (node?.value === value) return node;
      if (node?.children?.length) {
        const found = this.findNodeByValue(node.children, value);
        if (found) return found;
      }
    }
    return null;
  }

  getAllDescendantValues(node) {
    const res = [];
    (node?.children || []).forEach((child) => {
      res.push(child.value);
      res.push(...this.getAllDescendantValues(child));
    });
    return res;
  }

  expandIds(values = []) {
    const set = new Set();
    (values || []).forEach((val) => {
      if (!val) return;
      set.add(val);
      const node = this.findNodeByValue(this.locationOptions, val);
      if (node)
        this.getAllDescendantValues(node).forEach((cid) => set.add(cid));
    });
    return Array.from(set);
  }

  initFromPrevious(previousIds = []) {
    this.setLocationID(this.expandIds(previousIds));
  }

  handleStrictChange(checkedValues, _labels, extra) {
    if (!extra || !("triggerValue" in extra)) {
      this.setLocationID(this.expandIds(checkedValues || []));
      return;
    }
    const id = extra.triggerValue;
    const checked = !!extra.checked;

    const set = new Set(this.locationID);
    const node = this.findNodeByValue(this.locationOptions, id);

    if (checked) {
      set.add(id);
      if (node) this.getAllDescendantValues(node).forEach((v) => set.add(v));
    } else {
      set.delete(id);
      if (node) this.getAllDescendantValues(node).forEach((v) => set.delete(v));
    }
    this.setLocationID(Array.from(set));
  }

  get locationIDString() {
    if (!this.locationID.length) return "";
    return (
      "&locationID=" +
      this.locationID.map(encodeURIComponent).join("&locationID=")
    );
  }
}

export default BulkImportTaskStore;
