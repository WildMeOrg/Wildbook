import { useEffect } from 'react';
import { useIntl } from 'react-intl';
import { get } from 'lodash-es';

// import useSiteSettings from '../models/site/useSiteSettings';

export default function (message, configuration = {}) {
  const intl = useIntl();
  const appendSiteNameConfiguration = get(
    configuration,
    'appendSiteName',
    true,
  );
  const translateMessage = get(
    configuration,
    'translateMessage',
    true,
  );
  const refreshKey = get(configuration, 'refreshKey', null);
  const messageValues = get(configuration, 'messageValues', {});

//   const siteSettings = useSiteSettings();
//   const siteSettings = null;
//   const siteName = get(siteSettings, ['data', 'site.name', 'value']);
//   const appendSiteName = siteName && appendSiteNameConfiguration;
  document.title = "Wildbook"
//   useEffect(() => {
//     const translatedMessage = translateMessage
//       ? intl.formatMessage({ id: message }, messageValues)
//       : message;
//     if (appendSiteName && siteName) {
//       document.title = `${translatedMessage} • ${siteName}`;
//     } else {
//       document.title = translatedMessage;
//     }
//   }, [message, refreshKey, siteName, appendSiteName]);


}
