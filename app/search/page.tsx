"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useMemo, useState } from "react";
import StoreHeader from "../components/StoreHeader";
import { catalog, type CatalogProduct } from "../catalog/catalog-data";
import CatalogProductCard from "../catalog/components/CatalogProductCard";
import CatalogProductDialog from "../catalog/components/CatalogProductDialog";

export default function SearchPage() {
  const searchParams = useSearchParams();
  const query = (searchParams.get("q") ?? "").trim();
  const [selectedProduct, setSelectedProduct] = useState<CatalogProduct | null>(null);
  const products = useMemo(() => {
    if (!query) return [];
    const normalized = query.toLocaleLowerCase("ru");
    const seen = new Set<string>();
    return Object.values(catalog).flatMap((category) => category.products).filter((product) => {
      if (seen.has(product.id)) return false;
      seen.add(product.id);
      return `${product.name} ${product.description} ${product.color} ${product.material}`.toLocaleLowerCase("ru").includes(normalized);
    });
  }, [query]);

  return <main className="catalog-page">
    <StoreHeader />
    <div className="catalog-page-body">
      <div className="catalog-breadcrumbs"><Link href="/">Главная</Link><span>·</span><span>Поиск</span></div>
      <div className="catalog-title-row"><div><span className="section-kicker">Каталог Amra</span><h1>{query ? `«${query}»` : "Поиск"}</h1></div><span>{products.length} найдено</span></div>
      {products.length > 0 ? <section className="catalog-product-grid" aria-label={`Результаты поиска: ${query}`}>
        {products.map((product) => <CatalogProductCard product={product} onOpen={() => setSelectedProduct(product)} key={product.id} />)}
      </section> : <section className="utility-empty">
        <span>{query ? "Ничего не найдено" : "Введите название товара"}</span>
        <h2>{query ? "Попробуйте изменить запрос" : "Поиск по каталогу"}</h2>
        <p>{query ? "Можно искать по названию, цвету или материалу." : "Нажмите значок поиска в шапке."}</p>
      </section>}
    </div>
    {selectedProduct && <CatalogProductDialog product={selectedProduct} onClose={() => setSelectedProduct(null)} key={selectedProduct.id} />}
  </main>;
}
