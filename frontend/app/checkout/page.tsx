"use client";

import { FormEvent, useEffect, useState } from "react";
import type { CustomerAddress } from "../api/generated";
import { cookie, customerApi, orderingApi } from "../api/client";
import StoreHeader from "../components/StoreHeader";
import { formatPrice, useShop } from "../components/ShopState";
import { deliveryPrice } from "../lib/order-totals";
import styles from "./checkout.module.css";

type Fields = { email:string; recipientName:string; phone:string; postalCode:string; city:string; street:string; apartment:string };
const empty:Fields = {email:"",recipientName:"",phone:"",postalCode:"",city:"",street:"",apartment:""};

function normalizePhone(value:string) {
  const digits=value.replace(/\D/g,"");
  if(digits.length===11&&digits.startsWith("8"))return `+7${digits.slice(1)}`;
  if(digits.startsWith("7"))return `+${digits}`;
  return value.trim().startsWith("+")?`+${digits}`:digits;
}

export default function CheckoutPage() {
  const {cart,cartTotal,cartVersion,loading,refreshCart}=useShop();
  const [busy,setBusy]=useState(false),[error,setError]=useState("");
  const [fields,setFields]=useState<Fields>(empty),[fieldErrors,setFieldErrors]=useState<Partial<Record<keyof Fields,string>>>({});
  const [addresses,setAddresses]=useState<CustomerAddress[]>([]),[selectedAddress,setSelectedAddress]=useState("");
  const delivery=deliveryPrice(cartTotal),total=cartTotal+delivery;

  useEffect(()=>{void customerApi.getCustomerProfile().then(profile=>{setAddresses(profile.addresses);setFields(current=>({...current,email:profile.email??current.email,recipientName:profile.displayName??current.recipientName,phone:profile.phone??current.phone}))}).catch(()=>{})},[]);
  const update=(key:keyof Fields,value:string)=>{setFields(current=>({...current,[key]:value}));setFieldErrors(current=>({...current,[key]:undefined}))};
  const chooseAddress=(id:string)=>{setSelectedAddress(id);const address=addresses.find(item=>item.id===id);if(address)setFields(current=>({...current,recipientName:address.recipientName,phone:address.phone,postalCode:address.postalCode,city:address.city,street:address.street,apartment:address.apartment??""}))};

  async function submit(event:FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized={...fields,phone:normalizePhone(fields.phone)};
    const nextErrors:Partial<Record<keyof Fields,string>>={};
    if(!/^\S+@\S+\.\S+$/.test(normalized.email))nextErrors.email="Введите корректный email";
    if(!normalized.recipientName.trim())nextErrors.recipientName="Введите имя получателя";
    if(!/^\+[1-9][0-9]{7,14}$/.test(normalized.phone))nextErrors.phone="Введите номер в формате +7 999 123-45-67";
    if(!normalized.postalCode.trim())nextErrors.postalCode="Введите индекс";
    if(!normalized.city.trim())nextErrors.city="Введите город";
    if(!normalized.street.trim())nextErrors.street="Введите улицу и дом";
    if(Object.keys(nextErrors).length){setFields(normalized);setFieldErrors(nextErrors);document.querySelector<HTMLElement>(`[name="${Object.keys(nextErrors)[0]}"]`)?.focus();return}
    setBusy(true);setError("");setFields(normalized);
    const storageKey=`amra-checkout-key-v${cartVersion}`;
    const idempotencyKey=localStorage.getItem(storageKey)??`checkout-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    localStorage.setItem(storageKey,idempotencyKey);
    try {
      const created=await orderingApi.checkout({idempotencyKey,xAMRACSRF:decodeURIComponent(cookie("AMRA_CSRF")||"browser-csrf-token"),checkoutRequest:{cartVersion,email:normalized.email,recipientName:normalized.recipientName.trim(),phone:normalized.phone,postalCode:normalized.postalCode.trim(),city:normalized.city.trim(),street:normalized.street.trim(),apartment:normalized.apartment.trim()||undefined}});
      if(created.guestAccessToken)localStorage.setItem(`amra-order-token-${created.publicNumber}`,created.guestAccessToken);
      localStorage.removeItem(storageKey);await refreshCart();window.location.assign(`/orders/${encodeURIComponent(created.publicNumber)}`);
    } catch(reason) {const message=(reason as Error).message;setError(message.includes("stock")||message.includes("остат")?"Некоторые товары закончились. Вернитесь в корзину и обновите состав заказа.":message.includes("version")||message.includes("измен")?"Корзина изменилась. Обновите страницу и проверьте заказ.":"Не удалось оформить заказ. Проверьте данные и соединение — повторная отправка безопасна.")
    } finally {setBusy(false)}
  }

  const field=(key:keyof Fields,label:string,props:React.InputHTMLAttributes<HTMLInputElement>={})=><label className={key==="email"||key==="street"||key==="apartment"?styles.wide:undefined}>{label}<input {...props} name={key} value={fields[key]} aria-invalid={Boolean(fieldErrors[key])} aria-describedby={fieldErrors[key]?`${key}-error`:undefined} onChange={event=>update(key,event.target.value)} onBlur={key==="phone"?()=>update("phone",normalizePhone(fields.phone)):undefined}/>{fieldErrors[key]&&<small id={`${key}-error`} className={styles.fieldError}>{fieldErrors[key]}</small>}</label>;
  return <main className="utility-page"><StoreHeader/><div className="utility-shell"><div className="utility-breadcrumbs"><a href="/">Главная</a><span>·</span><a href="/cart">Корзина</a><span>·</span><span>Оформление</span></div><header className="utility-heading"><div><span className="section-kicker">Последний шаг</span><h1>Оформление заказа</h1></div></header>
    {!loading&&cart.length===0?<section className="utility-empty"><h2>Корзина пуста</h2><p>Добавьте товары перед оформлением заказа.</p><a className="utility-primary-link" href="/">Перейти к покупкам<span>›</span></a></section>:<div className={styles.layout}><form className={styles.form} noValidate onSubmit={submit}><h2>Получатель и доставка</h2>{addresses.length>0&&<label className={styles.savedAddress}>Сохранённый адрес<select value={selectedAddress} onChange={event=>chooseAddress(event.target.value)}><option value="">Заполнить другой адрес</option>{addresses.map(address=><option key={address.id} value={address.id}>{address.label} · {address.city}, {address.street}</option>)}</select></label>}<div className={styles.fields}>
      {field("email","Email",{type:"email",autoComplete:"email"})}{field("recipientName","Имя получателя",{autoComplete:"name",maxLength:160})}{field("phone","Телефон",{type:"tel",autoComplete:"tel",placeholder:"+7 999 123-45-67"})}{field("postalCode","Индекс",{autoComplete:"postal-code",maxLength:20})}{field("city","Город",{autoComplete:"address-level2",maxLength:120})}{field("street","Улица и дом",{autoComplete:"street-address",maxLength:240})}{field("apartment","Квартира или офис (необязательно)",{maxLength:40})}
    </div>{error&&<p className={styles.error} role="alert">{error}</p>}<p className={styles.consent}>Нажимая «Подтвердить заказ», вы принимаете <a href="/offer">условия оферты</a> и соглашаетесь с <a href="/privacy">обработкой персональных данных</a>.</p><button className={styles.submit} disabled={busy||loading}>{busy?"Оформляем…":"Подтвердить заказ"}<span>›</span></button></form>
    <aside className={styles.summary}><span className="section-kicker">Состав заказа</span><h2>{formatPrice(total)}</h2><ul>{cart.map(line=><li key={line.variantId}><span>{line.name}<small>{line.variantLabel} · {line.quantity} шт.</small></span><strong>{formatPrice(line.lineSubtotal)}</strong></li>)}</ul><dl className={styles.breakdown}><div><dt>Товары</dt><dd>{formatPrice(cartTotal)}</dd></div><div><dt>Доставка</dt><dd>{delivery?formatPrice(delivery):"Бесплатно"}</dd></div></dl><div className={styles.total}><span>К оплате</span><span>{formatPrice(total)}</span></div></aside></div>}
  </div></main>;
}
