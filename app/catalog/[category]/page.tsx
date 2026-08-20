"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import StoreHeader from "../../components/StoreHeader";
import { useShop } from "../../components/ShopState";
import { CartButtonContent, FavoriteIcon } from "../../components/ShopIcons";
import { catalog, toShopProduct, type CatalogKey, type CatalogProduct as Product } from "../catalog-data";

const PAGE_SIZE = 5;

function ProductCatalogCard({ product, onOpen }: { product: Product; onOpen: () => void }) {
  const { toggleFavorite, isFavorite } = useShop();
  const liked = isFavorite(product.id);

  return <article className="catalog-product-card">
    <button className="catalog-card-open" onClick={onOpen} aria-label={`Открыть ${product.name}`}>
      <span className={`product-image ${product.colorClass}`}>
        <span className="new-badge">{product.isNew ? "NEW" : "AMRA"}</span>
        <span className={`product-object product-object-${product.art}`} aria-hidden="true" />
      </span>
      <span className="catalog-card-compact">
        <span className="product-meta"><h3>{product.name}</h3><strong>{product.price.toLocaleString("ru-RU")} ₽</strong></span>
        <span className="catalog-card-color">{product.color}<i aria-hidden="true" /></span>
      </span>
    </button>
    <button className={`product-heart ${liked ? "liked" : ""}`} onClick={() => toggleFavorite(toShopProduct(product))} aria-pressed={liked} aria-label={`${liked ? "Убрать" : "Добавить"} ${product.name} ${liked ? "из избранного" : "в избранное"}`}><FavoriteIcon active={liked} /></button>
  </article>;
}

function ProductDetailDialog({ product, onClose }: { product: Product; onClose: () => void }) {
  const router = useRouter();
  const { cart, addToCart, toggleFavorite, isFavorite } = useShop();
  const singleSize = product.sizes.length === 1;
  const [selectedSize, setSelectedSize] = useState(singleSize ? product.sizes[0] : "");
  const liked = isFavorite(product.id);
  const cartQuantity = cart.find((item) => item.id === product.id)?.quantity ?? 0;

  const handleCartAction = () => {
    if (cartQuantity > 0) {
      router.push("/cart");
      return;
    }
    addToCart(toShopProduct(product));
  };

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => event.key === "Escape" && onClose();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", closeOnEscape);
    return () => { document.body.style.overflow = previousOverflow; window.removeEventListener("keydown", closeOnEscape); };
  }, [onClose]);

  return <div className="product-dialog-backdrop" role="button" tabIndex={-1} aria-label="Закрыть карточку" onKeyDown={(event) => event.key === "Escape" && onClose()} onClick={(event) => event.target === event.currentTarget && onClose()}>
    <section className="product-dialog" role="dialog" aria-modal="true" aria-labelledby="product-dialog-title">
      <button className="product-dialog-close" onClick={onClose} aria-label="Закрыть карточку">×</button>
      <div className={`product-dialog-visual ${product.colorClass}`}><span className="new-badge">{product.isNew ? "NEW" : "AMRA"}</span><span className={`product-object product-object-${product.art}`} aria-hidden="true" /></div>
      <div className="product-dialog-copy">
        <span className="section-kicker">Амра Шоп · {product.color}</span>
        <h2 id="product-dialog-title">{product.name}</h2>
        <strong className="product-dialog-price">{product.price.toLocaleString("ru-RU")} ₽</strong>
        <p>{product.description}</p>
        <dl><div><dt>Цвет</dt><dd>{product.color}</dd></div><div><dt>Состав</dt><dd>{product.material}</dd></div><div><dt>Посадка</dt><dd>{singleSize ? "Универсальная" : "Свободная"}</dd></div></dl>
        <div className="product-dialog-sizes"><span>{singleSize ? "Размер" : "Выберите размер"}</span><div>{product.sizes.map((size) => <button className={selectedSize === size ? "active" : ""} onClick={() => setSelectedSize(size)} aria-pressed={selectedSize === size} key={size}>{size}</button>)}</div></div>
        <div className="product-dialog-actions"><button disabled={!selectedSize && cartQuantity === 0} className={`cart-action-button ${cartQuantity > 0 ? "is-added" : ""}`} onClick={handleCartAction}><CartButtonContent label={cartQuantity > 0 ? `В корзине · ${cartQuantity}` : selectedSize ? "В корзину" : "Выберите размер"} /></button><button className={liked ? "liked" : ""} onClick={() => toggleFavorite(toShopProduct(product))} aria-pressed={liked} aria-label="Добавить в избранное"><FavoriteIcon active={liked} /></button></div>
        <small>Бесплатная доставка от 5 000 ₽ · Возврат в течение 14 дней</small>
      </div>
    </section>
  </div>;
}

export default function CatalogPage() {
  const params = useParams<{ category: string }>();
  const router = useRouter();
  const searchParams = useSearchParams();
  const key: CatalogKey = params.category in catalog ? params.category as CatalogKey : "clothes";
  const current = catalog[key];
  const title = searchParams.get("section") || current.label;
  const [sort, setSort] = useState("popular");
  const [filterOpen, setFilterOpen] = useState(false);
  const [newOnly, setNewOnly] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState<Product | null>(null);

  const products = useMemo(() => {
    const result = newOnly ? current.products.filter((product) => product.isNew) : [...current.products];
    if (sort === "price") result.sort((a, b) => a.price - b.price);
    if (sort === "new") result.sort((a, b) => Number(Boolean(b.isNew)) - Number(Boolean(a.isNew)));
    return result;
  }, [current.products, newOnly, sort]);
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

  return <main className="catalog-page">
    <StoreHeader />
    <div className="catalog-page-body">
      <div className="catalog-breadcrumbs"><Link href="/">Главная</Link><span>·</span><span>{current.label}</span></div>
      <div className="catalog-title-row"><div><span className="section-kicker">Каталог Amra</span><h1>{title}</h1></div><span>{products.length} товара</span></div>
      <nav className="catalog-chips" aria-label={`Подкатегории: ${current.label}`}>
        {current.sections.map((item) => <Link className={item === title ? "active" : ""} href={`/catalog/${key}?section=${encodeURIComponent(item)}`} key={item}>{item}</Link>)}
      </nav>
      <div className="catalog-toolbar">
        <button aria-expanded={filterOpen} onClick={() => setFilterOpen((value) => !value)}>Фильтры <span>{filterOpen ? "−" : "+"}</span></button>
        <label>Сортировка <select value={sort} onChange={(event) => { setSort(event.target.value); updatePage(1, false); }}><option value="popular">По популярности</option><option value="new">Сначала новые</option><option value="price">Сначала дешевле</option></select></label>
      </div>
      {filterOpen && <aside className="catalog-filter-panel"><label><input type="checkbox" checked={newOnly} onChange={(event) => { setNewOnly(event.target.checked); updatePage(1, false); }} /> Только новинки</label><button onClick={() => { setNewOnly(false); updatePage(1, false); }}>Сбросить</button></aside>}
      <section className="catalog-product-grid" aria-label={`Товары: ${title}`}>
        {visibleProducts.map((product) => <ProductCatalogCard product={product} onOpen={() => setSelectedProduct(product)} key={product.id} />)}
      </section>
      {products.length === 0 && <p className="catalog-empty">В этой подборке пока нет товаров.</p>}
      {products.length > 0 && totalPages > 1 && <nav className="catalog-pagination" aria-label="Страницы каталога">
        <button className="pagination-arrow pagination-arrow-prev" onClick={() => updatePage(Math.max(1, currentPage - 1))} disabled={currentPage === 1} aria-label="Предыдущая страница"><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m12.5 4.5-5.5 5.5 5.5 5.5" /></svg></button>
        {Array.from({ length: totalPages }, (_, index) => index + 1).map((page) => <button className={page === currentPage ? "active" : ""} onClick={() => updatePage(page)} aria-current={page === currentPage ? "page" : undefined} key={page}>{page}</button>)}
        <button className="pagination-arrow pagination-arrow-next" onClick={() => updatePage(Math.min(totalPages, currentPage + 1))} disabled={currentPage === totalPages} aria-label="Следующая страница"><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m7.5 4.5 5.5 5.5-5.5 5.5" /></svg></button>
      </nav>}
    </div>
    {selectedProduct && <ProductDetailDialog product={selectedProduct} onClose={() => setSelectedProduct(null)} key={selectedProduct.id} />}
  </main>;
}
