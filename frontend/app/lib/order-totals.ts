export const DELIVERY_PRICE = 490;
export const FREE_DELIVERY_FROM = 5000;

export function deliveryPrice(itemsTotal: number) {
  return itemsTotal === 0 || itemsTotal >= FREE_DELIVERY_FROM ? 0 : DELIVERY_PRICE;
}
