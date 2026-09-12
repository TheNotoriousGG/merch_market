import AdminAuth from "./auth";

export default function AdminLayout({children}:{children:React.ReactNode}) {
  return <AdminAuth>{children}</AdminAuth>;
}
