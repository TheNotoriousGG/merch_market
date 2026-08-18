"use client";

import { useEffect, useState } from "react";
import { formatPrice, type ShopProduct, useShop } from "./ShopState";
import { CartButtonContent, FavoriteIcon } from "./ShopIcons";

const descriptions: Record<string, string> = {
  tee: "Плотный хлопок, выверенная свободная посадка и мягкая горловина для повседневных образов.",
  hoodie: "Объёмный силуэт, плотный мягкий трикотаж и глубокий капюшон без лишних деталей.",
  watch: "Чистая геометрия, контрастный циферблат и удобный ремешок на каждый день.",
  cap: "Мягкая посадка, регулируемая застёжка и аккуратная фирменная вышивка Amra.",
  bag: "Продуманная форма, удобный ремень и место для вещей, которые должны быть рядом.",
  tote: "Лёгкий вместительный шопер с внутренним карманом и усиленными ручками.",
  pants: "Свободная посадка, плотная ткань и точные пропорции для собранного силуэта.",
  shorts: "Мягкая ткань, эластичный пояс и комфортный объём для тёплого сезона.",
};

const sizesFor = (art: string) => ["watch", "cap", "bag", "tote"].includes(art) ? ["One size"] : ["S", "M", "L", "XL"];

export default function ShopProductDialog({ product, onClose }: { product: ShopProduct; onClose: () => void }) {
  const { addToCart, toggleFavorite, isFavorite, isInCart } = useShop();
  const sizes = sizesFor(product.art);
  const [selectedSize, setSelectedSize] = useState(sizes.length === 1 ? sizes[0] : "");
  const liked = isFavorite(product.id);

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => event.key === "Escape" && onClose();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    window.addEventListener("keydown", closeOnEscape);
    return () => { document.body.style.overflow = previousOverflow; window.removeEventListener("keydown", closeOnEscape); };
  }, [onClose]);

  return <div className="product-dialog-backdrop" role="button" tabIndex={-1} aria-label="Закрыть карточку" onKeyDown={(event) => event.key === "Escape" && onClose()} onClick={(event) => event.target === event.currentTarget && onClose()}>
    <section className="product-dialog" role="dialog" aria-modal="true" aria-labelledby="shop-product-title">
      <button className="product-dialog-close" onClick={onClose} aria-label="Закрыть карточку">×</button>
      <div className={`product-dialog-visual ${product.colorClass}`}><span className="new-badge">AMRA</span><span className={`product-object product-object-${product.art}`} aria-hidden="true" /></div>
      <div className="product-dialog-copy">
        <span className="section-kicker">Амра Шоп · Коллекция 2026</span>
        <h2 id="shop-product-title">{product.name}</h2><strong className="product-dialog-price">{formatPrice(product.price)}</strong>
        <p>{descriptions[product.art] || "Фирменная вещь Amra с продуманной конструкцией и вниманием к деталям."}</p>
        <dl><div><dt>Коллекция</dt><dd>Amra Base</dd></div><div><dt>Материал</dt><dd>{["bag", "watch"].includes(product.art) ? "Комбинированный" : "Премиальный хлопок"}</dd></div><div><dt>Доставка</dt><dd>1–3 рабочих дня</dd></div></dl>
        <div className="product-dialog-sizes"><span>{sizes.length === 1 ? "Размер" : "Выберите размер"}</span><div>{sizes.map((size) => <button className={selectedSize === size ? "active" : ""} onClick={() => setSelectedSize(size)} aria-pressed={selectedSize === size} key={size}>{size}</button>)}</div></div>
        <div className="product-dialog-actions"><button className={`cart-action-button ${isInCart(product.id) ? "is-added" : ""}`} disabled={!selectedSize} onClick={() => addToCart(product)}><CartButtonContent label={isInCart(product.id) ? "Добавить ещё" : selectedSize ? "В корзину" : "Выберите размер"} /></button><button className={liked ? "liked" : ""} onClick={() => toggleFavorite(product)} aria-pressed={liked} aria-label={`${liked ? "Убрать" : "Добавить"} ${product.name} ${liked ? "из избранного" : "в избранное"}`}><FavoriteIcon active={liked} /></button></div>
        <small>Бесплатная доставка от 5 000 ₽ · Возврат в течение 14 дней</small>
      </div>
    </section>
  </div>;
}
