"use client";
/* eslint-disable react-hooks/set-state-in-effect, jsx-a11y/label-has-associated-control */
import { FormEvent, useEffect, useState } from "react";
import { api, Category, commandHeaders } from "../api";
import { catalogSlug } from "../catalog-slug";
import styles from "../admin.module.css";

export default function Categories() {
  const [items, setItems] = useState<Category[]>([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [name, setName] = useState("");
  const load = async () => { try { setItems((await api<{items: Category[]}>("/admin/catalog/categories")).data.items); } catch (reason) { setError((reason as Error).message); } };
  useEffect(() => { void load(); }, []);
  const create = async (event: FormEvent) => {
    event.preventDefault();
    try {
      setError("");
      const slug = catalogSlug(name);
      if (!slug) throw new Error("Введите название категории буквами или цифрами");
      await api<Category>("/admin/catalog/categories", { method: "POST", headers: commandHeaders(), body: JSON.stringify({ slug, name: name.trim(), displayOrder: items.length }) });
      setName(""); setMessage("Категория создана. Теперь её можно назначать товарам."); await load();
    } catch (reason) { setError((reason as Error).message); }
  };
  return <>
    <div className={styles.heading}><div><p className={styles.eyebrow}>Каталог</p><h1>Категории</h1><p>Создавайте понятные покупателю разделы: например, «Одежда», «Сумки» или «Аксессуары».</p></div></div>
    {error && <div className={styles.error}>{error}</div>}{message && <div className={styles.message}>{message}</div>}
    <section className={styles.panel}><h2>Добавить категорию</h2><form onSubmit={create}><div className={styles.field}><label>Название категории</label><input required autoFocus className={styles.input} value={name} onChange={(event) => setName(event.target.value)} placeholder="Например, Одежда"/><span className={styles.hint}>Адрес страницы будет создан автоматически.</span></div><div className={styles.sectionActions}><button className={styles.primary}>Добавить категорию</button></div></form></section>
    <section className={styles.panel}><h2>Категории каталога</h2>{items.map((item) => <div className={styles.treeRow} key={item.id}><div><strong>{item.parentId ? "↳ " : ""}{item.name}</strong></div><span className={`${styles.badge} ${item.status === "ACTIVE" ? styles.active : styles.draft}`}>{item.status === "ACTIVE" ? "Показывается" : "Скрыта"}</span><span>{item.displayOrder + 1}</span></div>)}{items.length === 0 && <div className={styles.empty}>Категорий пока нет. Добавьте первую категорию выше.</div>}</section>
  </>;
}
