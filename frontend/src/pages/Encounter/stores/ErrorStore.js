import { makeAutoObservable } from "mobx";

class ErrorStore {
  encounterStore; 

    _errors = new Map();  
    _fieldErrors = new Map();
    uploadErrors = [];

  constructor(encounterStore) {
    this.encounterStore = encounterStore;
    makeAutoObservable(this, { encounterStore: false }, { autoBind: true });
  }

  get fieldErrors() {
    return this._fieldErrors;
  }

  getFieldError(sectionName, fieldPath) {
    return this._fieldErrors.get(`${sectionName}.${fieldPath}`) || null;
  }

  setFieldError(sectionName, fieldPath, errorMsg) {
    const key = `${sectionName}.${fieldPath}`;
    if (errorMsg) {
      this._fieldErrors.set(key, errorMsg);
    } else {
      this._fieldErrors.delete(key);
    }
  }

  get errors() {
    return this._errors;
  }

setErrors(sectionName, errorsObject) {
  const list = Array.isArray(errorsObject?.errors) ? errorsObject.errors : [];
  if (list.length === 0) {
    this._errors.delete(sectionName);
    return;
  }

  const msgs = list.map(e => `field name: ${e.fieldName || "unknown"}, message: ${e.code || "INVALID"}, value: ${e.value || "N/A"}`);
  this._errors.set(sectionName, msgs);
}

getSectionErrors(sectionName) {
  return this._errors.get(sectionName) || [];
}

hasSectionError(sectionName) {
  return (this._errors.get(sectionName)?.length || 0) > 0;
}

get sectionsWithErrors() {
  return Array.from(this._errors.keys());
}

clearSectionErrors(sectionName) {
  this._errors.delete(sectionName);
}

get hasErrors() {
  return this._errors.size > 0;
}

clearErrors() {
  this._errors.clear();
}

}

export default ErrorStore;