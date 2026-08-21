"use client";

import { useEffect, useRef, useState } from "react";
import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import StoreHeader from "./components/StoreHeader";
import { useShop } from "./components/ShopState";
import { CartButtonContent, FavoriteIcon } from "./components/ShopIcons";
import { loadStorefrontCategories, loadStorefrontProducts } from "./catalog/catalog-api";
import { toShopProduct, type CatalogProduct } from "./catalog/catalog-data";
import CatalogProductCard from "./catalog/components/CatalogProductCard";
import CatalogProductDialog from "./catalog/components/CatalogProductDialog";

type MenuKey = string;

type MenuItem = {
  id: MenuKey; label: string; icon: string;
  columns: Array<{ title: string; links: Array<{ slug:string; name:string }> }>;
};

const categoryIcons: Record<string, string> = {
  clothes: "shirt",
  accessories: "watch",
  bags: "bag",
  pants: "pants",
};

const catalogHref = (category: MenuKey, section?: string) =>
  `/catalog/${category}${section ? `?section=${encodeURIComponent(section)}` : ""}`;

const menuColumns = (children: Array<{slug:string;name:string}>) => {
  if (children.length === 0) return [];
  const size = Math.ceil(children.length / Math.min(3, Math.ceil(children.length / 3)));
  return Array.from({length: Math.ceil(children.length / size)}, (_, index) => ({
    title: index === 0 ? "Разделы" : "Ещё",
    links: children.slice(index * size, (index + 1) * size).map((child) => ({slug:child.slug,name:child.name})),
  }));
};

const campaigns = [
  {
    id: "catalog",
    eyebrow: "На каждый день",
    title: <>Худи и<br />свитшоты</>,
    description: <>Базовые модели свободного кроя<br />в спокойных оттенках.</>,
    link: "Перейти в раздел",
    href: "/catalog/clothes?section=hoodies",
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
  const router = useRouter();
  const [menu, setMenu] = useState<MenuItem[]>([]);
  const [active, setActive] = useState<MenuKey | null>(null);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [campaignIndex, setCampaignIndex] = useState(0);
  const [campaignPaused, setCampaignPaused] = useState(false);
  const [newOffset, setNewOffset] = useState(0);
  const [weeklyIndex, setWeeklyIndex] = useState(0);
  const [salePage, setSalePage] = useState(0);
  const [selectedProduct, setSelectedProduct] = useState<CatalogProduct | null>(null);
  const [publishedProducts, setPublishedProducts] = useState<CatalogProduct[]>([]);
  const { addToCart, toggleFavorite, isFavorite, isInCart } = useShop();
  const cartAction = (product: CatalogProduct) => isInCart(product.id) ? router.push("/cart") : addToCart(toShopProduct(product));
  const menuShellRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    void loadStorefrontCategories().then((categories) => {
      setMenu(categories.map((category) => {
        return {
          id: category.slug,
          label: category.name,
          icon: categoryIcons[category.slug] ?? "shirt",
          columns: menuColumns(category.children),
        };
      }));
    }).catch(() => setMenu([]));
  }, []);
  useEffect(() => {
    void loadStorefrontProducts().then(setPublishedProducts).catch(() => setPublishedProducts([]));
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
  const filteredProducts = publishedProducts.filter((product) => product.isNew);
  const visibleProducts = filteredProducts.length <= 4
    ? filteredProducts
    : Array.from({ length: 4 }, (_, index) => filteredProducts[(newOffset + index) % filteredProducts.length]);
  const weeklyProducts = publishedProducts.filter((product) => product.featured);
  const weeklyProduct = weeklyProducts.length > 0 ? weeklyProducts[weeklyIndex % weeklyProducts.length] : null;
  const saleProducts = publishedProducts.filter((product) => product.salePercent);
  const salePageSize = 3;
  const salePageCount = Math.max(1, Math.ceil(saleProducts.length / salePageSize));
  const visibleSaleProducts = saleProducts.slice(salePage * salePageSize, (salePage + 1) * salePageSize);

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
          <a className="campaign-link" href={campaign.href}>{campaign.link}<LinkArrow /></a>
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
                  <a href={catalogHref(activeItem.id, link.slug)} key={link.slug}>{link.name}</a>
                ))}
              </nav>
            </div>
          </section>}
        </div>
      </div>
      <div className="hero-art" aria-hidden="true">
        <Image className="campaign-hero-image" src="/amra-hero-hoodies-v1.png" alt="" fill priority sizes="100vw" />
        <div className="sun"/><div className="shirt"><span>А</span></div>
        <div className="weekly-product"><i /></div><div className="sale-type">40</div>
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

    {weeklyProduct&&<section className="weekly-section" id="weekly">
      <div className="weekly-heading">
        <div><span className="section-kicker">Редакционная подборка</span><h2>Товары недели</h2></div>
        {weeklyProducts.length>1&&<div className="carousel-controls weekly-carousel-controls" aria-label="Листать товары недели">
          <button onClick={()=>setWeeklyIndex((index)=>(index-1+weeklyProducts.length)%weeklyProducts.length)} aria-label="Предыдущий товар недели"><ArrowIcon direction="left"/></button>
          <span><b>{weeklyIndex+1}</b> / {weeklyProducts.length}</span>
          <button onClick={()=>setWeeklyIndex((index)=>(index+1)%weeklyProducts.length)} aria-label="Следующий товар недели"><ArrowIcon/></button>
        </div>}
      </div>
      <div className={`weekly-showcase ${weeklyProduct.colorClass}`}>
        <div className="weekly-visual">
          <span className="weekly-number">{String(weeklyIndex+1).padStart(2,"0")}</span>
          {weeklyProduct.imageUrl?<img className="catalog-product-photo" src={weeklyProduct.imageUrl} alt={weeklyProduct.name}/>:null}
          <span className="weekly-stamp">AMRA<br/>SELECTED</span>
          <button className="product-open-hit" onClick={()=>setSelectedProduct(weeklyProduct)} aria-label={`Открыть карточку ${weeklyProduct.name}`}/>
        </div>
        <div className="weekly-copy">
          <div className="weekly-deadline"><i/> Выбор команды Amra</div>
          <span className="section-kicker">Товар недели · № {String(weeklyIndex+1).padStart(2,"0")}</span>
          <button className="weekly-title-button" onClick={()=>setSelectedProduct(weeklyProduct)}><h3>{weeklyProduct.name}</h3></button>
          <p>{weeklyProduct.description}</p>
          <div className="weekly-facts"><span>Цвет <b>{weeklyProduct.color}</b></span><strong>{weeklyProduct.price.toLocaleString("ru-RU")} ₽</strong></div>
          <div className="weekly-actions">
            <button className={`weekly-buy cart-action-button ${isInCart(weeklyProduct.id)?"is-added":""}`} onClick={()=>cartAction(weeklyProduct)}><CartButtonContent added={isInCart(weeklyProduct.id)} label={isInCart(weeklyProduct.id)?"В корзине · Перейти":"В корзину"}/></button>
            <button className={`weekly-like ${isFavorite(weeklyProduct.id)?"liked":""}`} onClick={()=>toggleFavorite(toShopProduct(weeklyProduct))} aria-pressed={isFavorite(weeklyProduct.id)} aria-label="Добавить товар недели в избранное"><FavoriteIcon active={isFavorite(weeklyProduct.id)}/></button>
          </div>
          {weeklyProducts.length>1&&<div className="weekly-selectors" aria-label="Другие товары недели">{weeklyProducts.map((product,index)=><button key={product.id} className={index===weeklyIndex?"active":""} onClick={()=>setWeeklyIndex(index)} aria-pressed={index===weeklyIndex}><i style={product.imageUrl?{backgroundImage:`url(${product.imageUrl})`,backgroundSize:"cover"}:undefined}/><span>{product.name}</span><b>{String(index+1).padStart(2,"0")}</b></button>)}</div>}
        </div>
      </div>
    </section>}

    {saleProducts.length>0&&<section className="sale-section" id="sale">
      <div className="sale-panel">
        <div className="sale-message">
          <span className="sale-eyebrow">Специальные цены</span>
          <h2>Sale<br/>сейчас</h2>
          <p>Скидки на опубликованные товары, настроенные каталог-менеджером.</p>
          <a href="#sale-products">Смотреть распродажу<LinkArrow/></a>
          {salePageCount>1&&<div className="carousel-controls sale-carousel-controls" aria-label="Листать товары распродажи">
            <button onClick={()=>setSalePage((page)=>(page-1+salePageCount)%salePageCount)} aria-label="Предыдущие товары Sale"><ArrowIcon direction="left"/></button>
            <span><b>{salePage+1}</b> / {salePageCount}</span>
            <button onClick={()=>setSalePage((page)=>(page+1)%salePageCount)} aria-label="Следующие товары Sale"><ArrowIcon/></button>
          </div>}
        </div>
        <div className="sale-products" id="sale-products" aria-live="polite">{visibleSaleProducts.map((product)=><article className="sale-card" key={product.id}>
          <div className={`sale-card-visual ${product.colorClass}`}>
            <span className="sale-discount">−{product.salePercent}%</span>
            <button className={`sale-heart ${isFavorite(product.id)?"liked":""}`} onClick={()=>toggleFavorite(toShopProduct(product))} aria-pressed={isFavorite(product.id)} aria-label="Добавить товар в избранное"><FavoriteIcon active={isFavorite(product.id)}/></button>
            {product.imageUrl?<img className="catalog-product-photo" src={product.imageUrl} alt={product.name}/>:null}
            <button className="product-open-hit" onClick={()=>setSelectedProduct(product)} aria-label={`Открыть карточку ${product.name}`}/>
          </div>
          <div className="sale-card-copy">
            <button className="sale-title-button" onClick={()=>setSelectedProduct(product)}><h3>{product.name}</h3></button>
            <div className="sale-prices"><strong>{product.price.toLocaleString("ru-RU")} ₽</strong>{product.originalPrice&&<del>{product.originalPrice.toLocaleString("ru-RU")} ₽</del>}</div>
            <div className="sale-sizes"><span>Размеры:</span>{product.sizes.map((size)=><i key={size}>{size}</i>)}</div>
            <button className={`cart-action-button ${isInCart(product.id)?"is-added":""}`} onClick={()=>cartAction(product)}><CartButtonContent added={isInCart(product.id)} label={isInCart(product.id)?"В корзине · Перейти":"В корзину"}/></button>
          </div>
        </article>)}</div>
      </div>
    </section>}

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
            <div><span className="module-kicker">Магазин</span><a href="#new">Новинки</a><a href="#weekly">Товары недели</a><a href="#sale">Sale</a></div>
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
