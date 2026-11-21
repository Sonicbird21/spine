// Native session-invalidation protection for Spotify's liborbit-jni-spotify.so.
// Runtime inline byte-patch (no trampoline engine): each patch flips a branch or
// NOPs one call, forcing the binary down a path it already executes itself, so it
// cannot crash -- it only skips the teardown.
//
// liborbit is mapped directly out of split_config.arm64_v8a.apk (extractNativeLibs=
// false), so /proc/self/maps names the APK, not the .so, and the lib lives in a
// different linker namespace than this module -- so neither a soname match nor
// dl_iterate_phdr works. Instead we scan maps for ELF headers and pick the one whose
// bytes at all four patch sites match: a 128-bit fingerprint that uniquely identifies
// liborbit and doubles as the version guard. On any mismatch nothing is patched.
//
// shortcut: offsets + expected words are tied to Spotify arm64 build 148653771.
// On a Spotify update, re-derive them in IDA (search the log strings
// "Permanent OAuth error for session" / "Broadcasting forced logout for session").

#include <jni.h>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <cinttypes>
#include <atomic>
#include <sys/mman.h>
#include <unistd.h>
#include <fcntl.h>
#include <android/log.h>
#include "shadowhook.h"

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "SpineNative", __VA_ARGS__)

// --- Native logger surfacing ---------------------------------------------------
// liborbit logs through one central formatter (sub_15EF9C8) whose 1st arg is the
// level and 4th arg (x3) is the format string, but these never reach logcat. We
// inline-hook it and re-emit only the format strings that match our interest set,
// so a playback break prints its real native reason. Filtering keeps this hot,
// variadic function cheap and avoids flooding logcat.
static const uintptr_t kLoggerVa = 0x15EF9C8;

typedef void *(*logger_fn)(void *, void *, void *, const char *, void *, void *,
                           void *, void *, void *, void *, void *, void *);
static logger_fn orig_logger = nullptr;

static bool fmt_of_interest(const char *s) {
    // The "anchor not ready" line floods thousands of times; drop it explicitly.
    if (strstr(s, "anchor not ready")) return false;
    static const char *kNeedles[] = {
        "error", "logout", "licen", "playback", "token", "product", "renew",
        "restrict", "session", "expire", "premium", "unplay", "xresolve",
        "entitle", "revok", "invalid", "audiokey", "audio key", "fail", "denied",
        "forbidden", "not allowed", "buffered", "cannot",
    };
    for (auto n : kNeedles) if (strcasestr(s, n)) return true;
    return false;
}

// For the reason-bearing messages, pull the %s/%d args (arg0 = a5 = x4, etc.) so we
// see the actual failure reason, not just the format string. Arg layout is read from
// each message's own format specifiers, so the casts are type-correct and safe.
static void emit_with_reason(long level, const char *fmt, void *a5, void *a6, void *a7,
                             void *a8, void *a9, void *a10) {
    auto S = [](void *p) -> const char * { return p ? reinterpret_cast<const char *>(p) : "(null)"; };
    if (strstr(fmt, "playbackError:")) {
        __android_log_print(ANDROID_LOG_INFO, "SpineOrbit", "[L%ld] playbackError: %s (%ld)", level, S(a5), (long) a6);
    } else if (strstr(fmt, "Playback failed with error") || strstr(fmt, "Audio error occurred") ||
               strstr(fmt, "onClipPrepareError")) {
        __android_log_print(ANDROID_LOG_INFO, "SpineOrbit", "[L%ld] %s [reason=%s]", level, fmt, S(a5));
    } else if (strstr(fmt, "Decode Error")) {
        __android_log_print(ANDROID_LOG_INFO, "SpineOrbit", "[L%ld] Decode Error %s (%ld)", level, S(a5), (long) a6);
    } else if (strstr(fmt, "CDN failure")) {
        // "CDN failure %d->%d. Error: %s (%d). Http: %d."
        __android_log_print(ANDROID_LOG_INFO, "SpineOrbit", "[L%ld] CDN failure %ld->%ld err=%s (%ld) Http=%ld",
                            level, (long) a5, (long) a6, S(a7), (long) a8, (long) a9);
    } else if (strstr(fmt, "from CDN for head file")) {
        // "Error: %s, result: %d from CDN for head file"
        __android_log_print(ANDROID_LOG_INFO, "SpineOrbit", "[L%ld] CDN head-file err=%s result=%ld", level, S(a5), (long) a6);
    } else if (strstr(fmt, "spac reader")) {
        // "Failed to push data to spac reader, error_code %s (%d)"
        __android_log_print(ANDROID_LOG_INFO, "SpineOrbit", "[L%ld] spac push err=%s (%ld)", level, S(a5), (long) a6);
    } else if (strstr(fmt, "sendStreamStart")) {
        // stream_id=%s(a5) start_position=%llu(a6) media_type=%s(a7) is_video_on=%s(a8) reason_start=%s(a9)
        __android_log_print(ANDROID_LOG_INFO, "SpineOrbit", "[L%ld] streamStart media_type=%s reason_start=%s", level, S(a7), S(a9));
    } else if (strstr(fmt, "resolveContentUri")) {
        // playback_id=%s(a5) source literal, blob_status=%s(a6) blob_bytes=%zu(a7) content_uri=%s(a8)
        __android_log_print(ANDROID_LOG_INFO, "SpineOrbit", "[L%ld] resolve blob_status=%s content_uri=%s", level, S(a6), S(a8));
    } else if (strstr(fmt, "xresolve")) {
        __android_log_print(ANDROID_LOG_INFO, "SpineOrbit", "[L%ld] xresolve err %ld / %s", level, (long) a5, S(a6));
    } else if (strstr(fmt, "client-token")) {
        __android_log_print(ANDROID_LOG_INFO, "SpineOrbit", "[L%ld] client-token cat=%s val=%ld msg=%s", level, S(a5), (long) a6, S(a7));
    } else {
        __android_log_print(ANDROID_LOG_INFO, "SpineOrbit", "[L%ld] %s", level, fmt);
    }
}

static void *proxy_logger(void *a1, void *a2, void *a3, const char *fmt, void *a5,
                          void *a6, void *a7, void *a8, void *a9, void *a10,
                          void *a11, void *a12) {
    if (fmt && fmt_of_interest(fmt)) {
        emit_with_reason((long) a1, fmt, a5, a6, a7, a8, a9, a10);
    }
    return orig_logger(a1, a2, a3, fmt, a5, a6, a7, a8, a9, a10, a11, a12);
}

// --- Client-token gate -------------------------------------------------------------
// sub_B95910 is the client-token acquirer's completion handler (both the granted path
// and every failure path route through it). The result's error code at a2+88 is -1 on
// success (token granted) and an error code otherwise. We expose this as a gate: the Java
// product-state override only applies premium while the client-token is healthy. When the
// client-token wedges (restart/reconnect) it flips to a failure here -> gate OFF -> the
// override backs off to free -> the client-token recovers on the free state -> success
// here -> gate ON -> premium re-enables. Self-healing; no manual relogin.
// Default true so that if this hook never installs, premium behaves as before (ungated).
static std::atomic<bool> g_ct_ready{true};
static const uintptr_t kCtCompleteVa = 0xB95910;
typedef void *(*ct_complete_fn)(void *, void *);
static ct_complete_fn orig_ct_complete = nullptr;

static void *proxy_ct_complete(void *a1, void *a2) {
    if (a2) {
        int code = *reinterpret_cast<int *>(reinterpret_cast<char *>(a2) + 88);
        bool ok = (code == -1);
        if (g_ct_ready.exchange(ok) != ok) {
            LOG("client-token %s (code=%d) -> premium gate %s", ok ? "granted" : "failed", code, ok ? "OPEN" : "CLOSED");
        }
    }
    return orig_ct_complete(a1, a2);
}

static void install_ct_gate(uintptr_t base) {
    static bool done = false;
    if (done) return;
    done = true;
    void *stub = shadowhook_hook_func_addr(
        reinterpret_cast<void *>(base + kCtCompleteVa),
        reinterpret_cast<void *>(proxy_ct_complete),
        reinterpret_cast<void **>(&orig_ct_complete));
    if (stub == nullptr) LOG("client-token gate hook failed: %d (premium stays ungated)", shadowhook_get_errno());
    else LOG("client-token gate installed");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_spine_projectspine_hooks_spotify_misc_NativeSessionProtection_isClientTokenReady(JNIEnv *, jobject) {
    return g_ct_ready.load() ? JNI_TRUE : JNI_FALSE;
}

static void install_logger_hook(uintptr_t base) {
    static bool done = false;
    if (done) return;
    done = true;
    if (shadowhook_init(SHADOWHOOK_MODE_UNIQUE, false) != 0) {
        LOG("shadowhook_init failed: %d", shadowhook_get_errno());
        return;
    }
    void *stub = shadowhook_hook_func_addr(
        reinterpret_cast<void *>(base + kLoggerVa),
        reinterpret_cast<void *>(proxy_logger),
        reinterpret_cast<void **>(&orig_logger));
    if (stub == nullptr) LOG("native logger hook failed: %d", shadowhook_get_errno());
    else LOG("native logger hook installed (tag SpineOrbit)");
}

struct Patch {
    uintptr_t va;    // IDA virtual address (relative to liborbit's load base)
    uint32_t expect; // original instruction word
    uint32_t repl;   // replacement word
    const char *name;
};

static Patch kPatches[] = {
    // sub_BEC96C: stop our product-state override from triggering "Product state changed,
    // force renew". That renew reloads the OAuth token, and when it lands during Spotify's
    // client-token/auth handshake (cold start, reopen, periodic refresh) it wedges the
    // client-token -> every spclient request fails http_error_fail -> no audio key -> skip.
    // Suppressing the product-state-triggered renew leaves the normal on-expiry token
    // refresh intact, so streaming still gets a valid token.
    // @ 0xBEC9A4: TBZ -> B loc_BEC9CC (never run the force-renew block).
    {0xBEC9A4, 0x36000154, 0x1400000A, "oauth-skip-force-renew"},
    // @ 0xBECAD4: NOP TBZ (at-signal change never clears the token either).
    {0xBECAD4, 0x3607F773, 0xD503201F, "oauth-skip-atsignal-clear"},
};

struct Region {
    uintptr_t start, end;
    bool exec, readable;
    char path[176];
};

static Region g_regions[8192];
static int g_nreg;

// Read process memory through /proc/self/mem: pread returns an error (not SIGSEGV) if the
// page is unmapped. Needed because a maps region can be unmapped between parse_maps and the
// read during early startup (JIT/lib churn) -- a direct deref there crashes in memcmp.
static int g_mem_fd = -1;
static bool safe_read(uintptr_t addr, void *buf, size_t len) {
    if (g_mem_fd < 0) g_mem_fd = open("/proc/self/mem", O_RDONLY | O_CLOEXEC);
    if (g_mem_fd < 0) return false;
    return pread(g_mem_fd, buf, len, static_cast<off_t>(addr)) == static_cast<ssize_t>(len);
}

// A/B toggle: when false, liborbit is located and the native logger is installed,
// but the session patches are NOT applied -- to test if they cause the restart break.
// Force-renew suppression is confirmed harmful (premium-scoped token never issued ->
// on-demand denied after the first skip), so no native session patches are applied; the
// restart wedge is handled on the Java side by timing the product-state override.
static const bool g_apply_patches = false;

static void parse_maps() {
    g_nreg = 0;
    FILE *f = fopen("/proc/self/maps", "re");
    if (!f) return;
    char line[512];
    const int cap = (int) (sizeof g_regions / sizeof g_regions[0]);
    while (g_nreg < cap && fgets(line, sizeof line, f)) {
        uintptr_t s, e;
        char perm[8];
        if (sscanf(line, "%" SCNxPTR "-%" SCNxPTR " %7s", &s, &e, perm) != 3) continue;
        Region &r = g_regions[g_nreg];
        r.start = s;
        r.end = e;
        r.readable = perm[0] == 'r';
        r.exec = perm[2] == 'x';
        r.path[0] = 0;
        const char *p = strchr(line, '/'); // paths in maps are absolute
        if (p) {
            strncpy(r.path, p, sizeof r.path - 1);
            r.path[sizeof r.path - 1] = 0;
            char *nl = strchr(r.path, '\n');
            if (nl) *nl = 0;
        }
        g_nreg++;
    }
    fclose(f);
}

// Is [t, t+4) inside an executable mapping backed by the same file? Guards every
// code read/write so a wrong base can never dereference an unmapped address.
static bool in_exec_region(uintptr_t t, const char *path) {
    for (int i = 0; i < g_nreg; i++) {
        Region &r = g_regions[i];
        if (r.exec && t >= r.start && t + 4 <= r.end && strcmp(r.path, path) == 0) return true;
    }
    return false;
}

static bool verify_site(uintptr_t base, const char *path, const Patch &p) {
    uintptr_t t = base + p.va;
    if (!in_exec_region(t, path)) return false;
    uint32_t w;
    if (!safe_read(t, &w, sizeof w)) return false;
    return w == p.expect || w == p.repl; // repl so a re-scan still identifies the base
}

static uintptr_t find_base() {
    parse_maps();
    static const unsigned char elf_magic[4] = {0x7f, 'E', 'L', 'F'};
    for (int i = 0; i < g_nreg; i++) {
        Region &r = g_regions[i];
        if (!r.readable || r.path[0] == 0) continue;
        if (!strstr(r.path, ".apk") && !strstr(r.path, ".so")) continue;
        // An ELF header mapped here means this is some lib's base (first LOAD, vaddr 0).
        // safe_read, not a direct deref: the region may be unmapped by now (startup race).
        unsigned char magic[4];
        if (!safe_read(r.start, magic, 4) || memcmp(magic, elf_magic, 4) != 0) continue;
        uintptr_t base = r.start;
        bool ok = true;
        for (auto &p : kPatches) {
            if (!verify_site(base, r.path, p)) { ok = false; break; }
        }
        if (ok) {
            LOG("liborbit located: base %p (%s)", reinterpret_cast<void *>(base), r.path);
            return base;
        }
    }
    return 0;
}

static void patch_one(uintptr_t base, const Patch &p) {
    auto *addr = reinterpret_cast<uint32_t *>(base + p.va);
    if (*addr == p.repl) { LOG("%s: already patched", p.name); return; }
    if (*addr != p.expect) {
        LOG("%s: byte mismatch (got %08x want %08x) -- not patching", p.name, *addr, p.expect);
        return;
    }
    long ps = sysconf(_SC_PAGESIZE);
    auto page = reinterpret_cast<void *>(reinterpret_cast<uintptr_t>(addr) & ~(uintptr_t)(ps - 1));
    if (mprotect(page, ps, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        LOG("%s: mprotect failed", p.name);
        return;
    }
    *addr = p.repl;
    __builtin___clear_cache(reinterpret_cast<char *>(addr), reinterpret_cast<char *>(addr) + 4);
    mprotect(page, ps, PROT_READ | PROT_EXEC);
    LOG("%s: patched", p.name);
}

// true  = library located and patches attempted (stop polling)
// false = library not located yet (keep polling)
extern "C" JNIEXPORT jboolean JNICALL
Java_com_spine_projectspine_hooks_spotify_misc_NativeSessionProtection_tryInstall(JNIEnv *, jobject) {
    uintptr_t base = find_base();
    if (!base) {
        static bool warned = false;
        if (!warned) { warned = true; LOG("liborbit not located yet (scanned %d regions)", g_nreg); }
        return JNI_FALSE;
    }
    // A/B: patches disabled to test whether oauth-error-skip-teardown / forced-logout-nop
    // are blocking auth recovery on restart (the "wedged until relogin" break). find_base
    // still uses kPatches as its fingerprint; we just don't apply them.
    if (g_apply_patches) for (auto &p : kPatches) patch_one(base, p);
    install_logger_hook(base);
    install_ct_gate(base);
    return JNI_TRUE;
}
