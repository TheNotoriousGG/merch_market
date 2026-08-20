import styles from "./admin.module.css";
export default function AdminOverview(){return <><div className={styles.heading}><div><p className={styles.eyebrow}>Каталог магазина</p><h1>Управление каталогом</h1><p>Создавайте категории, добавляйте товары и готовьте их к публикации.</p></div><a className={styles.primary} href="/admin/catalog/products/new">Добавить товар</a></div><div className={styles.grid}>
  <section className={styles.card}><span className={styles.metric}>01</span><h2>Категории</h2><p>Создайте разделы, в которых покупатели будут находить товары.</p><a className={styles.secondary} href="/admin/categories">Открыть категории</a></section>
  <section className={styles.card}><span className={styles.metric}>02</span><h2>Товары</h2><p>Добавляйте описания, размеры и фотографии товаров.</p><a className={styles.secondary} href="/admin/catalog">Открыть товары</a></section>
  <section className={styles.card}><span className={styles.metric}>03</span><h2>Публикация</h2><p>Проверьте заполнение карточки и опубликуйте её на витрине.</p><a className={styles.secondary} href="/admin/catalog">Перейти к товарам</a></section>
  </div></>}
