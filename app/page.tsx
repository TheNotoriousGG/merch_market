"use client";

import { useEffect, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import StoreHeader from "./components/StoreHeader";
import { loadStorefrontCategories, loadStorefrontProducts } from "./catalog/catalog-api";
import type { CatalogProduct } from "./catalog/catalog-data";
import CatalogProductCard from "./catalog/components/CatalogProductCard";
import CatalogProductDialog from "./catalog/components/CatalogProductDialog";

type MenuKey = string;

type MenuItem = {
  id: MenuKey; label: string; icon: string;
  columns: Array<{ title: string; links: string[] }>;
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

const campaigns = [
  {
    id: "catalog",
    eyebrow: "Амра шоп",
    title: <>Актуальный<br />каталог</>,
    description: <>Только опубликованные товары<br />из каталога магазина.</>,
    link: "Смотреть новинки",
    tag: "AMRA",
  },
] as const;

function ArrowIcon({ direction = "right", external = false }: { direction?: "left" | "right"; external?: boolean }) {
  const path = external ? "M6 14 14 6M8 6h6v6" : direction === "left" ? "m12.5 4.5-5.5 5.5 5.5 5.5" : "m7.5 4.5 5.5 5.5-5.5 5.5";
  return <svg className="arrow-icon" viewBox="0 0 20 20" aria-hidden="true"><path d={path} /></svg>;
}

function LinkArrow({ external = false }: { external?: boolean }) {
  return <span className="link-arrow"><ArrowIcon external={external} /></span>;
}

export default function Home() {
  const [menu, setMenu] = useState<MenuItem[]>([]);
  const [active, setActive] = useState<MenuKey | null>(null);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [campaignIndex, setCampaignIndex] = useState(0);
  const [campaignPaused, setCampaignPaused] = useState(false);
  const [newOffset, setNewOffset] = useState(0);
  const [selectedProduct, setSelectedProduct] = useState<CatalogProduct | null>(null);
  const [publishedNewProducts, setPublishedNewProducts] = useState<CatalogProduct[]>([]);
  const menuShellRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    void loadStorefrontCategories().then((categories) => {
      setMenu(categories.map((category) => {
        return {
          id: category.slug,
          label: category.name,
          icon: "shirt",
          columns: menuColumns(category.children),
        };
      }));
    }).catch(() => setMenu([]));
  }, []);
  useEffect(() => {
    void loadStorefrontProducts({ onlyNew: true }).then(setPublishedNewProducts).catch(() => setPublishedNewProducts([]));
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
  const filteredProducts = publishedNewProducts;
  const visibleProducts = filteredProducts.length <= 4
    ? filteredProducts
    : Array.from({ length: 4 }, (_, index) => filteredProducts[(newOffset + index) % filteredProducts.length]);

  return <main>
    <StoreHeader
      mobileOpen={mobileOpen}
      onMobileToggle={() => setMobileOpen((value) => !value)}
      onNewClick={() => setNewOffset(0)}
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
              <nav className="mega-category-links" aria-label={`Подкатегории раздела ${activeItem.label}`}>
                {activeItem.columns.flatMap((column) => column.links).map((link) => (
                  <a href={catalogHref(activeItem.id, link)} key={link}>{link}</a>
                ))}
              </nav>
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
        <div><span className="section-kicker">Последние поступления</span><h2>Новинки</h2></div>
        <div className="section-tools">
          <a href="#new">Смотреть все<LinkArrow /></a>
        </div>
      </div>
      {visibleProducts.length>0?<div className="new-grid">{visibleProducts.map((product)=><CatalogProductCard product={product} onOpen={()=>setSelectedProduct(product)} key={product.id}/>)}</div>:<p className="catalog-empty">Опубликованных новинок пока нет.</p>}
      {filteredProducts.length > 4 && <div className="new-controls">
        <button onClick={() => setNewOffset((offset) => (offset - 4 + filteredProducts.length) % filteredProducts.length)} aria-label="Предыдущие новинки"><ArrowIcon direction="left" /></button>
        <span>{newOffset === 0 ? "1" : "2"} / 2</span>
        <button onClick={() => setNewOffset((offset) => (offset + 4) % filteredProducts.length)} aria-label="Следующие новинки"><ArrowIcon /></button>
      </div>}
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
            <div><span className="module-kicker">Магазин</span><a href="#new">Новинки</a></div>
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
    {selectedProduct && <CatalogProductDialog product={selectedProduct} onClose={() => setSelectedProduct(null)} />}
  </main>;
}
