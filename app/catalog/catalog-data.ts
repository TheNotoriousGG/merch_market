import type { ShopProduct } from "../components/ShopState";

export type CatalogProduct = ShopProduct & {
  description: string;
  color: string;
  material: string;
  sizes: string[];
  isNew?: boolean;
  imageUrl?: string;
  priceAvailable?: boolean;
};

export const toShopProduct = (product: CatalogProduct): ShopProduct => ({
  id: product.id,
  name: product.name,
  price: product.price,
  art: product.art,
  colorClass: product.colorClass,
  imageUrl: product.imageUrl,
});

const clothes: CatalogProduct[] = [
  { id:"series-01-tee", name:"Футболка «Серия 01»", price:2990, art:"tee", colorClass:"product-blue", description:"Плотная базовая футболка свободного кроя с мягкой горловиной.", color:"Графит", material:"100% хлопок", sizes:["XS","S","M","L","XL"], isNew:true },
  { id:"free-hoodie", name:"Худи свободного кроя", price:5490, art:"hoodie", colorClass:"product-lilac", description:"Объёмное худи с глубоким капюшоном и спокойной посадкой.", color:"Лавандовый", material:"80% хлопок, 20% полиэстер", sizes:["S","M","L","XL"], isNew:true },
  { id:"straight-pants", name:"Брюки прямого кроя", price:4990, art:"pants", colorClass:"product-sand", description:"Прямые брюки из плотной ткани для собранных повседневных образов.", color:"Графит", material:"65% хлопок, 35% полиэстер", sizes:["S","M","L","XL"] },
  { id:"base-shorts", name:"Шорты «База»", price:3490, art:"shorts", colorClass:"product-steel", description:"Свободные шорты с эластичным поясом и глубокими карманами.", color:"Стальной", material:"100% хлопок", sizes:["S","M","L"] },
  { id:"mono-tee", name:"Футболка Mono", price:2790, art:"tee", colorClass:"product-yellow", description:"Лаконичная футболка с небольшим знаком Amra на груди.", color:"Жёлтый", material:"100% хлопок", sizes:["XS","S","M","L"], isNew:true },
  { id:"north-hoodie", name:"Худи «Север»", price:5990, art:"hoodie", colorClass:"product-blue", description:"Тёплое худи лимитированной серии с плотным капюшоном.", color:"Ледяной", material:"85% хлопок, 15% полиэстер", sizes:["S","M","L"] },
  { id:"relax-shorts", name:"Шорты Relax", price:3290, art:"shorts", colorClass:"product-mint", description:"Мягкие повседневные шорты со свободной посадкой.", color:"Шалфей", material:"92% хлопок, 8% эластан", sizes:["S","M","L","XL"] },
  { id:"soft-pants", name:"Брюки Soft", price:5190, art:"pants", colorClass:"product-lilac", description:"Мягкие брюки с аккуратными стрелками и эластичным поясом.", color:"Лавандовый", material:"70% вискоза, 30% полиэстер", sizes:["S","M","L"] },
];

const accessories: CatalogProduct[] = [
  { id:"mono-watch", name:"Часы Amra Mono", price:8990, art:"watch", colorClass:"product-yellow", description:"Минималистичные часы с контрастным циферблатом и мягким ремешком.", color:"Графит", material:"Сталь, минеральное стекло", sizes:["One size"], isNew:true },
  { id:"embroidered-cap", name:"Кепка с вышивкой", price:1990, art:"cap", colorClass:"product-coral", description:"Шестипанельная кепка с регулируемой посадкой и вышивкой Amra.", color:"Тёмно-синий", material:"100% хлопок", sizes:["One size"] },
  { id:"daily-tote", name:"Шопер на каждый день", price:2490, art:"tote", colorClass:"product-mint", description:"Лёгкий шопер с внутренним карманом, который складывается сам в себя.", color:"Молочный", material:"100% полиэстер", sizes:["37 × 44 см"] },
  { id:"soft-box", name:"Сумка Soft Box", price:4490, art:"bag", colorClass:"product-pink", description:"Мягкая сумка с широким ремнём и карманом для важных мелочей.", color:"Песочный", material:"Экокожа, текстиль", sizes:["22 × 18 × 8 см"], isNew:true },
  { id:"yellow-cap", name:"Кепка Amra Base", price:2190, art:"cap", colorClass:"product-yellow", description:"Яркая кепка с мягким козырьком и регулируемой застёжкой.", color:"Жёлтый", material:"100% хлопок", sizes:["One size"], isNew:true },
  { id:"mono-shopper", name:"Шопер Mono", price:2290, art:"tote", colorClass:"product-steel", description:"Графичный шопер для ноутбука, документов и покупок.", color:"Графит", material:"100% хлопок", sizes:["39 × 42 см"] },
  { id:"mini-soft", name:"Сумка Mini Soft", price:3690, art:"bag", colorClass:"product-lilac", description:"Небольшая мягкая сумка для телефона, ключей и карт.", color:"Лавандовый", material:"Экокожа", sizes:["18 × 12 × 6 см"] },
  { id:"sport-watch", name:"Часы Amra Sport", price:7490, art:"watch", colorClass:"product-coral", description:"Лёгкие часы с влагозащитой и контрастным ремешком.", color:"Коралловый", material:"Алюминий, силикон", sizes:["One size"] },
];

const bags: CatalogProduct[] = [
  { ...accessories[3], id:"soft-box-bag" }, { ...accessories[2], id:"everyday-shopper" },
  { id:"mono-mini", name:"Сумка Mono Mini", price:3990, art:"bag", colorClass:"product-yellow", description:"Компактная сумка для телефона, карт и других необходимых вещей.", color:"Жёлтый", material:"Нейлон", sizes:["18 × 12 × 6 см"] },
  { id:"north-shopper", name:"Шопер «Север»", price:2790, art:"tote", colorClass:"product-blue", description:"Лимитированный шопер из плотной ткани с северной типографикой.", color:"Ледяной", material:"100% хлопок", sizes:["40 × 46 см"], isNew:true },
  { ...accessories[6], id:"mini-soft-bag" }, { ...accessories[5], id:"mono-shopper-bag" },
  { id:"coral-box", name:"Сумка Coral Box", price:4290, art:"bag", colorClass:"product-coral", description:"Каркасная сумка с мягкими углами и короткой ручкой.", color:"Коралловый", material:"Экокожа, текстиль", sizes:["24 × 17 × 9 см"], isNew:true },
  { id:"yellow-tote", name:"Шопер Yellow Line", price:2590, art:"tote", colorClass:"product-yellow", description:"Прочный шопер с контрастной жёлтой графикой.", color:"Молочный", material:"100% хлопок", sizes:["40 × 45 см"] },
];

const pants: CatalogProduct[] = [
  { ...clothes[2], id:"straight-fit", isNew:true }, { ...clothes[3], id:"base-shorts-pants" },
  { id:"relax-pants", name:"Брюки Relax", price:5290, art:"pants", colorClass:"product-mint", description:"Мягкие брюки с защипами и немного зауженным низом.", color:"Шалфей", material:"72% вискоза, 28% полиэстер", sizes:["S","M","L"] },
  { id:"mono-shorts", name:"Шорты Mono", price:3190, art:"shorts", colorClass:"product-lilac", description:"Лаконичные спортивные шорты с минимальной вышивкой.", color:"Лавандовый", material:"80% хлопок, 20% полиэстер", sizes:["S","M","L","XL"] },
  { id:"classic-pants", name:"Брюки Classic", price:5590, art:"pants", colorClass:"product-blue", description:"Классические брюки со стрелками и мягкой средней посадкой.", color:"Тёмно-синий", material:"60% шерсть, 40% вискоза", sizes:["S","M","L","XL"], isNew:true },
  { id:"sport-shorts", name:"Шорты Sport", price:2990, art:"shorts", colorClass:"product-coral", description:"Лёгкие спортивные шорты с внутренним шнурком.", color:"Коралловый", material:"100% полиэстер", sizes:["S","M","L"] },
  { id:"yellow-pants", name:"Брюки Yellow Line", price:4890, art:"pants", colorClass:"product-yellow", description:"Свободные брюки с контрастной строчкой и карманами.", color:"Графит", material:"100% хлопок", sizes:["S","M","L"] },
  { id:"home-shorts", name:"Шорты Home", price:2790, art:"shorts", colorClass:"product-mint", description:"Домашние шорты из мягкого трикотажа без лишних деталей.", color:"Шалфей", material:"95% хлопок, 5% эластан", sizes:["S","M","L","XL"] },
];

export const catalog = {
  clothes: { label:"Одежда", sections:["Вся одежда","Футболки и поло","Лонгсливы","Рубашки","Свитшоты и олимпийки","Худи","Брюки и шорты"], products:clothes },
  accessories: { label:"Аксессуары", sections:["Все аксессуары","Картхолдеры","Рюкзаки и сумки","Косметички","Брелоки","Бутылки и кружки","Головные уборы"], products:accessories },
  bags: { label:"Сумки", sections:["Все сумки","Шоперы","Рюкзаки","Сумки через плечо","Чехлы для ноутбука","Органайзеры"], products:bags },
  pants: { label:"Брюки", sections:["Все брюки","Джоггеры","Классические брюки","Повседневные шорты","Спортивные","Домашние"], products:pants },
};

export type CatalogKey = keyof typeof catalog;
