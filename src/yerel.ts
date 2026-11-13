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
  googleGiris(o: { clientId: string; nonce: string }): Promise<{ idToken: string }>;
  arkaPlanKur(o: { ayar: { sunucu: string; anahtar: string; vault: string; altKlasor: string; yazici: string; isler: OturumArkaPlan[]; gonderilecek: OturumArkaPlan[] } }): Promise<void>;
  /** yuklemeler: 0.11.8 — arka planda (ya da uygulamanın kendi yüklemesinde) buluta kabul edilen kayıtlar, yerel id → bulut iş id. */
  arkaPlanOku(): Promise<{ teslimler: Record<string, { id: string; sunucuId: string; yol: string; hash: string; baslik: string; mahrem: number; yazici: string }>; yuklemeler?: Record<string, string> }>;
  arkaPlanOnayla(o: { ids: string[] }): Promise<void>;
  /** 0.11.9: eşitleme izi uygulama ve arka plan arasında ortak; gerek: arka planda not geldi, eşitleme bekliyor. */
  esitlemeIzi(): Promise<{ iz: Record<string, string>; varmi: boolean; gerek: boolean; zaman: number; metin: string }>;
  esitlemeIziYaz(o: { iz: Record<string, string>; tamam?: boolean; metin?: string }): Promise<void>;
  esitlemeKilit(o: { al: boolean }): Promise<{ alindi?: boolean }>;
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
  kayitDurdur(): Promise<{ yol: string | null; sureSn: number; sesSn?: number; kesinti?: number }>;
  kayitDurumu(): Promise<KayitDurumu>;
  dosyaSec(): Promise<{ yol: string | null; ad: string | null }>;
  dosyaVarMi(o: { yol: string }): Promise<{ var: boolean }>;
  dosyaSil(o: { yol: string }): Promise<void>;
  yukle(o: { yol: string; url: string; anahtar: string; alanlar: Record<string, string>; id?: string }): Promise<{ durum: number; govde: string }>;
  klasorSec(): Promise<{ agac: string | null; ad: string | null; obsidian?: boolean }>;
  /** 0.11.1: seçili klasör vault kökü mü (.obsidian var mı). */
  vaultMi(o: { agac: string }): Promise<{ obsidian: boolean }>;
  /** 0.11.1: yanlış köke inmiş kopyayı siler; boşalan klasörler de kalkar. */
  vaultSil(o: { agac: string; yol: string }): Promise<{ silindi: boolean }>;
  dosyaYaz(o: { agac: string; yol: string; icerik: string; ekle: boolean; yoksa?: boolean }): Promise<{ ad: string; vardi?: boolean }>;
  bildir(o: { baslik: string; metin: string }): Promise<void>;
  hatirlaticiKur(o: { id: string; zamanMs: number; baslik: string; metin: string }): Promise<{ kuruldu: boolean }>;
  hatirlaticiSayisi(): Promise<{ sayi: number }>;
  /** 1.6.0: SHA-256("yuzuk:" + ANDROID_ID); aynı telefon yeniden eşleşince eski satırı iptal olur. */
  cihazIzi(): Promise<{ izi?: string }>;
  hatirlaticiEsitle(o: { liste: { id: string; zamanMs: number; bugun: boolean; baslik: string; metin: string }[] }): Promise<{ kurulan: number }>;
  /** 1.5.0: bildirimdeki "Yaptım / Yarın tekrar" uygulama açılmadan gidebilsin diye bulut adresi ve cihaz anahtarı. */
  hatirlaticiBaglanti(o: { sunucu: string; anahtar: string }): Promise<void>;
  /** Ağ yokken basılmış düğmeler; alınınca telefondaki sıradan çıkar. */
  hatirlaticiIslemleri(): Promise<{ islemler: { kimlik: string; islem: "yapildi" | "yarin"; an: number }[] }>;
  bildirimIzni(): Promise<{ verildi: boolean }>;
  paylasimAl(): Promise<Paylasim>;
  /** 0.12.0: dokunulan bildirim (uygulama kapalıyken açıldıysa bir kez verilir); yoksa boş nesne. */
  bildirimAl(): Promise<OkunanBildirim>;
  addListener(olay: "bildirim", dinleyici: (b: OkunanBildirim) => void): Promise<{ remove: () => Promise<void> }>;
  vaultListe(o: { agac: string }): Promise<{ dosyalar: { yol: string; boyut: number; mtime: number }[] }>;
  vaultOku(o: { agac: string; yol: string }): Promise<{ icerik: string }>;
  addListener(olay: "paylasim", dinleyici: (p: Paylasim) => void): Promise<{ remove: () => Promise<void> }>;
}

type OturumArkaPlan = { id: string; sunucuId?: string; yol: string; baslangic: string; baslik?: string };

/** Başka uygulamadan "Paylaş → Yüzük" ile gelen dosya (uygulamanın klasörüne kopyalanmış). */
export interface Paylasim {
  yol?: string;
  ad?: string | null;
  hata?: string;
}

/** Dokunularak açılan bildirimin içeriği. */
export interface OkunanBildirim {
  baslik?: string;
  metin?: string;
  zaman?: number;
}

export const yerel = registerPlugin<YuzukEklentisi>("Yuzuk");
