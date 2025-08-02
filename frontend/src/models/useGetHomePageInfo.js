
import queryKeys from '../constants/queryKeys';
import useFetch from '../hooks/useFetch';

export default function useGetSiteSettings() {

    return useFetch({
        queryKey: queryKeys.homePageInfo,
        url: '/home',
        // onSuccess,
        queryOptions: { retry: false },
    });
}
