package app.claudian.yuzuk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * 0.15.2 (22.11.2026): relay'e tek WebSocket. Uzun yoklama relay'in Durable Object'ini gün boyu uyanık tutuyordu ve ücretsiz
 * katmanın günlük süresini bitirdi (relay 22.11'de durdu). Soket boştayken relay uyur; "ping"i Cloudflare kendisi cevaplar.
 *
 * Android'de hazır WebSocket istemcisi yok; burada yalnız gerekeni yapan küçük bir istemci var (RFC 6455): TLS üzerinden
 * el sıkışma, maskeli metin çerçevesi gönderme, metin/ping/pong/kapanış okuma. Mesajlar küçük JSON'dur (relay en çok
 * ~800 KB ileten istekleri taşır). Bağımlılık yok.
 */
final class RelaySoketi implements AutoCloseable {
    private static final SecureRandom RASTGELE = new SecureRandom();
    private static final int AZAMI = 2 * 1024 * 1024;
    private final Socket soket;
    private final InputStream in;
    private final OutputStream out;
    private final Object yazma = new Object();

    private RelaySoketi(Socket s) throws IOException { soket = s; in = s.getInputStream(); out = s.getOutputStream(); }

    /** El sıkışma 101 değilse IOException ("desteklenmiyor") — çağıran uzun yoklamaya döner. */
    static RelaySoketi baglan(String adres, int okumaSuresiMs) throws IOException {
        URI u = URI.create(adres);
        boolean guvenli = "wss".equals(u.getScheme());
        int port = u.getPort() > 0 ? u.getPort() : (guvenli ? 443 : 80);
        Socket s;
        if (guvenli) {
            SSLSocket t = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
            t.connect(new InetSocketAddress(u.getHost(), port), 15_000);
            javax.net.ssl.SSLParameters p = t.getSSLParameters();
            p.setEndpointIdentificationAlgorithm("HTTPS");
            p.setServerNames(java.util.Collections.singletonList(new javax.net.ssl.SNIHostName(u.getHost())));
            t.setSSLParameters(p);
            t.startHandshake();
            s = t;
        } else {
            s = new Socket();
            s.connect(new InetSocketAddress(u.getHost(), port), 15_000);
        }
        s.setSoTimeout(15_000);
        byte[] k = new byte[16];
        RASTGELE.nextBytes(k);
        String anahtar = android.util.Base64.encodeToString(k, android.util.Base64.NO_WRAP);
        String yol = (u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath()) + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
        String istek = "GET " + yol + " HTTP/1.1\r\nHost: " + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") + "\r\n"
            + "Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + anahtar + "\r\nSec-WebSocket-Version: 13\r\n"
            + "User-Agent: Yuzuk\r\n\r\n";
        OutputStream o = s.getOutputStream();
        o.write(istek.getBytes(StandardCharsets.US_ASCII));
        o.flush();
        InputStream i = s.getInputStream();
        StringBuilder baslik = new StringBuilder();
        int son4 = 0;
        while (true) {
            int b = i.read();
            if (b < 0) { s.close(); throw new IOException("soket el sıkışması yarıda kaldı"); }
            baslik.append((char) b);
            son4 = (son4 << 8) | b;
            if (son4 == 0x0d0a0d0a) break;
            if (baslik.length() > 16_384) { s.close(); throw new IOException("soket el sıkışması çok uzun"); }
        }
        String h = baslik.toString();
        if (!h.startsWith("HTTP/1.1 101")) { s.close(); throw new IOException("desteklenmiyor: " + h.split("\r\n")[0]); }
        String beklenen;
        try {
            beklenen = android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((anahtar + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)), android.util.Base64.NO_WRAP);
        } catch (Exception e) { s.close(); throw new IOException(e); }
        if (!h.toLowerCase(java.util.Locale.ROOT).contains("sec-websocket-accept: " + beklenen.toLowerCase(java.util.Locale.ROOT))) {
            s.close();
            throw new IOException("soket el sıkışması doğrulanamadı");
        }
        s.setSoTimeout(okumaSuresiMs);
        return new RelaySoketi(s);
    }

    void gonder(String metin) throws IOException { cerceve(0x1, metin.getBytes(StandardCharsets.UTF_8)); }

    private void cerceve(int op, byte[] veri) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(veri.length + 14);
        b.write(0x80 | op);
        if (veri.length < 126) b.write(0x80 | veri.length);
        else if (veri.length < 65536) { b.write(0x80 | 126); b.write(veri.length >>> 8); b.write(veri.length & 0xff); }
        else { b.write(0x80 | 127); for (int i = 7; i >= 0; i--) b.write((int) (((long) veri.length >>> (8 * i)) & 0xff)); }
        byte[] maske = new byte[4];
        RASTGELE.nextBytes(maske);
        b.write(maske, 0, 4);
        for (int i = 0; i < veri.length; i++) b.write(veri[i] ^ maske[i & 3]);
        synchronized (yazma) { out.write(b.toByteArray()); out.flush(); }
    }

    /** Bir sonraki metin mesajı; sunucu kapatırsa null. Ping'e kendisi pong döner. Süre dolarsa SocketTimeoutException. */
    String oku() throws IOException {
        ByteArrayOutputStream mesaj = new ByteArrayOutputStream();
        while (true) {
            int b0 = in.read(), b1 = in.read();
            if (b0 < 0 || b1 < 0) return null;
            int op = b0 & 0x0f;
            long uzunluk = b1 & 0x7f;
            if (uzunluk == 126) uzunluk = ((long) zorunlu() << 8) | zorunlu();
            else if (uzunluk == 127) { uzunluk = 0; for (int i = 0; i < 8; i++) uzunluk = (uzunluk << 8) | zorunlu(); }
            if (uzunluk > AZAMI || mesaj.size() + uzunluk > AZAMI) throw new IOException("soket mesajı çok büyük");
            byte[] maske = (b1 & 0x80) != 0 ? new byte[]{(byte) zorunlu(), (byte) zorunlu(), (byte) zorunlu(), (byte) zorunlu()} : null;
            byte[] veri = new byte[(int) uzunluk];
            int n = 0;
            while (n < veri.length) { int r = in.read(veri, n, veri.length - n); if (r < 0) return null; n += r; }
            if (maske != null) for (int i = 0; i < veri.length; i++) veri[i] ^= maske[i & 3];
            if (op == 0x8) { try { cerceve(0x8, new byte[0]); } catch (IOException ignored) { } return null; }
            if (op == 0x9) { cerceve(0xA, veri); continue; }
            if (op == 0xA) continue;
            mesaj.write(veri, 0, veri.length);
            if ((b0 & 0x80) != 0) return mesaj.toString("UTF-8");
        }
    }

    private int zorunlu() throws IOException { int b = in.read(); if (b < 0) throw new IOException("soket kapandı"); return b; }

    @Override public void close() { try { cerceve(0x8, new byte[0]); } catch (Exception ignored) { } try { soket.close(); } catch (Exception ignored) { } }
}
