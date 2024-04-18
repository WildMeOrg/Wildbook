import axios from 'axios';

export default async function changeIndividualMergeState(action, mergeId) {

	let json = {};
	json['mergeId'] = mergeId;
	json['action'] = action;

	console.log("Trying to change individual merge state on mergeId "+mergeId+" to "+action+".");

    const response = await axios.post('/ScheduledIndividualMergeUpdate', json, {
        headers: {
            'Content-Type': 'application/json'
        }
    });
    
    console.log('changeIndividualMergeState result:', response.json());

    return response.json();
	
}