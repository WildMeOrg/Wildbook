import de from "../../../locale/de.json";
import en from "../../../locale/en.json";
import es from "../../../locale/es.json";
import fr from "../../../locale/fr.json";
import it from "../../../locale/it.json";

const BOX_KEYS = [
  "BULKIMPORT_ERROR_INVALID_BBOX",
  "BULKIMPORT_ERROR_INVALID_THETA",
  "BULKIMPORT_ERROR_INVALID_VIEWPOINT",
  "BULKIMPORT_WARNING_IACLASS_NOT_MATCHABLE",
  "BULKIMPORT_UNBOXED_PHOTOS_WARNING",
  "BULK_IMPORT_INSTRUCTIONS_BOXES",
  "BULK_IMPORT_CONTINUE_MODAL_REUPLOAD_SPREADSHEET",
];

test.each([
  ["en", en],
  ["de", de],
  ["es", es],
  ["fr", fr],
  ["it", it],
])("%s has every bulk-import box message", (_, messages) => {
  BOX_KEYS.forEach((key) => expect(messages[key]).toBeTruthy());
  expect(messages.BULKIMPORT_WARNING_IACLASS_NOT_MATCHABLE).toContain(
    "{allowed}",
  );
  expect(messages.BULKIMPORT_UNBOXED_PHOTOS_WARNING).toContain("{count");
});
