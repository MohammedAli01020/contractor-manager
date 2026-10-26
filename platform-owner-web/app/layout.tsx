import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "إدارة المنصة | نظام إدارة المقاولات",
  description: "إدارة شركات المقاولات وحسابات مديري الشركات بأمان",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ar" dir="rtl">
      <body>{children}</body>
    </html>
  );
}
