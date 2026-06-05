import { getIndividualQueryKey } from "../../constants/queryKeys";
import useFetch from "../../hooks/useFetch";

export default function useGetIndividual(id) {
  return useFetch({
    queryKey: getIndividualQueryKey(id),
    url: `/individuals/${id}`,
    dataAccessor: (result) => result?.data,
    queryOptions: { retry: false },
  });
}
