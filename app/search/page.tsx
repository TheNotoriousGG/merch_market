"use client";

import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import StoreHeader from "../components/StoreHeader";
import { type CatalogProduct } from "../catalog/catalog-data";
import CatalogProductCard from "../catalog/components/CatalogProductCard";
import CatalogProductDialog from "../catalog/components/CatalogProductDialog";
import { loadStorefrontProducts } from "../catalog/catalog-api";

export default function SearchPage() {
  const searchParams = useSearchParams();
  const query = (searchParams.get("q") ?? "").trim();
  const [selectedProduct, setSelectedProduct] = useState<CatalogProduct | null>(null);
  const [apiProducts, setApiProducts] = useState<CatalogProduct[]>([]);
  useEffect(() => {
    if (!query) { setApiProducts([]); return; }
    let active = true;
    void loadStorefrontProducts({ query }).then((items) => active && setApiProducts(items)).catch(() => active && setApiProducts([]));
    return () => { active = false; };
  }, [query]);
  const products = useMemo(() => {
    if (!query) return [];
    return apiProducts;
  }, [apiProducts, query]);

  return <main className="catalog-page">
    <StoreHeader />
    <div className="catalog-page-body">
      <div className="catalog-breadcrumbs"><a href="/">Главная</a><span>·</span><span>Поиск</span></div>
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
