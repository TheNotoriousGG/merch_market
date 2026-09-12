"use client";

import { FormEvent, useState } from "react";
import { cookie, orderingApi } from "../api/client";
import StoreHeader from "../components/StoreHeader";
import { formatPrice, useShop } from "../components/ShopState";
import styles from "./checkout.module.css";

export default function CheckoutPage() {
  const { cart, cartTotal, cartVersion, loading, refreshCart } = useShop();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const data = new FormData(event.currentTarget);
    const storageKey = `amra-checkout-key-v${cartVersion}`;
    const idempotencyKey = localStorage.getItem(storageKey)
      ?? `checkout-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    localStorage.setItem(storageKey, idempotencyKey);
    try {
      const created = await orderingApi.checkout({
        idempotencyKey,
        xAMRACSRF: decodeURIComponent(cookie("AMRA_CSRF") || "browser-csrf-token"),
        checkoutRequest: {
          cartVersion,
          email: String(data.get("email")), recipientName: String(data.get("recipientName")),
          phone: String(data.get("phone")), postalCode: String(data.get("postalCode")),
          city: String(data.get("city")), street: String(data.get("street")),
          apartment: String(data.get("apartment")) || undefined,
        },
      });
      if (created.guestAccessToken) {
        localStorage.setItem(`amra-order-token-${created.publicNumber}`, created.guestAccessToken);
      }
      localStorage.removeItem(storageKey);
      await refreshCart();
      window.location.assign(`/orders/${encodeURIComponent(created.publicNumber)}`);
    } catch {
      setError("Не удалось оформить заказ. Повторная отправка безопасно продолжит эту же попытку.");
    } finally { setBusy(false); }
  }

  return <main className="utility-page"><StoreHeader /><div className="utility-shell">
    <div className="utility-breadcrumbs"><a href="/">Главная</a><span>·</span><a href="/cart">Корзина</a><span>·</span><span>Оформление</span></div>
    <header className="utility-heading"><div><span className="section-kicker">Последний шаг</span><h1>Оформление заказа</h1></div></header>
    {!loading && cart.length === 0 ? <section className="utility-empty"><h2>Корзина пуста</h2><p>Добавьте товары перед оформлением заказа.</p><a className="utility-primary-link" href="/">Перейти к покупкам<span>›</span></a></section>
    : <div className={styles.layout}><form className={styles.form} onSubmit={submit}><h2>Получатель и доставка</h2><div className={styles.fields}>
      <label className={styles.wide}>Email<input name="email" type="email" autoComplete="email" required /></label>
      <label>Имя получателя<input name="recipientName" autoComplete="name" maxLength={160} required /></label>
      <label>Телефон<input name="phone" type="tel" autoComplete="tel" placeholder="+79991234567" pattern="\+[1-9][0-9]{7,14}" required /></label>
      <label>Индекс<input name="postalCode" autoComplete="postal-code" maxLength={20} required /></label>
      <label>Город<input name="city" autoComplete="address-level2" maxLength={120} required /></label>
      <label className={styles.wide}>Улица и дом<input name="street" autoComplete="street-address" maxLength={240} required /></label>
      <label className={styles.wide}>Квартира или офис<input name="apartment" maxLength={40} /></label>
    </div>{error && <p className={styles.error} role="alert">{error}</p>}<button className={styles.submit} disabled={busy || loading}>{busy ? "Оформляем…" : "Подтвердить заказ"}<span>›</span></button></form>
    <aside className={styles.summary}><span className="section-kicker">Состав заказа</span><h2>{formatPrice(cartTotal)}</h2><ul>{cart.map(line => <li key={line.variantId}><span>{line.name}<small>{line.variantLabel} · {line.quantity} шт.</small></span><strong>{formatPrice(line.lineSubtotal)}</strong></li>)}</ul><div className={styles.total}><span>К оплате</span><span>{formatPrice(cartTotal)}</span></div></aside></div>}
  </div></main>;
}
