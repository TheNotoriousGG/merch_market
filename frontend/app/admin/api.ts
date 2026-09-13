import type {
  AdminCategory,
  AdminMedia,
  AdminProduct,
  AdminProductPage,
  AdminProductSummary,
  AdminStorefrontBanner,
  AdminStorefrontBannerList,
  AdminVariant,
  CatalogAttributeValue,
  InventoryBalance,
  InventoryWarehouseOverview,
  InventoryStockItem,
  InventoryMovementView,
  ProductMerchandising,
} from "../api/generated";
import { API_BASE } from "../api/client";

export { API_BASE };
export type Category = AdminCategory;
export type ProductSummary = AdminProductSummary;
export type VariantAttribute = CatalogAttributeValue;
export type Variant = AdminVariant;
export type ProductMedia = AdminMedia;
export type { ProductMerchandising };
export type Product = AdminProduct;
export type ProductPage = AdminProductPage;
export type Balance = InventoryBalance;
export type WarehouseOverview = InventoryWarehouseOverview;
export type StockItem = InventoryStockItem;
export type StockMovement = InventoryMovementView;
export type StorefrontBanner = AdminStorefrontBanner;
export type StorefrontBannerList = AdminStorefrontBannerList;

function cookie(name:string) { if (typeof document === "undefined") return ""; return document.cookie.split("; ").find(v=>v.startsWith(`${name}=`))?.split("=").slice(1).join("=") ?? ""; }
export async function api<T>(path:string, init:RequestInit = {}):Promise<{data:T;etag:string|null}> {
  const method = init.method ?? "GET";
  const headers = new Headers(init.headers);
  if (init.body) headers.set("Content-Type","application/json");
  if (!/^(GET|HEAD|OPTIONS)$/i.test(method)) headers.set("X-AMRA-CSRF", decodeURIComponent(cookie("AMRA_CSRF")));
  const response = await fetch(`${API_BASE}${path}`, {...init, headers, credentials:"include"});
  if (!response.ok) {
    if (response.status === 401 && typeof window !== "undefined") window.dispatchEvent(new Event("amra:unauthorized"));
    const problem = await response.json().catch(()=>null) as {detail?:string;title?:string}|null;
    const raw = problem?.detail ?? problem?.title ?? `Не удалось выполнить действие (${response.status})`;
    const friendly = raw
      .replace(/primary media/gi, "главной фотографии")
      .replace(/active variant/gi, "доступного размера")
      .replace(/primary category/gi, "категории")
      .replace(/namespace conflicts?/gi, "конфликтов адреса")
      .replace(/slug/gi, "адреса страницы")
      .replace(/sku/gi, "артикула");
    const localized = friendly.replace(
      /Choose another (?:primary image|главной фотографии) before deleting the current (?:primary image|главной фотографии)/i,
      "Сначала назначьте другую фотографию главной, затем удалите текущую.",
    );
    throw new Error(localized);
  }
  const data = response.status === 204 ? undefined as T : await response.json() as T;
  return {data, etag:response.headers.get("ETag")};
}
export const commandHeaders = (etag?:string|null) => ({...(etag?{"If-Match":etag}:{}), "Idempotency-Key":crypto.randomUUID()});
