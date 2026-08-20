"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import { useShop } from "./ShopState";

const productWord = (count: number) => {
  const mod10 = count % 10;
  const mod100 = count % 100;
  if (mod10 === 1 && mod100 !== 11) return "товар";
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "товара";
  return "товаров";
};

export default function StoreHeader({
  mobileOpen = false,
  onMobileToggle,
  onNewClick,
}: {
  mobileOpen?: boolean;
  onMobileToggle?: () => void;
  onNewClick?: () => void;
}) {
  const [isScrolled, setIsScrolled] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const { cartCount, favoriteCount } = useShop();

  useEffect(() => {
    const handleScroll = () => setIsScrolled(window.scrollY > 36);
    handleScroll();
    window.addEventListener("scroll", handleScroll, { passive: true });
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  return <header className={`site-header ${isScrolled ? "is-scrolled" : ""}`}>
    <a className="logo amra-logo" href="/" aria-label="Амра Шоп, на главную">
      <span className="amra-mark"><Image src="/amra-brand-reference.png" alt="" width={510} height={136} priority /></span>
      <span className="amra-wordmark">амра шоп</span>
    </a>
    <button className="mobile-toggle" onClick={onMobileToggle} aria-expanded={mobileOpen}>
      {mobileOpen ? "Закрыть" : "Меню"}
    </button>
    <div className="header-center">
      <nav className="header-nav" aria-label="Основная навигация">
        <a href="/#new" onClick={onNewClick}>Новинки</a>
        <a href="/#weekly">Товары недели</a>
        <a className="sale-link" href="/#sale">Sale</a>
        <a href="/#buyers">Покупателям</a>
      </nav>
      <div className="header-actions">
        <button className="header-icon search-icon" aria-label="Открыть поиск" title="Поиск" aria-expanded={searchOpen} onClick={() => setSearchOpen(true)}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></svg>
        </button>
        <a className="header-icon heart-icon" href="/favorites" aria-label={`Избранное, ${favoriteCount} ${productWord(favoriteCount)}`} title="Избранное">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78L12 21.23l8.84-8.84a5.5 5.5 0 0 0 0-7.78Z" /></svg>
          {favoriteCount > 0 && <span className="icon-badge favorite-badge">{favoriteCount}</span>}
        </a>
        <a className="header-icon account-icon" href="/account" aria-label="Личный кабинет" title="Личный кабинет">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8" r="4" /><path d="M4.5 21a7.5 7.5 0 0 1 15 0" /></svg>
        </a>
        <a className="header-icon bag-icon" href="/cart" aria-label={`Корзина, ${cartCount} ${productWord(cartCount)}`} title="Корзина">
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 8h12l1 13H5L6 8Z" /><path d="M9 9V6a3 3 0 0 1 6 0v3" /></svg>
          <span className="icon-badge">{cartCount}</span>
        </a>
      </div>
    </div>
    {searchOpen && <div className="header-search-backdrop" role="presentation" onMouseDown={() => setSearchOpen(false)}>
      <section className="header-search-panel" role="search" aria-label="Поиск по каталогу" onMouseDown={(event) => event.stopPropagation()}>
        <form action="/search" method="get">
          <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-4-4" /></svg>
          <input name="q" type="search" placeholder="Название товара" aria-label="Название товара" autoFocus required />
          <button type="submit">Найти</button>
        </form>
        <button className="header-search-close" type="button" onClick={() => setSearchOpen(false)} aria-label="Закрыть поиск">×</button>
      </section>
    </div>}
  </header>;
}
