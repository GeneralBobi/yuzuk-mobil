/** Bulutun sağlık cevabını telefonun gösterdiği duruma çevirir.
 *
 *  0.12.1 (13.11.2026): bulut 503'ün nedenini `tur` alanında söylüyordu (onay · dugum · musait · access) ama telefon
 *  hepsini "ulaşılamıyor" diye gösteriyordu. Başka cihaza kurulumda eşleşme başarılıyken kullanıcı bağlantının koptuğunu
 *  sanıyordu; oysa bekleyen şey cihaz onayıydı. Her neden kendi kısa etiketini alır. */

export type BaglantiDurumu = "bilinmiyor" | "bagli" | "kapali" | "anahtar" | "onay";

export interface Baglanti {
  durum: BaglantiDurumu;
  /** Ayarlar'daki kısa durum etiketi. */
  etiket: string;
  /** Tek cümlelik açıklama; bağlıyken boş kalabilir. */
  metin: string;
}

interface SaglikGovdesi { detail?: string; tur?: string; konusmaci?: boolean }

export const BAKILIYOR: Baglanti = { durum: "bilinmiyor", etiket: "bakılıyor", metin: "" };
export const INTERNET_YOK: Baglanti = { durum: "kapali", etiket: "internet yok", metin: "İnternet yok. Kayıtlar telefonda." };

const NEDEN: Record<string, { durum: BaglantiDurumu; etiket: string }> = {
  onay: { durum: "onay", etiket: "onay bekliyor" },
  bilgisayar_onayi: { durum: "kapali", etiket: "bilgisayar onayı bekliyor" },
  dugum: { durum: "kapali", etiket: "bilgisayar kapalı" },
  musait: { durum: "kapali", etiket: "bilgisayar pilde" },
  access: { durum: "kapali", etiket: "ulaşılamıyor" },
};

export function saglikSonucu(status: number, govde: SaglikGovdesi = {}): Baglanti {
  if (status === 401) return { durum: "anahtar", etiket: "yeniden eşleştir", metin: "Bilgisayarda Claudian → Yüzük → Telefonu bağla bölümünden yeni kod al." };
  if ((status === 503 || status === 403) && govde.tur === "onay") {
    const metin = "Bilgisayarda Claudian → Yüzük → Telefonu bağla bölümünden yeni kod al. Telefonun onayı bu kodla tamamlanır.";
    return { ...NEDEN.onay, metin };
  }
  if (status === 503) {
    const n = NEDEN[govde.tur ?? ""] ?? { durum: "kapali" as const, etiket: "bilgisayar kapalı" };
    return { ...n, metin: govde.detail ?? "Bilgisayar çevrim dışı. Kayıtlar telefonda." };
  }
  if (status < 200 || status >= 300) return { durum: "kapali", etiket: "bulut yanıt vermiyor", metin: `Bulut ${status} döndü. Kayıtlar telefonda bekler.` };
  return { durum: "bagli", etiket: "bağlı", metin: govde.konusmaci ? "Bağlı · konuşmacılar ayrılıyor" : "Bağlı" };
}

/** Eşleştirme cevabı: kod kabul edildi; cihaz onaylı değilse bunu hemen söyler. */
export function eslesmeMetni(onay?: string): string {
  return onay === "bekliyor" ? "Eşleşti · onay bekliyor" : "Eşleşti";
}
