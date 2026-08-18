import LegalPage from "../components/LegalPage";

export default function PrivacyPage() {
  return <LegalPage title="Политика конфиденциальности">
    <h2>Какие данные мы используем</h2><p>Контактные данные, сведения о заказе и техническая информация, необходимая для работы магазина.</p>
    <h2>Для чего нужны данные</h2><p>Чтобы оформить и доставить заказ, ответить на обращение и улучшать работу сервиса.</p>
    <h2>Как связаться</h2><p>По вопросам обработки данных напишите на <a href="mailto:hello@amra.shop">hello@amra.shop</a>.</p>
  </LegalPage>;
}
