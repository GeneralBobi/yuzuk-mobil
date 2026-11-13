import AVFoundation
import Capacitor
import CryptoKit
import UIKit
import UniformTypeIdentifiers
import UserNotifications

/*
 Yüzük'ün iOS yerel katmanı (13.11.2026, ilk sürüm). Web katmanı (src/yerel.ts) Android'dekiyle aynıdır; bu dosya
 aynı "Yuzuk" eklentisini iOS'un imkânlarıyla karşılar:

   kayıt        AVAudioRecorder, 16 kHz mono AAC; ekran kilitliyken sürer (UIBackgroundModes: audio)
   vault        Dosyalar'dan seçilen klasör, güvenlik kapsamlı yer imiyle saklanır ("agac" = yer imi kimliği)
   yükleme      çok parçalı gövde geçici dosyaya kurulur, URLSession ile gönderilir
   hatırlatıcı  UNUserNotificationCenter yerel bildirimleri

 iOS'ta bu sürümde olmayanlar açıkça "yok" döner; web katmanı bunları Android'de bilgisayar kapalıyken kullanılan
 yedek yollar olarak görür: telefonda yazıya dökme (canli*, model*), Google ile giriş, arka plan teslim işçisi.

 Bu dosya Windows'ta yazıldı ve derlenmedi; ilk doğrulama .github/workflows/ios.yml içindeki simülatör derlemesidir.
*/

final class YuzukViewController: CAPBridgeViewController {
    override func capacitorDidLoad() {
        bridge?.registerPluginInstance(YuzukEklentisi())
    }
}

@objc(YuzukEklentisi)
final class YuzukEklentisi: CAPPlugin, CAPBridgedPlugin, UIDocumentPickerDelegate, AVAudioRecorderDelegate {
    let identifier = "YuzukEklentisi"
    let jsName = "Yuzuk"
    let pluginMethods: [CAPPluginMethod] = [
        "googleGiris", "arkaPlanKur", "arkaPlanOku", "arkaPlanOnayla", "esitlemeIzi", "esitlemeIziYaz", "esitlemeKilit",
        "canliBaslat", "canliDurdur", "canliDurumu", "dokumOku", "modelDurumu", "modelIndir", "modelSil", "kenarlariYenile",
        "izinIste", "kayitBaslat", "kayitDurdur", "kayitDurumu", "dosyaSec", "dosyaVarMi", "dosyaSil", "yukle", "klasorSec",
        "vaultMi", "vaultSil", "dosyaYaz", "bildir", "hatirlaticiKur", "hatirlaticiSayisi", "cihazIzi", "hatirlaticiEsitle",
        "hatirlaticiBaglanti", "hatirlaticiIslemleri", "bildirimIzni", "paylasimAl", "bildirimAl", "vaultListe", "vaultOku",
    ].map { CAPPluginMethod(name: $0, returnType: CAPPluginReturnPromise) }

    private let fm = FileManager.default
    private var kaydedici: AVAudioRecorder?
    private var kayitBaslangic: Date?
    private var kayitHatasi: String?
    private var bekleyenSecim: CAPPluginCall?
    private var secimKlasor = false
    private var kilit = false

    // MARK: - yerler

    private var durumDizini: URL {
        let d = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("yuzuk", isDirectory: true)
        try? fm.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    private var kayitDizini: URL {
        let d = fm.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("kayit", isDirectory: true)
        try? fm.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    private func oku(_ ad: String) -> [String: Any] {
        guard let veri = try? Data(contentsOf: durumDizini.appendingPathComponent(ad)),
              let j = try? JSONSerialization.jsonObject(with: veri) as? [String: Any] else { return [:] }
        return j
    }

    private func yaz(_ ad: String, _ deger: [String: Any]) {
        guard let veri = try? JSONSerialization.data(withJSONObject: deger) else { return }
        try? veri.write(to: durumDizini.appendingPathComponent(ad), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }

    /// Uygulamanın kendi klasörleri dışındaki bir yol kabul edilmez.
    private func kendiDosyam(_ yol: String?) -> URL? {
        guard let yol, !yol.isEmpty else { return nil }
        let u = URL(fileURLWithPath: yol).standardizedFileURL
        let kokler = [kayitDizini.standardizedFileURL.path, fm.temporaryDirectory.standardizedFileURL.path]
        return kokler.contains(where: { u.path.hasPrefix($0 + "/") }) ? u : nil
    }

    // MARK: - izin ve kayıt

    @objc func izinIste(_ call: CAPPluginCall) {
        AVAudioSession.sharedInstance().requestRecordPermission { verildi in call.resolve(["mikrofon": verildi]) }
    }

    @objc func kayitBaslat(_ call: CAPPluginCall) {
        if kaydedici?.isRecording == true { return call.reject("Kayıt zaten sürüyor.") }
        let oturum = AVAudioSession.sharedInstance()
        do {
            try oturum.setCategory(.playAndRecord, mode: .default, options: [.allowBluetooth, .defaultToSpeaker])
            try oturum.setActive(true)
            let dosya = kayitDizini.appendingPathComponent("\(Int(Date().timeIntervalSince1970 * 1000)).m4a")
            let ayar: [String: Any] = [AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 16000, AVNumberOfChannelsKey: 1,
                                       AVEncoderBitRateKey: 48000, AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue]
            let k = try AVAudioRecorder(url: dosya, settings: ayar)
            k.delegate = self
            k.isMeteringEnabled = true
            guard k.record() else { return call.reject("Kayıt başlatılamadı.") }
            kaydedici = k
            kayitBaslangic = Date()
            kayitHatasi = nil
            call.resolve(["yol": dosya.path])
        } catch { call.reject("Kayıt başlatılamadı: \(error.localizedDescription)") }
    }

    @objc func kayitDurdur(_ call: CAPPluginCall) {
        guard let k = kaydedici else { return call.resolve(["yol": NSNull(), "sureSn": 0]) }
        let sure = kayitBaslangic.map { Int(Date().timeIntervalSince($0)) } ?? 0
        let ses = Int(k.currentTime)
        k.stop()
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        let yol = k.url.path
        kaydedici = nil
        kayitBaslangic = nil
        let var_ = fm.fileExists(atPath: yol)
        call.resolve(["yol": var_ ? yol : NSNull(), "sureSn": sure, "sesSn": var_ ? ses : 0, "kesinti": 0])
    }

    @objc func kayitDurumu(_ call: CAPPluginCall) {
        var seviye: Float = 0
        if let k = kaydedici, k.isRecording {
            k.updateMeters()
            seviye = max(0, min(1, pow(10, k.averagePower(forChannel: 0) / 20) * 4))
        }
        call.resolve(["calisiyor": kaydedici?.isRecording == true,
                      "baslangicMs": Int((kayitBaslangic?.timeIntervalSince1970 ?? 0) * 1000),
                      "seviye": seviye, "hata": kayitHatasi ?? NSNull()])
    }

    func audioRecorderEncodeErrorDidOccur(_ recorder: AVAudioRecorder, error: Error?) {
        kayitHatasi = error?.localizedDescription ?? "Kayıt kesildi."
    }

    // MARK: - dosya seçimi

    private func seciciAc(_ call: CAPPluginCall, klasor: Bool) {
        bekleyenSecim = call
        secimKlasor = klasor
        DispatchQueue.main.async {
            let secici = klasor
                ? UIDocumentPickerViewController(forOpeningContentTypes: [.folder])
                : UIDocumentPickerViewController(forOpeningContentTypes: [.audio, .movie], asCopy: true)
            secici.delegate = self
            secici.allowsMultipleSelection = false
            self.bridge?.viewController?.present(secici, animated: true)
        }
    }

    @objc func dosyaSec(_ call: CAPPluginCall) { seciciAc(call, klasor: false) }
    @objc func klasorSec(_ call: CAPPluginCall) { seciciAc(call, klasor: true) }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        bekleyenSecim?.resolve(secimKlasor ? ["agac": NSNull(), "ad": NSNull()] : ["yol": NSNull(), "ad": NSNull()])
        bekleyenSecim = nil
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let call = bekleyenSecim, let u = urls.first else { return }
        bekleyenSecim = nil
        if secimKlasor {
            let acildi = u.startAccessingSecurityScopedResource()
            defer { if acildi { u.stopAccessingSecurityScopedResource() } }
            guard let imi = try? u.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil) else {
                return call.reject("Klasöre kalıcı erişim alınamadı.")
            }
            let kimlik = UUID().uuidString
            var imler = oku("klasorler.json")
            imler[kimlik] = imi.base64EncodedString()
            yaz("klasorler.json", imler)
            let obsidian = fm.fileExists(atPath: u.appendingPathComponent(".obsidian").path)
            call.resolve(["agac": kimlik, "ad": u.lastPathComponent, "obsidian": obsidian])
        } else {
            let hedef = kayitDizini.appendingPathComponent("\(Int(Date().timeIntervalSince1970 * 1000))-\(u.lastPathComponent)")
            do {
                try fm.copyItem(at: u, to: hedef)
                call.resolve(["yol": hedef.path, "ad": u.lastPathComponent])
            } catch { call.reject("Dosya alınamadı: \(error.localizedDescription)") }
        }
    }

    @objc func dosyaVarMi(_ call: CAPPluginCall) {
        call.resolve(["var": kendiDosyam(call.getString("yol")).map { fm.fileExists(atPath: $0.path) } ?? false])
    }

    @objc func dosyaSil(_ call: CAPPluginCall) {
        if let u = kendiDosyam(call.getString("yol")) { try? fm.removeItem(at: u) }
        call.resolve()
    }

    // MARK: - vault (güvenlik kapsamlı klasör)

    /// Seçili klasörde çalışır; erişim iş bitince bırakılır. Yol klasörün dışına çıkamaz.
    private func vaultta<T>(_ call: CAPPluginCall, _ is_: (URL) throws -> T) -> T? {
        guard let kimlik = call.getString("agac"), let b64 = oku("klasorler.json")[kimlik] as? String,
              let imi = Data(base64Encoded: b64) else { call.reject("Klasör seçili değil."); return nil }
        var bayat = false
        guard let kok = try? URL(resolvingBookmarkData: imi, options: [], relativeTo: nil, bookmarkDataIsStale: &bayat) else {
            call.reject("Klasöre erişim kalmamış; yeniden seç."); return nil
        }
        let acildi = kok.startAccessingSecurityScopedResource()
        defer { if acildi { kok.stopAccessingSecurityScopedResource() } }
        do { return try is_(kok) } catch { call.reject(error.localizedDescription); return nil }
    }

    private func icYol(_ kok: URL, _ yol: String?) throws -> URL {
        let parcalar = (yol ?? "").split(separator: "/").map(String.init)
        guard !parcalar.isEmpty, !parcalar.contains(where: { $0 == ".." || $0 == "." }) else {
            throw NSError(domain: "Yuzuk", code: 1, userInfo: [NSLocalizedDescriptionKey: "Geçersiz yol."])
        }
        return parcalar.reduce(kok) { $0.appendingPathComponent($1) }
    }

    @objc func vaultMi(_ call: CAPPluginCall) {
        if let o = vaultta(call, { self.fm.fileExists(atPath: $0.appendingPathComponent(".obsidian").path) }) { call.resolve(["obsidian": o]) }
    }

    @objc func dosyaYaz(_ call: CAPPluginCall) {
        let sonuc: [String: Any]? = vaultta(call) { kok in
            let hedef = try self.icYol(kok, call.getString("yol"))
            let vardi = self.fm.fileExists(atPath: hedef.path)
            if call.getBool("yoksa", false), vardi { return ["ad": hedef.lastPathComponent, "vardi": true] }
            try self.fm.createDirectory(at: hedef.deletingLastPathComponent(), withIntermediateDirectories: true)
            let veri = Data((call.getString("icerik") ?? "").utf8)
            if call.getBool("ekle", false), vardi, let tutamac = try? FileHandle(forWritingTo: hedef) {
                defer { try? tutamac.close() }
                tutamac.seekToEndOfFile()
                tutamac.write(veri)
            } else { try veri.write(to: hedef, options: .atomic) }
            return ["ad": hedef.lastPathComponent]
        }
        if let sonuc { call.resolve(sonuc) }
    }

    @objc func vaultOku(_ call: CAPPluginCall) {
        if let metin = vaultta(call, { try String(contentsOf: try self.icYol($0, call.getString("yol")), encoding: .utf8) }) { call.resolve(["icerik": metin]) }
    }

    @objc func vaultSil(_ call: CAPPluginCall) {
        let silindi: Bool? = vaultta(call) { kok in
            let hedef = try self.icYol(kok, call.getString("yol"))
            guard self.fm.fileExists(atPath: hedef.path) else { return false }
            try self.fm.removeItem(at: hedef)
            var ust = hedef.deletingLastPathComponent()
            while ust.path != kok.path, (try? self.fm.contentsOfDirectory(atPath: ust.path).isEmpty) == true {
                try? self.fm.removeItem(at: ust)
                ust = ust.deletingLastPathComponent()
            }
            return true
        }
        if let silindi { call.resolve(["silindi": silindi]) }
    }

    @objc func vaultListe(_ call: CAPPluginCall) {
        let liste: [[String: Any]]? = vaultta(call) { kok in
            var dosyalar: [[String: Any]] = []
            let anahtarlar: [URLResourceKey] = [.fileSizeKey, .contentModificationDateKey, .isRegularFileKey]
            guard let gezgin = self.fm.enumerator(at: kok, includingPropertiesForKeys: anahtarlar, options: [.skipsHiddenFiles]) else { return [] }
            for case let u as URL in gezgin where u.pathExtension.lowercased() == "md" {
                let d = try u.resourceValues(forKeys: Set(anahtarlar))
                guard d.isRegularFile == true else { continue }
                let goreli = String(u.path.dropFirst(kok.path.count + 1))
                dosyalar.append(["yol": goreli, "boyut": d.fileSize ?? 0, "mtime": Int((d.contentModificationDate?.timeIntervalSince1970 ?? 0) * 1000)])
            }
            return dosyalar
        }
        if let liste { call.resolve(["dosyalar": liste]) }
    }

    // MARK: - yükleme

    @objc func yukle(_ call: CAPPluginCall) {
        guard let dosya = kendiDosyam(call.getString("yol")), fm.fileExists(atPath: dosya.path),
              let adres = call.getString("url").flatMap(URL.init(string:)), adres.scheme == "https",
              let anahtar = call.getString("anahtar") else { return call.reject("Yükleme bilgisi eksik.") }
        let sinir = "yuzuk-\(UUID().uuidString)"
        let govde = fm.temporaryDirectory.appendingPathComponent("\(sinir).govde")
        fm.createFile(atPath: govde.path, contents: nil)
        guard let cikis = try? FileHandle(forWritingTo: govde), let giris = try? FileHandle(forReadingFrom: dosya) else { return call.reject("Kayıt okunamadı.") }
        for (k, v) in call.getObject("alanlar") ?? [:] {
            cikis.write(Data("--\(sinir)\r\nContent-Disposition: form-data; name=\"\(k)\"\r\n\r\n\(v)\r\n".utf8))
        }
        cikis.write(Data("--\(sinir)\r\nContent-Disposition: form-data; name=\"ses\"; filename=\"\(dosya.lastPathComponent)\"\r\nContent-Type: application/octet-stream\r\n\r\n".utf8))
        while case let parca = giris.readData(ofLength: 1 << 20), !parca.isEmpty { cikis.write(parca) }
        cikis.write(Data("\r\n--\(sinir)--\r\n".utf8))
        try? cikis.close()
        try? giris.close()
        var istek = URLRequest(url: adres, timeoutInterval: 1800)
        istek.httpMethod = "POST"
        istek.setValue("Bearer \(anahtar)", forHTTPHeaderField: "Authorization")
        istek.setValue("multipart/form-data; boundary=\(sinir)", forHTTPHeaderField: "Content-Type")
        // Uygulama arka plana geçerse yükleme bitene kadar süre istenir; kayıt her durumda telefonda kalır.
        let gorev = UIApplication.shared.beginBackgroundTask(withName: "yuzuk-yukleme")
        URLSession.shared.uploadTask(with: istek, fromFile: govde) { veri, yanit, hata in
            try? self.fm.removeItem(at: govde)
            UIApplication.shared.endBackgroundTask(gorev)
            if let hata { return call.reject(hata.localizedDescription) }
            call.resolve(["durum": (yanit as? HTTPURLResponse)?.statusCode ?? 0, "govde": String(data: veri ?? Data(), encoding: .utf8) ?? ""])
        }.resume()
    }

    // MARK: - bildirim ve hatırlatıcı

    private let merkez = UNUserNotificationCenter.current()
    private let onEk = "yuzuk-hatirlatici-"

    @objc func bildirimIzni(_ call: CAPPluginCall) {
        merkez.requestAuthorization(options: [.alert, .sound, .badge]) { verildi, _ in call.resolve(["verildi": verildi]) }
    }

    private func bildirimKur(id: String, baslik: String, metin: String, zamanMs: Double?, tamam: @escaping (Bool) -> Void) {
        let icerik = UNMutableNotificationContent()
        icerik.title = baslik
        icerik.body = metin
        icerik.sound = .default
        var tetik: UNNotificationTrigger?
        if let zamanMs {
            let kalan = zamanMs / 1000 - Date().timeIntervalSince1970
            guard kalan > 1 else { return tamam(false) }
            tetik = UNTimeIntervalNotificationTrigger(timeInterval: kalan, repeats: false)
        }
        merkez.add(UNNotificationRequest(identifier: id, content: icerik, trigger: tetik)) { tamam($0 == nil) }
    }

    @objc func bildir(_ call: CAPPluginCall) {
        bildirimKur(id: UUID().uuidString, baslik: call.getString("baslik") ?? "Yüzük", metin: call.getString("metin") ?? "", zamanMs: nil) { _ in call.resolve() }
    }

    @objc func hatirlaticiKur(_ call: CAPPluginCall) {
        guard let id = call.getString("id"), let zaman = call.getDouble("zamanMs") else { return call.reject("Hatırlatıcı bilgisi eksik.") }
        bildirimKur(id: onEk + id, baslik: call.getString("baslik") ?? "", metin: call.getString("metin") ?? "", zamanMs: zaman) { call.resolve(["kuruldu": $0]) }
    }

    /// Gelen liste tamdır: listede olmayan kurulu hatırlatıcı iptal edilir. iOS en çok 64 bekleyen bildirim tutar; en yakınlar kurulur.
    @objc func hatirlaticiEsitle(_ call: CAPPluginCall) {
        let liste = (call.getArray("liste") as? [[String: Any]] ?? [])
            .filter { ($0["zamanMs"] as? Double ?? 0) / 1000 > Date().timeIntervalSince1970 + 1 }
            .sorted { ($0["zamanMs"] as? Double ?? 0) < ($1["zamanMs"] as? Double ?? 0) }
            .prefix(60)
        merkez.getPendingNotificationRequests { bekleyen in
            self.merkez.removePendingNotificationRequests(withIdentifiers: bekleyen.map(\.identifier).filter { $0.hasPrefix(self.onEk) })
            let grup = DispatchGroup()
            var kurulan = 0
            for h in liste {
                guard let id = h["id"] as? String, let zaman = h["zamanMs"] as? Double else { continue }
                grup.enter()
                self.bildirimKur(id: self.onEk + id, baslik: h["baslik"] as? String ?? "", metin: h["metin"] as? String ?? "", zamanMs: zaman) {
                    if $0 { kurulan += 1 }
                    grup.leave()
                }
            }
            grup.notify(queue: .main) { call.resolve(["kurulan": kurulan]) }
        }
    }

    @objc func hatirlaticiSayisi(_ call: CAPPluginCall) {
        merkez.getPendingNotificationRequests { b in call.resolve(["sayi": b.filter { $0.identifier.hasPrefix(self.onEk) }.count]) }
    }

    @objc func hatirlaticiBaglanti(_ call: CAPPluginCall) { call.resolve() } // bildirim düğmeleri bu sürümde yok; anahtar ayrıca saklanmaz
    @objc func hatirlaticiIslemleri(_ call: CAPPluginCall) { call.resolve(["islemler": []]) }
    @objc func bildirimAl(_ call: CAPPluginCall) { call.resolve([:]) }
    @objc func paylasimAl(_ call: CAPPluginCall) { call.resolve([:]) }

    // MARK: - cihaz, eşitleme izi, arka plan

    @objc func cihazIzi(_ call: CAPPluginCall) {
        guard let kimlik = UIDevice.current.identifierForVendor?.uuidString else { return call.resolve([:]) }
        let ozet = SHA256.hash(data: Data("yuzuk:\(kimlik)".utf8)).map { String(format: "%02x", $0) }.joined()
        call.resolve(["izi": ozet])
    }

    @objc func esitlemeIzi(_ call: CAPPluginCall) {
        let d = oku("esitleme.json")
        call.resolve(["iz": d["iz"] ?? [:], "varmi": d["iz"] != nil, "gerek": false, "zaman": d["zaman"] ?? 0, "metin": d["metin"] ?? ""])
    }

    @objc func esitlemeIziYaz(_ call: CAPPluginCall) {
        var d: [String: Any] = ["iz": call.getObject("iz") ?? [:], "metin": call.getString("metin") ?? ""]
        if call.getBool("tamam", false) { d["zaman"] = Int(Date().timeIntervalSince1970 * 1000) } else { d["zaman"] = oku("esitleme.json")["zaman"] ?? 0 }
        yaz("esitleme.json", d)
        call.resolve()
    }

    @objc func esitlemeKilit(_ call: CAPPluginCall) {
        if call.getBool("al", false) {
            let alindi = !kilit
            kilit = true
            call.resolve(["alindi": alindi])
        } else { kilit = false; call.resolve([:]) }
    }

    // iOS'ta arka plan teslim işçisi yok: kayıtlar uygulama öne geldiğinde gönderilir ve teslim alınır.
    @objc func arkaPlanKur(_ call: CAPPluginCall) { call.resolve() }
    @objc func arkaPlanOku(_ call: CAPPluginCall) { call.resolve(["teslimler": [:], "yuklemeler": [:]]) }
    @objc func arkaPlanOnayla(_ call: CAPPluginCall) { call.resolve() }
    @objc func kenarlariYenile(_ call: CAPPluginCall) { call.resolve() }

    // MARK: - bu sürümde iOS'ta olmayanlar

    private func yok(_ call: CAPPluginCall, _ ne: String) { call.unavailable("\(ne) iOS'ta henüz yok.") }
    @objc func googleGiris(_ call: CAPPluginCall) { yok(call, "Google ile giriş") }
    @objc func canliBaslat(_ call: CAPPluginCall) { yok(call, "Canlı yazıya dökme") }
    @objc func canliDurdur(_ call: CAPPluginCall) { call.resolve(["dokum": NSNull(), "sureSn": 0]) }
    @objc func canliDurumu(_ call: CAPPluginCall) {
        call.resolve(["calisiyor": false, "baslangicMs": 0, "seviye": 0, "isleniyor": false, "gonderilen": 0, "yerelde": 0,
                      "bekleyen": 0, "cumle": 0, "durum": NSNull(), "dokum": NSNull()])
    }
    @objc func dokumOku(_ call: CAPPluginCall) { yok(call, "Telefonda döküm") }
    @objc func modelDurumu(_ call: CAPPluginCall) { call.resolve(["hazir": false, "indiriliyor": false, "indirilen": 0, "boyut": 0, "hata": NSNull()]) }
    @objc func modelIndir(_ call: CAPPluginCall) { yok(call, "Telefonda yazıya dökme modeli") }
    @objc func modelSil(_ call: CAPPluginCall) { call.resolve() }
}
