const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

import type { CatalogProduct } from "./catalog-data";

export type StorefrontCategory = {
  id: string;
  slug: string;
  name: string;
  displayOrder: number;
  children: StorefrontCategory[];
};

export async function loadStorefrontCategories() {
  const response = await fetch(`${API_BASE}/catalog/categories`, { credentials: "include" });
  if (!response.ok) throw new Error("Не удалось загрузить категории");
  return (await response.json() as {categories: StorefrontCategory[]}).categories;
}

type StorefrontProductSummary = {
  id: string;
  slug: string;
  name: string;
  shortDescription: string;
  priceMinor?: number | null;
  newArrival?: boolean;
  onSale?: boolean;
  salePercent?: number | null;
  featured?: boolean;
  currency: "RUB";
  primaryMedia: { url: string; alt: string };
  publishedAt: string;
  variantOptions: Array<{ type: string; values: Array<{ label: string; colorHex?: string | null }> }>;
};

export async function loadStorefrontProducts(options: { category?: string; query?: string; onlyNew?: boolean } = {}) {
  const query = new URLSearchParams({ page: "0", size: "60", sort: "NEWEST" });
  if (options.category) query.set("category", options.category);
  if (options.query) query.set("q", options.query);
  if (options.onlyNew) query.set("new", "true");
  const response = await fetch(`${API_BASE}/catalog/products?${query}`, { credentials: "include" });
  if (!response.ok) throw new Error("Не удалось загрузить товары");
  const payload = await response.json() as { items: StorefrontProductSummary[] };
  return payload.items.map((item): CatalogProduct => {
    const sizes = item.variantOptions
      .filter((option) => option.type === "SIZE" || option.type === "DIMENSION")
      .flatMap((option) => option.values.map((value) => value.label));
    const colors = item.variantOptions
      .filter((option) => option.type === "COLOR")
      .flatMap((option) => option.values.map((value) => ({ label: value.label, hex: value.colorHex ?? undefined })));
    const originalPrice = (item.priceMinor ?? 0) / 100;
    const salePercent = item.onSale ? item.salePercent ?? 0 : 0;
    const price = salePercent > 0 ? Math.round(originalPrice * (100 - salePercent)) / 100 : originalPrice;
    return {
      id: item.id,
      name: item.name,
      price,
      priceAvailable: (item.priceMinor ?? 0) > 0,
      art: "product",
      colorClass: "product-steel",
      description: item.shortDescription,
      color: colors.map((color) => color.label).join(", ") || "Не указан",
      colors,
      material: "Не указан",
      sizes: sizes.length > 0 ? sizes : ["One size"],
      imageUrl: item.primaryMedia.url,
      isNew: item.newArrival ?? options.onlyNew ?? false,
      originalPrice: salePercent > 0 ? originalPrice : undefined,
      salePercent: salePercent || undefined,
      featured: item.featured ?? false,
    };
  });
}
