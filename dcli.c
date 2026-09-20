#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <errno.h>
#include <time.h>
#include <signal.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define DEFAULT_HOST    "127.0.0.1"
#define DEFAULT_PORT    12345
#define DEFAULT_PACKAGE "com.termux"
#define KEY_FILE        ".dcli_key"
#define HOST_FILE       ".dcli_host"
#define PKG_FILE        ".dcli_package"
#define BUF_SIZE        4096

/* ================= 语言 ================= */
static int g_zh = 0;

static void init_lang(void) {
    const char *lang = getenv("LANG");
    if (lang && (strncmp(lang, "zh", 2) == 0 ||
                 strncmp(lang, "ZH", 2) == 0 ||
                 strncmp(lang, "Zh", 2) == 0)) {
        g_zh = 1;
    }
}

static const char *tr(const char *en, const char *zh) {
    return g_zh ? zh : en;
}

/* ================= SHA1 ================= */
typedef struct {
    uint32_t state[5];
    uint32_t count[2];
    unsigned char buffer[64];
} SHA1_CTX;

#define ROL(v, b) (((v) << (b)) | ((v) >> (32 - (b))))

static void sha1_transform(uint32_t state[5], const unsigned char buffer[64]) {
    uint32_t a, b, c, d, e, w[80];
    for (int i = 0; i < 16; i++) {
        w[i] = ((uint32_t)buffer[i*4]   << 24) |
               ((uint32_t)buffer[i*4+1] << 16) |
               ((uint32_t)buffer[i*4+2] << 8)  |
               ((uint32_t)buffer[i*4+3]);
    }
    for (int i = 16; i < 80; i++)
        w[i] = ROL(w[i-3] ^ w[i-8] ^ w[i-14] ^ w[i-16], 1);

    a = state[0]; b = state[1]; c = state[2]; d = state[3]; e = state[4];
    for (int i = 0; i < 80; i++) {
        uint32_t f, k;
        if (i < 20)      { f = (b & c) | ((~b) & d); k = 0x5A827999; }
        else if (i < 40) { f = b ^ c ^ d;            k = 0x6ED9EBA1; }
        else if (i < 60) { f = (b & c) | (b & d) | (c & d); k = 0x8F1BBCDC; }
        else             { f = b ^ c ^ d;            k = 0xCA62C1D6; }
        uint32_t tmp = ROL(a, 5) + f + e + k + w[i];
        e = d; d = c; c = ROL(b, 30); b = a; a = tmp;
    }
    state[0] += a; state[1] += b; state[2] += c; state[3] += d; state[4] += e;
}

static void sha1_init(SHA1_CTX *ctx) {
    ctx->state[0] = 0x67452301;
    ctx->state[1] = 0xEFCDAB89;
    ctx->state[2] = 0x98BADCFE;
    ctx->state[3] = 0x10325476;
    ctx->state[4] = 0xC3D2E1F0;
    ctx->count[0] = ctx->count[1] = 0;
}

static void sha1_update(SHA1_CTX *ctx, const unsigned char *data, size_t len) {
    size_t i, j;
    j = (ctx->count[0] >> 3) & 63;
    if ((ctx->count[0] += (uint32_t)(len << 3)) < (len << 3)) ctx->count[1]++;
    ctx->count[1] += (uint32_t)(len >> 29);
    if ((j + len) > 63) {
        memcpy(&ctx->buffer[j], data, (i = 64 - j));
        sha1_transform(ctx->state, ctx->buffer);
        for (; i + 63 < len; i += 64)
            sha1_transform(ctx->state, &data[i]);
        j = 0;
    } else {
        i = 0;
    }
    memcpy(&ctx->buffer[j], &data[i], len - i);
}

static void sha1_final(unsigned char digest[20], SHA1_CTX *ctx) {
    unsigned char finalcount[8];
    for (int i = 0; i < 8; i++) {
        finalcount[i] = (unsigned char)
            ((ctx->count[(i >= 4 ? 0 : 1)] >> ((3 - (i & 3)) * 8)) & 255);
    }
    unsigned char c = 0200;
    sha1_update(ctx, &c, 1);
    while ((ctx->count[0] & 504) != 448) {
        c = 0000;
        sha1_update(ctx, &c, 1);
    }
    sha1_update(ctx, finalcount, 8);
    for (int i = 0; i < 20; i++) {
        digest[i] = (unsigned char)
            ((ctx->state[i >> 2] >> ((3 - (i & 3)) * 8)) & 255);
    }
}

/* ================= HMAC-SHA1 ================= */
static void hmac_sha1(const unsigned char *key, size_t key_len,
                      const unsigned char *data, size_t data_len,
                      unsigned char out[20]) {
    unsigned char k[64] = {0};
    if (key_len > 64) {
        SHA1_CTX ctx;
        sha1_init(&ctx);
        sha1_update(&ctx, key, key_len);
        sha1_final(k, &ctx);
    } else {
        memcpy(k, key, key_len);
    }
    unsigned char ipad[64], opad[64];
    for (int i = 0; i < 64; i++) {
        ipad[i] = k[i] ^ 0x36;
        opad[i] = k[i] ^ 0x5c;
    }
    unsigned char inner[20];
    SHA1_CTX ctx;

    sha1_init(&ctx);
    sha1_update(&ctx, ipad, 64);
    sha1_update(&ctx, data, data_len);
    sha1_final(inner, &ctx);

    sha1_init(&ctx);
    sha1_update(&ctx, opad, 64);
    sha1_update(&ctx, inner, 20);
    sha1_final(out, &ctx);
}

/* ================= TOTP ================= */
static void compute_totp(const char *key, char *out, size_t out_size) {
    uint64_t t = (uint64_t)(time(NULL) / 30);
    unsigned char msg[8];
    for (int i = 7; i >= 0; i--) {
        msg[i] = (unsigned char)(t & 0xFF);
        t >>= 8;
    }
    unsigned char hmac[20];
    hmac_sha1((const unsigned char *)key, strlen(key), msg, 8, hmac);
    int off = hmac[19] & 0x0F;
    uint32_t code = ((hmac[off]   & 0x7F) << 24) |
                    ((hmac[off+1] & 0xFF) << 16) |
                    ((hmac[off+2] & 0xFF) << 8)  |
                    (hmac[off+3]  & 0xFF);
    code %= 1000000;
    snprintf(out, out_size, "%06u", code);
}

/* ================= 命令表 ================= */
struct cmd_help {
    const char *name;
    const char *desc;
    const char *usage;
    const char *example;
};

static struct cmd_help HELP_EN[] = {
    {"ping",              "Test connection",     "dcli ping",                       "dcli ping"},
    {"lock_now",          "Lock screen now",     "dcli lock_now",                   "dcli lock_now"},
    {"hide",              "Hide app",            "dcli hide <package>",             "dcli hide com.example.app"},
    {"unhide",            "Unhide app",          "dcli unhide <package>",           "dcli unhide com.example.app"},
    {"suspend",           "Suspend app",         "dcli suspend <package>",          "dcli suspend com.example.app"},
    {"resume",            "Resume app",          "dcli resume <package>",           "dcli resume com.example.app"},
    {"block_uninstall",   "Block uninstall",     "dcli block_uninstall <package>",  "dcli block_uninstall com.example.app"},
    {"unblock_uninstall", "Unblock uninstall",   "dcli unblock_uninstall <package>","dcli unblock_uninstall com.example.app"},
};

static struct cmd_help HELP_ZH[] = {
    {"ping",              "测试连接",         "dcli ping",                       "dcli ping"},
    {"lock_now",          "立即锁屏",         "dcli lock_now",                   "dcli lock_now"},
    {"hide",              "隐藏指定应用",     "dcli hide <包名>",                 "dcli hide com.example.app"},
    {"unhide",            "取消隐藏指定应用", "dcli unhide <包名>",               "dcli unhide com.example.app"},
    {"suspend",           "挂起指定应用",     "dcli suspend <包名>",              "dcli suspend com.example.app"},
    {"resume",            "恢复挂起指定应用", "dcli resume <包名>",               "dcli resume com.example.app"},
    {"block_uninstall",   "阻止卸载指定应用", "dcli block_uninstall <包名>",      "dcli block_uninstall com.example.app"},
    {"unblock_uninstall", "允许卸载指定应用", "dcli unblock_uninstall <包名>",    "dcli unblock_uninstall com.example.app"},
};

#define HELP_COUNT (sizeof(HELP_EN) / sizeof(HELP_EN[0]))

static struct cmd_help *help_table(void) {
    return g_zh ? HELP_ZH : HELP_EN;
}

static int find_command(const char *name) {
    struct cmd_help *h = help_table();
    for (size_t i = 0; i < HELP_COUNT; i++) {
        if (strcmp(h[i].name, name) == 0) return (int)i;
    }
    return -1;
}

static void print_global_help(void) {
    struct cmd_help *h = help_table();

    printf("dcli - %s\n\n", tr("DO Server CLI client", "DO Server 命令行客户端"));
    printf("%s\n", tr("Usage:", "用法:"));
    printf("  dcli [--host <ip>] [--package <pkg>] help [command]\n");
    printf("  dcli [--host <ip>] [--package <pkg>] <command> [args]\n\n");
    printf("%s\n", tr("Options:", "选项:"));
    printf("%s\n", tr("  --host, -H <ip>      Server IP (default 127.0.0.1)",
                      "  --host, -H <ip>      服务端 IP（默认 127.0.0.1）"));
    printf("%s\n", tr("  --package, -P <pkg>  Client package name (default com.termux)",
                      "  --package, -P <包名> 客户端包名（默认 com.termux）"));
    printf("%s\n", tr("  --                   Stop option parsing",
                      "  --                   停止解析后续选项"));
    printf("\n%s\n", tr("Available commands:", "可用命令:"));
    for (size_t i = 0; i < HELP_COUNT; i++) {
        printf("  %-18s %s\n", h[i].name, h[i].desc);
    }
    printf("\n%s\n", tr("Remote examples:", "远程连接示例:"));
    printf("  dcli --host 192.168.1.100 ping\n");
    printf("%s export DCLI_HOST=192.168.1.100\n",
           tr("  or env:", "  或环境变量:"));
    printf("%s echo 192.168.1.100 > ~/.dcli_host\n",
           tr("  or file:", "  或写入文件:"));
    printf("\n%s\n", tr("Examples:", "示例:"));
    printf("  dcli ping\n");
    printf("  dcli lock_now\n");
    printf("  dcli hide com.example.app\n");
}

static void print_command_help(const char *cmd) {
    struct cmd_help *h = help_table();
    int idx = find_command(cmd);
    if (idx >= 0) {
        printf("%s %s\n", tr("Usage: ", "用法:  "), h[idx].usage);
        printf("%s %s\n", tr("Desc:  ", "说明:  "), h[idx].desc);
        printf("%s %s\n", tr("Example:", "示例:  "), h[idx].example);
        return;
    }
    printf("%s %s\n", tr("Unknown command:", "未知命令:"), cmd);
    printf("%s\n", tr("Run 'dcli help' for all commands",
                      "用 'dcli help' 查看所有命令"));
    exit(1);
}

/* ================= 配置读取 ================= */
static char *read_field(const char *fname, const char *envname) {
    if (envname) {
        const char *env = getenv(envname);
        if (env && *env) return strdup(env);
    }

    const char *home = getenv("HOME");
    char path[512];
    FILE *fp = fopen(fname, "r");
    if (!fp && home) {
        snprintf(path, sizeof(path), "%s/%s", home, fname);
        fp = fopen(path, "r");
    }
    if (!fp) return NULL;

    char buf[512];
    if (!fgets(buf, sizeof(buf), fp)) {
        fclose(fp);
        return NULL;
    }
    fclose(fp);

    size_t len = strlen(buf);
    while (len > 0 && (buf[len-1] == '\n' || buf[len-1] == '\r'))
        buf[--len] = 0;
    if (len == 0) return NULL;

    return strdup(buf);
}

/* ================= 带 EINTR 重试的 send/recv ================= */
static ssize_t send_all(int fd, const char *buf, size_t len) {
    size_t sent = 0;
    while (sent < len) {
        ssize_t n = send(fd, buf + sent, len - sent, 0);
        if (n < 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        if (n == 0) break;
        sent += (size_t)n;
    }
    return (ssize_t)sent;
}

static ssize_t recv_some(int fd, char *buf, size_t len) {
    ssize_t n;
    do {
        n = recv(fd, buf, len, 0);
    } while (n < 0 && errno == EINTR);
    return n;
}

/* ================= TCP ================= */
static int send_command(const char *host, const char *pkg,
                        const char *cmd_line, const char *key,
                        char *reply, size_t reply_size) {
    char totp[8];
    compute_totp(key, totp, sizeof(totp));

    char payload[BUF_SIZE];
    snprintf(payload, sizeof(payload), "%s %s %s\n", pkg, totp, cmd_line);

    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        snprintf(reply, reply_size, "%s", tr("Error: socket failed",
                                              "错误: 创建 socket 失败"));
        return -1;
    }

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(DEFAULT_PORT);
    if (inet_pton(AF_INET, host, &addr.sin_addr) != 1) {
        snprintf(reply, reply_size, "%s %s", tr("Error: invalid IP",
                                                 "错误: 非法 IP"), host);
        close(sock);
        return -1;
    }

    if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        snprintf(reply, reply_size, "%s %s:%d",
                 tr("Error: cannot connect to", "错误: 无法连接"),
                 host, DEFAULT_PORT);
        close(sock);
        return -1;
    }

    if (send_all(sock, payload, strlen(payload)) < 0) {
        snprintf(reply, reply_size, "%s", tr("Error: send failed",
                                              "错误: 发送失败"));
        close(sock);
        return -1;
    }

    size_t total = 0;
    while (total < reply_size - 1) {
        ssize_t n = recv_some(sock, reply + total, reply_size - 1 - total);
        if (n <= 0) break;
        total += (size_t)n;
        if (reply[total - 1] == '\n') break;
    }
    reply[total] = 0;

    size_t len = strlen(reply);
    while (len > 0 && (reply[len-1] == '\n' || reply[len-1] == '\r'))
        reply[--len] = 0;

    close(sock);
    return 0;
}

/* ================= main ================= */
int main(int argc, char **argv) {
    signal(SIGPIPE, SIG_IGN);

    init_lang();

    char *host_arg = NULL;
    char *pkg_arg = NULL;
    char *positional[128];
    int pos_count = 0;
    int stop_parse = 0;

    for (int i = 1; i < argc; i++) {
        if (stop_parse) {
            if (pos_count >= 128) break;
            positional[pos_count++] = argv[i];
            continue;
        }

        if (strcmp(argv[i], "--") == 0) {
            stop_parse = 1;
            continue;
        }

        if (strcmp(argv[i], "--host") == 0 || strcmp(argv[i], "-H") == 0) {
            if (i + 1 >= argc) {
                fprintf(stderr, "%s %s\n",
                        tr("Error: missing argument after",
                           "错误: 后缺参数"), argv[i]);
                return 2;
            }
            host_arg = argv[i + 1];
            i++;
            continue;
        }

        if (strncmp(argv[i], "--host=", 7) == 0) {
            host_arg = argv[i] + 7;
            continue;
        }

        if (strcmp(argv[i], "--package") == 0 || strcmp(argv[i], "-P") == 0) {
            if (i + 1 >= argc) {
                fprintf(stderr, "%s %s\n",
                        tr("Error: missing argument after",
                           "错误: 后缺参数"), argv[i]);
                return 2;
            }
            pkg_arg = argv[i + 1];
            i++;
            continue;
        }

        if (strncmp(argv[i], "--package=", 10) == 0) {
            pkg_arg = argv[i] + 10;
            continue;
        }

        /* 允许 --help / -h 作为命令后缀 */
        if (strcmp(argv[i], "--help") == 0 || strcmp(argv[i], "-h") == 0) {
            if (pos_count >= 128) break;
            positional[pos_count++] = argv[i];
            continue;
        }

        /* 未知的以 - 开头的选项 → 报错 */
        if (argv[i][0] == '-' && argv[i][1] != '\0') {
            fprintf(stderr, "%s %s\n",
                    tr("Error: unknown option:",
                       "错误: 未知选项:"), argv[i]);
            return 2;
        }

        if (pos_count >= 128) break;
        positional[pos_count++] = argv[i];
    }

    if (pos_count == 0) {
        print_global_help();
        return 0;
    }

    /* dcli help / dcli --help / dcli help <cmd> */
    if (strcmp(positional[0], "help") == 0 ||
        strcmp(positional[0], "--help") == 0) {
        if (pos_count < 2) {
            print_global_help();
        } else {
            print_command_help(positional[1]);
        }
        return 0;
    }

    /* dcli <cmd> --help / dcli <cmd> -h */
    if (pos_count >= 2 &&
        (strcmp(positional[1], "--help") == 0 ||
         strcmp(positional[1], "-h") == 0)) {
        if (find_command(positional[0]) >= 0) {
            print_command_help(positional[0]);
            return 0;
        }
    }

    char cmd_line[BUF_SIZE] = {0};
    for (int i = 0; i < pos_count; i++) {
        if (i > 0) strncat(cmd_line, " ",
                           sizeof(cmd_line) - strlen(cmd_line) - 1);
        strncat(cmd_line, positional[i],
                sizeof(cmd_line) - strlen(cmd_line) - 1);
    }

    char *key = read_field(KEY_FILE, "DCLI_KEY");
    if (!key) {
        fprintf(stderr, "%s\n", tr("Error: no key found",
                                    "错误: 未找到密钥"));
        fprintf(stderr, "%s\n", tr("Copy the key from the App to ~/.dcli_key",
                                    "请从 App 复制密钥，写入 ~/.dcli_key"));
        fprintf(stderr, "%s\n", tr("or set env DCLI_KEY",
                                    "或设置环境变量 DCLI_KEY"));
        return 1;
    }

    char *host;
    if (host_arg) {
        host = strdup(host_arg);
    } else {
        host = read_field(HOST_FILE, "DCLI_HOST");
        if (!host) host = strdup(DEFAULT_HOST);
    }

    char *pkg;
    if (pkg_arg) {
        pkg = strdup(pkg_arg);
    } else {
        pkg = read_field(PKG_FILE, "DCLI_PACKAGE");
        if (!pkg) pkg = strdup(DEFAULT_PACKAGE);
    }

    char reply[BUF_SIZE];
    if (send_command(host, pkg, cmd_line, key,
                     reply, sizeof(reply)) < 0) {
        fprintf(stderr, "%s\n", reply);
        free(key);
        free(host);
        free(pkg);
        return 1;
    }

    free(key);
    free(host);
    free(pkg);

    if (strcmp(reply, "Success") == 0) {
        printf("Success\n");
        return 0;
    }
    if (strncmp(reply, "Success ", 8) == 0) {
        printf("%s\n", reply + 8);
        return 0;
    }

    printf("%s\n", reply);
    return 1;
}
