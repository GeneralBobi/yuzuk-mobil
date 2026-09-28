// Web katmanının yapamadığı işler Android tarafında (YuzukEklentisi.kt): kilitli ekranda kayıt,
// Obsidian klasörüne yazma (SAF), büyük dosyayı akışla yükleme, bildirim.
import { registerPlugin } from "@capacitor/core";

export interface KayitDurumu {
  calisiyor: boolean;
  baslangicMs: number;
  seviye: number; // 0..1, son okumadaki ses şiddeti
  hata?: string | null;
}

export interface CanliDurum extends KayitDurumu {
  isleniyor: boolean; // mikrofon kapandı, son parçalar metne dönüyor
  gonderilen: number; // bilgisayarda metne dönen parça
  yerelde: number; // telefonda metne dönen parça (bağlantı yokken)
  bekleyen: number; // henüz metne dönmemiş parça (bellekte)
  cumle: number;
  durum: "bilgisayar" | "telefon" | "bekliyor" | null;
  dokum: string | null;
}

export interface ModelDurumu { hazir: boolean; indiriliyor: boolean; indirilen: number; boyut: number; hata: string | null }

export interface YuzukEklentisi {
  canliBaslat(o: { sunucu: string; anahtar: string; dil?: string; testPcm?: string }): Promise<{ dokum: string }>;
  canliDurdur(): Promise<{ dokum: string | null; sureSn: number }>;
  canliDurumu(): Promise<CanliDurum>;
  dokumOku(o: { yol: string }): Promise<{ dokum: string; bitti: boolean; cumle: number }>;
  modelDurumu(): Promise<ModelDurumu>;
  modelIndir(): Promise<void>;
  modelSil(): Promise<void>;
  kenarlariYenile(): Promise<void>;
  izinIste(): Promise<{ mikrofon: boolean }>;
  kayitBaslat(): Promise<{ yol: string }>;
  kayitDurdur(): Promise<{ yol: string | null; sureSn: number }>;
  kayitDurumu(): Promise<KayitDurumu>;
  dosyaSec(): Promise<{ yol: string | null; ad: string | null }>;
  dosyaVarMi(o: { yol: string }): Promise<{ var: boolean }>;
  dosyaSil(o: { yol: string }): Promise<void>;
  yukle(o: { yol: string; url: string; anahtar: string; alanlar: Record<string, string> }): Promise<{ durum: number; govde: string }>;
  klasorSec(): Promise<{ agac: string | null; ad: string | null }>;
  dosyaYaz(o: { agac: string; yol: string; icerik: string; ekle: boolean; yoksa?: boolean }): Promise<{ ad: string; vardi?: boolean }>;
  bildir(o: { baslik: string; metin: string }): Promise<void>;
  hatirlaticiKur(o: { id: string; zamanMs: number; baslik: string; metin: string }): Promise<{ kuruldu: boolean }>;
  hatirlaticiSayisi(): Promise<{ sayi: number }>;
  hatirlaticiEsitle(o: { liste: { id: string; zamanMs: number; bugun: boolean; baslik: string; metin: string }[] }): Promise<{ kurulan: number }>;
  /** 1.5.0: bildirimdeki "Yaptım / Yarın tekrar" uygulama açılmadan gidebilsin diye bulut adresi ve cihaz anahtarı. */
  hatirlaticiBaglanti(o: { sunucu: string; anahtar: string }): Promise<void>;
  /** Ağ yokken basılmış düğmeler; alınınca telefondaki sıradan çıkar. */
  hatirlaticiIslemleri(): Promise<{ islemler: { kimlik: string; islem: "yapildi" | "yarin"; an: number }[] }>;
  bildirimIzni(): Promise<{ verildi: boolean }>;
  paylasimAl(): Promise<Paylasim>;
  vaultListe(o: { agac: string }): Promise<{ dosyalar: { yol: string; boyut: number; mtime: number }[] }>;
  vaultOku(o: { agac: string; yol: string }): Promise<{ icerik: string }>;
  addListener(olay: "paylasim", dinleyici: (p: Paylasim) => void): Promise<{ remove: () => Promise<void> }>;
}

/** Başka uygulamadan "Paylaş → Claudian" ile gelen dosya (uygulamanın klasörüne kopyalanmış). */
export interface Paylasim {
  yol?: string;
  ad?: string | null;
  hata?: string;
}

export const yerel = registerPlugin<YuzukEklentisi>("Yuzuk");
