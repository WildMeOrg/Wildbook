import React from "react";
import { FormattedMessage } from "react-intl";

const authenticatedMenu = (
  username,
  showclassicsubmit,
  showClassicEncounterSearch,
  showHowToPhotograph,
) => [
  {
    Submit: [
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_REPORTENCOUNTER"
            defaultMessage="Report an Encounter"
          />
        ),
        href: `${process.env.PUBLIC_URL}/report`,
      },
      ...(showclassicsubmit
        ? [
            {
              name: (
                <FormattedMessage
                  id="MENU_LEARN_REPORTENCOUNTER_CLASSIC"
                  defaultMessage="Report an Encounter(Classic)"
                />
              ),
              href: "/submit.jsp",
            },
          ]
        : []),
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_BULKIMPORT"
            defaultMessage="Bulk Import"
          />
        ),
        href: `${process.env.PUBLIC_URL}/bulk-import`,
      },
    ],
  },
  {
    MENU_RESOURCES: [
      {
        name: <FormattedMessage id="MENU_WILDBOOK_DOCUMENTATION" />,
        href: `https://wildbook.docs.wildme.org/`,
      },
      {
        name: <FormattedMessage id="ABOUT_US" />,
        href: "/react/about-us",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_CONTACTUS"
            defaultMessage="Contact Us"
          />
        ),
        href: "/contactus.jsp",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_POLICIES_AND_DATA"
            defaultMessage="Policies and Data"
          />
        ),
        href: `${process.env.PUBLIC_URL}/policies-and-data?section=citing_wildbook`,
        sub: [
          {
            name: <FormattedMessage id="MENU_LEARN_PRIVACYPOLICY" />,
            href: `${process.env.PUBLIC_URL}/policies-and-data?section=privacy_policy`,
          },
          {
            name: <FormattedMessage id="MENU_LEARN_TERMSOFUSE" />,
            href: `${process.env.PUBLIC_URL}/policies-and-data?section=terms_of_use`,
          },
          {
            name: <FormattedMessage id="MENU_LEARN_CITINGWILDBOOK" />,
            href: `${process.env.PUBLIC_URL}/policies-and-data?section=citing_wildbook`,
          },
        ],
      },
      ...(showHowToPhotograph
        ? [
            {
              name: (
                <FormattedMessage
                  id="MENU_LEARN_HOWTOPHOTOGRAPH"
                  defaultMessage="How to Photograph"
                />
              ),
              href: "/react/how-to-photograph",
            },
          ]
        : []),
    ],
  },

  {
    MENU_DATA: [
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_MYENCOUNTERS"
            defaultMessage="My Encounters"
          />
        ),
        href: `${process.env.PUBLIC_URL}/encounter-search?username=${username}`,
        sub: [
          {
            name: (
              <FormattedMessage
                id="MENU_LEARN_APPROVEDANIMALS"
                defaultMessage="My Approved Animals"
              />
            ),
            href: `${process.env.PUBLIC_URL}/encounter-search?username=${username}&state=approved`,
          },
          {
            name: (
              <FormattedMessage
                id="MENU_LEARN_UNAPPROVEDANIMALS"
                defaultMessage="My Unapproved Animals"
              />
            ),
            href: `${process.env.PUBLIC_URL}/encounter-search?username=${username}&state=unapproved`,
          },
          {
            name: (
              <FormattedMessage
                id="MENU_LEARN_UNIDENTIFIABLEANIMALS"
                defaultMessage="My Unidentifiable Animals"
              />
            ),
            href: `${process.env.PUBLIC_URL}/encounter-search?username=${username}&state=unidentifiable`,
          },
        ],
      },
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_MYINDIVIDUALS"
            defaultMessage="My Individuals"
          />
        ),
        href: `/individualSearchResults.jsp?username=${username}`,
      },
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_MYSIGHTINGS"
            defaultMessage="My Sightings"
          />
        ),
        href: `/occurrenceSearchResults.jsp?submitterID=${username}`,
      },
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_MYBULKIMPORTS"
            defaultMessage="My Bulk Imports"
          />
        ),
        href: "/imports.jsp",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_MYPROJECTS"
            defaultMessage="My Projects"
          />
        ),
        href: "/react/projects/overview",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_ANIMALS_INDIVIDUALGALLERY"
            defaultMessage="Individual Gallery"
          />
        ),
        href: "/gallery.jsp",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_ANIMALS_ANIMALCALENDAR"
            defaultMessage="Animal Calendar"
          />
        ),
        href: "/react/encounter-search?calendar=true",
      },
    ],
  },
  {
    SEARCH: [
      {
        name: (
          <FormattedMessage
            id="MENU_SEARCH_ENCOUNTERS"
            defaultMessage="Encounters"
          />
        ),
        href: `${process.env.PUBLIC_URL}/encounter-search`,
      },
      ...(showClassicEncounterSearch
        ? [
            {
              name: (
                <FormattedMessage
                  id="MENU_SEARCH_ENCOUNTERS_CLASSIC"
                  defaultMessage="Encounters (Classic)"
                />
              ),
              href: "/encounters/encounterSearch.jsp",
            },
          ]
        : []),
      {
        name: (
          <FormattedMessage
            id="MENU_SEARCH_INDIVIDUALS"
            defaultMessage="Individuals"
          />
        ),
        href: "/individualSearch.jsp",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_SEARCH_SIGHTINGS"
            defaultMessage="Sightings"
          />
        ),
        href: "/occurrenceSearch.jsp",
      },
    ],
  },
  {
    ADMINISTER: [
      {
        name: (
          <FormattedMessage
            id="MENU_ADMINISTER_USERMANAGEMENT"
            defaultMessage="User Management"
          />
        ),
        href: "/appadmin/users.jsp?context=context0",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_ADMINISTER_LIBRARYADMINISTRATION"
            defaultMessage="Library Administration"
          />
        ),
        href: "/appadmin/admin.jsp",
      },
      {
        name: (
          <FormattedMessage id="MENU_ADMINISTER_LOGS" defaultMessage="Logs" />
        ),
        href: "/react/admin/logs",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_ADMINISTER_PHOTOKEYWORDS"
            defaultMessage="Photo Keywords"
          />
        ),
        href: "/appadmin/kwAdmin.jsp",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_ADMINISTER_SOFTWAREDOCS"
            defaultMessage="Software Documentation"
          />
        ),
        href: "https://wildbook.docs.wildme.org/introduction/index.html",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_ADMINISTER_DATAINTEGRITY"
            defaultMessage="Data Integrity"
          />
        ),
        href: "/appadmin/dataIntegrity.jsp",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_ADMINISTER_BULKIMPORTLOGS"
            defaultMessage="Bulk Import Logs"
          />
        ),
        href: "/imports.jsp",
      },
    ],
  },
];

const unAuthenticatedMenu = (showclassicsubmit, showHowToPhotograph) => [
  {
    Submit: [
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_REPORTENCOUNTER"
            defaultMessage="Report an Encounter"
          />
        ),
        href: `${process.env.PUBLIC_URL}/report`,
      },
      ...(showclassicsubmit
        ? [
            {
              name: (
                <FormattedMessage
                  id="MENU_LEARN_REPORTENCOUNTER_CLASSIC"
                  defaultMessage="Report an Encounter(Classic)"
                />
              ),
              href: "/submit.jsp",
            },
          ]
        : []),
    ],
  },
  {
    MENU_RESOURCES: [
      {
        name: <FormattedMessage id="MENU_WILDBOOK_DOCUMENTATION" />,
        href: `https://wildbook.docs.wildme.org/`,
      },
      {
        name: <FormattedMessage id="ABOUT_US" />,
        href: "/react/about-us",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_CONTACTUS"
            defaultMessage="Contact Us"
          />
        ),
        href: "/contactus.jsp",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_POLICIES_AND_DATA"
            defaultMessage="Policies and Data"
          />
        ),
        href: `${process.env.PUBLIC_URL}/policies-and-data?section=citing_wildbook`,
        sub: [
          {
            name: <FormattedMessage id="MENU_LEARN_PRIVACYPOLICY" />,
            href: `${process.env.PUBLIC_URL}/policies-and-data?section=privacy_policy`,
          },
          {
            name: <FormattedMessage id="MENU_LEARN_TERMSOFUSE" />,
            href: `${process.env.PUBLIC_URL}/policies-and-data?section=terms_of_use`,
          },
          {
            name: <FormattedMessage id="MENU_LEARN_CITINGWILDBOOK" />,
            href: `${process.env.PUBLIC_URL}/policies-and-data?section=citing_wildbook`,
          },
        ],
      },
      ...(showHowToPhotograph
        ? [
            {
              name: (
                <FormattedMessage
                  id="MENU_LEARN_HOWTOPHOTOGRAPH"
                  defaultMessage="How to Photograph"
                />
              ),
              href: "/react/how-to-photograph",
            },
          ]
        : []),
    ],
  },

  {
    MENU_DATA: [
      {
        name: (
          <FormattedMessage
            id="MENU_ANIMALS_INDIVIDUALGALLERY"
            defaultMessage="Individual Gallery"
          />
        ),
        href: "/gallery.jsp",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_ANIMALS_ANIMALCALENDAR"
            defaultMessage="Animal Calendar"
          />
        ),
        href: "/react/encounter-search?calendar=true",
      },
    ],
  },
];

export { authenticatedMenu, unAuthenticatedMenu };
