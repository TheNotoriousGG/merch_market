import Link from "next/link";
import styles from "./admin.module.css";

const navigation = [["/admin","Обзор"],["/admin/catalog","Товары"],["/admin/categories","Категории"],["/admin/collections","Коллекции"],["/admin/inventory","Склад"]] as const;
export default function AdminLayout({children}:{children:React.ReactNode}) {
  return <div className={styles.shell}>
    <aside className={styles.sidebar}><Link className={styles.brand} href="/admin">амра <span>admin</span></Link>
      <nav className={styles.nav} aria-label="Административные разделы">{navigation.map(([href,label])=><Link key={href} href={href}>{label}</Link>)}</nav>
      <div className={styles.sidebarFoot}>Каталог и склад<br/>Рабочая среда сотрудника</div>
    </aside>
    <div className={styles.workspace}><header className={styles.topbar}><strong>Панель управления</strong><div className={styles.topActions}><span className={styles.connection}>● Backend session</span><Link className={styles.storeLink} href="/">На витрину ↗</Link></div></header><main className={styles.content}>{children}</main></div>
  </div>;
}
