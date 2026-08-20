"use client";

import { FavoriteIcon } from "../../components/ShopIcons";
import { useShop } from "../../components/ShopState";
import { toShopProduct, type CatalogProduct } from "../catalog-data";

export default function CatalogProductCard({ product, onOpen }: { product: CatalogProduct; onOpen: () => void }) {
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
