"use client";

import StoreHeader from "../components/StoreHeader";

const orders = [
  { id:"AM-24018", date:"12 августа 2026", status:"Доставлен", total:"8 480 ₽", items:"Сумка Soft Box · Футболка «Серия 01»" },
  { id:"AM-23871", date:"27 июля 2026", status:"Доставлен", total:"5 490 ₽", items:"Худи свободного кроя" },
  { id:"AM-23104", date:"03 мая 2026", status:"Доставлен", total:"10 980 ₽", items:"Часы Amra Mono · Кепка с вышивкой" },
];

export default function AccountPage() {
  return <main className="utility-page account-page">
    <StoreHeader />
    <div className="utility-shell">
      <div className="utility-breadcrumbs"><a href="/">Главная</a><span>·</span><span>Личный кабинет</span></div>
      <header className="utility-heading"><div><span className="section-kicker">Профиль покупателя</span><h1>Добрый день,<br />Алексей</h1></div><span>Клиент с 2024 года</span></header>

      <div className="account-grid">
        <section className="account-profile-card">
          <div className="account-avatar">А</div><div><span className="section-kicker">Личные данные</span><h2>Алексей Морозов</h2><p>alexey.morozov@example.com<br />+7 999 123-45-67</p></div><button>Изменить</button>
        </section>
        <section className="account-address-card"><span className="section-kicker">Основной адрес</span><h2>Москва</h2><p>ул. Большая Дмитровка, 12<br />Квартира 24</p><button>Настроить адреса</button></section>
        <section className="account-bonus-card"><span className="section-kicker">Amra Circle</span><strong>1 240</strong><p>бонусов доступно</p><i>Уровень · Серебро</i></section>
      </div>

      <section className="orders-section">
        <header><div><span className="section-kicker">Заказы</span><h2>История покупок</h2></div><span>{orders.length} заказа</span></header>
        <div className="orders-list">{orders.map((order, index) => <article className="order-card" key={order.id}>
          <div className="order-number"><span>0{index + 1}</span><b>{order.id}</b></div><div><span>Дата</span><strong>{order.date}</strong></div><div className="order-items"><span>Состав заказа</span><strong>{order.items}</strong></div><div><span>Статус</span><strong className="order-status">{order.status}</strong></div><div><span>Сумма</span><strong>{order.total}</strong></div><button aria-label={`Открыть заказ ${order.id}`}><svg viewBox="0 0 20 20" aria-hidden="true"><path d="m7.5 4.5 5.5 5.5-5.5 5.5" /></svg></button>
        </article>)}</div>
      </section>
    </div>
  </main>;
}
