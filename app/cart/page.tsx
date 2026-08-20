"use client";

import { useState } from "react";
import StoreHeader from "../components/StoreHeader";
import { formatPrice, type ShopProduct, useShop } from "../components/ShopState";
import ShopProductDialog from "../components/ShopProductDialog";

export default function CartPage() {
  const { cart, cartCount, cartTotal, setQuantity, removeFromCart, toggleFavorite, isFavorite } = useShop();
  const [selectedProduct, setSelectedProduct] = useState<ShopProduct | null>(null);
  const delivery = cartTotal >= 5000 || cartTotal === 0 ? 0 : 490;
  const positionLabel = cartCount % 10 === 1 && cartCount % 100 !== 11 ? "позиция" : cartCount % 10 >= 2 && cartCount % 10 <= 4 && (cartCount % 100 < 12 || cartCount % 100 > 14) ? "позиции" : "позиций";

  return <main className="utility-page">
    <StoreHeader />
    <div className="utility-shell">
      <div className="utility-breadcrumbs"><a href="/">Главная</a><span>·</span><span>Корзина</span></div>
      <header className="utility-heading"><div><span className="section-kicker">Ваш заказ</span><h1>Корзина</h1></div><span>{cartCount} {positionLabel}</span></header>

      {cart.length === 0 ? <section className="utility-empty">
        <span className="empty-symbol empty-bag">□</span><h2>Корзина ждёт вещей</h2><p>Добавьте товары из новых поступлений или редакционной подборки.</p>
        <a className="utility-primary-link" href="/#new">Перейти к покупкам<span>›</span></a>
      </section> : <div className="cart-layout">
        <section className="cart-list" aria-label="Товары в корзине">
          {cart.map((product) => <article className="cart-line" key={product.id}>
            <div className={`cart-line-visual ${product.colorClass}`}><span className={`product-object product-object-${product.art}`} aria-hidden="true" /><button className="product-open-hit" onClick={() => setSelectedProduct(product)} aria-label={`Открыть карточку ${product.name}`} /></div>
            <div className="cart-line-main"><span className="section-kicker">Amra Shop</span><button className="cart-title-button" onClick={() => setSelectedProduct(product)}><h2>{product.name}</h2></button><strong>{formatPrice(product.price)}</strong><div className="cart-line-tools"><div className="quantity-control"><button onClick={() => setQuantity(product.id, product.quantity - 1)} aria-label={`Уменьшить количество ${product.name}`}>−</button><span>{product.quantity}</span><button onClick={() => setQuantity(product.id, product.quantity + 1)} aria-label={`Увеличить количество ${product.name}`}>+</button></div><button className={isFavorite(product.id) ? "active" : ""} onClick={() => toggleFavorite(product)}>{isFavorite(product.id) ? "В избранном" : "В избранное"}</button><button onClick={() => removeFromCart(product.id)}>Удалить</button></div></div>
            <strong className="cart-line-total">{formatPrice(product.price * product.quantity)}</strong>
          </article>)}
        </section>
        <aside className="cart-summary">
          <span className="section-kicker">Итого</span><h2>{formatPrice(cartTotal + delivery)}</h2>
          <dl><div><dt>Товары · {cartCount}</dt><dd>{formatPrice(cartTotal)}</dd></div><div><dt>Доставка</dt><dd>{delivery ? formatPrice(delivery) : "Бесплатно"}</dd></div></dl>
          <button>Перейти к оформлению<span>›</span></button><p>Оплата после подтверждения заказа. Возврат — в течение 14 дней.</p>
        </aside>
      </div>}
    </div>
    {selectedProduct && <ShopProductDialog product={selectedProduct} onClose={() => setSelectedProduct(null)} />}
  </main>;
}
