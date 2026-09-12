"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import type { CustomerOrder } from "../../api/generated";
import { orderingApi } from "../../api/client";
import StoreHeader from "../../components/StoreHeader";
import { formatPrice } from "../../components/ShopState";
import styles from "../../checkout/checkout.module.css";

export default function OrderPage() {
  const { publicNumber } = useParams<{ publicNumber: string }>();
  const [order, setOrder] = useState<CustomerOrder | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    void orderingApi.getCustomerOrder({
      publicNumber,
      xGuestOrderToken: localStorage.getItem(`amra-order-token-${publicNumber}`) ?? undefined,
    }).then(setOrder).catch(() => setFailed(true));
  }, [publicNumber]);

  return <main className="utility-page"><StoreHeader /><div className="utility-shell">
    <div className="utility-breadcrumbs"><a href="/">Главная</a><span>·</span><span>Заказ</span></div>
    {order ? <section className={styles.success}><span className="section-kicker">Заказ подтверждён</span><strong>{order.publicNumber}</strong><h1>Спасибо за заказ</h1><p>Состав и стоимость {formatPrice(order.totalMinor / 100)} зафиксированы. Подтверждение отправим на {order.email}.</p><a href="/">Вернуться в магазин</a></section>
      : failed ? <section className="utility-empty"><h1>Заказ не найден</h1><p>Проверьте ссылку или откройте заказ в том же браузере, где он был оформлен.</p><a className="utility-primary-link" href="/">В магазин<span>›</span></a></section>
      : <section className="utility-empty"><h1>Загружаем заказ…</h1></section>}
  </div></main>;
}
