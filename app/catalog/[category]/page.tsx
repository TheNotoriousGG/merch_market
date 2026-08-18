"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import StoreHeader from "../../components/StoreHeader";
import { useShop, type ShopProduct } from "../../components/ShopState";
import { CartButtonContent, FavoriteIcon } from "../../components/ShopIcons";

type Product = {
  id: string; name: string; price: number; art: string; colorClass: string;
  description: string; color: string; material: string; sizes: string[]; isNew?: boolean;
};

const toShopProduct = (product: Product): ShopProduct => ({ id: product.id, name: product.name, price: product.price, art: product.art, colorClass: product.colorClass });

const clothes: Product[] = [
  { id:"series-01-tee", name:"Футболка «Серия 01»", price:2990, art:"tee", colorClass:"product-blue", description:"Плотная базовая футболка свободного кроя с мягкой горловиной.", color:"Графит", material:"100% хлопок", sizes:["XS","S","M","L","XL"], isNew:true },
  { id:"free-hoodie", name:"Худи свободного кроя", price:5490, art:"hoodie", colorClass:"product-lilac", description:"Объёмное худи с глубоким капюшоном и спокойной посадкой.", color:"Лавандовый", material:"80% хлопок, 20% полиэстер", sizes:["S","M","L","XL"], isNew:true },
  { id:"straight-pants", name:"Брюки прямого кроя", price:4990, art:"pants", colorClass:"product-sand", description:"Прямые брюки из плотной ткани для собранных повседневных образов.", color:"Графит", material:"65% хлопок, 35% полиэстер", sizes:["S","M","L","XL"] },
  { id:"base-shorts", name:"Шорты «База»", price:3490, art:"shorts", colorClass:"product-steel", description:"Свободные шорты с эластичным поясом и глубокими карманами.", color:"Стальной", material:"100% хлопок", sizes:["S","M","L"] },
  { id:"mono-tee", name:"Футболка Mono", price:2790, art:"tee", colorClass:"product-yellow", description:"Лаконичная футболка с небольшим знаком Amra на груди.", color:"Жёлтый", material:"100% хлопок", sizes:["XS","S","M","L"], isNew:true },
  { id:"north-hoodie", name:"Худи «Север»", price:5990, art:"hoodie", colorClass:"product-blue", description:"Тёплое худи лимитированной серии с плотным капюшоном.", color:"Ледяной", material:"85% хлопок, 15% полиэстер", sizes:["S","M","L"] },
  { id:"relax-shorts", name:"Шорты Relax", price:3290, art:"shorts", colorClass:"product-mint", description:"Мягкие повседневные шорты со свободной посадкой.", color:"Шалфей", material:"92% хлопок, 8% эластан", sizes:["S","M","L","XL"] },
  { id:"soft-pants", name:"Брюки Soft", price:5190, art:"pants", colorClass:"product-lilac", description:"Мягкие брюки с аккуратными стрелками и эластичным поясом.", color:"Лавандовый", material:"70% вискоза, 30% полиэстер", sizes:["S","M","L"] },
];

const accessories: Product[] = [
  { id:"mono-watch", name:"Часы Amra Mono", price:8990, art:"watch", colorClass:"product-yellow", description:"Минималистичные часы с контрастным циферблатом и мягким ремешком.", color:"Графит", material:"Сталь, минеральное стекло", sizes:["One size"], isNew:true },
  { id:"embroidered-cap", name:"Кепка с вышивкой", price:1990, art:"cap", colorClass:"product-coral", description:"Шестипанельная кепка с регулируемой посадкой и вышивкой Amra.", color:"Тёмно-синий", material:"100% хлопок", sizes:["One size"] },
  { id:"daily-tote", name:"Шопер на каждый день", price:2490, art:"tote", colorClass:"product-mint", description:"Лёгкий шопер с внутренним карманом, который складывается сам в себя.", color:"Молочный", material:"100% полиэстер", sizes:["37 × 44 см"] },
  { id:"soft-box", name:"Сумка Soft Box", price:4490, art:"bag", colorClass:"product-pink", description:"Мягкая сумка с широким ремнём и карманом для важных мелочей.", color:"Песочный", material:"Экокожа, текстиль", sizes:["22 × 18 × 8 см"], isNew:true },
  { id:"yellow-cap", name:"Кепка Amra Base", price:2190, art:"cap", colorClass:"product-yellow", description:"Яркая кепка с мягким козырьком и регулируемой застёжкой.", color:"Жёлтый", material:"100% хлопок", sizes:["One size"], isNew:true },
  { id:"mono-shopper", name:"Шопер Mono", price:2290, art:"tote", colorClass:"product-steel", description:"Графичный шопер для ноутбука, документов и покупок.", color:"Графит", material:"100% хлопок", sizes:["39 × 42 см"] },
  { id:"mini-soft", name:"Сумка Mini Soft", price:3690, art:"bag", colorClass:"product-lilac", description:"Небольшая мягкая сумка для телефона, ключей и карт.", color:"Лавандовый", material:"Экокожа", sizes:["18 × 12 × 6 см"] },
  { id:"sport-watch", name:"Часы Amra Sport", price:7490, art:"watch", colorClass:"product-coral", description:"Лёгкие часы с влагозащитой и контрастным ремешком.", color:"Коралловый", material:"Алюминий, силикон", sizes:["One size"] },
];

const bags: Product[] = [
  { ...accessories[3], id:"soft-box-bag" },
  { ...accessories[2], id:"everyday-shopper" },
  { id:"mono-mini", name:"Сумка Mono Mini", price:3990, art:"bag", colorClass:"product-yellow", description:"Компактная сумка для телефона, карт и других необходимых вещей.", color:"Жёлтый", material:"Нейлон", sizes:["18 × 12 × 6 см"] },
  { id:"north-shopper", name:"Шопер «Север»", price:2790, art:"tote", colorClass:"product-blue", description:"Лимитированный шопер из плотной ткани с северной типографикой.", color:"Ледяной", material:"100% хлопок", sizes:["40 × 46 см"], isNew:true },
  { ...accessories[6], id:"mini-soft-bag" },
  { ...accessories[5], id:"mono-shopper-bag" },
  { id:"coral-box", name:"Сумка Coral Box", price:4290, art:"bag", colorClass:"product-coral", description:"Каркасная сумка с мягкими углами и короткой ручкой.", color:"Коралловый", material:"Экокожа, текстиль", sizes:["24 × 17 × 9 см"], isNew:true },
  { id:"yellow-tote", name:"Шопер Yellow Line", price:2590, art:"tote", colorClass:"product-yellow", description:"Прочный шопер с контрастной жёлтой графикой.", color:"Молочный", material:"100% хлопок", sizes:["40 × 45 см"] },
];

const pants: Product[] = [
  { ...clothes[2], id:"straight-fit", isNew:true },
  { ...clothes[3], id:"base-shorts-pants" },
  { id:"relax-pants", name:"Брюки Relax", price:5290, art:"pants", colorClass:"product-mint", description:"Мягкие брюки с защипами и немного зауженным низом.", color:"Шалфей", material:"72% вискоза, 28% полиэстер", sizes:["S","M","L"] },
  { id:"mono-shorts", name:"Шорты Mono", price:3190, art:"shorts", colorClass:"product-lilac", description:"Лаконичные спортивные шорты с минимальной вышивкой.", color:"Лавандовый", material:"80% хлопок, 20% полиэстер", sizes:["S","M","L","XL"] },
  { id:"classic-pants", name:"Брюки Classic", price:5590, art:"pants", colorClass:"product-blue", description:"Классические брюки со стрелками и мягкой средней посадкой.", color:"Тёмно-синий", material:"60% шерсть, 40% вискоза", sizes:["S","M","L","XL"], isNew:true },
  { id:"sport-shorts", name:"Шорты Sport", price:2990, art:"shorts", colorClass:"product-coral", description:"Лёгкие спортивные шорты с внутренним шнурком.", color:"Коралловый", material:"100% полиэстер", sizes:["S","M","L"] },
  { id:"yellow-pants", name:"Брюки Yellow Line", price:4890, art:"pants", colorClass:"product-yellow", description:"Свободные брюки с контрастной строчкой и карманами.", color:"Графит", material:"100% хлопок", sizes:["S","M","L"] },
  { id:"home-shorts", name:"Шорты Home", price:2790, art:"shorts", colorClass:"product-mint", description:"Домашние шорты из мягкого трикотажа без лишних деталей.", color:"Шалфей", material:"95% хлопок, 5% эластан", sizes:["S","M","L","XL"] },
];

const catalog = {
  clothes: { label:"Одежда", sections:["Вся одежда","Футболки и поло","Лонгсливы","Рубашки","Свитшоты и олимпийки","Худи","Брюки и шорты"], products:clothes },
  accessories: { label:"Аксессуары", sections:["Все аксессуары","Картхолдеры","Рюкзаки и сумки","Косметички","Брелоки","Бутылки и кружки","Головные уборы"], products:accessories },
  bags: { label:"Сумки", sections:["Все сумки","Шоперы","Рюкзаки","Сумки через плечо","Чехлы для ноутбука","Органайзеры"], products:bags },
  pants: { label:"Брюки", sections:["Все брюки","Джоггеры","Классические брюки","Повседневные шорты","Спортивные","Домашние"], products:pants },
};

type CatalogKey = keyof typeof catalog;
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
  const { addToCart, toggleFavorite, isFavorite, isInCart } = useShop();
  const singleSize = product.sizes.length === 1;
  const [selectedSize, setSelectedSize] = useState(singleSize ? product.sizes[0] : "");
  const liked = isFavorite(product.id);
  const added = isInCart(product.id);

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
        <div className="product-dialog-actions"><button disabled={!selectedSize} className={`cart-action-button ${added ? "is-added" : ""}`} onClick={() => addToCart(toShopProduct(product))}><CartButtonContent label={added ? "Добавить ещё" : selectedSize ? "В корзину" : "Выберите размер"} /></button><button className={liked ? "liked" : ""} onClick={() => toggleFavorite(toShopProduct(product))} aria-pressed={liked} aria-label="Добавить в избранное"><FavoriteIcon active={liked} /></button></div>
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
