const menu = [
    {'Learn': [
        {name: 'About Wildbook', href: '/overview.jsp'},
        {name: 'Citing Wildbook', href: '/citing.jsp'},
        {name: 'Wildbook for Birds', href: '/about'},
        {name: 'Wildbook for Sharks', href: '/about'},
    ]},
    {'Submit': [
        {name: 'Report a Sighting', href: '/submit.jsp'},
        {name: 'Bulk Import', href: '/import/instructions.jsp'},
    ]},
    {'Individuals': [
        {name: 'Search', href: '/individuals'},
        {name: 'Add Individual', href: '/individuals'},
        {name: 'Bulk Import', href: '/individuals'},
    ]},
    {'Sightings': [
        {name: 'Search', href: '/sightings'},
        {name: 'Add Sighting', href: '/sightings'},
        {name: 'Bulk Import', href: '/sightings'},
    ]},
    {'Encounters': [
        {name: 'Search', href: '/encounters'},
        {name: 'Add Encounter', href: '/encounters'},
        {name: 'Bulk Import', href: '/encounters'},
    ]},
    {'Administers': [
        {name: 'Search', href: '/administers'},
        {name: 'Add Administer', href: '/administers'},
        {name: 'Bulk Import', href: '/administers'},
    ]},
]


export default menu;