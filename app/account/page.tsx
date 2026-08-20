"use client";

import StoreHeader from "../components/StoreHeader";

export default function AccountPage() {
  return <main className="utility-page account-page">
    <StoreHeader />
    <div className="utility-shell">
      <div className="utility-breadcrumbs"><a href="/">Главная</a><span>·</span><span>Личный кабинет</span></div>
      <header className="utility-heading"><div><span className="section-kicker">Профиль покупателя</span><h1>Личный кабинет</h1></div></header>
      <section className="utility-empty"><span>Профиль пока не подключён</span><h2>Здесь появятся ваши данные и заказы</h2><p>Мы покажем их после подключения покупательского аккаунта к backend.</p></section>
    </div>
  </main>;
}
