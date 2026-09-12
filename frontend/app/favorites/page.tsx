"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import StoreHeader from "../components/StoreHeader";
import { formatPrice, type ShopProduct, useShop } from "../components/ShopState";
import ShopProductDialog from "../components/ShopProductDialog";
import { CartButtonContent, FavoriteIcon } from "../components/ShopIcons";

export default function FavoritesPage() {
  const router = useRouter();
  const { favorites, toggleFavorite, addToCart, isInCart } = useShop();
  const [selectedProduct, setSelectedProduct] = useState<ShopProduct | null>(null);
  const countLabel = favorites.length % 10 === 1 && favorites.length % 100 !== 11 ? "товар" : favorites.length % 10 >= 2 && favorites.length % 10 <= 4 && (favorites.length % 100 < 12 || favorites.length % 100 > 14) ? "товара" : "товаров";

  return <main className="utility-page">
    <StoreHeader />
    <div className="utility-shell">
      <div className="utility-breadcrumbs"><a href="/">Главная</a><span>·</span><span>Избранное</span></div>
      <header className="utility-heading">
        <div><span className="section-kicker">Личная подборка</span><h1>Избранное</h1></div>
        <span>{favorites.length} {countLabel}</span>
      </header>

      {favorites.length === 0 ? <section className="utility-empty">
        <span className="empty-symbol">♡</span><h2>Здесь пока пусто</h2><p>Отмечайте понравившиеся вещи сердцем — они сохранятся на этой странице.</p>
        <a className="utility-primary-link" href="/#new">Смотреть новинки<span>›</span></a>
      </section> : <section className="favorites-grid" aria-label="Избранные товары">
        {favorites.map((product) => <article className="favorite-card" key={product.id}>
          <div className={`favorite-visual ${product.colorClass}`}>{product.imageUrl?<img className="catalog-product-photo" src={product.imageUrl} alt=""/>:<span className={`product-object product-object-${product.art}`} aria-hidden="true" />}<button className="product-open-hit" onClick={() => setSelectedProduct(product)} aria-label={`Открыть карточку ${product.name}`} /><button className="liked" onClick={() => toggleFavorite(product)} aria-label={`Убрать ${product.name} из избранного`}><FavoriteIcon active /></button></div>
          <div className="favorite-copy"><div><button className="favorite-title-button" onClick={() => setSelectedProduct(product)}><h2>{product.name}</h2></button><strong>{formatPrice(product.price)}</strong></div><button className={`cart-action-button ${isInCart(product.id)?"is-added":""}`} onClick={() => isInCart(product.id)?router.push("/cart"):addToCart(product)}><CartButtonContent added={isInCart(product.id)} label={isInCart(product.id)?"В корзине · Перейти":"В корзину"} /></button></div>
        </article>)}
      </section>}
    </div>
    {selectedProduct && <ShopProductDialog product={selectedProduct} onClose={() => setSelectedProduct(null)} />}
  </main>;
}
