import LocationFilter from '../../components/filterFields/LocationFilter';
import DateFilter from '../../components/filterFields/DateFilter';
import ObservationAttributeFilter from '../../components/filterFields/ObservationAttributeFilter';
import ImageLabelFilter from '../../components/filterFields/ImageLabelFilter';
import IdentityFilter from '../../components/filterFields/IdentityFilter';
import TagsFilter from '../../components/filterFields/TagsFilter';
import SocialFilter from '../../components/filterFields/SocialFilter';
import MetadataFilter from '../../components/filterFields/MetadataFilter';
import ApplyQueryFilter from '../../components/filterFields/ApplyQueryFilter';

import BiologicalSamplesAndAnalysesFilter from '../../components/filterFields/BiologicalSamplesAndAnalysesFilter';

export default function useEncounterSearchSchemas() {


  return [
    
    {
      id: 'location',
      labelId: 'FILTER_LOCATION',
      FilterComponent: LocationFilter,
    },
    {
      id: 'date',
      labelId: 'FILTER_DATE',
      FilterComponent: DateFilter,
    },
    {
      id: 'observation',
      labelId: 'FILTER_OBSERVATION_ATTRIBUTE',
      FilterComponent: ObservationAttributeFilter,
    },
    {
      id: 'imageLabel',
      labelId: 'FILTER_IMAGE_LABEL',
      FilterComponent: ImageLabelFilter,
    },
    {
      id: 'identity',
      labelId: 'FILTER_IDENTITY',
      FilterComponent: IdentityFilter,
    },
    {
      id: 'tags',
      labelId: 'FILTER_TAGS',
      FilterComponent: TagsFilter,
    },
    {
      id: 'biologicalSample',
      labelId: 'FILTER_BIOLOGICAL_SAMPLE',
      FilterComponent: BiologicalSamplesAndAnalysesFilter,
    },
    {
      id: 'social',
      labelId: 'FILTER_SOCIAL',
      FilterComponent: SocialFilter,
    },
    {
      id: 'metadata',
      labelId: 'FILTER_METADATA',
      FilterComponent: MetadataFilter,
    },
    {
      id: 'applyQuery',
      labelId: 'Apply Query ID',
      FilterComponent: ApplyQueryFilter,
    },
    
  ];
}
