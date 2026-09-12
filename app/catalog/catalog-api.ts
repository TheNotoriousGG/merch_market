import type { CatalogProduct } from "./catalog-data";
import type { CatalogCategoryNode as StorefrontCategory } from "../api/generated";
import { catalogApi } from "../api/client";

export type { StorefrontCategory };

export async function loadStorefrontCategories() {
  return (await catalogApi.getCatalogCategories()).categories;
}

export async function loadStorefrontProducts(options: { category?: string; query?: string; onlyNew?: boolean } = {}) {
  const payload = await catalogApi.getCatalogProducts({
    page: 0,
    size: 60,
    sort: "NEWEST",
    category: options.category,
    q: options.query,
    _new: options.onlyNew,
  });
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
      slug: item.slug,
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
