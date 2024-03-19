import { FormattedMessage } from 'react-intl';

const authenticatedMenu = (username) => [
    {
        'Learn': [
            { name: <FormattedMessage id="menu.learn.aboutWildbook" defaultMessage="About Wildbook" />, href: '/overview.jsp' },
            { name: <FormattedMessage id="menu.learn.contactUs" defaultMessage="Contact Us" />, href: '/contactus.jsp' },
            { name: <FormattedMessage id="menu.learn.citingWildbook" defaultMessage="Citing Wildbook" />, href: '/citing.jsp' },
            { name: <FormattedMessage id="menu.learn.howToPhotograph" defaultMessage="How to Photograph" />, href: '/photographing.jsp' },
            { name: <FormattedMessage id="menu.learn.privacyPolicy" defaultMessage="Privacy Policy" />, href: '/photographing.jsp' },
            { name: <FormattedMessage id="menu.learn.termsOfUse" defaultMessage="Terms of Use" />, href: '/photographing.jsp' },
            { name: <FormattedMessage id="menu.learn.learnMore" defaultMessage="Learn more about Wildbook" />, href: 'https://www.wildme.org/#/wildbook' },
        ]
    },
    {
        'Submit': [
            { name: <FormattedMessage id="menu.submit.reportSighting" defaultMessage="Report a Sighting" />, href: '/submit.jsp' },
            { name: <FormattedMessage id="menu.submit.bulkImport" defaultMessage="Bulk Import" />, href: '/import/instructions.jsp' },
        ]
    },
    {
        'My_Data': [
            { 
                name: <FormattedMessage id="menu.myData.myEncounters" defaultMessage="My Encounters" />, 
                sub: [
                    { name: <FormattedMessage id="menu.myData.approvedAnimals" defaultMessage="My Approved Animals" />, href: `/encounters/searchResults.jsp?username=${username}&state=approved` },
                    { name: <FormattedMessage id="menu.myData.unapprovedAnimals" defaultMessage="My Unapproved Animals" />, href: `/encounters/searchResults.jsp?username=${username}&state=unapproved` },
                    { name: <FormattedMessage id="menu.myData.unidentifiableAnimals" defaultMessage="My Unidentifiable Animals" />, href: `/encounters/searchResults.jsp?username=${username}&state=unidentifiable` },
                ]
            },
            { name: <FormattedMessage id="menu.myData.myIndividuals" defaultMessage="My Individuals" />, href: `/individualSearchResults.jsp?username=${username}` },
            { name: <FormattedMessage id="menu.myData.mySightings" defaultMessage="My Sightings" />, href: `/occurrenceSearchResults.jsp?submitterID=${username}` },
            { name: <FormattedMessage id="menu.myData.myBulkImports" defaultMessage="My Bulk Imports" />, href: '/imports.jsp' },
            { name: <FormattedMessage id="menu.myData.myProjects" defaultMessage="My Projects" />, href: '/projects/projectList.jsp' },
        ]
    },
    {
        'Search': [        
            { name: <FormattedMessage id="menu.search.encounters" defaultMessage="Encounters" />, href: '/encounters/encounterSearch.jsp' },
            { name: <FormattedMessage id="menu.search.individuals" defaultMessage="Individuals" />, href: '/individualSearch.jsp' },
            { name: <FormattedMessage id="menu.search.sightings" defaultMessage="Sightings" />, href: '/occurrenceSearch.jsp' },
        ]
    },
    {
        'Animals': [
            { name: <FormattedMessage id="menu.animals.individualGallery" defaultMessage="Individual Gallery" />, href: '/gallery.jsp' },
            { name: <FormattedMessage id="menu.animals.animalCalendar" defaultMessage="Animal Calendar" />, href: '/xcalendar/calendar.jsp' },
        ]   
    },
    {
        'Administer': [
            { name: <FormattedMessage id="menu.administer.myAccount" defaultMessage="My Account" />, href: '/myAccount.jsp' },
            { name: <FormattedMessage id="menu.administer.manageAccounts" defaultMessage="Manage My Accounts" />, href: '/myUsers.jsp' },
            { name: <FormattedMessage id="menu.administer.userManagement" defaultMessage="User Management" />, href: '/appadmin/users.jsp?context=context0' },
            { name: <FormattedMessage id="menu.administer.libraryAdmin" defaultMessage="Library Administration" />, href: '/appadmin/admin.jsp' },
            { name: <FormattedMessage id="menu.administer.logs" defaultMessage="Logs" />, href: '/appadmin/logs.jsp' },
            { name: <FormattedMessage id="menu.administer.photoKeywords" defaultMessage="Photo Keywords" />, href: '/appadmin/kwAdmin.jsp' },
            { name: <FormattedMessage id="menu.administer.softwareDocs" defaultMessage="Software Documentation" />, href: 'https://docs.wildme.org/product-docs/en/wildbook/introduction/' },
            { name: <FormattedMessage id="menu.administer.dataIntegrity" defaultMessage="Data Integrity" />, href: '/appadmin/dataIntegrity.jsp' },
        ]     
    }
];

const unAuthenticatedMenu = [
    {
        'Learn': [
            { name: <FormattedMessage id="menu.learn.aboutWildbook" defaultMessage="About Wildbook" />, href: '/overview.jsp' },
            { name: <FormattedMessage id="menu.learn.contactUs" defaultMessage="Contact Us" />, href: '/photographing.jsp' },
            { name: <FormattedMessage id="menu.learn.citingWildbook" defaultMessage="Citing Wildbook" />, href: '/citing.jsp' },
            { name: <FormattedMessage id="menu.learn.howToPhotograph" defaultMessage="How to Photograph" />, href: '/photographing.jsp' },
            { name: <FormattedMessage id="menu.learn.privacyPolicy" defaultMessage="Privacy Policy" />, href: '/photographing.jsp' },
            { name: <FormattedMessage id="menu.learn.termsOfUse" defaultMessage="Terms of Use" />, href: '/photographing.jsp' },
            { name: <FormattedMessage id="menu.learn.learnMore" defaultMessage="Learn more about Wildbook" />, href: 'https://www.wildme.org/#/wildbook' },
        ]
    }, 
    {
        'Submit': [
            { name: <FormattedMessage id="menu.submit.reportSighting" defaultMessage="Report a Sighting" />, href: '/submit.jsp' },
        ]      
    },       
    {
        'Animals': [
            { name: <FormattedMessage id="menu.animals.individualGallery" defaultMessage="Individual Gallery" />, href: '/gallery.jsp' },
            { name: <FormattedMessage id="menu.animals.animalCalendar" defaultMessage="Animal Calendar" />, href: '/xcalendar/calendar.jsp' },
        ]   
    }
];

export { authenticatedMenu, unAuthenticatedMenu };
