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

export default function useEncounterSearchSchemas() {
  // const {
  //   regionOptions,
  //   speciesOptions,
  //   pipelineStateOptions,
  //   stageOptions,
  // } = useOptions();

  // const labeledSexOptions = sexOptions.map(o => ({
  //   labelId: o?.filterLabelId || o.labelId,
  //   value: o.value,
  // }));

  // const { data: siteSettings } = useGetSiteSettings();


  return [
    
    {
      id: 'location',
      labelId: 'Location',
      FilterComponent: LocationFilter,
    },
    {
      id: 'date',
      labelId: 'Date',
      FilterComponent: DateFilter,
    },
    {
      id: 'observation',
      labelId: 'Observation Attribute',
      FilterComponent: ObservationAttributeFilter,
    },
    {
      id: 'imageLabel',
      labelId: 'Image Label',
      FilterComponent: ImageLabelFilter,
    },
    {
      id: 'identity',
      labelId: 'Identity',
      FilterComponent: IdentityFilter,
    },
    {
      id: 'tags',
      labelId: 'Tags Filter',
      FilterComponent: TagsFilter,
    },
    {
      id: 'biologicalSample',
      labelId: 'Biological Sample',
      FilterComponent: IdentityFilter,
    },
    {
      id: 'social',
      labelId: 'Social',
      FilterComponent: SocialFilter,
    },
    {
      id: 'metadata',
      labelId: 'Meta Data',
      FilterComponent: MetadataFilter,
    },
    // {
    //   id: 'verbatimLocality',
    //   labelId: 'FREEFORM_LOCATION',
    //   FilterComponent: SubstringFilter,
    //   filterComponentProps: {
    //     filterId: 'verbatimLocality',
    //     queryTerms: ['verbatimLocality', 'locationId_value'],
    //   },
    // },
    // {
    //   id: 'comments',
    //   labelId: 'NOTES',
    //   FilterComponent: SubstringFilter,
    //   filterComponentProps: {
    //     filterId: 'comments',
    //     queryTerms: ['comments'],
    //   },
    // },
    // {
    //   id: 'time',
    //   labelId: 'SIGHTING_DATE_RANGE',
    //   FilterComponent: DateRangeFilter,
    //   filterComponentProps: { queryTerm: 'time', filterId: 'time' },
    // },
    // {
    //   id: 'species',
    //   labelId: 'SPECIES',
    //   FilterComponent: OptionTermFilter,
    //   filterComponentProps: {
    //     filterId: 'species',
    //     queryTerm: 'taxonomy_guids',
    //     choices: speciesOptions,
    //   },
    // },
    // {
    //   id: 'pipelineState',
    //   labelId: 'PIPELINE_STATE',
    //   FilterComponent: OptionTermFilter,
    //   filterComponentProps: {
    //     filterId: 'pipelineState',
    //     queryTerm: 'pipelineState',
    //     choices: pipelineStateOptions,
    //   },
    // },
    // {
    //   id: 'numberEncounters',
    //   labelId: 'NUMBER_ENCOUNTERS',
    //   FilterComponent: IntegerFilter,
    //   filterComponentProps: {
    //     filterId: 'numberEncounters',
    //     queryTerm: 'numberEncounters',
    //   },
    // },
    // {
    //   id: 'numberImages',
    //   labelId: 'NUMBER_IMAGES',
    //   FilterComponent: IntegerFilter,
    //   filterComponentProps: {
    //     filterId: 'numberImages',
    //     queryTerm: 'numberImages',
    //   },
    // },
    // {
    //   id: 'numberAnnotations',
    //   labelId: 'NUMBER_ANNOTATIONS',
    //   FilterComponent: IntegerFilter,
    //   filterComponentProps: {
    //     filterId: 'numberAnnotations',
    //     queryTerm: 'numberAnnotations',
    //   },
    // },
    // {
    //   id: 'numberIndividuals',
    //   labelId: 'NUMBER_OF_INDIVIDUALS',
    //   FilterComponent: IntegerFilter,
    //   filterComponentProps: {
    //     filterId: 'numberIndividuals',
    //     queryTerm: 'numberIndividuals',
    //   },
    // },
    // {
    //   id: 'stage',
    //   labelId: 'SIGHTING_STATE',
    //   FilterComponent: OptionTermFilter,
    //   filterComponentProps: {
    //     filterId: 'Stage',
    //     queryTerm: 'stage',
    //     choices: stageOptions,
    //   },
    // },
    // {
    //   id: 'latlong',
    //   labelId: 'EXACT_LOCATION',
    //   FilterComponent: PointDistanceFilter,
    //   filterComponentProps: {
    //     filterId: 'latlong',
    //     queryTerm: 'location_geo_point',
    //   },
    // },
    
  ];
}
