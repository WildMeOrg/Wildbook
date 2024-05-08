import axios from 'axios';

const getMergeNotifications = async () => {
    try {
        const response = await axios.post('/UserGetNotifications', {}, {
            headers: {
                'Content-Type': 'application/json',
            },
        });
        const data = response.data;
        if (data.notifications) {
            return data.notifications;        }
        return[];
    } catch (err) {        
    }

    return [];
};

export default getMergeNotifications;