import useFetch from "../../hooks/useFetch";

export default function useGetIndividualEncounters(id) {
  return useFetch({
    queryKey: ["individualEncounters", id],
    url: `/individuals/${id}/encounters`,
    dataAccessor: (result) => result?.data?.encounters ?? [],
    queryOptions: { retry: false },
  });
}
