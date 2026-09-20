#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <time.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define DEFAULT_HOST "127.0.0.1"
#define DEFAULT_PORT 12345
#define KEY_FILE ".dcli_key"
#define HOST_FILE ".dcli_host"
#define BUF_SIZE 4096

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

static struct cmd_help HELP[] = {
    {"ping",     "测试连接是否正常",     "dcli ping",              "dcli ping"},
    {"lock_now", "立即锁屏",             "dcli lock_now",          "dcli lock_now"},
    {"hide",     "隐藏指定应用",         "dcli hide <package>",    "dcli hide com.example.app"},
    {"unhide",   "取消隐藏指定应用",     "dcli unhide <package>",  "dcli unhide com.example.app"},
};

#define HELP_COUNT (sizeof(HELP) / sizeof(HELP[0]))

static void print_global_help(void) {
    printf("dcli - DO Server 命令行客户端\n\n");
    printf("用法:\n");
    printf("  dcli [--host <ip>] help [command]\n");
    printf("  dcli [--host <ip>] <command> [args]\n\n");
    printf("选项:\n");
    printf("  --host, -H <ip>   指定服务端 IP（默认 127.0.0.1）\n\n");
    printf("可用命令:\n");
    for (size_t i = 0; i < HELP_COUNT; i++)
        printf("  %-12s %s\n", HELP[i].name, HELP[i].desc);
    printf("\n远程连接示例:\n");
    printf("  dcli --host 192.168.1.100 ping\n");
    printf("  或设置环境变量: export DCLI_HOST=192.168.1.100\n");
    printf("  或写入文件:    echo 192.168.1.100 > ~/.dcli_host\n");
    printf("\n示例:\n");
    printf("  dcli ping\n");
    printf("  dcli lock_now\n");
    printf("  dcli hide com.example.app\n");
    printf("  dcli help hide\n");
}

static void print_command_help(const char *cmd) {
    for (size_t i = 0; i < HELP_COUNT; i++) {
        if (strcmp(HELP[i].name, cmd) == 0) {
            printf("用法:  %s\n", HELP[i].usage);
            printf("说明:  %s\n", HELP[i].desc);
            printf("示例:  %s\n", HELP[i].example);
            return;
        }
    }
    printf("未知命令: %s\n", cmd);
    printf("用 'dcli help' 查看所有命令\n");
    exit(1);
}

/* ================= 配置文件读取 ================= */
static char *read_file_field(const char *fname, const char *envname) {
    /* 1. 环境变量 */
    if (envname) {
        const char *env = getenv(envname);
        if (env && *env) return strdup(env);
    }

    /* 2. 当前目录 → HOME */
    const char *home = getenv("HOME");
    char path[512];
    FILE *fp = fopen(fname, "r");
    if (!fp && home) {
        snprintf(path, sizeof(path), "%s/%s", home, fname);
        fp = fopen(path, "r");
    }
    if (!fp) return NULL;

    char buf[256];
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

/* ================= TCP ================= */
static int send_command(const char *host, int port,
                        const char *cmd_line, const char *key,
                        char *reply, size_t reply_size) {
    char totp[8];
    compute_totp(key, totp, sizeof(totp));

    char payload[BUF_SIZE];
    snprintf(payload, sizeof(payload), "%s %s\n", totp, cmd_line);

    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        snprintf(reply, reply_size, "错误: 创建 socket 失败");
        return -1;
    }

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    if (inet_pton(AF_INET, host, &addr.sin_addr) != 1) {
        snprintf(reply, reply_size, "错误: 非法 IP 地址 %s", host);
        close(sock);
        return -1;
    }

    if (connect(sock, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        snprintf(reply, reply_size, "错误: 无法连接 %s:%d", host, port);
        close(sock);
        return -1;
    }

    if (send(sock, payload, strlen(payload), 0) < 0) {
        snprintf(reply, reply_size, "错误: 发送失败");
        close(sock);
        return -1;
    }

    size_t total = 0;
    while (total < reply_size - 1) {
        ssize_t n = recv(sock, reply + total, reply_size - 1 - total, 0);
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
    /* 解析 --host / -H 和剩下的参数 */
    char *host_arg = NULL;
    char *positional[128];
    int pos_count = 0;

    for (int i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--host") == 0 || strcmp(argv[i], "-H") == 0) {
            if (i + 1 >= argc) {
                fprintf(stderr, "错误: %s 后缺参数\n", argv[i]);
                return 1;
            }
            host_arg = argv[i + 1];
            i++;
        } else if (strncmp(argv[i], "--host=", 7) == 0) {
            host_arg = argv[i] + 7;
        } else {
            if (pos_count >= 128) break;
            positional[pos_count++] = argv[i];
        }
    }

    /* 无参数 → 全局帮助 */
    if (pos_count == 0) {
        print_global_help();
        return 0;
    }

    /* help / --help → 本地处理 */
    if (strcmp(positional[0], "help") == 0 ||
        strcmp(positional[0], "--help") == 0) {
        if (pos_count < 2) {
            print_global_help();
        } else {
            print_command_help(positional[1]);
        }
        return 0;
    }

    /* 拼接命令 */
    char cmd_line[BUF_SIZE] = {0};
    for (int i = 0; i < pos_count; i++) {
        if (i > 0) strncat(cmd_line, " ",
                  sizeof(cmd_line) - strlen(cmd_line) - 1);
        strncat(cmd_line, positional[i],
                sizeof(cmd_line) - strlen(cmd_line) - 1);
    }

    /* 读密钥 */
    char *key = read_file_field(KEY_FILE, "DCLI_KEY");
    if (!key) {
        fprintf(stderr, "错误: 未找到密钥\n");
        fprintf(stderr, "请从 App 复制密钥，写入 ~/.dcli_key\n");
        fprintf(stderr, "或设置环境变量 DCLI_KEY\n");
        return 1;
    }

    /* 解析主机 */
    char *host = NULL;
    if (host_arg) {
        host = strdup(host_arg);
    } else {
        host = read_file_field(HOST_FILE, "DCLI_HOST");
        if (!host) host = strdup(DEFAULT_HOST);
    }

    /* 发送 */
    char reply[BUF_SIZE];
    if (send_command(host, DEFAULT_PORT, cmd_line, key,
                     reply, sizeof(reply)) < 0) {
        fprintf(stderr, "%s\n", reply);
        free(key);
        free(host);
        return 1;
    }

    free(key);
    free(host);

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
