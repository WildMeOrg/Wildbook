import LocationFilter from "../../components/filterFields/LocationFilter";
import DateFilter from "../../components/filterFields/DateFilter";
import ObservationAttributeFilter from "../../components/filterFields/ObservationAttributeFilter";
import ImageLabelFilter from "../../components/filterFields/ImageLabelFilter";
import IdentityFilter from "../../components/filterFields/IdentityFilter";
import TagsFilter from "../../components/filterFields/TagsFilter";
import SocialFilter from "../../components/filterFields/SocialFilter";
import MetadataFilter from "../../components/filterFields/MetadataFilter";
import ApplyQueryFilter from "../../components/filterFields/ApplyQueryFilter";
import SightingsObservationAttributeFilter from "../../components/filterFields/SightingsObservationAttributeFilter";
import BiologicalSamplesAndAnalysesFilter from "../../components/filterFields/BiologicalSamplesAndAnalysesFilter";
import SightingsLocationFilter from "../../components/filterFields/SightingsLocationFilter";
import IndividualsObservationAttributeFilter from "../../components/filterFields/IndividualsObservationAttributeFilter";
import IndividualEstimateFilter from "../../components/filterFields/IndividualEstimateFilter";
import IndividualDateFilter from "../../components/filterFields/IndividualDateFilter";

export default function useEncounterSearchSchemas() {
  return [
    {
      id: "Encounters",
      labelId: "MENU_SEARCH_ENCOUNTERS",
    },
    {
      id: "location",
      labelId: "FILTER_LOCATION",
      FilterComponent: LocationFilter,
    },
    {
      id: "date",
      labelId: "FILTER_DATE",
      FilterComponent: DateFilter,
    },
    {
      id: "observation",
      labelId: "FILTER_OBSERVATION_ATTRIBUTE",
      FilterComponent: ObservationAttributeFilter,
    },
    {
      id: "imageLabel",
      labelId: "FILTER_IMAGE_LABEL",
      FilterComponent: ImageLabelFilter,
    },

    {
      id: "tags",
      labelId: "FILTER_TAGS",
      FilterComponent: TagsFilter,
    },
    {
      id: "biologicalSample",
      labelId: "FILTER_BIOLOGICAL_SAMPLE",
      FilterComponent: BiologicalSamplesAndAnalysesFilter,
    },

    {
      id: "metadata",
      labelId: "FILTER_METADATA",
      FilterComponent: MetadataFilter,
    },
    {
      id: "applySearchId",
      labelId: "APPLY_SEARCH_ID",
      FilterComponent: ApplyQueryFilter,
    },
    {
      id: "Sightings",
      labelId: "MENU_SEARCH_SIGHTINGS",
    },
    {
      id: "sightingslocation",
      labelId: "FILTER_LOCATION",
      FilterComponent: SightingsLocationFilter,
    },
    {
      id: "identity",
      labelId: "FILTER_IDENTITY",
      FilterComponent: IdentityFilter,
    },
    {
      id: "social",
      labelId: "FILTER_SOCIAL",
      FilterComponent: SocialFilter,
    },
    {
      id: "sightingsobservation",
      labelId: "FILTER_OBSERVATION_ATTRIBUTE",
      FilterComponent: SightingsObservationAttributeFilter,
    },
    {
      id: "individualsEstimate",
      labelId: "FILTER_INDIVIDUAL_ESTIMATE",
      FilterComponent: IndividualEstimateFilter,
    },
    {
      id: "Individuals",
      labelId: "MENU_SEARCH_INDIVIDUALS",
    },
    {
      id: "individualsdate",
      labelId: "FILTER_DATE",
      FilterComponent: IndividualDateFilter,
    },
    {
      id: "individualsobservation",
      labelId: "FILTER_OBSERVATION_ATTRIBUTE",
      FilterComponent: IndividualsObservationAttributeFilter,
    },
  ];
}
