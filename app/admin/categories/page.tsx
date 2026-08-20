"use client";
/* eslint-disable react-hooks/set-state-in-effect, jsx-a11y/label-has-associated-control */
import { FormEvent, useEffect, useState } from "react";
import { api, Category, commandHeaders } from "../api";
import { catalogSlug } from "../catalog-slug";
import styles from "../admin.module.css";

const etag = (version: number) => `"v${version}"`;

export default function Categories() {
  const [items, setItems] = useState<Category[]>([]);
  const [name, setName] = useState("");
  const [parentId, setParentId] = useState("");
  const [editingId, setEditingId] = useState("");
  const [editingName, setEditingName] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [showArchive, setShowArchive] = useState(false);

  const load = async () => { try { setItems((await api<{items: Category[]}>("/admin/catalog/categories")).data.items); } catch (reason) { setError((reason as Error).message); } };
  useEffect(() => { void load(); }, []);

  const create = async (event: FormEvent) => {
    event.preventDefault(); setBusy(true); setError(""); setMessage("");
    try {
      const slug = catalogSlug(name);
      if (!slug) throw new Error("Введите название категории");
      await api<Category>("/admin/catalog/categories", { method: "POST", headers: commandHeaders(), body: JSON.stringify({ ...(parentId ? {parentId} : {}), slug, name: name.trim(), displayOrder: items.filter((item) => (item.parentId ?? "") === parentId).length }) });
      setName(""); setParentId(""); setMessage("Категория добавлена"); await load();
    } catch (reason) { setError((reason as Error).message); } finally { setBusy(false); }
  };

  const rename = async (item: Category) => {
    if (!editingName.trim()) return; setBusy(true); setError("");
    try { await api<Category>(`/admin/catalog/categories/${item.id}`, { method: "PATCH", headers: commandHeaders(etag(item.version)), body: JSON.stringify({name: editingName.trim()}) }); setEditingId(""); setMessage("Название сохранено"); await load(); }
    catch (reason) { setError((reason as Error).message); } finally { setBusy(false); }
  };

  const toggle = async (item: Category) => {
    setBusy(true); setError("");
    try { const status = item.status === "ACTIVE" ? "HIDDEN" : "ACTIVE"; await api<Category>(`/admin/catalog/categories/${item.id}`, { method: "PATCH", headers: commandHeaders(etag(item.version)), body: JSON.stringify({status}) }); setMessage(status === "ACTIVE" ? "Категория опубликована" : "Категория скрыта"); await load(); }
    catch (reason) { setError((reason as Error).message); } finally { setBusy(false); }
  };

  const archive = async (item: Category) => {
    if (!confirm(`Перенести категорию «${item.name}» в архив?`)) return;
    setBusy(true); setError("");
    try { await api<Category>(`/admin/catalog/categories/${item.id}`, { method: "PATCH", headers: commandHeaders(etag(item.version)), body: JSON.stringify({status:"ARCHIVED"}) }); setMessage("Категория перенесена в архив"); await load(); }
    catch (reason) { setError((reason as Error).message); } finally { setBusy(false); }
  };

  const restore = async (item: Category) => {
    setBusy(true); setError("");
    try { await api<Category>(`/admin/catalog/categories/${item.id}`, { method: "PATCH", headers: commandHeaders(etag(item.version)), body: JSON.stringify({status:"HIDDEN"}) }); setMessage("Категория восстановлена и пока скрыта с витрины"); await load(); }
    catch (reason) { setError((reason as Error).message); } finally { setBusy(false); }
  };

  const remove = async (item: Category) => {
    if (!confirm(`Удалить категорию «${item.name}» навсегда? Это возможно только если в ней нет товаров и подразделов.`)) return;
    setBusy(true); setError("");
    try { await api<void>(`/admin/catalog/categories/${item.id}`, { method: "DELETE", headers: commandHeaders(etag(item.version)) }); setMessage("Категория удалена"); await load(); }
    catch (reason) { setError((reason as Error).message); } finally { setBusy(false); }
  };

  const visibleItems = items.filter((item) => showArchive ? item.status === "ARCHIVED" : item.status !== "ARCHIVED");

  return <>
    <div className={styles.heading}><div><p className={styles.eyebrow}>Каталог</p><h1>Категории</h1><p>Разделы, по которым покупатели находят товары.</p></div></div>
    {error && <div className={styles.error}>{error}</div>}{message && <div className={styles.message}>{message}</div>}
    <section className={styles.panel}><h2>Добавить категорию</h2><form onSubmit={create}><div className={styles.formGrid}>
      <div className={styles.field}><label>Название</label><input required autoFocus className={styles.input} value={name} onChange={(event) => setName(event.target.value)} placeholder="Например, Одежда"/></div>
      <div className={styles.field}><label>В каком разделе</label><select className={styles.select} value={parentId} onChange={(event) => setParentId(event.target.value)}><option value="">Основная категория</option>{items.filter(item=>item.status!=="ARCHIVED").map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></div>
    </div><div className={styles.sectionActions}><button disabled={busy} className={styles.primary}>{busy ? "Добавляем…" : "Добавить категорию"}</button></div></form></section>
    <nav className={styles.viewTabs} aria-label="Разделы категорий"><button className={!showArchive?styles.viewTabActive:undefined} onClick={()=>setShowArchive(false)}>Рабочие категории</button><button className={showArchive?styles.viewTabActive:undefined} onClick={()=>setShowArchive(true)}>Архив <span>{items.filter(item=>item.status==="ARCHIVED").length}</span></button></nav>
    <section className={styles.panel}><div className={styles.panelHeading}><h2>{showArchive?"Архив":"Структура каталога"}</h2></div>{visibleItems.map((item) => <div className={styles.categoryRow} key={item.id}>
      <div>{editingId === item.id ? <input autoFocus className={styles.input} value={editingName} onChange={(event) => setEditingName(event.target.value)}/> : <strong>{item.parentId ? "↳ " : ""}{item.name}</strong>}</div>
      <span className={`${styles.badge} ${item.status === "ACTIVE" ? styles.active : styles.draft}`}>{item.status === "ACTIVE" ? "На витрине" : item.status === "ARCHIVED"?"В архиве":"Скрыта"}</span>
      <div className={styles.rowActions}>{item.status==="ARCHIVED"?<><button disabled={busy} className={styles.textButton} onClick={()=>void restore(item)}>Восстановить</button><button disabled={busy} className={styles.textButton} onClick={()=>void remove(item)}>Удалить навсегда</button></>:editingId === item.id ? <><button disabled={busy} className={styles.textButton} onClick={() => void rename(item)}>Сохранить</button><button className={styles.textButton} onClick={() => setEditingId("")}>Отмена</button></> : <><button className={styles.textButton} onClick={() => {setEditingId(item.id);setEditingName(item.name)}}>Переименовать</button><button disabled={busy} className={styles.textButton} onClick={() => void toggle(item)}>{item.status === "ACTIVE" ? "Скрыть" : "Показать"}</button><button disabled={busy} className={styles.textButton} onClick={()=>void archive(item)}>В архив</button></>}</div>
    </div>)}{visibleItems.length === 0 && <div className={styles.empty}>{showArchive?"Архив пуст.":"Категорий пока нет. Добавьте первую категорию выше."}</div>}</section>
  </>;
}
