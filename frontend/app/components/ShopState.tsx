"use client";

import { createContext, useContext, useEffect, useMemo, useState } from "react";
import type { Cart } from "../api/generated";
import { cookie, customerApi } from "../api/client";
import { loadStorefrontProducts } from "../catalog/catalog-api";
import { toShopProduct } from "../catalog/catalog-data";

export type ShopProduct = {
  id: string;
  name: string;
  price: number;
  art: string;
  colorClass: string;
  imageUrl?: string;
  slug?: string;
};

export type CartLine = ShopProduct & {
  variantId: string;
  variantLabel: string;
  quantity: number;
  available: boolean;
  lineSubtotal: number;
  discount: number;
  promotionName?: string;
};

type ShopContextValue = {
  cart: CartLine[];
  favorites: ShopProduct[];
  cartCount: number;
  favoriteCount: number;
  cartTotal: number;
  cartNotices: string[];
  cartVersion: number;
  loading: boolean;
  addToCart: (product: ShopProduct, variantId: string) => void;
  removeFromCart: (id: string) => void;
  setQuantity: (id: string, quantity: number) => void;
  toggleFavorite: (product: ShopProduct) => void;
  isFavorite: (id: string) => boolean;
  isInCart: (id: string) => boolean;
  refreshCart: () => Promise<void>;
};

const ShopContext = createContext<ShopContextValue | null>(null);
const csrf = () => cookie("AMRA_CSRF") || "browser-csrf-token";
const etag = (version: number) => `"v${version}"`;

function toCartLines(payload: Cart, catalog: Map<string, ShopProduct>): CartLine[] {
  return payload.items.map((line) => {
    const product = catalog.get(line.productId);
    return {
      id: line.productId,
      name: line.name,
      price: line.unitPriceMinor / 100,
      art: product?.art ?? "product",
      colorClass: product?.colorClass ?? "product-steel",
      imageUrl: product?.imageUrl,
      slug: line.slug,
      variantId: line.variantId,
      variantLabel: line.variantLabel,
      quantity: line.quantity,
      available: line.available,
      lineSubtotal: line.lineSubtotalMinor / 100,
      discount: line.discountMinor / 100,
      promotionName: line.promotionName,
    };
  });
}

export function ShopStateProvider({ children }: { children: React.ReactNode }) {
  const [cartPayload, setCartPayload] = useState<Cart | null>(null);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [favorites, setFavorites] = useState<ShopProduct[]>([]);
  const [products, setProducts] = useState<Map<string, ShopProduct>>(new Map());
  const [loading, setLoading] = useState(true);

  const applyCart = (payload: Cart, catalog = products) => {
    setCartPayload(payload);
    setCart(toCartLines(payload, catalog));
  };

  useEffect(() => {
    let active = true;
    void Promise.all([
      loadStorefrontProducts(),
      customerApi.getCustomerContext(),
      customerApi.getCart(),
    ]).then(([catalog, context, remoteCart]) => {
      if (!active) return;
      const byId = new Map(catalog.map((product) => [product.id, toShopProduct(product)]));
      setProducts(byId);
      setFavorites(Array.from(context.favoriteProductIds).flatMap((id) => {
        const product = byId.get(id);
        return product ? [product] : [];
      }));
      setCartPayload(remoteCart);
      setCart(toCartLines(remoteCart, byId));
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, []);

  const value = useMemo<ShopContextValue>(() => {
    const removeFromCart = (id: string) => {
      const line = cart.find((item) => item.id === id);
      if (!line || !cartPayload) return;
      void customerApi.removeCartItem({
        variantId: line.variantId,
        ifMatch: etag(cartPayload.version),
        xAMRACSRF: csrf(),
      }).then((updated) => applyCart(updated));
    };

    return {
      cart,
      favorites,
      loading,
      cartCount: cart.reduce((sum, item) => sum + item.quantity, 0),
      favoriteCount: favorites.length,
      cartTotal: cartPayload ? cartPayload.subtotalMinor / 100 : 0,
      cartNotices: cartPayload?.notices.map((notice) => notice.message) ?? [],
      cartVersion: cartPayload?.version ?? 0,
      addToCart: (product, variantId) => {
        if (!product.slug || !variantId || cart.some((item) => item.id === product.id)) return;
        void customerApi.setCartItem({
            variantId,
            ifMatch: etag(cartPayload?.version ?? 0),
            xAMRACSRF: csrf(),
            setCartItemRequest: { quantity: 1 },
          }).then((updated) => applyCart(updated));
      },
      removeFromCart,
      setQuantity: (id, quantity) => {
        const line = cart.find((item) => item.id === id);
        if (!line || !cartPayload) return;
        if (quantity <= 0) {
          removeFromCart(id);
          return;
        }
        void customerApi.setCartItem({
          variantId: line.variantId,
          ifMatch: etag(cartPayload.version),
          xAMRACSRF: csrf(),
          setCartItemRequest: { quantity },
        }).then((updated) => applyCart(updated));
      },
      toggleFavorite: (product) => {
        const selected = favorites.some((item) => item.id === product.id);
        setFavorites((current) => selected
          ? current.filter((item) => item.id !== product.id)
          : [...current, products.get(product.id) ?? product]);
        const operation = selected
          ? customerApi.removeFavorite({ productId: product.id, xAMRACSRF: csrf() })
          : customerApi.addFavorite({ productId: product.id, xAMRACSRF: csrf() });
        void operation.catch(() => {
          setFavorites((current) => selected
            ? [...current, products.get(product.id) ?? product]
            : current.filter((item) => item.id !== product.id));
        });
      },
      isFavorite: (id) => favorites.some((item) => item.id === id),
      isInCart: (id) => cart.some((item) => item.id === id),
      refreshCart: async () => applyCart(await customerApi.getCart()),
    };
  }, [cart, cartPayload, favorites, products]);

  return <ShopContext.Provider value={value}>{children}</ShopContext.Provider>;
}

export function useShop() {
  const context = useContext(ShopContext);
  if (!context) throw new Error("useShop должен использоваться внутри ShopStateProvider");
  return context;
}

export const formatPrice = (value: number) => `${value.toLocaleString("ru-RU")} ₽`;
