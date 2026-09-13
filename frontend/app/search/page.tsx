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
  const [loading,setLoading]=useState(Boolean(query)),[error,setError]=useState(""),[attempt,setAttempt]=useState(0);
  useEffect(() => {
    if (!query) { setApiProducts([]); setLoading(false); setError(""); return; }
    let active = true;
    setLoading(true);setError("");
    void loadStorefrontProducts({ query }).then((items) => {if(active)setApiProducts(items)}).catch(() => {if(active)setError("Не удалось выполнить поиск. Проверьте соединение и попробуйте ещё раз.")}).finally(()=>active&&setLoading(false));
    return () => { active = false; };
  }, [query,attempt]);
  const products = useMemo(() => {
    if (!query) return [];
    return apiProducts;
  }, [apiProducts, query]);

  return <main className="catalog-page">
    <StoreHeader />
    <div className="catalog-page-body">
      <div className="catalog-breadcrumbs"><a href="/">Главная</a><span>·</span><span>Поиск</span></div>
      <div className="catalog-title-row"><div><span className="section-kicker">Каталог Amra</span><h1>{query ? `«${query}»` : "Поиск"}</h1></div>{!loading&&!error&&<span>{products.length} {resultWord(products.length)}</span>}</div>
      {loading?<section className="utility-empty" aria-live="polite"><h2>Ищем товары…</h2><p>Проверяем каталог по вашему запросу.</p></section>:error?<section className="utility-empty" role="alert"><h2>Поиск временно недоступен</h2><p>{error}</p><button className="utility-primary-link" onClick={()=>setAttempt(value=>value+1)}>Попробовать снова<span>›</span></button></section>:products.length > 0 ? <section className="catalog-product-grid" aria-label={`Результаты поиска: ${query}`}>
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

function resultWord(value:number){const mod100=value%100,mod10=value%10;return mod10===1&&mod100!==11?"товар найден":mod10>=2&&mod10<=4&&(mod100<12||mod100>14)?"товара найдено":"товаров найдено"}
