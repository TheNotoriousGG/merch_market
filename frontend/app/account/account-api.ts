import type { CustomerOrderList, CustomerProfile, SaveCustomerAddressRequest } from "../api/generated";
import { cookie as browserCookie, customerApi, orderingApi } from "../api/client";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export type CustomerAccount = { id:string; phone:string; displayName?:string|null };
export type PhoneChallenge = { challengeId:string; phone:string; expiresInSeconds:number; developmentCode?:string|null };

function cookie(name:string) {
  if (typeof document === "undefined") return "";
  return document.cookie.split("; ").find((value)=>value.startsWith(`${name}=`))?.split("=").slice(1).join("=") ?? "";
}

async function request<T>(path:string, init:RequestInit = {}) {
  const method = init.method ?? "GET";
  const headers = new Headers(init.headers);
  if (init.body) headers.set("Content-Type", "application/json");
  if (!/^(GET|HEAD|OPTIONS)$/i.test(method)) {
    if (!cookie("AMRA_CSRF")) await fetch(`${API_BASE}/session`, {credentials:"include"});
    headers.set("X-AMRA-CSRF", decodeURIComponent(cookie("AMRA_CSRF")));
  }
  const response = await fetch(`${API_BASE}${path}`, {...init, headers, credentials:"include"});
  if (!response.ok) {
    const problem = await response.json().catch(()=>null) as {detail?:string;title?:string}|null;
    throw new Error(problem?.detail ?? problem?.title ?? "Не удалось выполнить действие");
  }
  return response.status===204?undefined as T:await response.json() as T;
}

export const loadAccount = () => request<CustomerAccount>("/customer/account");
export const startPhoneAuthentication = (phone:string) => request<PhoneChallenge>("/customer/auth/phone/start", {method:"POST",body:JSON.stringify({phone})});
export const verifyPhone = (challengeId:string, code:string) => request<CustomerAccount>("/customer/auth/phone/verify", {method:"POST",body:JSON.stringify({challengeId,code})});
export const logoutCustomer = () => request<void>("/customer/auth/logout", {method:"POST"});
export const loadCustomerProfile = (): Promise<CustomerProfile> => customerApi.getCustomerProfile();
export const loadCustomerOrders = (): Promise<CustomerOrderList> => orderingApi.listCustomerOrders();
export const saveCustomerAddress = (address: SaveCustomerAddressRequest) => customerApi.createCustomerAddress({
  xAMRACSRF: browserCookie("AMRA_CSRF") || "browser-csrf-token",
  saveCustomerAddressRequest: address,
});
export const removeCustomerAddress = (resourceId: string) => customerApi.deleteCustomerAddress({
  resourceId,
  xAMRACSRF: browserCookie("AMRA_CSRF") || "browser-csrf-token",
});
export const startEmailVerification = (email: string) => customerApi.startEmailVerification({
  xAMRACSRF: browserCookie("AMRA_CSRF") || "browser-csrf-token",
  startEmailVerificationRequest: { email },
});
export const verifyCustomerEmail = (challengeId: string, code: string) => customerApi.verifyCustomerEmail({
  challengeId,
  xAMRACSRF: browserCookie("AMRA_CSRF") || "browser-csrf-token",
  verifyEmailRequest: { code },
});
