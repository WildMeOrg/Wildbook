
const getCollaborationNotifications = async () => {
    try {
      const response = await fetch(`/Collaborate?json=1&getNotifications=1`);   
      const data = await response.json();   
      const parser = new DOMParser();
      const doc = parser.parseFromString(data?.content, 'text/html');
      const title = doc.querySelector('h2')?.innerText;

      if(title) {
        const invites = [...doc.querySelectorAll('.collaboration-invite-notification')];        
        return { collaborationTitle: title, collaborationData: invites };        
      } else {
        return { collaborationTitle: '', collaborationData: [] };
      }
    } catch (error) {
      console.error('Error:', error);
      return { collaborationTitle: '', collaborationData: [] }; 
    }
};

export default getCollaborationNotifications;
