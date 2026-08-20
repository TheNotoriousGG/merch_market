"use client";

import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { loadStorefrontProducts } from "../catalog/catalog-api";
import { toShopProduct } from "../catalog/catalog-data";

export type ShopProduct = {
  id: string;
  name: string;
  price: number;
  art: string;
  colorClass: string;
  imageUrl?: string;
};

export type CartLine = ShopProduct & { quantity: number };

type ShopContextValue = {
  cart: CartLine[];
  favorites: ShopProduct[];
  cartCount: number;
  favoriteCount: number;
  cartTotal: number;
  addToCart: (product: ShopProduct) => void;
  removeFromCart: (id: string) => void;
  setQuantity: (id: string, quantity: number) => void;
  toggleFavorite: (product: ShopProduct) => void;
  isFavorite: (id: string) => boolean;
  isInCart: (id: string) => boolean;
};

const ShopContext = createContext<ShopContextValue | null>(null);
const STORAGE_KEY = "amra-shop-state-v1";

export function ShopStateProvider({ children }: { children: React.ReactNode }) {
  const [cart, setCart] = useState<CartLine[]>([]);
  const [favorites, setFavorites] = useState<ShopProduct[]>([]);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    queueMicrotask(() => {
      try {
        const saved = window.localStorage.getItem(STORAGE_KEY);
        if (saved) {
          const parsed = JSON.parse(saved) as { cart?: CartLine[]; favorites?: ShopProduct[] };
          setCart(Array.isArray(parsed.cart) ? parsed.cart : []);
          setFavorites(Array.isArray(parsed.favorites) ? parsed.favorites : []);
        }
      } catch { /* Начинаем с пустого локального состояния. */ }
      setReady(true);
    });
  }, []);

  useEffect(() => {
    if (!ready) return;
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ cart, favorites }));
  }, [cart, favorites, ready]);

  useEffect(() => {
    if (!ready) return;
    let active = true;
    void loadStorefrontProducts().then((products) => {
      if (!active) return;
      const currentProducts = new Map(products.map((product) => [product.id, toShopProduct(product)]));
      setCart((current) => current.flatMap((line) => {
        const product = currentProducts.get(line.id);
        return product ? [{ ...product, quantity: line.quantity }] : [];
      }));
      setFavorites((current) => current.flatMap((favorite) => {
        const product = currentProducts.get(favorite.id);
        return product ? [product] : [];
      }));
    }).catch(() => { /* Сохраняем локальное состояние, если каталог временно недоступен. */ });
    return () => { active = false; };
  }, [ready]);

  const value = useMemo<ShopContextValue>(() => ({
    cart,
    favorites,
    cartCount: cart.reduce((sum, item) => sum + item.quantity, 0),
    favoriteCount: favorites.length,
    cartTotal: cart.reduce((sum, item) => sum + item.price * item.quantity, 0),
    addToCart: (product) => setCart((current) => {
      const existing = current.find((item) => item.id === product.id);
      return existing
        ? current.map((item) => item.id === product.id ? { ...item, quantity: item.quantity + 1 } : item)
        : [...current, { ...product, quantity: 1 }];
    }),
    removeFromCart: (id) => setCart((current) => current.filter((item) => item.id !== id)),
    setQuantity: (id, quantity) => setCart((current) => quantity <= 0 ? current.filter((item) => item.id !== id) : current.map((item) => item.id === id ? { ...item, quantity } : item)),
    toggleFavorite: (product) => setFavorites((current) => current.some((item) => item.id === product.id) ? current.filter((item) => item.id !== product.id) : [...current, product]),
    isFavorite: (id) => favorites.some((item) => item.id === id),
    isInCart: (id) => cart.some((item) => item.id === id),
  }), [cart, favorites]);

  return <ShopContext.Provider value={value}>{children}</ShopContext.Provider>;
}

export function useShop() {
  const context = useContext(ShopContext);
  if (!context) throw new Error("useShop должен использоваться внутри ShopStateProvider");
  return context;
}

export const formatPrice = (value: number) => `${value.toLocaleString("ru-RU")} ₽`;
