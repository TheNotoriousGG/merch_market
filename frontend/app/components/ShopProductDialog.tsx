"use client";

import type {CatalogProduct} from "../catalog/catalog-data";
import CatalogProductDialog from "../catalog/components/CatalogProductDialog";
import type {ShopProduct} from "./ShopState";

export default function ShopProductDialog({product,onClose}:{product:ShopProduct;onClose:()=>void}) {
  const catalogProduct:CatalogProduct={...product,description:"",color:"",colors:[],material:"",sizes:[],priceAvailable:true};
  return <CatalogProductDialog product={catalogProduct} onClose={onClose}/>;
}
