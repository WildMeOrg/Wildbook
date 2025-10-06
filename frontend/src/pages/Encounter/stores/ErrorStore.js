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

  setError(key, errorMessage) {
    if (errorMessage) {
      this._errors.set(key, errorMessage);
    } else {
      this._errors.delete(key);
    }
  }

  setErrors(errorsObject) {
    this._errors.clear();

    if (errorsObject && typeof errorsObject === 'object') {
      Object.entries(errorsObject).forEach(([key, value]) => {
        this._errors.set(key, value);
      });
    }
  }

  clearErrors() {
    this._errors.clear();
  }

  getError(key) {
    return this._errors.get(key) || null;
  }

  get hasErrors() {
    return this._errors.size > 0;
  }

 
}

export default ErrorStore;