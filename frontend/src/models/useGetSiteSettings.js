
import queryKeys from '../constants/queryKeys';
import useFetch from '../hooks/useFetch';

export default function useGetSiteSettings() {

    return useFetch({
        queryKey: queryKeys.me,
        url: '/site-settings',
        // onSuccess,
        queryOptions: { retry: false },
    });
}
