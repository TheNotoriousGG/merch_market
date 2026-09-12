export function FavoriteIcon({ active = false }: { active?: boolean }) {
  return <svg className="favorite-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 20.5 4.55 13.4A5.2 5.2 0 0 1 12 6.15a5.2 5.2 0 0 1 7.45 7.25L12 20.5Z" fill={active ? "currentColor" : "none"} /></svg>;
}

export function CartButtonContent({ label = "В корзину", added = false }: { label?: string; added?: boolean }) {
  return <><span className="cart-action-label" aria-live="polite">{label}</span><span className="cart-action-icon">{added?<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m5 12.5 4.5 4.5L19 7.5" /></svg>:<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 8h12l1 13H5L6 8Z" /><path d="M9 9V6a3 3 0 0 1 6 0v3" /></svg>}</span></>;
}
