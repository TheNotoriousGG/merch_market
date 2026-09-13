"use client";

import { type FormEvent, useEffect, useState } from "react";
import StoreHeader from "../components/StoreHeader";
import type { CustomerOrderSummary, CustomerProfile, EmailVerificationChallenge } from "../api/generated";
import { loadAccount, loadCustomerOrders, loadCustomerProfile, logoutCustomer, removeCustomerAddress, saveCustomerAddress, startEmailVerification, startPhoneAuthentication, verifyCustomerEmail, verifyPhone, type CustomerAccount, type PhoneChallenge } from "./account-api";

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
  const [email,setEmail]=useState("");
  const [emailCode,setEmailCode]=useState("");
  const [emailChallenge,setEmailChallenge]=useState<EmailVerificationChallenge|null>(null);
  const [orders,setOrders]=useState<CustomerOrderSummary[]>([]);
  const [resendIn,setResendIn]=useState(0);

  useEffect(()=>{void loadAccount().then(setAccount).catch(()=>{}).finally(()=>setLoading(false))},[]);
  useEffect(()=>{if(account){void loadCustomerProfile().then(setProfile).catch(()=>{});void loadCustomerOrders().then(result=>setOrders(result.items)).catch(()=>{})}},[account]);
  useEffect(()=>{if(resendIn<=0)return;const timer=window.setInterval(()=>setResendIn(value=>Math.max(0,value-1)),1000);return()=>window.clearInterval(timer)},[resendIn]);

  const start=async(event:FormEvent)=>{event.preventDefault();setBusy(true);setError("");const digits=phone.replace(/\D/g,"");const normalized=digits.length===11&&digits.startsWith("8")?`+7${digits.slice(1)}`:digits.startsWith("7")?`+${digits}`:phone;setPhone(normalized);try{setChallenge(await startPhoneAuthentication(normalized));setResendIn(30)}catch(reason){setError((reason as Error).message)}finally{setBusy(false)}};
  const verify=async(event:FormEvent)=>{event.preventDefault();if(!challenge)return;setBusy(true);setError("");try{setAccount(await verifyPhone(challenge.challengeId,code));setChallenge(null);setCode("")}catch(reason){setError((reason as Error).message)}finally{setBusy(false)}};
  const logout=async()=>{setBusy(true);try{await logoutCustomer();setAccount(null);setPhone("")}finally{setBusy(false)}};
  const addAddress=async(event:FormEvent<HTMLFormElement>)=>{event.preventDefault();setBusy(true);const data=new FormData(event.currentTarget);try{await saveCustomerAddress({label:String(data.get("label")),recipientName:String(data.get("recipientName")),phone:String(data.get("phone")),postalCode:String(data.get("postalCode")),city:String(data.get("city")),street:String(data.get("street")),apartment:String(data.get("apartment"))||undefined});setProfile(await loadCustomerProfile());setShowAddress(false)}catch(reason){setError((reason as Error).message)}finally{setBusy(false)}};
  const deleteAddress=async(id:string)=>{if(!confirm("Удалить этот адрес доставки?"))return;setBusy(true);try{await removeCustomerAddress(id);setProfile(await loadCustomerProfile())}finally{setBusy(false)}};
  const resendCode=async()=>{if(!challenge||resendIn>0)return;setBusy(true);setError("");try{setChallenge(await startPhoneAuthentication(challenge.phone));setCode("");setResendIn(30)}catch(reason){setError((reason as Error).message)}finally{setBusy(false)}};
  const requestEmailCode=async(event:FormEvent)=>{event.preventDefault();setBusy(true);setError("");try{setEmailChallenge(await startEmailVerification(email))}catch(reason){setError((reason as Error).message)}finally{setBusy(false)}};
  const confirmEmail=async(event:FormEvent)=>{event.preventDefault();if(!emailChallenge)return;setBusy(true);setError("");try{setProfile(await verifyCustomerEmail(emailChallenge.challengeId,emailCode));setEmailChallenge(null);setEmailCode("")}catch(reason){setError((reason as Error).message)}finally{setBusy(false)}};

  return <main className="utility-page account-page">
    <StoreHeader />
    <div className="utility-shell">
      <div className="utility-breadcrumbs"><a href="/">Главная</a><span>·</span><span>Личный кабинет</span></div>
      <header className="utility-heading"><div><span className="section-kicker">Профиль покупателя</span><h1>Личный кабинет</h1></div></header>
      {loading?<section className="account-auth-card account-auth-loading"><span>Загружаем профиль…</span></section>:account?<section className="account-grid"><section className="account-profile-card"><span className="account-avatar">А</span><div><span className="section-kicker">Ваш профиль</span><h2>{profile?.displayName||account.displayName||"Покупатель Amra"}</h2><p>{account.phone}</p>{profile?.email&&<p>{profile.email}{profile.emailVerified?" · подтверждён":" · требует подтверждения"}</p>}</div><button disabled={busy} onClick={()=>void logout()}>Выйти</button></section><section className="account-address-card"><span className="section-kicker">Доставка</span><h2>Адреса</h2>{profile?.addresses.length?profile.addresses.map(address=><div className="account-address-row" key={address.id}><div><strong>{address.label}</strong><p>{address.city}, {address.street}{address.apartment?`, ${address.apartment}`:""}</p></div><button disabled={busy} onClick={()=>void deleteAddress(address.id)}>Удалить</button></div>):<p>Добавьте адрес, чтобы быстрее оформить следующий заказ.</p>}<button disabled={busy} onClick={()=>setShowAddress(value=>!value)}>{showAddress?"Закрыть":"Добавить адрес"}</button>{showAddress&&<form className="account-address-form" onSubmit={addAddress}><input name="label" aria-label="Название адреса" placeholder="Например, Дом" required/><input name="recipientName" aria-label="Получатель" placeholder="Имя и фамилия" required/><input name="phone" aria-label="Телефон получателя" type="tel" defaultValue={account.phone} required/><input name="postalCode" aria-label="Индекс" placeholder="Индекс" required/><input name="city" aria-label="Город" placeholder="Город" required/><input name="street" aria-label="Улица и дом" placeholder="Улица и дом" required/><input name="apartment" aria-label="Квартира" placeholder="Квартира (необязательно)"/><button disabled={busy}>Сохранить адрес</button></form>}</section><section className="account-address-card"><span className="section-kicker">Безопасность</span><h2>Email</h2>{profile?.emailVerified?<p>{profile.email} подтверждён</p>:emailChallenge?<form className="account-email-form" onSubmit={confirmEmail}><input aria-label="Код подтверждения email" value={emailCode} onChange={event=>setEmailCode(event.target.value.replace(/\D/g,""))} maxLength={6} placeholder="Код из письма" required/>{emailChallenge.developmentCode&&<small>Локальный код: {emailChallenge.developmentCode}</small>}<button disabled={busy||emailCode.length!==6}>Подтвердить</button></form>:<form className="account-email-form" onSubmit={requestEmailCode}><input aria-label="Email" type="email" value={email} onChange={event=>setEmail(event.target.value)} placeholder="you@example.com" required/><button disabled={busy}>Получить код</button></form>}</section><section className="account-bonus-card"><span className="section-kicker">Заказы</span><strong>{orders.length}</strong><p>История заказов</p>{orders.length?orders.map(order=><a key={order.publicNumber} href={`/orders/${order.publicNumber}`}><b>{order.publicNumber}</b><span>{order.status==="CONFIRMED"?"Подтверждён":"Отменён"} · {order.itemCount} шт. · {(order.totalMinor/100).toLocaleString("ru-RU")} ₽</span></a>):<i>После первой покупки заказ появится здесь</i>}</section></section>:<section className="account-auth-layout">
        <div className="account-auth-intro"><span className="section-kicker">Быстрый вход</span><h2>{challenge?"Подтвердите номер":"Войдите или зарегистрируйтесь"}</h2><p>{challenge?`Мы отправили шестизначный код на ${challenge.phone}`:"Введите номер телефона. Если вы у нас впервые, профиль создастся автоматически."}</p><div className="account-auth-benefits"><span>Заказы и статусы в одном месте</span><span>Быстрое оформление покупок</span><span>Избранное всегда под рукой</span></div></div>
        <div className="account-auth-card">
          {!challenge?<form onSubmit={start}><label htmlFor="account-phone">Номер телефона</label><input id="account-phone" type="tel" inputMode="tel" autoComplete="tel" placeholder="+7 999 123-45-67" value={phone} onChange={event=>setPhone(event.target.value)} required/><button disabled={busy}>{busy?"Отправляем код…":"Получить код"}</button><small>Нажимая кнопку, вы соглашаетесь с условиями обработки персональных данных.</small></form>:<form onSubmit={verify}><label htmlFor="account-code">Код из SMS</label><input id="account-code" className="account-code-input" inputMode="numeric" autoComplete="one-time-code" maxLength={6} placeholder="000000" value={code} onChange={event=>setCode(event.target.value.replace(/\D/g,""))} required/><small>{code.length<6?`Введите ещё ${6-code.length} цифр${6-code.length===1?"у":"ы"}`:"Код готов к проверке"}</small><button disabled={busy||code.length!==6}>{busy?"Проверяем…":"Подтвердить и войти"}</button>{challenge.developmentCode&&<div className="account-dev-code"><span>Код для локальной разработки</span><strong>{challenge.developmentCode}</strong></div>}<button className="account-auth-back" type="button" disabled={busy||resendIn>0} onClick={()=>void resendCode()}>{resendIn>0?`Отправить код повторно через ${resendIn} сек.`:"Отправить код повторно"}</button><button className="account-auth-back" type="button" onClick={()=>{setChallenge(null);setCode("");setError("")}}>Изменить номер</button></form>}
          {error&&<p className="account-auth-error" role="alert">{error}</p>}
        </div>
      </section>}
    </div>
  </main>;
}
