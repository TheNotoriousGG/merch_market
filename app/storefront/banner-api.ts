import type { StorefrontBanner as PublicBanner } from "../api/generated";
import { storefrontApi } from "../api/client";

export type { PublicBanner };

export async function loadStorefrontBanners(): Promise<PublicBanner[]> {
  return (await storefrontApi.listStorefrontBanners()).items;
}
