import Link from "next/link";
import StoreHeader from "./StoreHeader";

export default function LegalPage({ title, children }: { title: string; children: React.ReactNode }) {
  return <main className="legal-page">
    <StoreHeader />
    <article>
      <div className="catalog-breadcrumbs"><Link href="/">Главная</Link><span>·</span><span>Документы</span></div>
      <span className="section-kicker">Амра Шоп</span>
      <h1>{title}</h1>
      {children}
      <p className="legal-note">Это демонстрационная версия магазина. Перед запуском текст должен быть проверен юристом и дополнен реквизитами продавца.</p>
    </article>
  </main>;
}
