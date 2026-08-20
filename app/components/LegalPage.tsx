import Link from "next/link";
import StoreHeader from "./StoreHeader";
import styles from "./LegalPage.module.css";

export default function LegalPage({ title, children }: { title: string; children: React.ReactNode }) {
  return <main className={styles.page}>
    <StoreHeader />
    <article className={styles.content}>
      <div className="catalog-breadcrumbs"><Link href="/">Главная</Link><span>·</span><span>Документы</span></div>
      <span className={styles.kicker}>Амра Шоп</span>
      <h1>{title}</h1>
      {children}
      <p className={styles.note}>Это демонстрационная версия магазина. Перед запуском текст должен быть проверен юристом и дополнен реквизитами продавца.</p>
    </article>
  </main>;
}
