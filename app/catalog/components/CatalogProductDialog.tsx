"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { CartButtonContent, FavoriteIcon } from "../../components/ShopIcons";
import { useShop } from "../../components/ShopState";
import { toShopProduct, type CatalogProduct } from "../catalog-data";

export default function CatalogProductDialog({ product, onClose }: { product: CatalogProduct; onClose: () => void }) {
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
