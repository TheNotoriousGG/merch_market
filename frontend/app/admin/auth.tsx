"use client";

import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { API_BASE, api, commandHeaders } from "./api";
import styles from "./admin.module.css";

type Session = {
  authenticated: boolean;
  emailVerified: boolean;
  subject?: string;
  displayName?: string;
  permissions: Array<"CUSTOMER" | "CATALOG_MANAGER" | "WAREHOUSE_MANAGER" | "ADMIN">;
};

const backendOrigin = API_BASE.replace(/\/api\/v1\/?$/, "");
const loginUrl = `${backendOrigin}/oauth2/authorization/keycloak`;

const navigation = [
  { href: "/admin", label: "Главная", icon: "⌂", permissions: ["CATALOG_MANAGER", "WAREHOUSE_MANAGER", "ADMIN"] },
  { href: "/admin/catalog", label: "Товары", icon: "□", permissions: ["CATALOG_MANAGER", "ADMIN"] },
  { href: "/admin/categories", label: "Категории", icon: "≡", permissions: ["CATALOG_MANAGER", "ADMIN"] },
  { href: "/admin/banners", label: "Баннеры", icon: "▰", permissions: ["CATALOG_MANAGER", "ADMIN"] },
  { href: "/admin/inventory", label: "Остатки", icon: "↕", permissions: ["WAREHOUSE_MANAGER"] },
] as const;

function Breadcrumbs({pathname}:{pathname:string}) {
  const items = pathname === "/admin" ? [] : pathname === "/admin/catalog" ? ["Товары"] : pathname === "/admin/categories" ? ["Категории"] : pathname === "/admin/banners" ? ["Баннеры"] : pathname === "/admin/inventory" ? ["Остатки"] : pathname === "/admin/catalog/products/new" ? ["Товары", "Новый товар"] : pathname.startsWith("/admin/catalog/products/") ? ["Товары", "Редактирование"] : [];
  if (items.length === 0) return null;
  return <nav className={styles.breadcrumbs} aria-label="Путь"><a href="/admin">Главная</a>{items.map((item,index)=><span key={item}><b>›</b>{index===items.length-1?<strong>{item}</strong>:<a href="/admin/catalog">{item}</a>}</span>)}</nav>;
}

export default function AdminAuth({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [session, setSession] = useState<Session | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    api<Session>("/session")
      .then(({ data }) => {
        if (!active) return;
        if (!data.authenticated) {
          window.location.replace(loginUrl);
          return;
        }
        setSession(data);
      })
      .catch((reason: Error) => active && setError(reason.message));

    const requireLogin = () => window.location.replace(loginUrl);
    window.addEventListener("amra:unauthorized", requireLogin);
    return () => {
      active = false;
      window.removeEventListener("amra:unauthorized", requireLogin);
    };
  }, []);

  const logout = async () => {
    try {
      await api<void>("/session/logout", { method: "POST", headers: commandHeaders() });
      window.location.replace("/");
    } catch (reason) {
      setError((reason as Error).message);
    }
  };

  if (error) {
    return <main className={styles.authScreen}><section className={styles.authCard}><span className={styles.authMark}>амра <b>admin</b></span><h1>Не удалось проверить доступ</h1><p>{error}</p><button className={styles.primary} onClick={() => window.location.reload()}>Повторить</button></section></main>;
  }

  if (!session) {
    return <main className={styles.authScreen}><section className={styles.authCard}><span className={styles.authMark}>амра <b>admin</b></span><div className={styles.authLoader} aria-hidden="true"/><h1>Проверяем доступ</h1><p>Перенаправляем в защищённый вход.</p></section></main>;
  }

  const canUseAdmin = session.emailVerified && session.permissions.some(permission => permission !== "CUSTOMER");
  if (!canUseAdmin) {
    return <main className={styles.authScreen}><section className={styles.authCard}><span className={styles.authMark}>амра <b>admin</b></span><h1>Недостаточно прав</h1><p>Учётная запись подтверждена, но ей не назначена роль сотрудника.</p><button className={styles.secondary} onClick={() => void logout()}>Выйти</button></section></main>;
  }

  const permittedNavigation = navigation.filter(item => item.permissions.some(permission => session.permissions.includes(permission)));
  return <div className={styles.shell}>
    <aside className={styles.sidebar}><a className={styles.brand} href="/admin">амра <span>admin</span></a><span className={styles.navCaption}>Управление магазином</span>
      <nav className={styles.nav} aria-label="Административные разделы">{permittedNavigation.map(item => {
        const active = item.href === "/admin" ? pathname === item.href : pathname.startsWith(item.href);
        return <a className={active ? styles.navActive : undefined} aria-current={active ? "page" : undefined} key={item.href} href={item.href}><i aria-hidden="true">{item.icon}</i>{item.label}</a>;
      })}</nav>
      <div className={styles.sidebarFoot}>Каталог и склад<br/>Рабочая среда сотрудника</div>
    </aside>
    <div className={styles.workspace}><header className={styles.topbar}><span className={styles.workspaceName}>Каталог Amra Shop</span><div className={styles.topActions}><span className={styles.connection}>● {session.displayName || "Сотрудник"}</span><a className={styles.storeLink} href="/">Открыть магазин ↗</a><button className={styles.logout} onClick={() => void logout()}>Выйти</button></div></header><main className={styles.content}><Breadcrumbs pathname={pathname}/>{children}</main></div>
  </div>;
}
