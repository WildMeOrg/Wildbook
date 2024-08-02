// import useOptions from '../../hooks/useOptions';
// import OptionTermFilter from '../../components/filterFields/OptionTermFilter';
import PointDistanceFilter from '../../components/filterFields/PointDistanceFilter';
// import SubstringFilter from '../../components/filterFields/SubstringFilter';
// import DateRangeFilter from '../../components/filterFields/DateRangeFilter';
// import useSiteSettings from '../site/useSiteSettings';
// import IntegerFilter from '../../components/filterFields/IntegerFilter';
// import autogenNameFilter from '../../components/filterFields/autogenNameFilter';
// import useBuildFilter from '../../components/filterFields/useBuildFilter';
// import sexOptions from '../../constants/sexOptions';
import LocationFilter from '../../components/filterFields/LocationFilter';
import DateFilter from '../../components/filterFields/DateFilter';
import ObservationAttributeFilter from '../../components/filterFields/ObservationAttributeFilter';
import ImageLabelFilter from '../../components/filterFields/ImageLabelFilter';
import IdentityFilter from '../../components/filterFields/IdentityFilter';
import TagsFilter from '../../components/filterFields/TagsFilter';
import SocialFilter from '../../components/filterFields/SocialFilter';
import MetadataFilter from '../../components/filterFields/MetadataFilter';

import useGetSiteSettings from '../../models/useGetSiteSettings';
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
    
  ];
}
