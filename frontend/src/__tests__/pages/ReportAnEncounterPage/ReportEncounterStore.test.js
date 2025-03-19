import { ReportEncounterStore } from "../../../pages/ReportsAndManagamentPages/ReportEncounterStore";
import axios from "axios";

jest.mock("axios");

describe("ReportEncounterStore", () => {
  let store;

  beforeEach(() => {
    store = new ReportEncounterStore();
  });

  test("should initialize with default values", () => {
    expect(store.imageRequired).toBe(true);
    expect(store.imageSectionFileNames).toEqual([]);
    expect(store.imageCount).toBe(0);
    expect(store.dateTimeSection.value).toBe(null);
    expect(store.speciesSection.value).toBe("");
    expect(store.success).toBe(false);
  });

  test("should set imageSectionSubmissionId", () => {
    store.setImageSectionSubmissionId("12345");
    expect(store.imageSectionSubmissionId).toBe("12345");
  });

  test("should add and remove filenames in imageSectionFileNames", () => {
    store.setImageSectionFileNames("image1.jpg", "add");
    expect(store.imageSectionFileNames).toContain("image1.jpg");

    store.setImageSectionFileNames("image1.jpg", "remove");
    expect(store.imageSectionFileNames).not.toContain("image1.jpg");
  });

  test("should validate emails correctly", () => {
    store.setSubmitterEmail("test@example.com");
    expect(store.validateEmails()).toBe(true);

    store.setSubmitterEmail("invalid-email");
    expect(store.validateEmails()).toBe(false);
  });

  test("should validate required fields correctly", () => {
    store.setSpeciesSectionValue("Lion");
    store.setDateTimeSectionValue("2024-03-18T12:00:00");
    store.setLocationId("123-location");
    store.setImageRequired(false);

    expect(store.validateFields()).toBe(true);

    store.setImageRequired(true);
    expect(store.validateFields()).toBe(false);
  });

  test("should submit report successfully", async () => {
    axios.post.mockResolvedValue({ status: 200, data: { message: "Success" } });

    store.setImageSectionFileNames("image1.jpg", "add");
    store.setSpeciesSectionValue("Tiger");
    store.setDateTimeSectionValue("2024-03-18T12:00:00");
    store.setLocationId("123-location");

    const response = await store.submitReport();

    expect(response).toEqual({ message: "Success" });
    expect(store.success).toBe(true);
    expect(store.finished).toBe(true);
    expect(store.imageSectionFileNames).toEqual([]);
  });

  test("should handle submission failure", async () => {
    axios.post.mockRejectedValue({
      response: { data: { errors: "Submission failed" } },
    });

    store.setImageSectionFileNames("image1.jpg", "add");
    store.setSpeciesSectionValue("Elephant");
    store.setDateTimeSectionValue("2024-03-18T12:00:00");
    store.setLocationId("123-location");

    await store.submitReport();

    expect(store.showSubmissionFailedAlert).toBe(true);
    expect(store.error).toBe("Submission failed");
  });

  test("should set and get latitude and longitude", () => {
    store.setLat(45.4215);
    store.setLon(-75.6972);
    expect(store.lat).toBe(45.4215);
    expect(store.lon).toBe(-75.6972);
  });

  test("should correctly update success and finished flags", () => {
    store._success = true;
    store._finished = true;
    expect(store.success).toBe(true);
    expect(store.finished).toBe(true);
  });

  test("should correctly handle EXIF data updates", () => {
    store.setExifDateTime("2024-03-18T10:00:00");
    expect(store.exifDateTime).toContain("2024-03-18T10:00:00");
  });

  test("should correctly update sign-in modal state", () => {
    store.setSignInModalShow(true);
    expect(store.signInModalShow).toBe(true);
    store.setSignInModalShow(false);
    expect(store.signInModalShow).toBe(false);
  });

  test("should correctly update additional comments section", () => {
    store.setCommentsSectionValue("This is a test comment.");
    expect(store.additionalCommentsSection.value).toBe(
      "This is a test comment.",
    );
  });

  test("should correctly update and validate follow-up submitter and photographer emails", () => {
    store.setSubmitterEmail("submitter@example.com");
    store.setPhotographerEmail("photographer@example.com");
    expect(store.validateEmails()).toBe(true);
  });

  test("should correctly update and remove additional emails", () => {
    store.setAdditionalEmails("test1@example.com,test2@example.com");
    expect(store.followUpSection.additionalEmails).toBe(
      "test1@example.com,test2@example.com",
    );
  });

  test("should set and get various properties correctly", () => {
    store.setImageRequired(false);
    expect(store.imageRequired).toBe(false);

    store.setImageCount(5);
    expect(store.imageCount).toBe(5);

    store.setDateTimeSectionValue("2024-03-18T15:00:00");
    expect(store.dateTimeSection.value).toBe("2024-03-18T15:00:00");

    store.setSpeciesSectionValue("Giraffe");
    expect(store.speciesSection.value).toBe("Giraffe");

    store.setLocationError(true);
    expect(store.placeSection.error).toBe(true);
  });

  test("should validate empty additional emails", () => {
    store.setAdditionalEmails("");
    expect(store.validateEmails()).toBe(true);
  });

  test("should validate incorrectly formatted additional emails", () => {
    store.setAdditionalEmails("wrong-email,another@valid.com");
    expect(store.validateEmails()).toBe(false);
  });

  test("should correctly set and clear error states", () => {
    store.setImageSectionError(true);
    expect(store.imageSectionError).toBe(true);

    store.setImageSectionError(false);
    expect(store.imageSectionError).toBe(false);
  });

  test("should return false if required dateTimeSection is missing", () => {
    store.setDateTimeSectionValue(null);
    expect(store.validateFields()).toBe(false);
  });

  test("should handle empty locationId validation correctly", () => {
    store.setLocationId(null);
    expect(store.validateFields()).toBe(false);
  });

  test("should submit report with non-required image field", async () => {
    axios.post.mockResolvedValue({ status: 200, data: { message: "Success" } });

    store.setImageRequired(false);
    store.setSpeciesSectionValue("Bear");
    store.setDateTimeSectionValue("2024-03-18T12:00:00");
    store.setLocationId("789-location");

    const response = await store.submitReport();

    expect(response).toEqual({ message: "Success" });
    expect(store.success).toBe(true);
  });

  test("should not submit report if fields are invalid", async () => {
    store.setSpeciesSectionValue("");
    store.setDateTimeSectionValue("");
    store.setLocationId(null);

    await store.submitReport();

    expect(store.success).toBe(false);
  });

  test("should handle sign-in modal correctly", () => {
    store.setSignInModalShow(true);
    expect(store.signInModalShow).toBe(true);
    store.setSignInModalShow(false);
    expect(store.signInModalShow).toBe(false);
  });
});
