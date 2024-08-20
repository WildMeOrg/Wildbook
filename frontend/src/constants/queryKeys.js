function sortIfArray(value) {
    return Array.isArray(value) ? value.sort() : value;
  }
  
  export default {
    me: 'me',
    settingsSchema: 'settingsSchema',
    siteInfo: 'siteInfo',
    homePageInfo: 'homePageInfo',
    notifications: 'notifications',
    detectionConfig: 'detectionConfig',
    keywords: 'keywords',
    users: 'users',
    collaborations: 'collaborations',
    assetGroupSightings: 'assetGroupSightings',
    allNotifications: 'allNotifications',
    unreadNotifications: 'unreadNotifications',
    twitterBotTestResults: 'twitterBotTestResults',
    publicData: 'publicData',
    socialGroups: 'socialGroups',
    publicAssetGroupSightings: 'publicAssetGroupSightings',
    sageJobs: ['sage', 'jobs'],
  };
  
  export function getEncounterQueryKey(guid) {
    return ['encounter', guid];
  }
  
  export function getSightingQueryKey(guid) {
    return ['sighting', guid];
  }
  
  export function getSightingMatchResultsQueryKey(guid) {
    return ['sightingMatchResults', guid];
  }
  
  export function getAGSQueryKey(guid) {
    return ['assetGroupSighting', guid];
  }
  
  export function getIndividualQueryKey(guid) {
    return ['individual', guid];
  }
  
  export function getIndividualMergeRequestQueryKey(guid) {
    return ['individualMergeRequest', guid];
  }
  
  export function getMergeConflictsQueryKey(guids) {
    return ['individualMergeConflicts', guids];
  }
  
  export function getUserQueryKey(guid) {
    return ['user', guid];
  }
  
  export function getUserSightingsQueryKey(guid, query, params) {
    return ['userSightings', guid, query, params];
  }
  
  export function getUserAgsQueryKey(guid, params) {
    return ['userAgs', guid, params];
  }
  
  export function getAssetGroupQueryKey(guid) {
    return ['assetGroup', guid];
  }
  
  export function getNotificationQueryKey(guid) {
    return ['notification', guid];
  }
  
  export function getIndividualTermQueryKey(searchTerm) {
    return ['individualQuickSearch', searchTerm];
  }
  
  export function getIndividualsByGuidsQueryKey(individualGuids) {
    return ['individualQuickSearch', sortIfArray(individualGuids)];
  }
  
  export function getSightingTermQueryKey(searchTerm) {
    return ['sightingQuickSearch', searchTerm];
  }
  
  export function getEncounterTermQueryKey(searchTerm) {
    return ['encounterQuickSearch', searchTerm];
  }
  
  export function getSocialGroupQueryKey(guid) {
    return ['socialGroup', guid];
  }
  
  export function getIndividualFilterQueryKey(
    filters,
    page,
    rowsPerPage,
  ) {
    return ['individualFilterSearch', filters, page, rowsPerPage];
  }
  
  export function getSightingFilterQueryKey(
    filters,
    page,
    rowsPerPage,
  ) {
    return ['sightingFilterSearch', filters, page, rowsPerPage];
  }
  
  export function getEncounterFilterQueryKey(
    filters,
    size,
    from,
    sortname,
    sortorder
  ) {
    return ['encounterFilterSearch', filters, size, from, sortname, sortorder]; 
  }
  
  export function getAuditLogQueryKey(filters, page, rowsPerPage) {
    return ['auditLogFilterSearch', filters, page, rowsPerPage];
  }
  