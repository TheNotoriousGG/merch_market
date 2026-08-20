export const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export type Category = { id:string; parentId?:string|null; slug:string; name:string; displayOrder:number; status:"ACTIVE"|"HIDDEN"; version:number; updatedAt:string };
export type ProductSummary = { id:string; slug:string; name:string; status:"DRAFT"|"ACTIVE"|"ARCHIVED"; primaryCategoryId:string; variantCount:number; mediaCount:number; hasPrimaryMedia:boolean; version:number; updatedAt:string };
export type Product = { id:string; slug:string; name:string; shortDescription:string; description:string; status:"DRAFT"|"ACTIVE"|"ARCHIVED"; primaryCategoryId:string; categoryIds:string[]; collectionIds:string[]; characteristics:unknown[]; version:number; updatedAt:string };
export type ProductPage = { items:ProductSummary[]; page:{page:number;size:number;totalElements:number;totalPages:number} };
export type Balance = { warehouseCode:string; variantId:string; onHand:number; reserved:number; available:number; version:number; updatedAt:string };

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
    throw new Error(problem?.detail ?? problem?.title ?? `Backend вернул ${response.status}`);
  }
  const data = response.status === 204 ? undefined as T : await response.json() as T;
  return {data, etag:response.headers.get("ETag")};
}
export const commandHeaders = (etag?:string|null) => ({...(etag?{"If-Match":etag}:{}), "Idempotency-Key":crypto.randomUUID()});
