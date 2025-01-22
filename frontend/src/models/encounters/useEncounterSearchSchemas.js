import LocationFilter from "../../components/filterFields/LocationFilter";
import DateFilter from "../../components/filterFields/DateFilter";
import ObservationAttributeFilter from "../../components/filterFields/ObservationAttributeFilter";
import ImageLabelFilter from "../../components/filterFields/ImageLabelFilter";
import IdentityFilter from "../../components/filterFields/IdentityFilter";
import TagsFilter from "../../components/filterFields/TagsFilter";
import SocialFilter from "../../components/filterFields/SocialFilter";
import MetadataFilter from "../../components/filterFields/MetadataFilter";
import ApplyQueryFilter from "../../components/filterFields/ApplyQueryFilter";

import BiologicalSamplesAndAnalysesFilter from "../../components/filterFields/BiologicalSamplesAndAnalysesFilter";

export default function useEncounterSearchSchemas() {
  return [
    {
      id: "Encounters",
      labelId: "FILTER_ENCOUNTER",
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
      labelId: "FILTER_SIGHTINGS",
    },
    {
      id: "location",
      labelId: "FILTER_LOCATION",
      FilterComponent: LocationFilter,
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
      id: "observation",
      labelId: "FILTER_OBSERVATION_ATTRIBUTE",
      FilterComponent: ObservationAttributeFilter,
    },
    {
      id: "Individuals",
      labelId: "FILTER_INDIVIDUALS",
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
  ];
}
