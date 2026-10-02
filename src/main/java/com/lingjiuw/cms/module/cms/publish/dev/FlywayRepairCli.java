package com.lingjiuw.cms.module.cms.publish.dev;

import org.flywaydb.core.Flyway;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 开发用的 Flyway 修复入口：把 {@code flyway_schema_history} 里的校验和**重新对齐到磁盘上的文件**。
 *
 * <p><b>为什么它存在</b>：演示站点的种子迁移在本机数据库里先跑过一次，之后文件又被改了几处
 * （这是协作期最常见的一种状态）。Flyway 的校验和是按字节算的，于是下一次启动直接拒绝：
 * <pre>
 * Migration checksum mismatch for migration version 20261003090001
 *   -&gt; Applied to database : -1332127154
 *   -&gt; Resolved locally    : -366972022
 * </pre>
 * 应用这时候**起不来**（Flyway 挂在 Spring 的启动链上），所以修它需要一个不依赖 Spring 的入口。
 * {@code flyway.repair()} 正是为这件事设计的官方动作：它只改历史表里的校验和，**不重跑、不删表、
 * 不动数据**。
 *
 * <p><b>它不是什么</b>：不是"忽略迁移失败"的开关。{@code backend/AGENTS.md} 的规则（已执行过的
 * 迁移保持字节不变）依然成立；这个入口只用于"本机开发库里已经跑过、之后又合法地改过"的文件。
 * 生产环境不该用它——那里应当新增一个迁移文件来表达改动。
 *
 * <p>用法：{@code FlywayRepairCli <jdbcUrl> <user> <password> [--force]}。
 *
 * <p><b>两个防呆</b>（这是会改写历史表的动作，宁可多问一句）：
 * <ul>
 *   <li>连接参数**必填**：以前留有"本机默认库 + 默认口令"的兜底，参数拼错时会静默连上默认库
 *       并执行 repair，源码里也因此存了一份明文口令；</li>
 *   <li>默认只允许**本机**数据库：连非本机地址（很可能是生产）必须显式加 {@code --force}。</li>
 * </ul>
 */
public final class FlywayRepairCli {

    /** 允许直接 repair 的 host（本机）；其余一律要 {@code --force}。 */
    private static final List<String> LOCAL_HOSTS = List.of("localhost", "127.0.0.1", "::1");

    private static final String USAGE =
            "用法: FlywayRepairCli <jdbcUrl> <user> <password> [--force]\n"
                    + "  --force：允许对非本机数据库执行 repair（生产环境不该用它）";

    private FlywayRepairCli() {
    }

    public static void main(String[] args) {
        List<String> positional = new ArrayList<>();
        boolean force = false;
        for (String arg : args) {
            if ("--force".equals(arg)) {
                force = true;
            } else {
                positional.add(arg);
            }
        }
        if (positional.size() < 3) {
            System.err.println(USAGE);
            System.exit(2);
        }
        String url = positional.get(0);
        String user = positional.get(1);
        String password = positional.get(2);
        if (!force && !isLocal(url)) {
            System.err.println("拒绝执行：" + url + " 不是本机数据库。repair() 会改写 flyway_schema_history，"
                    + "掩盖迁移篡改；生产库请改为新增一个迁移文件。确实要在远端开发库上修复时加 --force。");
            System.exit(2);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .load();

        try {
            var info = flyway.info();
            System.out.println("迁移总数：" + info.all().length);
            for (var migration : info.all()) {
                String state = migration.getState() == null ? "?" : migration.getState().getDisplayName();
                String checksum = migration.getChecksum() == null ? "-" : String.valueOf(migration.getChecksum());
                System.out.println("  " + migration.getVersion() + "  " + state + "  checksum=" + checksum
                        + "  " + migration.getDescription());
            }
            var result = flyway.repair();
            System.out.println("修复动作 " + result.repairActions.size() + " 条：");
            for (String action : result.repairActions) {
                System.out.println("  " + action);
            }
            // 修复后立刻验证一次：validate 通过才说明应用能正常启动
            flyway.validate();
            System.out.println("validate 通过，应用可以正常启动");
        } catch (Exception e) {
            // 原来只抛原始堆栈，且"修复动作 N 条"已经打印过——使用者会误以为已经成功。
            // 这里给出明确的失败结论与非零退出码。
            System.err.println("Flyway 修复失败（上面打印的修复动作未通过 validate，应用仍可能起不来）: "
                    + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** jdbcUrl 是否指向本机：取 {@code //} 之后、第一个 {@code /} 或 {@code :} 之前的 host 段。 */
    private static boolean isLocal(String jdbcUrl) {
        String text = jdbcUrl == null ? "" : jdbcUrl.toLowerCase(Locale.ROOT);
        int start = text.indexOf("//");
        if (start < 0) {
            // 没有 host 段的 URL（内存库之类）不在本判定的范围内
            return true;
        }
        String rest = text.substring(start + 2);
        int end = rest.length();
        for (char stop : new char[]{'/', ':'}) {
            int index = rest.indexOf(stop);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        String host = rest.substring(0, end);
        return host.isEmpty() || LOCAL_HOSTS.contains(host);
    }
}
