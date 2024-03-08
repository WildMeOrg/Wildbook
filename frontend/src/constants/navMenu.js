const menu = [
    {'Submit': [
        {name: 'Report a Sighting', href: '/submit.jsp'},
        {name: 'Bulk Import', href: '/import/instructions.jsp'},
    ]},
    {'Learn': [
        {name: 'About Wildbook', href: '/overview.jsp'},
        {name: 'Contact Us', href: '/photographing.jsp'},
        {name: 'Citing Wildbook', href: '/citing.jsp'},
        {name: 'How to photograph', href: '/photographing.jsp'},
        {name: 'Privacy Policy', href: '/photographing.jsp'},
        {name: 'Terms of Use', href: '/photographing.jsp'},
        {name: 'Learn more about wildbook', href: 'https://www.wildme.org/#/wildbook'},
    ]},
    {'My Data': [
        {name: 'View my encounters', href: 'encounters/searchResults.jsp?username=tomcat'},
        {name: 'View my individuals', href: '/import/instructions.jsp'},
        {name: 'View my sightings', href: '/import/instructions.jsp'},
        {name: 'View my bulk imports', href: '/imports.jsp'},
        {name: 'View my projects', href: '/projects/projectList.jsp'},
    ]},
    {'Search': [        
        {name: 'Encounters', href: '/encounters/encounterSearch.jsp'},
        {name: 'Individuals', href: '/individualSearch.jsp'},
        {name: 'Sightings', href: '/occurrenceSearch.jsp'},
    ]},
    {'Animals': [
        {name: 'Individual Gallery', href: '/gallery.jsp'},
        {name: 'Animal Calendar', href: '/import/instructions.jsp'},
    ]},   
    {'Administers': [
        {name: 'My Account', href: '/myAccount.jsp'},
        {name: 'Manage My Accounts', href: '/myUsers.jsp'},
        {name: 'User Management', href: '/appadmin/users.jsp?context=context0'},
        {name: 'Library Administration', href: '/appadmin/admin.jsp'},
        {name: 'Logs', href: '/appadmin/logs.jsp'},
        {name: 'Photo Keywords', href: '/appadmin/kwAdmin.jsp'},
        {name: 'Software Documentation', href: 'https://docs.wildme.org/product-docs/en/wildbook/introduction/'},
        {name: 'Data Integrity', href: 'appadmin/dataIntegrity.jsp'},
    ]}, 

    // {'Sightings': [
    //     {name: 'Search', href: '/occurrenceSearch.jsp'},
    //     {name: 'View All', href: '/occurrenceSearchResults.jsp'},
    // ]},

    // {'Individuals': [        
    //     {name: 'Gallery', href: '/gallery.jsp'},
    //     {name: 'View All', href: '/individualSearchResults.jsp'},
    // ]},
    
    // {'Encounters': [
    //     {name: 'Search', href: '/encounters/encounterSearch.jsp'},
    // ]},
    
]


export default menu;