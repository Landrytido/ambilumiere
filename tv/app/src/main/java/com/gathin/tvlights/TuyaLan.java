package com.gathin.tvlights;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * A Tuya device on the local network, protocol 3.5 (TCP 6668, AES-GCM frames prefixed 0x6699) or 3.3 (older
 * devices: AES-ECB frames prefixed 0x55AA).
 * After open() a reader thread keeps the connection alive (heartbeat) and hands every datapoint update to the
 * listener: both what the device pushes and the echo of our own commands. Same wire formats as tinytuya's.
 *
 * 3.5 frame: 00 00 66 99 | 00 00 | seq(4) | cmd(4) | length(4) | iv(12) | ciphertext | tag(16) | 00 00 99 66,
 * length = iv + ciphertext + tag, and the 14 bytes after the prefix are the GCM's additional data.
 * 3.5 session: the key negotiation (cmd 3, 4, 5) runs under the local key and ends with a key made of both nonces.
 *
 * 3.3 frame: 00 00 55 AA | seq(4) | cmd(4) | length(4) | payload | crc32(4) | 00 00 AA 55, length = payload + 8.
 * The payload is the JSON encrypted with the local key (AES-ECB); for a control command it is led by "3.3" and
 * 12 zero bytes. An answer from the device is led by a 4-byte return code. No session key.
 */
final class TuyaLan {

    interface Listener {
        /** A datapoint update ({"20":true,...}), on the reader thread. */
        void onDps(JSONObject dps);

        /** The connection ended without close() being called, on the reader thread. */
        void onClosed();
    }

    private static final int CMD_SESS_START = 3, CMD_SESS_RESP = 4, CMD_SESS_FINISH = 5;
    private static final int CMD_HEART_BEAT = 9, CMD_CONTROL_NEW = 0x0d, CMD_DP_QUERY_NEW = 0x10;
    private static final int CMD_CONTROL_33 = 7, CMD_DP_QUERY_33 = 0x0a;
    private static final int HEADER_33 = 16, TRAILER_33 = 8;
    private static final int HEADER = 18, IV = 12, TAG = 16, SUFFIX = 4, MAX_LENGTH = 1 << 16;
    private static final int CONNECT_MS = 3000;
    private static final int READ_TICK_MS = 1000;
    private static final long HEARTBEAT_MS = 8000L;
    private static final byte[] PREFIX = {0x00, 0x00, 0x66, (byte) 0x99};
    private static final byte[] SUFFIX_BYTES = {0x00, 0x00, (byte) 0x99, 0x66};
    private static final byte[] PREFIX_33 = {0x00, 0x00, 0x55, (byte) 0xAA};
    private static final byte[] SUFFIX_33 = {0x00, 0x00, (byte) 0xAA, 0x55};
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final class Frame {
        final int cmd;
        final byte[] payload;

        Frame(int cmd, byte[] payload) {
            this.cmd = cmd;
            this.payload = payload;
        }
    }

    private final String host;
    private final int port;
    private final String deviceId;
    private final byte[] localKey;
    private final boolean v33;
    private volatile Listener listener;

    /** Guards the socket, the output stream, the sequence number and what is sent. */
    private final Object sendLock = new Object();
    private final Object queryLock = new Object();
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private int seq;
    private volatile byte[] key;
    private volatile boolean open;
    private volatile BlockingQueue<JSONObject> queryWaiter;

    /** What has been read from the socket and not yet taken as a frame; the reader's alone (or open()'s, before it starts). */
    private byte[] buf = new byte[1024];
    private int buffered;

    TuyaLan(String host, int port, String deviceId, String localKey) {
        this(host, port, deviceId, localKey, "3.5");
    }

    /** version is "3.5" or "3.3". */
    TuyaLan(String host, int port, String deviceId, String localKey, String version) {
        this.v33 = "3.3".equals(version);
        this.host = host;
        this.port = port;
        this.deviceId = deviceId;
        this.localKey = localKey.getBytes(StandardCharsets.UTF_8);
    }

    void setListener(Listener l) {
        listener = l;
    }

    boolean isOpen() {
        return open;
    }

    void open() throws IOException {
        close();
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_MS);
            s.setSoTimeout(CONNECT_MS);
            s.setTcpNoDelay(true);
            s.setKeepAlive(true);
            synchronized (sendLock) {
                socket = s;
                in = s.getInputStream();
                out = s.getOutputStream();
                key = localKey;
                seq = 1;
            }
            buffered = 0;
            if (!v33) negotiate();
            open = true;
        } catch (IOException | RuntimeException e) {
            shutdown(s);
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
        s.setSoTimeout(READ_TICK_MS);
        Thread t = new Thread(() -> readLoop(s), "TuyaLan-" + host);
        t.setDaemon(true);
        t.start();
    }

    /** Local key to session key: three frames, each side proving it holds the local key. */
    private void negotiate() throws IOException {
        try {
            byte[] localNonce = random(16);
            send(CMD_SESS_START, localNonce, false);
            Frame resp = null;
            for (int i = 0; i < 3 && (resp == null || resp.cmd != CMD_SESS_RESP); i++) resp = readFrame(true);
            if (resp == null || resp.cmd != CMD_SESS_RESP || resp.payload.length < 48) {
                throw new IOException("session key: no answer from the device");
            }
            byte[] remoteNonce = Arrays.copyOfRange(resp.payload, 0, 16);
            byte[] expected = hmac(localKey, localNonce);
            if (!MessageDigest.isEqual(expected, Arrays.copyOfRange(resp.payload, 16, 48))) {
                throw new IOException("session key: the device does not know this local key");
            }
            send(CMD_SESS_FINISH, hmac(localKey, remoteNonce), false);
            byte[] mixed = new byte[16];
            for (int i = 0; i < 16; i++) mixed[i] = (byte) (localNonce[i] ^ remoteNonce[i]);
            // AES-GCM of the mixed nonces under the local key, without additional data; its first 16 bytes are the key.
            byte[] sealed = gcm(true, localKey, Arrays.copyOfRange(localNonce, 0, IV), null, mixed);
            key = Arrays.copyOfRange(sealed, 0, 16);
        } catch (GeneralSecurityException e) {
            throw new IOException("session key: " + e.getMessage(), e);
        }
    }

    JSONObject queryStatus() throws IOException {
        synchronized (queryLock) {
            BlockingQueue<JSONObject> waiter = new LinkedBlockingQueue<>();
            queryWaiter = waiter;
            try {
                if (v33) {
                    String ask = "{\"gwId\":\"" + deviceId + "\",\"devId\":\"" + deviceId + "\",\"uid\":\""
                            + deviceId + "\",\"t\":\"" + now() + "\"}";
                    send(CMD_DP_QUERY_33, ask.getBytes(StandardCharsets.UTF_8), false);
                } else {
                    send(CMD_DP_QUERY_NEW, "{}".getBytes(StandardCharsets.UTF_8), false);
                }
                JSONObject answer = waiter.poll(CONNECT_MS, TimeUnit.MILLISECONDS);
                if (answer == null) throw new IOException("no answer to the status query");
                return answer;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            } finally {
                queryWaiter = null;
            }
        }
    }

    void setDps(JSONObject dps) throws IOException {
        try {
            JSONObject command = new JSONObject();
            if (v33) {
                command.put("devId", deviceId);
                command.put("uid", deviceId);
                command.put("t", String.valueOf(now()));
                command.put("dps", dps);
                send(CMD_CONTROL_33, command.toString().getBytes(StandardCharsets.UTF_8), true);
                return;
            }
            command.put("protocol", 5);
            command.put("t", System.currentTimeMillis() / 1000);
            command.put("data", new JSONObject().put("dps", dps));
            send(CMD_CONTROL_NEW, command.toString().getBytes(StandardCharsets.UTF_8), true);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    void close() {
        Socket s;
        synchronized (sendLock) {
            s = socket;
        }
        open = false;
        if (s != null) shutdown(s);
    }

    // ---- reader ------------------------------------------------------------------------------------------------

    private void readLoop(Socket s) {
        long lastBeat = System.currentTimeMillis();
        try {
            while (open && socket == s) {
                try {
                    dispatch(readFrame(false));
                } catch (SocketTimeoutException idle) {
                    // nothing arrived this tick: the heartbeat below is all there is to do
                }
                long now = System.currentTimeMillis();
                if (now - lastBeat >= HEARTBEAT_MS) {
                    String beat = "{\"gwId\":\"" + deviceId + "\",\"devId\":\"" + deviceId + "\"}";
                    send(CMD_HEART_BEAT, beat.getBytes(StandardCharsets.UTF_8), false);
                    lastBeat = now;
                }
            }
        } catch (IOException | RuntimeException ended) {
            // falls through to the cleanup
        }
        boolean unexpected;
        synchronized (sendLock) {
            unexpected = open && socket == s;
        }
        shutdown(s);
        if (unexpected) {
            open = false;
            Listener l = listener;
            if (l != null) {
                try {
                    l.onClosed();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private void dispatch(Frame f) {
        JSONObject json = parse(f.payload);
        JSONObject dps = json == null ? null : dpsOf(json);
        if (f.cmd == CMD_DP_QUERY_NEW || (v33 && f.cmd == CMD_DP_QUERY_33)) {
            BlockingQueue<JSONObject> waiter = queryWaiter;
            if (waiter != null) waiter.offer(dps != null ? dps : new JSONObject());
            return;
        }
        Listener l = listener;
        if (dps != null && dps.length() > 0 && l != null) {
            try {
                l.onDps(dps);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static long now() {
        return System.currentTimeMillis() / 1000;
    }

    private static JSONObject parse(byte[] payload) {
        if (payload.length == 0 || payload[0] != '{') return null;
        try {
            return new JSONObject(new String(payload, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            return null;
        }
    }

    private static JSONObject dpsOf(JSONObject json) {
        JSONObject dps = json.optJSONObject("dps");
        if (dps == null) {
            JSONObject data = json.optJSONObject("data");
            if (data != null) dps = data.optJSONObject("dps");
        }
        return dps;
    }

    // ---- frames ------------------------------------------------------------------------------------------------

    /** Next frame from the socket. A SocketTimeoutException leaves what was read in place for the next call. */
    private Frame readFrame(boolean handshake) throws IOException {
        for (;;) {
            Frame f = takeFrame(handshake);
            if (f != null) return f;
            if (buffered == buf.length) buf = Arrays.copyOf(buf, Math.min(buf.length * 2, MAX_LENGTH + 64));
            int n = in.read(buf, buffered, buf.length - buffered);
            if (n < 0) throw new IOException("the device closed the connection");
            buffered += n;
        }
    }

    /** A whole frame from the buffer, or null when more bytes are needed. Bytes before a frame prefix are dropped. */
    private Frame takeFrame(boolean handshake) throws IOException {
        if (v33) return takeFrame33();
        int start = indexOf(buf, buffered, PREFIX);
        if (start < 0) {
            // keep the last 3 bytes: they may be the beginning of a prefix
            int keep = Math.min(3, buffered);
            System.arraycopy(buf, buffered - keep, buf, 0, keep);
            buffered = keep;
            return null;
        }
        if (start > 0) {
            System.arraycopy(buf, start, buf, 0, buffered - start);
            buffered -= start;
        }
        if (buffered < HEADER) return null;
        int length = readInt(buf, 14);
        if (length < IV + TAG || length > MAX_LENGTH) {
            // not a real frame: skip this prefix and look for the next one
            System.arraycopy(buf, 1, buf, 0, buffered - 1);
            buffered -= 1;
            return takeFrame(handshake);
        }
        int total = HEADER + length + SUFFIX;
        if (buffered < total) {
            if (buf.length < total) buf = Arrays.copyOf(buf, total);
            return null;
        }
        byte[] aad = Arrays.copyOfRange(buf, 4, HEADER);
        int cmd = readInt(buf, 10);
        byte[] iv = Arrays.copyOfRange(buf, HEADER, HEADER + IV);
        byte[] sealed = Arrays.copyOfRange(buf, HEADER + IV, HEADER + length);
        System.arraycopy(buf, total, buf, 0, buffered - total);
        buffered -= total;
        byte[] plain;
        try {
            plain = gcm(false, key, iv, aad, sealed);
        } catch (GeneralSecurityException e) {
            throw new IOException("frame rejected (bad authentication tag)", e);
        }
        // A 4-byte return code leads the payload (a JSON or a nonce never starts with a zero byte, except in the
        // handshake answer, where the nonce follows the code whatever it is), then possibly the version header.
        int off = 0;
        if (plain.length >= 4 && (plain[0] == 0 || (handshake && cmd == CMD_SESS_RESP))) off = 4;
        if (plain.length >= off + 15 && plain[off] == '3' && plain[off + 1] == '.' && plain[off + 2] == '5') off += 15;
        return new Frame(cmd, Arrays.copyOfRange(plain, off, plain.length));
    }

    /** 3.3: a whole frame from the buffer, or null when more bytes are needed. Same resync as takeFrame. */
    private Frame takeFrame33() throws IOException {
        int start = indexOf(buf, buffered, PREFIX_33);
        if (start < 0) {
            int keep = Math.min(3, buffered);
            System.arraycopy(buf, buffered - keep, buf, 0, keep);
            buffered = keep;
            return null;
        }
        if (start > 0) {
            System.arraycopy(buf, start, buf, 0, buffered - start);
            buffered -= start;
        }
        if (buffered < HEADER_33) return null;
        int length = readInt(buf, 12);
        if (length < TRAILER_33 || length > MAX_LENGTH) {
            System.arraycopy(buf, 1, buf, 0, buffered - 1);
            buffered -= 1;
            return takeFrame33();
        }
        int total = HEADER_33 + length;
        if (buffered < total) {
            if (buf.length < total) buf = Arrays.copyOf(buf, total);
            return null;
        }
        int cmd = readInt(buf, 8);
        byte[] body = Arrays.copyOfRange(buf, HEADER_33, total - TRAILER_33);
        int crc = readInt(buf, total - TRAILER_33);
        CRC32 sum = new CRC32();
        sum.update(buf, 0, total - TRAILER_33);
        System.arraycopy(buf, total, buf, 0, buffered - total);
        buffered -= total;
        if ((int) sum.getValue() != crc) throw new IOException("frame rejected (bad checksum)");
        // Led by a 4-byte return code and/or the "3.3" + 12 zeros header (a pushed update has the header only).
        int off = 0;
        if (has33(body, 0)) off = 15;
        else if (has33(body, 4)) off = 19;
        else if (body.length % 16 == 4) off = 4;
        byte[] plain = new byte[0];
        if (body.length - off >= 16 && (body.length - off) % 16 == 0) {
            try {
                plain = ecb(false, localKey, Arrays.copyOfRange(body, off, body.length));
            } catch (GeneralSecurityException e) {
                plain = new byte[0];   // garbled: ignored, not fatal
            }
        }
        return new Frame(cmd, plain);
    }

    private static boolean has33(byte[] b, int at) {
        return b.length >= at + 15 && b[at] == '3' && b[at + 1] == '.' && b[at + 2] == '3';
    }

    private void send33(int cmd, byte[] json, boolean versionHeader) throws IOException {
        byte[] sealed;
        try {
            sealed = ecb(true, localKey, json);
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
        int lead = versionHeader ? 15 : 0;
        synchronized (sendLock) {
            if (socket == null || out == null) throw new IOException("closed");
            byte[] frame = new byte[HEADER_33 + lead + sealed.length + TRAILER_33];
            System.arraycopy(PREFIX_33, 0, frame, 0, 4);
            writeInt(frame, 4, seq++);
            writeInt(frame, 8, cmd);
            writeInt(frame, 12, lead + sealed.length + TRAILER_33);
            if (versionHeader) {
                frame[HEADER_33] = '3';
                frame[HEADER_33 + 1] = '.';
                frame[HEADER_33 + 2] = '3';
            }
            System.arraycopy(sealed, 0, frame, HEADER_33 + lead, sealed.length);
            CRC32 sum = new CRC32();
            sum.update(frame, 0, frame.length - TRAILER_33);
            writeInt(frame, frame.length - TRAILER_33, (int) sum.getValue());
            System.arraycopy(SUFFIX_33, 0, frame, frame.length - 4, 4);
            out.write(frame);
            out.flush();
        }
    }

    private void send(int cmd, byte[] payload, boolean versionHeader) throws IOException {
        if (v33) {
            send33(cmd, payload, versionHeader);
            return;
        }
        byte[] body = payload;
        if (versionHeader) {
            body = new byte[15 + payload.length];
            body[0] = '3';
            body[1] = '.';
            body[2] = '5';
            System.arraycopy(payload, 0, body, 15, payload.length);
        }
        synchronized (sendLock) {
            if (socket == null || out == null) throw new IOException("closed");
            byte[] iv = random(IV);
            byte[] head = new byte[HEADER];
            System.arraycopy(PREFIX, 0, head, 0, 4);
            writeInt(head, 6, seq++);
            writeInt(head, 10, cmd);
            writeInt(head, 14, body.length + IV + TAG);
            byte[] sealed;
            try {
                sealed = gcm(true, key, iv, Arrays.copyOfRange(head, 4, HEADER), body);
            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }
            byte[] frame = new byte[HEADER + IV + sealed.length + SUFFIX];
            System.arraycopy(head, 0, frame, 0, HEADER);
            System.arraycopy(iv, 0, frame, HEADER, IV);
            System.arraycopy(sealed, 0, frame, HEADER + IV, sealed.length);
            System.arraycopy(SUFFIX_BYTES, 0, frame, frame.length - SUFFIX, SUFFIX);
            out.write(frame);
            out.flush();
        }
    }

    // ---- helpers -----------------------------------------------------------------------------------------------

    private void shutdown(Socket s) {
        synchronized (sendLock) {
            if (socket == s) {
                socket = null;
                in = null;
                out = null;
            }
        }
        try {
            s.close();
        } catch (IOException ignored) {
        }
    }

    /** AES-128-GCM; Java appends the tag to the ciphertext and expects it there. */
    private static byte[] gcm(boolean encrypt, byte[] key, byte[] iv, byte[] aad, byte[] data)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG * 8, iv));
        if (aad != null) c.updateAAD(aad);
        return c.doFinal(data);
    }

    private static byte[] ecb(boolean encrypt, byte[] key, byte[] data) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("AES/ECB/PKCS5Padding");
        c.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
        return c.doFinal(data);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] random(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private static int indexOf(byte[] data, int len, byte[] pattern) {
        for (int i = 0; i + pattern.length <= len; i++) {
            int j = 0;
            while (j < pattern.length && data[i + j] == pattern[j]) j++;
            if (j == pattern.length) return i;
        }
        return -1;
    }

    private static int readInt(byte[] b, int at) {
        return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
    }

    private static void writeInt(byte[] b, int at, int v) {
        b[at] = (byte) (v >>> 24);
        b[at + 1] = (byte) (v >>> 16);
        b[at + 2] = (byte) (v >>> 8);
        b[at + 3] = (byte) v;
    }
}
