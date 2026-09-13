"use client";

import {useEffect,useMemo,useState} from "react";
import {useRouter} from "next/navigation";
import type {CatalogProductDetail,CatalogProductVariant} from "../../api/generated";
import {catalogApi,inventoryApi} from "../../api/client";
import {CartButtonContent,FavoriteIcon} from "../../components/ShopIcons";
import {useShop} from "../../components/ShopState";
import {toShopProduct,type CatalogProduct} from "../catalog-data";

type Choice={valueCode:string;label:string;colorHex?:string|null};
const attr=(variant:CatalogProductVariant,type:"COLOR"|"SIZE"|"DIMENSION")=>variant.attributes.find(item=>item.type===type);
const choices=(variants:CatalogProductVariant[],type:"COLOR"|"SIZE",fallback?:"DIMENSION")=>Array.from(new Map(variants.flatMap(variant=>{const value=attr(variant,type)??(fallback?attr(variant,fallback):undefined);return value?[[value.valueCode,value] as const]:[]})).values());

export default function CatalogProductDialog({product,onClose}:{product:CatalogProduct;onClose:()=>void}) {
  const router=useRouter(),{cart,addToCart,toggleFavorite,isFavorite}=useShop();
  const [detail,setDetail]=useState<CatalogProductDetail|null>(null),[available,setAvailable]=useState<Set<string>>(new Set()),[loading,setLoading]=useState(true),[error,setError]=useState("");
  const [selectedColor,setSelectedColor]=useState(""),[selectedSize,setSelectedSize]=useState("");
  const cartLine=cart.find(item=>item.id===product.id),liked=isFavorite(product.id),variants=useMemo(()=>detail?.variants??[],[detail]);
  const colors=useMemo(()=>choices(variants,"COLOR"),[variants]),sizes=useMemo(()=>choices(variants,"SIZE","DIMENSION"),[variants]);

  useEffect(()=>{let active=true;if(!product.slug){setError("Карточка товара недоступна");setLoading(false);return}void catalogApi.getCatalogProduct({slug:product.slug}).then(async data=>{if(!active)return;setDetail(data);const stock=await inventoryApi.getInventoryAvailability({variantId:data.variants.map(item=>item.id)});if(!active)return;setAvailable(new Set(stock.items.filter(item=>item.status==="IN_STOCK").map(item=>item.variantId)));const colorOptions=choices(data.variants,"COLOR"),sizeOptions=choices(data.variants,"SIZE","DIMENSION");if(colorOptions.length===1)setSelectedColor(colorOptions[0].valueCode);if(sizeOptions.length===1)setSelectedSize(sizeOptions[0].valueCode)}).catch(()=>active&&setError("Не удалось загрузить варианты товара. Попробуйте ещё раз.")).finally(()=>active&&setLoading(false));return()=>{active=false}},[product.slug]);
  useEffect(()=>{const close=(event:KeyboardEvent)=>event.key==="Escape"&&onClose();const overflow=document.body.style.overflow;document.body.style.overflow="hidden";window.addEventListener("keydown",close);return()=>{document.body.style.overflow=overflow;window.removeEventListener("keydown",close)}},[onClose]);

  const selectedVariant=variants.find(variant=>(!colors.length||attr(variant,"COLOR")?.valueCode===selectedColor)&&(!sizes.length||(attr(variant,"SIZE")??attr(variant,"DIMENSION"))?.valueCode===selectedSize));
  const optionAvailable=(kind:"color"|"size",code:string)=>variants.some(variant=>available.has(variant.id)&&(kind==="color"?attr(variant,"COLOR")?.valueCode===code&&(!selectedSize||(attr(variant,"SIZE")??attr(variant,"DIMENSION"))?.valueCode===selectedSize):(attr(variant,"SIZE")??attr(variant,"DIMENSION"))?.valueCode===code&&(!selectedColor||attr(variant,"COLOR")?.valueCode===selectedColor)));
  const add=()=>{if(cartLine){router.push("/cart");return}if(!selectedVariant||!available.has(selectedVariant.id)){setError("Выберите доступный цвет и размер.");return}setError("");addToCart(toShopProduct(product),selectedVariant.id)};

  return <div className="product-dialog-backdrop" role="presentation" onClick={event=>event.target===event.currentTarget&&onClose()}><section className="product-dialog" role="dialog" aria-modal="true" aria-labelledby="product-dialog-title">
    <button className="product-dialog-close" onClick={onClose} aria-label="Закрыть карточку">×</button><div className={`product-dialog-visual ${product.colorClass}`}><span className="new-badge">{product.isNew?"NEW":"AMRA"}</span>{product.imageUrl?<img className="catalog-product-photo" src={product.imageUrl} alt=""/>:<span className={`product-object product-object-${product.art}`} aria-hidden="true"/>}</div>
    <div className="product-dialog-copy"><span className="section-kicker">Амра Шоп</span><h2 id="product-dialog-title">{product.name}</h2><strong className="product-dialog-price">{product.priceAvailable===false?"Цена появится позже":`${product.price.toLocaleString("ru-RU")} ₽`}</strong><p>{detail?.description||product.description||"Загружаем описание товара…"}</p>{loading&&<p role="status">Проверяем варианты и остатки…</p>}
      {colors.length>0&&<Options label="Цвет" values={colors} selected={selectedColor} available={code=>optionAvailable("color",code)} onSelect={code=>{setSelectedColor(code);setError("")}}/>}{sizes.length>0&&<Options label="Размер" values={sizes} selected={selectedSize} available={code=>optionAvailable("size",code)} onSelect={code=>{setSelectedSize(code);setError("")}}/>}
      {error&&<p className="product-dialog-error" role="alert">{error}</p>}<div className="product-dialog-actions"><button className={`cart-action-button ${cartLine?"is-added":""}`} onClick={add} disabled={loading||product.priceAvailable===false}>{cartLine?<CartButtonContent added label="В корзине · Перейти"/>:<CartButtonContent added={false} label={selectedVariant&&available.has(selectedVariant.id)?"В корзину":"Выберите доступный вариант"}/>}</button><button className={liked?"liked":""} onClick={()=>toggleFavorite(toShopProduct(product))} aria-pressed={liked} aria-label={`${liked?"Убрать из":"Добавить в"} избранное`}><FavoriteIcon active={liked}/></button></div><small>Недоступные варианты отмечены и не выбираются.</small>
    </div></section></div>;
}

function Options({label,values,selected,available,onSelect}:{label:string;values:Choice[];selected:string;available:(code:string)=>boolean;onSelect:(code:string)=>void}){return <div className="product-dialog-sizes"><span>Выберите {label.toLowerCase()}</span><div>{values.map(value=>{const enabled=available(value.valueCode);return <button className={selected===value.valueCode?"active":""} disabled={!enabled} onClick={()=>onSelect(value.valueCode)} aria-pressed={selected===value.valueCode} title={enabled?undefined:"Нет в наличии"} key={value.valueCode}>{value.label}</button>})}</div></div>}
