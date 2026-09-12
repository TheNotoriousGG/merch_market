"use client";

import { type FormEvent, useEffect, useState } from "react";
import StoreHeader from "../components/StoreHeader";
import type { CustomerProfile } from "../api/generated";
import { loadAccount, loadCustomerProfile, logoutCustomer, removeCustomerAddress, saveCustomerAddress, startPhoneAuthentication, verifyPhone, type CustomerAccount, type PhoneChallenge } from "./account-api";

export default function AccountPage() {
  const [account,setAccount]=useState<CustomerAccount|null>(null);
  const [challenge,setChallenge]=useState<PhoneChallenge|null>(null);
  const [phone,setPhone]=useState("");
  const [code,setCode]=useState("");
  const [loading,setLoading]=useState(true);
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState("");
  const [profile,setProfile]=useState<CustomerProfile|null>(null);
  const [showAddress,setShowAddress]=useState(false);

  useEffect(()=>{void loadAccount().then(setAccount).catch(()=>{}).finally(()=>setLoading(false))},[]);
  useEffect(()=>{if(account)void loadCustomerProfile().then(setProfile).catch(()=>{})},[account]);

  const start=async(event:FormEvent)=>{event.preventDefault();setBusy(true);setError("");try{setChallenge(await startPhoneAuthentication(phone))}catch(reason){setError((reason as Error).message)}finally{setBusy(false)}};
  const verify=async(event:FormEvent)=>{event.preventDefault();if(!challenge)return;setBusy(true);setError("");try{setAccount(await verifyPhone(challenge.challengeId,code));setChallenge(null);setCode("")}catch(reason){setError((reason as Error).message)}finally{setBusy(false)}};
  const logout=async()=>{setBusy(true);try{await logoutCustomer();setAccount(null);setPhone("")}finally{setBusy(false)}};
  const addAddress=async(event:FormEvent<HTMLFormElement>)=>{event.preventDefault();setBusy(true);const data=new FormData(event.currentTarget);try{await saveCustomerAddress({label:String(data.get("label")),recipientName:String(data.get("recipientName")),phone:String(data.get("phone")),postalCode:String(data.get("postalCode")),city:String(data.get("city")),street:String(data.get("street")),apartment:String(data.get("apartment"))||undefined});setProfile(await loadCustomerProfile());setShowAddress(false)}catch(reason){setError((reason as Error).message)}finally{setBusy(false)}};
  const deleteAddress=async(id:string)=>{setBusy(true);try{await removeCustomerAddress(id);setProfile(await loadCustomerProfile())}finally{setBusy(false)}};

  return <main className="utility-page account-page">
    <StoreHeader />
    <div className="utility-shell">
      <div className="utility-breadcrumbs"><a href="/">Главная</a><span>·</span><span>Личный кабинет</span></div>
      <header className="utility-heading"><div><span className="section-kicker">Профиль покупателя</span><h1>Личный кабинет</h1></div></header>
      {loading?<section className="account-auth-card account-auth-loading"><span>Загружаем профиль…</span></section>:account?<section className="account-grid"><section className="account-profile-card"><span className="account-avatar">А</span><div><span className="section-kicker">Ваш профиль</span><h2>{profile?.displayName||account.displayName||"Покупатель Amra"}</h2><p>{account.phone}</p>{profile?.email&&<p>{profile.email}{profile.emailVerified?" · подтверждён":" · требует подтверждения"}</p>}</div><button disabled={busy} onClick={()=>void logout()}>Выйти</button></section><section className="account-address-card"><span className="section-kicker">Доставка</span><h2>Адреса</h2>{profile?.addresses.length?profile.addresses.map(address=><div className="account-address-row" key={address.id}><div><strong>{address.label}</strong><p>{address.city}, {address.street}{address.apartment?`, ${address.apartment}`:""}</p></div><button disabled={busy} onClick={()=>void deleteAddress(address.id)}>Удалить</button></div>):<p>Добавьте адрес, чтобы быстрее оформить следующий заказ.</p>}<button disabled={busy} onClick={()=>setShowAddress(value=>!value)}>{showAddress?"Закрыть":"Добавить адрес"}</button>{showAddress&&<form className="account-address-form" onSubmit={addAddress}><input name="label" placeholder="Название: Дом" required/><input name="recipientName" placeholder="Получатель" required/><input name="phone" type="tel" defaultValue={account.phone} required/><input name="postalCode" placeholder="Индекс" required/><input name="city" placeholder="Город" required/><input name="street" placeholder="Улица и дом" required/><input name="apartment" placeholder="Квартира (необязательно)"/><button disabled={busy}>Сохранить адрес</button></form>}</section><section className="account-bonus-card"><span className="section-kicker">Заказы</span><strong>0</strong><p>История заказов</p><i>Всё впереди</i></section></section>:<section className="account-auth-layout">
        <div className="account-auth-intro"><span className="section-kicker">Быстрый вход</span><h2>{challenge?"Подтвердите номер":"Войдите или зарегистрируйтесь"}</h2><p>{challenge?`Мы отправили шестизначный код на ${challenge.phone}`:"Введите номер телефона. Если вы у нас впервые, профиль создастся автоматически."}</p><div className="account-auth-benefits"><span>Заказы и статусы в одном месте</span><span>Быстрое оформление покупок</span><span>Избранное всегда под рукой</span></div></div>
        <div className="account-auth-card">
          {!challenge?<form onSubmit={start}><label htmlFor="account-phone">Номер телефона</label><input id="account-phone" type="tel" inputMode="tel" autoComplete="tel" placeholder="+7 999 123-45-67" value={phone} onChange={event=>setPhone(event.target.value)} required/><button disabled={busy}>{busy?"Отправляем код…":"Получить код"}</button><small>Нажимая кнопку, вы соглашаетесь с условиями обработки персональных данных.</small></form>:<form onSubmit={verify}><label htmlFor="account-code">Код из SMS</label><input id="account-code" className="account-code-input" inputMode="numeric" autoComplete="one-time-code" maxLength={6} placeholder="000000" value={code} onChange={event=>setCode(event.target.value.replace(/\D/g,""))} required/><button disabled={busy||code.length!==6}>{busy?"Проверяем…":"Подтвердить и войти"}</button>{challenge.developmentCode&&<div className="account-dev-code"><span>Код для локальной разработки</span><strong>{challenge.developmentCode}</strong></div>}<button className="account-auth-back" type="button" onClick={()=>{setChallenge(null);setCode("");setError("")}}>Изменить номер</button></form>}
          {error&&<p className="account-auth-error" role="alert">{error}</p>}
        </div>
      </section>}
    </div>
  </main>;
}
