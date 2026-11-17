import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "CORE — Operations Workspace",
  description: "Manage inventory, move orders forward, and find answers in your operational knowledge.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
