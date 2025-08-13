import React from "react";
import { observer } from "mobx-react-lite";
import useGetSiteSettings from "../../../models/useGetSiteSettings";
import { FormattedMessage } from "react-intl";

const GalleryView = observer(({ store }) => {
  const { data } = useGetSiteSettings();

  return (
    <div>
      <h1>
        <FormattedMessage id="galleryView.title" defaultMessage="Gallery View" />
      </h1>
      </div>

  );
});

export default GalleryView;