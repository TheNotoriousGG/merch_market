import type { ShopProduct } from "../components/ShopState";

export type CatalogProduct = ShopProduct & {
  description: string;
  color: string;
  colors: Array<{ label: string; hex?: string }>;
  material: string;
  sizes: string[];
  isNew?: boolean;
  imageUrl?: string;
  priceAvailable?: boolean;
};

export const toShopProduct = (product: CatalogProduct): ShopProduct => ({
  id: product.id,
  name: product.name,
  price: product.price,
  art: product.art,
  colorClass: product.colorClass,
  imageUrl: product.imageUrl,
});
