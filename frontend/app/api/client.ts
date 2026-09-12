import {
  CatalogApi,
  Configuration,
  CustomerApi,
  StorefrontApi,
} from "./generated";

export const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export function cookie(name: string) {
  if (typeof document === "undefined") return "";
  return document.cookie
    .split("; ")
    .find((value) => value.startsWith(`${name}=`))
    ?.split("=")
    .slice(1)
    .join("=") ?? "";
}

const configuration = new Configuration({
  basePath: API_BASE,
  credentials: "include",
  fetchApi: async (input, init = {}) => {
    const headers = new Headers(init.headers);
    const method = init.method ?? "GET";
    if (!/^(GET|HEAD|OPTIONS)$/i.test(method)) {
      headers.set("X-AMRA-CSRF", decodeURIComponent(cookie("AMRA_CSRF")));
    }
    return fetch(input, { ...init, headers, credentials: "include" });
  },
});

export const catalogApi = new CatalogApi(configuration);
export const customerApi = new CustomerApi(configuration);
export const storefrontApi = new StorefrontApi(configuration);
