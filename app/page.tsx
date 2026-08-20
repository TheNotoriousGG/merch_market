"use client";

import { useEffect, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import StoreHeader from "./components/StoreHeader";
import { ShopProduct, useShop } from "./components/ShopState";
import ShopProductDialog from "./components/ShopProductDialog";
import { CartButtonContent, FavoriteIcon } from "./components/ShopIcons";
import { loadStorefrontCategories } from "./catalog/catalog-api";

type MenuKey = string;

type MenuItem = {
  id: MenuKey; label: string; icon: string;
  columns: Array<{ title: string; links: string[] }>;
  feature?: { eyebrow: string; title: string; color: string };
};

const fallbackMenu: MenuItem[] = [
  { id: "clothes", label: "Одежда", icon: "shirt", columns: [
    { title: "Основное", links: ["Вся одежда", "Футболки и поло", "Лонгсливы", "Рубашки"] },
    { title: "Тёплый слой", links: ["Свитшоты и олимпийки", "Толстовки", "Худи"] },
    { title: "Низ и костюмы", links: ["Блейзеры и пиджаки", "Брюки и шорты", "Носки"] },
  ], feature: { eyebrow: "База", title: "Комфорт на каждый день", color: "#bde9dc" } },
  { id: "accessories", label: "Аксессуары", icon: "watch", columns: [
    { title: "С собой", links: ["Все аксессуары", "Картхолдеры", "Рюкзаки и сумки", "Косметички"] },
    { title: "Для деталей", links: ["Брелоки", "Бутылки и кружки", "Головные уборы", "Зонты"] },
  ], feature: { eyebrow: "2 = 1", title: "Брелоки с платёжным чипом", color: "#c8d8ff" } },
  { id: "bags", label: "Сумки", icon: "bag", columns: [
    { title: "Сумки", links: ["Все сумки", "Шоперы", "Рюкзаки", "Сумки через плечо"] },
    { title: "Для техники", links: ["Чехлы для ноутбука", "Органайзеры", "Косметички"] },
  ], feature: { eyebrow: "Новинка", title: "Вместится всё важное", color: "#f4c9ce" } },
  { id: "pants", label: "Брюки", icon: "pants", columns: [
    { title: "Брюки", links: ["Все брюки", "Джоггеры", "Классические брюки"] },
    { title: "Шорты", links: ["Повседневные", "Спортивные", "Домашние"] },
  ], feature: { eyebrow: "Новая база", title: "Свободный крой на каждый день", color: "#fee45a" } },
];

const products: Array<{
  id: number; name: string; price: string; category: MenuKey;
  className: string; art: string; colors: string[];
}> = [
  { id: 1, name: "Футболка «Серия 01»", price: "2 990 ₽", category: "clothes", className: "product-blue", art: "tee", colors: ["#202226", "#f1efe8", "#9ebbdc"] },
  { id: 2, name: "Худи свободного кроя", price: "5 490 ₽", category: "clothes", className: "product-lilac", art: "hoodie", colors: ["#655d7d", "#d7d0e9"] },
  { id: 3, name: "Часы Amra Mono", price: "8 990 ₽", category: "accessories", className: "product-yellow", art: "watch", colors: ["#202226", "#ffdd2d"] },
  { id: 4, name: "Кепка с вышивкой", price: "1 990 ₽", category: "accessories", className: "product-coral", art: "cap", colors: ["#253142", "#ee977f"] },
  { id: 5, name: "Сумка Soft Box", price: "4 490 ₽", category: "bags", className: "product-pink", art: "bag", colors: ["#d9b884", "#f0cbd1"] },
  { id: 6, name: "Шопер на каждый день", price: "2 490 ₽", category: "bags", className: "product-mint", art: "tote", colors: ["#202226", "#b9dfd5"] },
  { id: 7, name: "Брюки прямого кроя", price: "4 990 ₽", category: "pants", className: "product-sand", art: "pants", colors: ["#313338", "#c9b89c"] },
  { id: 8, name: "Шорты «База»", price: "3 490 ₽", category: "pants", className: "product-steel", art: "shorts", colors: ["#637183", "#e9e7e1"] },
];

const categoryLabels: Record<string, string> = {
  clothes: "Одежда", accessories: "Аксессуары", bags: "Сумки", pants: "Брюки",
};

const catalogHref = (category: MenuKey, section?: string) =>
  `/catalog/${category}${section ? `?section=${encodeURIComponent(section)}` : ""}`;

const menuColumns = (children: Array<{name:string}>) => {
  if (children.length === 0) return [];
  const size = Math.ceil(children.length / Math.min(3, Math.ceil(children.length / 3)));
  return Array.from({length: Math.ceil(children.length / size)}, (_, index) => ({
    title: index === 0 ? "Разделы" : "Ещё",
    links: children.slice(index * size, (index + 1) * size).map((child) => child.name),
  }));
};

const weeklyPicks = [
  {
    id: "soft-box", name: "Сумка Soft Box", price: "4 490 ₽", art: "bag", className: "weekly-pink",
    eyebrow: "Выбор команды · № 01", description: "Мягкая геометрия, тёплый оттенок и место для всего, что должно быть рядом.",
    color: "Песочный",
  },
  {
    id: "mono-watch", name: "Часы Amra Mono", price: "8 990 ₽", art: "watch", className: "weekly-yellow",
    eyebrow: "Выбор команды · № 02", description: "Чистый циферблат и контрастный ремешок для тех, кто ценит точные детали.",
    color: "Графит",
  },
  {
    id: "free-hoodie", name: "Худи свободного кроя", price: "5 490 ₽", art: "hoodie", className: "weekly-lilac",
    eyebrow: "Выбор команды · № 03", description: "Плотный хлопок, спокойный объём и силуэт, который подходит к любому ритму.",
    color: "Лавандовый",
  },
] as const;

const saleProducts = [
  { id: "sale-hoodie", name: "Худи Amra Base", oldPrice: "6 490 ₽", price: "4 490 ₽", discount: "−31%", art: "hoodie", color: "sale-card-lilac", sizes: ["S", "M", "L"] },
  { id: "sale-pants", name: "Брюки Relax", oldPrice: "5 290 ₽", price: "3 490 ₽", discount: "−34%", art: "pants", color: "sale-card-mint", sizes: ["M", "L"] },
  { id: "sale-cap", name: "Кепка Mono", oldPrice: "2 290 ₽", price: "1 390 ₽", discount: "−39%", art: "cap", color: "sale-card-coral", sizes: ["One size"] },
  { id: "sale-shirt", name: "Футболка Outline", oldPrice: "3 290 ₽", price: "2 190 ₽", discount: "−33%", art: "tee", color: "sale-card-yellow", sizes: ["S", "L"] },
  { id: "sale-bag", name: "Сумка Mini Box", oldPrice: "4 990 ₽", price: "3 290 ₽", discount: "−34%", art: "bag", color: "sale-card-sand", sizes: ["One size"] },
  { id: "sale-watch", name: "Часы Mono Light", oldPrice: "8 990 ₽", price: "5 990 ₽", discount: "−33%", art: "watch", color: "sale-card-blue", sizes: ["One size"] },
] as const;

const campaigns = [
  {
    id: "drop",
    eyebrow: "Амра шоп · Серия 01",
    title: <>Одна тема —<br />один сильный экран</>,
    description: <>Лимитированная коллекция вещей,<br />которые говорят за тебя.</>,
    link: "Открыть дроп",
    tag: "НОВОЕ",
  },
  {
    id: "weekly",
    eyebrow: "Выбор редакции · Неделя 34",
    title: <>Один предмет —<br />весь образ</>,
    description: <>Сумка недели: мягкая форма,<br />точный цвет и ничего лишнего.</>,
    link: "Смотреть товар недели",
    tag: "ВЫБОР",
  },
  {
    id: "sale",
    eyebrow: "Только до воскресенья",
    title: <>Sale<br />до −40%</>,
    description: <>Последние размеры и любимые вещи<br />по особенным ценам.</>,
    link: "Перейти в Sale",
    tag: "−40%",
  },
] as const;

function ArrowIcon({ direction = "right", external = false }: { direction?: "left" | "right"; external?: boolean }) {
  const path = external ? "M6 14 14 6M8 6h6v6" : direction === "left" ? "m12.5 4.5-5.5 5.5 5.5 5.5" : "m7.5 4.5 5.5 5.5-5.5 5.5";
  return <svg className="arrow-icon" viewBox="0 0 20 20" aria-hidden="true"><path d={path} /></svg>;
}

function LinkArrow({ external = false }: { external?: boolean }) {
  return <span className="link-arrow"><ArrowIcon external={external} /></span>;
}

const numericPrice = (value: string) => Number(value.replace(/\D/g, ""));
const shopProduct = (product: { id: string | number; name: string; price: string; art: string; className?: string; color?: string }): ShopProduct => ({
  id: String(product.id), name: product.name, price: numericPrice(product.price), art: product.art, colorClass: product.className || product.color || "product-blue",
});

export default function Home() {
  const [menu, setMenu] = useState<MenuItem[]>(fallbackMenu);
  const { addToCart, toggleFavorite, isFavorite } = useShop();
  const [active, setActive] = useState<MenuKey | null>(null);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [campaignIndex, setCampaignIndex] = useState(0);
  const [campaignPaused, setCampaignPaused] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<MenuKey | null>(null);
  const [newOffset, setNewOffset] = useState(0);
  const [weeklyIndex, setWeeklyIndex] = useState(0);
  const [salePage, setSalePage] = useState(0);
  const [selectedShopProduct, setSelectedShopProduct] = useState<ShopProduct | null>(null);
  const menuShellRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    void loadStorefrontCategories().then((categories) => {
      setMenu(categories.map((category) => {
        const presentation = fallbackMenu.find((item) => item.id === category.slug);
        return {
          id: category.slug,
          label: category.name,
          icon: presentation?.icon ?? "shirt",
          columns: menuColumns(category.children),
          feature: presentation?.feature,
        };
      }));
    }).catch(() => setMenu(fallbackMenu));
  }, []);
  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") { setActive(null); setMobileOpen(false); }
    };
    const closeOutsideMenu = (event: PointerEvent) => {
      if (menuShellRef.current && !menuShellRef.current.contains(event.target as Node)) setActive(null);
    };
    window.addEventListener("keydown", closeOnEscape);
    document.addEventListener("pointerdown", closeOutsideMenu);
    return () => {
      window.removeEventListener("keydown", closeOnEscape);
      document.removeEventListener("pointerdown", closeOutsideMenu);
    };
  }, []);

  useEffect(() => {
    if (campaignPaused || window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const timer = window.setInterval(() => {
      setCampaignIndex((index) => (index + 1) % campaigns.length);
    }, 7000);
    return () => window.clearInterval(timer);
  }, [campaignPaused]);

  const activeItem = menu.find((item) => item.id === active);
  const campaign = campaigns[campaignIndex];
  const filteredProducts = selectedCategory ? products.filter((product) => product.category === selectedCategory) : products;
  const visibleProducts = filteredProducts.length <= 4
    ? filteredProducts
    : Array.from({ length: 4 }, (_, index) => filteredProducts[(newOffset + index) % filteredProducts.length]);
  const weeklyPick = weeklyPicks[weeklyIndex];
  const salePageSize = 3;
  const salePageCount = Math.ceil(saleProducts.length / salePageSize);
  const visibleSaleProducts = saleProducts.slice(salePage * salePageSize, (salePage + 1) * salePageSize);
  const showPreviousWeekly = () => setWeeklyIndex((index) => (index - 1 + weeklyPicks.length) % weeklyPicks.length);
  const showNextWeekly = () => setWeeklyIndex((index) => (index + 1) % weeklyPicks.length);

  return <main>
    <StoreHeader
      mobileOpen={mobileOpen}
      onMobileToggle={() => setMobileOpen((value) => !value)}
      onNewClick={() => { setSelectedCategory(null); setNewOffset(0); }}
    />

    <section
      className={`hero campaign-${campaign.id}`}
      id="campaigns"
      onMouseEnter={() => setCampaignPaused(true)}
      onMouseLeave={() => setCampaignPaused(false)}
    >
      <div className="hero-copy">
        <div className="campaign-copy" key={campaign.id} aria-live="polite">
          <span className="hero-label">{campaign.eyebrow}</span>
          <h1>{campaign.title}</h1>
          <p>{campaign.description}</p>
          <a className="campaign-link" href="#new">{campaign.link}<LinkArrow /></a>
        </div>
        <div className="campaign-controls">
          <div className="campaign-dots" aria-label={`История ${campaignIndex + 1} из ${campaigns.length}`}>
            {campaigns.map((item, index) => (
              <button
                key={item.id}
                className={index === campaignIndex ? "active" : ""}
                onClick={() => setCampaignIndex(index)}
                aria-label={`Показать историю ${index + 1}: ${item.eyebrow}`}
                aria-current={index === campaignIndex ? "true" : undefined}
              />
            ))}
          </div>
        </div>
        <div ref={menuShellRef} className={`menu-shell ${mobileOpen ? "mobile-open" : ""}`}>
          <nav className="menu-nav" aria-label="Категории магазина">
            {menu.map((item) => <button key={item.id} className={active === item.id ? "active" : ""}
              aria-expanded={active === item.id} aria-controls={`panel-${item.id}`}
              aria-pressed={active === item.id}
              onClick={() => setActive((current) => current === item.id ? null : item.id)}>
              <span className="menu-icon" aria-hidden="true"><span className={`glyph glyph-${item.icon}`} /></span><span>{item.label}</span>
            </button>)}
          </nav>
          {activeItem && <section className="mega-panel" id={`panel-${activeItem.id}`} aria-label={`Разделы категории ${activeItem.label}`}>
            <header className="mega-panel-head">
              <div><span>Каталог</span><h2>{activeItem.label}</h2></div>
              <a href={catalogHref(activeItem.id)}>Смотреть всё<LinkArrow /></a>
            </header>
            <div className="mega-content">
              <div className="mega-columns">{activeItem.columns.map((column) => <div className="menu-column" key={column.title}>
                <p>{column.title}</p>
                {column.links.map((link) => <a href={catalogHref(activeItem.id, link)} key={link}>{link}</a>)}
              </div>)}</div>
              {activeItem.feature && <aside className="menu-feature" style={{ background: activeItem.feature.color }} aria-label={`${activeItem.feature.eyebrow}: ${activeItem.feature.title}`}>
                <span>{activeItem.feature.eyebrow}</span><strong>{activeItem.feature.title}</strong>
              </aside>}
            </div>
          </section>}
        </div>
      </div>
      <div className="hero-art" aria-hidden="true">
        <div className="sun"/><div className="shirt"><span>А</span></div>
        <div className="weekly-product"><i /></div><div className="sale-type">40</div>
        <div className="tag">{campaign.tag}</div>
      </div>
    </section>

    <section className="catalog new-products" id="new">
      <div className="section-heading">
        <div><span className="section-kicker">Последние поступления</span><h2>Новинки{selectedCategory ? ` · ${menu.find((item) => item.id === selectedCategory)?.label ?? categoryLabels[selectedCategory] ?? ""}` : ""}</h2></div>
        <div className="section-tools">
          <a href="#new">Смотреть все<LinkArrow /></a>
        </div>
      </div>
      <div className="new-grid">
        {visibleProducts.map((product) => <article className="product-card" key={product.id}>
          <div className={`product-image ${product.className}`}>
            <span className="new-badge">NEW</span>
            <button className={`product-heart ${isFavorite(String(product.id)) ? "liked" : ""}`} onClick={() => toggleFavorite(shopProduct(product))} aria-pressed={isFavorite(String(product.id))} aria-label={`${isFavorite(String(product.id)) ? "Убрать" : "Добавить"} ${product.name} ${isFavorite(String(product.id)) ? "из избранного" : "в избранное"}`}><FavoriteIcon active={isFavorite(String(product.id))} /></button>
            <span className={`product-object product-object-${product.art}`} aria-hidden="true" />
            <button className="product-open-hit" onClick={() => setSelectedShopProduct(shopProduct(product))} aria-label={`Открыть карточку ${product.name}`} />
            <button className="quick-add cart-action-button" onClick={() => addToCart(shopProduct(product))}><CartButtonContent /></button>
          </div>
          <div className="product-meta"><button className="product-title-button" onClick={() => setSelectedShopProduct(shopProduct(product))}><h3>{product.name}</h3></button><strong>{product.price}</strong></div>
          <div className="color-dots" aria-label={`${product.colors.length} цвета`}>
            {product.colors.map((color) => <i key={color} style={{ background: color }} />)}
          </div>
        </article>)}
      </div>
      {filteredProducts.length > 4 && <div className="new-controls">
        <button onClick={() => setNewOffset((offset) => (offset - 4 + filteredProducts.length) % filteredProducts.length)} aria-label="Предыдущие новинки"><ArrowIcon direction="left" /></button>
        <span>{newOffset === 0 ? "1" : "2"} / 2</span>
        <button onClick={() => setNewOffset((offset) => (offset + 4) % filteredProducts.length)} aria-label="Следующие новинки"><ArrowIcon /></button>
      </div>}
    </section>

    <section className="weekly-section" id="weekly">
      <div className="weekly-heading">
        <div><span className="section-kicker">Редакционная подборка</span><h2>Товары недели</h2></div>
        <div className="carousel-controls weekly-carousel-controls" aria-label="Листать товары недели">
          <button onClick={showPreviousWeekly} aria-label="Предыдущий товар недели"><ArrowIcon direction="left" /></button>
          <span><b>{weeklyIndex + 1}</b> / {weeklyPicks.length}</span>
          <button onClick={showNextWeekly} aria-label="Следующий товар недели"><ArrowIcon /></button>
        </div>
      </div>
      <div className={`weekly-showcase ${weeklyPick.className}`}>
        <div className="weekly-visual" key={`visual-${weeklyPick.id}`}>
          <span className="weekly-number">0{weeklyIndex + 1}</span>
          <span className={`product-object product-object-${weeklyPick.art}`} aria-hidden="true" />
          <span className="weekly-stamp">AMRA<br />SELECTED</span>
          <button className="product-open-hit" onClick={() => setSelectedShopProduct(shopProduct(weeklyPick))} aria-label={`Открыть карточку ${weeklyPick.name}`} />
        </div>
        <div className="weekly-copy" key={`copy-${weeklyPick.id}`}>
          <div className="weekly-deadline"><i /> До конца недели: 3 дня</div>
          <span className="section-kicker">{weeklyPick.eyebrow}</span>
          <button className="weekly-title-button" onClick={() => setSelectedShopProduct(shopProduct(weeklyPick))}><h3>{weeklyPick.name}</h3></button>
          <p>{weeklyPick.description}</p>
          <div className="weekly-facts"><span>Цвет <b>{weeklyPick.color}</b></span><strong>{weeklyPick.price}</strong></div>
          <div className="weekly-actions">
            <button className="weekly-buy cart-action-button" onClick={() => addToCart(shopProduct(weeklyPick))}><CartButtonContent /></button>
            <button className={`weekly-like ${isFavorite(weeklyPick.id) ? "liked" : ""}`} onClick={() => toggleFavorite(shopProduct(weeklyPick))} aria-pressed={isFavorite(weeklyPick.id)} aria-label={`${isFavorite(weeklyPick.id) ? "Убрать" : "Добавить"} ${weeklyPick.name} ${isFavorite(weeklyPick.id) ? "из избранного" : "в избранное"}`}><FavoriteIcon active={isFavorite(weeklyPick.id)} /></button>
          </div>
          <div className="weekly-selectors" aria-label="Другие товары недели">
            {weeklyPicks.map((item, index) => (
              <button key={item.id} className={index === weeklyIndex ? "active" : ""} onClick={() => setWeeklyIndex(index)} aria-pressed={index === weeklyIndex}>
                <i className={item.className} /><span>{item.name}</span><b>0{index + 1}</b>
              </button>
            ))}
          </div>
        </div>
      </div>
    </section>

    <section className="stories-section" id="collections">
      <div className="stories-heading">
        <div><span className="section-kicker">Редакция Amra</span><h2>Истории и коллекции</h2></div>
        <a href="#collections">Все истории<LinkArrow /></a>
      </div>
      <div className="stories-grid">
        <article className="story-main">
          <div className="story-copy">
            <span>Лимитированная коллаборация</span>
            <h3>Amra<br />× Север</h3>
            <p>Тёплые вещи, вдохновлённые северной архитектурой, камнем и долгими прогулками.</p>
            <a href="#new">Смотреть коллекцию<LinkArrow /></a>
          </div>
          <div className="north-art" aria-hidden="true"><i /><b>СЕВЕР<br />01</b></div>
        </article>
        <article className="story-small story-mono">
          <div className="small-story-copy"><span>Новая линия</span><h3>Серия<br />Mono</h3><a href="#new" aria-label="Открыть серию Mono"><ArrowIcon /></a></div>
          <div className="mono-art" aria-hidden="true"><i className="product-object product-object-watch" /></div>
        </article>
        <article className="story-small story-bundle">
          <div className="small-story-copy"><span>Специальное предложение</span><h3>Собери<br />комплект</h3><p>Выгода 15% на три вещи</p><a href="#new" aria-label="Собрать комплект"><ArrowIcon /></a></div>
          <div className="bundle-art" aria-hidden="true"><i className="product-object product-object-tee" /><b className="product-object product-object-bag" /></div>
        </article>
      </div>
    </section>

    <section className="sale-section" id="sale">
      <div className="sale-panel">
        <div className="sale-message">
          <span className="sale-eyebrow">Финальные цены · до 25 августа</span>
          <h2>Sale<br />до −40%</h2>
          <p>Последние размеры из прошлых коллекций. Когда закончатся — повторов не будет.</p>
          <a href="#sale-products">Смотреть всю распродажу<LinkArrow /></a>
          <div className="sale-timer"><i /> Осталось 7 дней</div>
          <div className="carousel-controls sale-carousel-controls" aria-label="Листать товары распродажи">
            <button onClick={() => setSalePage((page) => (page - 1 + salePageCount) % salePageCount)} aria-label="Предыдущие товары Sale"><ArrowIcon direction="left" /></button>
            <span><b>{salePage + 1}</b> / {salePageCount}</span>
            <button onClick={() => setSalePage((page) => (page + 1) % salePageCount)} aria-label="Следующие товары Sale"><ArrowIcon /></button>
          </div>
        </div>
        <div className="sale-products" id="sale-products" aria-live="polite">
          {visibleSaleProducts.map((product) => <article className="sale-card" key={product.id}>
            <div className={`sale-card-visual ${product.color}`}>
              <span className="sale-discount">{product.discount}</span>
              <button className={`sale-heart ${isFavorite(product.id) ? "liked" : ""}`} onClick={() => toggleFavorite(shopProduct(product))} aria-pressed={isFavorite(product.id)} aria-label={`${isFavorite(product.id) ? "Убрать" : "Добавить"} ${product.name} ${isFavorite(product.id) ? "из избранного" : "в избранное"}`}><FavoriteIcon active={isFavorite(product.id)} /></button>
              <span className={`product-object product-object-${product.art}`} aria-hidden="true" />
              <button className="product-open-hit" onClick={() => setSelectedShopProduct(shopProduct(product))} aria-label={`Открыть карточку ${product.name}`} />
            </div>
            <div className="sale-card-copy">
              <button className="sale-title-button" onClick={() => setSelectedShopProduct(shopProduct(product))}><h3>{product.name}</h3></button>
              <div className="sale-prices"><strong>{product.price}</strong><del>{product.oldPrice}</del></div>
              <div className="sale-sizes"><span>Остались:</span>{product.sizes.map((size) => <i key={size}>{size}</i>)}</div>
              <button className="cart-action-button" onClick={() => addToCart(shopProduct(product))}><CartButtonContent /></button>
            </div>
          </article>)}
        </div>
      </div>
    </section>

    <footer className="site-footer" id="buyers">
      <div className="footer-modular">
        <div className="footer-modular-head">
          <a className="footer-logo" href="#campaigns" aria-label="Амра Шоп, наверх">
            <span className="amra-mark"><Image src="/amra-brand-reference.png" alt="" width={510} height={136} /></span>
            <span>амра шоп</span>
          </a>
          <p>Вещи для повседневной жизни, в которых есть характер.</p>
        </div>
        <div className="footer-module-grid">
          <section className="footer-module footer-module-news">
            <span className="module-kicker">Новости Amra</span>
            <h2>Будем на<br />связи</h2>
            <p>Новые дропы, редкие коллаборации и закрытые цены — не чаще двух раз в месяц.</p>
            <form onSubmit={(event) => event.preventDefault()}>
              <label htmlFor="footer-email">Электронная почта</label>
              <input id="footer-email" type="email" placeholder="name@example.com" required />
              <button type="submit" aria-label="Подписаться на новости"><ArrowIcon /></button>
            </form>
            <small>Подписываясь, вы соглашаетесь с политикой обработки данных.</small>
          </section>
          <a className="footer-module footer-module-help" href="mailto:hello@amra.shop?subject=Помощь%20с%20заказом">
            <span className="module-icon" aria-hidden="true">?</span>
            <span><b>Нужна помощь?</b><small>Доставка, возврат, размеры и ответы на вопросы.</small></span>
            <i aria-hidden="true"><ArrowIcon external /></i>
          </a>
          <section className="footer-module footer-module-social">
            <span className="module-kicker">Мы рядом</span>
            <h3>Следите за Amra</h3>
            <div className="footer-socials"><a href="https://vk.com/" aria-label="ВКонтакте">VK</a><a href="https://t.me/" aria-label="Telegram">TG</a><a href="https://www.pinterest.com/" aria-label="Pinterest">P</a></div>
          </section>
          <nav className="footer-module footer-module-links" aria-label="Ссылки в подвале">
            <div><span className="module-kicker">Магазин</span><a href="#new">Новинки</a><a href="#collections">Коллекции</a><a href="#weekly">Товары недели</a><a href="#sale">Sale</a></div>
            <div><span className="module-kicker">Категории</span>{menu.map((item) => <Link href={`/catalog/${item.id}`} key={item.id}>{item.label}</Link>)}</div>
          </nav>
          <section className="footer-module footer-module-contact">
            <span className="module-kicker">Связаться</span>
            <a href="mailto:hello@amra.shop">hello@amra.shop</a>
            <div><a href="tel:+78005553535">8 800 555-35-35</a><small>Ежедневно с 9:00 до 21:00</small></div>
          </section>
        </div>
      </div>
      <div className="footer-bottom">
        <span>© 2026 Amra Shop</span><div><Link href="/privacy">Политика конфиденциальности</Link><Link href="/offer">Публичная оферта</Link></div><span>Демонстрационный магазин</span>
      </div>
    </footer>
    {selectedShopProduct && <ShopProductDialog product={selectedShopProduct} onClose={() => setSelectedShopProduct(null)} />}
  </main>;
}
