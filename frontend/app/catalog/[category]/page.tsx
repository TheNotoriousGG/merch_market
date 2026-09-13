"use client";

import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import StoreHeader from "../../components/StoreHeader";
import CatalogProductCard from "../components/CatalogProductCard";
import CatalogProductDialog from "../components/CatalogProductDialog";
import { type CatalogProduct as Product } from "../catalog-data";
import { loadStorefrontCategories, loadStorefrontProducts, type StorefrontCategory } from "../catalog-api";

const PAGE_SIZE = 5;

export default function CatalogPage() {
  const params = useParams<{ category: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const key = params.category;
  const [category, setCategory] = useState<StorefrontCategory | null>(null);
  const [categoriesLoaded, setCategoriesLoaded] = useState(false);
  useEffect(() => { void loadStorefrontCategories().then((items) => setCategory(items.find((item) => item.slug === key) ?? null)).finally(() => setCategoriesLoaded(true)); }, [key]);
  const sectionSlug = searchParams.get("section");
  const selectedSection = category?.children.find((child) => child.slug === sectionSlug || child.name === sectionSlug) ?? null;
  const current = { label: category?.name ?? "Каталог", sections: category ? [{slug:"",name:category.name}, ...category.children.map((child) => ({slug:child.slug,name:child.name}))] : [] };
  const title = selectedSection?.name ?? current.label;
  const requestedCategory = selectedSection?.slug ?? key;
  const [sort, setSort] = useState("popular");
  const [filterOpen, setFilterOpen] = useState(false);
  const [newOnly, setNewOnly] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);
  const [apiProducts, setApiProducts] = useState<Product[]>([]);
  const [productsLoading, setProductsLoading] = useState(true);
  const [productsError,setProductsError]=useState(""),[attempt,setAttempt]=useState(0);
  useEffect(() => {
    let active = true;
    setProductsLoading(true);setProductsError("");
    void loadStorefrontProducts({ category: requestedCategory })
      .then((items) => active && setApiProducts(items))
      .catch(() => active && setProductsError("Не удалось загрузить товары. Проверьте соединение и попробуйте ещё раз."))
      .finally(() => active && setProductsLoading(false));
    return () => { active = false; };
  }, [requestedCategory,attempt]);

  const products = useMemo(() => {
    const result = newOnly ? apiProducts.filter((product) => product.isNew) : [...apiProducts];
    if (sort === "price") result.sort((a, b) => a.price - b.price);
    if (sort === "new") result.sort((a, b) => Number(Boolean(b.isNew)) - Number(Boolean(a.isNew)));
    return result;
  }, [apiProducts, newOnly, sort]);
  const totalPages = Math.max(1, Math.ceil(products.length / PAGE_SIZE));
  const pageFromUrl = Number.parseInt(searchParams.get("page") || "1", 10);
  const currentPage = Math.min(Math.max(Number.isFinite(pageFromUrl) ? pageFromUrl : 1, 1), totalPages);
  const visibleProducts = products.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  const updatePage = (nextPage: number, scrollToGrid = true) => {
    const query = new URLSearchParams(searchParams.toString());
    if (nextPage <= 1) query.delete("page"); else query.set("page", String(nextPage));
    query.delete("view");
    const suffix = query.toString();
    router.replace(`/catalog/${key}${suffix ? `?${suffix}` : ""}`, { scroll: false });
    if (scrollToGrid) window.requestAnimationFrame(() => document.querySelector(".catalog-toolbar")?.scrollIntoView({ behavior:"smooth", block:"start" }));
  };

  const navigateSection = (slug:string) => {
    if (slug === (selectedSection?.slug ?? "")) return;
    setProductsLoading(true);
    router.push(slug ? `/catalog/${key}?section=${slug}` : `/catalog/${key}`, { scroll:false });
  };

  return <main className="catalog-page">
    <StoreHeader />
    <div className="catalog-page-body">
      <div className="catalog-breadcrumbs"><a href="/">Главная</a><span>·</span><span>{current.label}</span></div>
      <div className="catalog-title-row"><div><span className="section-kicker">Каталог Amra</span><h1>{categoriesLoaded&&!category?"Раздел не найден":title}</h1></div>{!productsLoading&&!productsError&&<span>{products.length} {productWord(products.length)}</span>}</div>
      <nav className="catalog-chips" aria-label={`Подкатегории: ${current.label}`}>
        {category&&current.sections.map((item) => <button type="button" className={item.slug === (selectedSection?.slug ?? "") ? "active" : ""} onClick={()=>navigateSection(item.slug)} key={item.slug||key}>{item.name}</button>)}
      </nav>
      <div className="catalog-toolbar">
        <button aria-expanded={filterOpen} onClick={() => setFilterOpen((value) => !value)}>Фильтры <span>{filterOpen ? "−" : "+"}</span></button>
        <label>Сортировка <select value={sort} onChange={(event) => { setSort(event.target.value); updatePage(1, false); }}><option value="popular">По популярности</option><option value="new">Сначала новые</option><option value="price">Сначала дешевле</option></select></label>
      </div>
      {filterOpen && <aside className="catalog-filter-panel"><label><input type="checkbox" checked={newOnly} onChange={(event) => { setNewOnly(event.target.checked); updatePage(1, false); }} /> Только новинки</label><button onClick={() => { setNewOnly(false); updatePage(1, false); }}>Сбросить</button></aside>}
      {productsError?<section className="utility-empty" role="alert"><h2>Каталог временно недоступен</h2><p>{productsError}</p><button className="utility-primary-link" onClick={()=>setAttempt(value=>value+1)}>Попробовать снова<span>›</span></button></section>:<section className={`catalog-product-grid ${productsLoading?"is-loading":""}`} aria-busy={productsLoading} aria-label={`Товары: ${title}`}>
        {visibleProducts.map((product) => <CatalogProductCard product={product} onOpen={() => setSelectedProduct(product)} key={product.id} />)}
      </section>}
      {productsLoading&&<p className="catalog-loading" role="status">Загружаем подборку…</p>}
      {!productsLoading&&products.length === 0 && <p className="catalog-empty">В этой подборке пока нет товаров.</p>}
      {!productsLoading&&products.length > 0 && totalPages > 1 && <nav className="catalog-pagination" aria-label="Страницы каталога">
        <button className="pagination-arrow pagination-arrow-prev" onClick={() => updatePage(Math.max(1, currentPage - 1))} disabled={currentPage === 1} aria-label="Предыдущая страница"><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m12.5 4.5-5.5 5.5 5.5 5.5" /></svg></button>
        {Array.from({ length: totalPages }, (_, index) => index + 1).map((page) => <button className={page === currentPage ? "active" : ""} onClick={() => updatePage(page)} aria-current={page === currentPage ? "page" : undefined} key={page}>{page}</button>)}
        <button className="pagination-arrow pagination-arrow-next" onClick={() => updatePage(Math.min(totalPages, currentPage + 1))} disabled={currentPage === totalPages} aria-label="Следующая страница"><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m7.5 4.5 5.5 5.5-5.5 5.5" /></svg></button>
      </nav>}
    </div>
    {selectedProduct && <CatalogProductDialog product={selectedProduct} onClose={() => setSelectedProduct(null)} key={selectedProduct.id} />}
  </main>;
}

function productWord(value:number){const mod100=value%100,mod10=value%10;return mod10===1&&mod100!==11?"товар":mod10>=2&&mod10<=4&&(mod100<12||mod100>14)?"товара":"товаров"}
