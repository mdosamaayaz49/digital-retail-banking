import { useQuery } from "@tanstack/react-query";
import { createContext, useContext } from "react";
import { api, previewAccounts, type Account, type Profile } from "./banking";

export const Context = createContext({
  live: false,
  notify: (_message: string) => {},
});
export const useBank = () => useContext(Context);
export function useAccounts() {
  const { live } = useBank();
  return useQuery({
    queryKey: ["accounts", live],
    queryFn: () =>
      live ? api<Account[]>("/accounts") : Promise.resolve(previewAccounts),
  });
}
export function useProfile() {
  const { live } = useBank();
  return useQuery({
    queryKey: ["profile", live],
    queryFn: () =>
      live
        ? api<Profile>("/customers/me")
        : Promise.resolve({
            display_name: "Amira Rahman",
            email: "amira@example.test",
            phone: "+60 10 000 0001",
            kyc_status: "VERIFIED",
          }),
  });
}
