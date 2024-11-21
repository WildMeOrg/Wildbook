import React from "react";
import { FormattedMessage } from "react-intl";

const authenticatedMenu = (username, showclassicsubmit) => [
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
        href: "/import/instructions.jsp",
      },
    ],
  },
  {
    Learn: [
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_ABOUTWILDBOOK"
            defaultMessage="About Wildbook"
          />
        ),
        href: "/overview.jsp",
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
            id="MENU_LEARN_CITINGWILDBOOK"
            defaultMessage="Citing Wildbook"
          />
        ),
        href: `${process.env.PUBLIC_URL}/citation`,
      },
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_HOWTOPHOTOGRAPH"
            defaultMessage="How to Photograph"
          />
        ),
        href: "/photographing.jsp",
      },
      // { name: <FormattedMessage id="MENU_LEARN_PRIVACYPOLICY" defaultMessage="Privacy Policy" />, href: '/privacyPolicy.jsp' },
      // { name: <FormattedMessage id="MENU_LEARN_TERMSOFUSE" defaultMessage="Terms of Use" />, href: '/termsOfUse.jsp' },
    ],
  },

  {
    My_Data: [
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
        href: "/projects/projectList.jsp",
      },
    ],
  },
  {
    Search: [
      {
        name: (
          <FormattedMessage
            id="MENU_SEARCH_ENCOUNTERS"
            defaultMessage="Encounters"
          />
        ),
        href: `${process.env.PUBLIC_URL}/encounter-search`,
      },
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
    Animals: [
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
        href: "/xcalendar/calendar.jsp",
      },
    ],
  },
  {
    Administer: [
      {
        name: (
          <FormattedMessage
            id="MENU_ADMINISTER_MANAGEACCOUNTS"
            defaultMessage="Manage My Accounts"
          />
        ),
        href: "/myUsers.jsp",
      },
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
        href: "/appadmin/logs.jsp",
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

const unAuthenticatedMenu = (showclassicsubmit) => [
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
    Learn: [
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_ABOUTWILDBOOK"
            defaultMessage="About Wildbook"
          />
        ),
        href: "/overview.jsp",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_CONTACTUS"
            defaultMessage="Contact Us"
          />
        ),
        href: "/photographing.jsp",
      },
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_CITINGWILDBOOK"
            defaultMessage="Citing Wildbook"
          />
        ),
        href: `${process.env.PUBLIC_URL}/citation`,
      },
      {
        name: (
          <FormattedMessage
            id="MENU_LEARN_HOWTOPHOTOGRAPH"
            defaultMessage="How to Photograph"
          />
        ),
        href: "/photographing.jsp",
      },
      //   {
      //     name: (
      //       <FormattedMessage
      //         id="MENU_LEARN_PRIVACYPOLICY"
      //         defaultMessage="Privacy Policy"
      //       />
      //     ),
      //     href: "/privacy-policy.jsp",
      //   },
      //   {
      //     name: (
      //       <FormattedMessage
      //         id="MENU_LEARN_TERMSOFUSE"
      //         defaultMessage="Terms of Use"
      //       />
      //     ),
      //     href: "/terms-of-use.jsp",
      //   },
    ],
  },

  {
    Animals: [
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
        href: "/xcalendar/calendar.jsp",
      },
    ],
  },
];

export { authenticatedMenu, unAuthenticatedMenu };
