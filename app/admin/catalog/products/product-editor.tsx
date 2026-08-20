"use client";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, Category, commandHeaders, Product } from "../../api";
import styles from "../../admin.module.css";

type Form = { name:string; slug:string; shortDescription:string; description:string; primaryCategoryId:string };
type Upload = { objectKey:string; uploadUrl:string; contentType:string };
const empty:Form = { name:"", slug:"", shortDescription:"", description:"", primaryCategoryId:"" };

export default function ProductEditor({ id }: { id?:string }) {
  const router=useRouter();
  const [form,setForm]=useState<Form>(empty), [categories,setCategories]=useState<Category[]>([]);
  const [etag,setEtag]=useState<string|null>(null), [status,setStatus]=useState("DRAFT");
  const [error,setError]=useState(""), [message,setMessage]=useState(""), [dirty,setDirty]=useState(false);
  const [sku,setSku]=useState(""), [label,setLabel]=useState(""), [file,setFile]=useState<File|null>(null), [alt,setAlt]=useState("");
  const update=(key:keyof Form,value:string)=>{setForm(v=>({...v,[key]:value}));setDirty(true)};

  useEffect(()=>{
    void api<{items:Category[]}>("/admin/catalog/categories").then(r=>setCategories(r.data.items));
    if(id) void api<Product>(`/admin/catalog/products/${id}`).then(r=>{setForm({name:r.data.name,slug:r.data.slug,shortDescription:r.data.shortDescription,description:r.data.description,primaryCategoryId:r.data.primaryCategoryId});setStatus(r.data.status);setEtag(r.etag)}).catch(e=>setError(e.message));
  },[id]);
  useEffect(()=>{const warn=(e:BeforeUnloadEvent)=>{if(dirty)e.preventDefault()};addEventListener("beforeunload",warn);return()=>removeEventListener("beforeunload",warn)},[dirty]);

  const save=async(e:FormEvent)=>{e.preventDefault();try{setError("");const body={...form,categoryIds:[form.primaryCategoryId],collectionIds:[],characteristics:[]};if(id){const r=await api<Product>(`/admin/catalog/products/${id}`,{method:"PATCH",headers:commandHeaders(etag),body:JSON.stringify(body)});setEtag(r.etag);setDirty(false);setMessage("Основное сохранено")}else{const r=await api<Product>("/admin/catalog/products",{method:"POST",headers:commandHeaders(),body:JSON.stringify(body)});setDirty(false);router.push(`/admin/catalog/products/${r.data.id}`)}}catch(err){setError((err as Error).message)}};
  const createVariant=async()=>{if(!id)return;try{const r=await api<object>(`/admin/catalog/products/${id}/variants`,{method:"POST",headers:commandHeaders(etag),body:JSON.stringify({sku,label,displayOrder:0,attributes:[{definitionCode:"size",definitionName:"Размер",type:"SIZE",valueCode:label.toLowerCase(),label}]})});setEtag(r.etag);setSku("");setLabel("");setMessage("Вариант создан; SKU теперь неизменяем")}catch(err){setError((err as Error).message)}};
  const addMedia=async()=>{if(!id||!file)return;try{setMessage("Загружаю файл в MinIO…");const upload=await api<Upload>(`/admin/catalog/products/${id}/media/uploads`,{method:"POST",body:JSON.stringify({fileName:file.name,contentType:file.type,size:file.size})});const put=await fetch(upload.data.uploadUrl,{method:"PUT",headers:{"Content-Type":file.type},body:file});if(!put.ok)throw new Error(`MinIO вернул ${put.status}`);const image=await createImageBitmap(file);const r=await api<object>(`/admin/catalog/products/${id}/media`,{method:"POST",headers:commandHeaders(etag),body:JSON.stringify({objectKey:upload.data.objectKey,contentType:file.type,width:image.width,height:image.height,alt,displayOrder:0,primary:true})});image.close();setEtag(r.etag);setFile(null);setMessage("Файл загружен и привязан как primary-медиа")}catch(err){setError((err as Error).message)}};
  const publish=async()=>{if(!id||!confirm("Опубликовать товар на витрине?"))return;try{const r=await api<Product>(`/admin/catalog/products/${id}/publish`,{method:"POST",headers:commandHeaders(etag)});setEtag(r.etag);setStatus(r.data.status);setMessage("Товар опубликован")}catch(err){setError((err as Error).message)}};

  return <><div className={styles.heading}><div><p className={styles.eyebrow}>{id?`Товар · ${status}`:"Новый черновик"}</p><h1>{id?form.name||"Редактор товара":"Добавить товар"}</h1><p>Явное сохранение по секциям; сервер проверит доменные правила.</p></div></div><div className={styles.steps}>{["Основное","Категории","Варианты / SKU","Медиа","Публикация"].map(x=><div className={styles.step} key={x}>{x}</div>)}</div>{error&&<div className={styles.error}>{error}</div>}{message&&<div className={styles.message}>{message}</div>}
    <form className={styles.panel} onSubmit={save}><h2>Основное и категории</h2><div className={styles.formGrid}><Field label="Название"><input required className={styles.input} value={form.name} onChange={e=>update("name",e.target.value)}/></Field><Field label="Slug"><input required className={styles.input} value={form.slug} onChange={e=>update("slug",e.target.value)}/></Field><Field label="Короткое описание" full><input required className={styles.input} value={form.shortDescription} onChange={e=>update("shortDescription",e.target.value)}/></Field><Field label="Описание" full><textarea required className={styles.textarea} value={form.description} onChange={e=>update("description",e.target.value)}/></Field><Field label="Основная категория"><select required className={styles.select} value={form.primaryCategoryId} onChange={e=>update("primaryCategoryId",e.target.value)}><option value="">Выберите</option>{categories.map(c=><option key={c.id} value={c.id}>{c.name} · {c.status}</option>)}</select></Field></div><div className={styles.sectionActions}><button className={styles.primary}>{id?"Сохранить основное":"Создать черновик"}</button></div></form>
    {id&&<><section className={styles.panel}><h2>Новый вариант</h2><div className={styles.formGrid}><Field label="SKU (не меняется после создания)"><input className={styles.input} value={sku} onChange={e=>setSku(e.target.value.toUpperCase())} placeholder="AMRA-HOODIE-BLACK-M"/></Field><Field label="Размер / подпись"><input className={styles.input} value={label} onChange={e=>setLabel(e.target.value)} placeholder="M"/></Field></div><div className={styles.sectionActions}><button className={styles.secondary} onClick={()=>void createVariant()}>Добавить вариант</button></div></section>
      <section className={styles.panel}><h2>Медиа</h2><p className={styles.hint}>Файл загружается напрямую в MinIO по короткоживущему URL; storage secret остаётся на backend.</p><div className={styles.formGrid}><Field label="Изображение · JPEG, PNG или WebP до 10 МБ"><input className={styles.input} type="file" accept="image/jpeg,image/png,image/webp" onChange={e=>setFile(e.target.files?.[0]??null)}/></Field><Field label="Alt-текст"><input className={styles.input} value={alt} onChange={e=>setAlt(e.target.value)}/></Field></div><div className={styles.sectionActions}><button disabled={!file||!alt} className={styles.secondary} onClick={()=>void addMedia()}>Загрузить и привязать</button></div></section>
      <section className={styles.panel}><h2>Публикация</h2><p className={styles.hint}>Backend проверит категорию, вариант, характеристики, primary media и namespace.</p><div className={styles.sectionActions}><button className={styles.danger} onClick={()=>void publish()} disabled={status==="ACTIVE"}>Опубликовать</button></div></section></>}
  </>;
}

function Field({label,full=false,children}:{label:string;full?:boolean;children:React.ReactNode}){return <div className={`${styles.field} ${full?styles.full:""}`}><label>{label}</label>{children}</div>}
