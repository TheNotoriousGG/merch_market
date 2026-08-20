const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

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
