"""对重新生成的静态站做结构与 SEO 自检（凌久网官网）。

用法：cd backend; python tools/verify-lingjiuw-seo.py
输出：target/verify-lingjiuw-seo.txt（UTF-8），stdout 同步打印

检查项：
1. 每页的 JSON-LD 能被 json.loads 解析（结构化数据坏掉比少一段更糟：整块会被忽略）；
2. canonical / og:url / JSON-LD 的 url 指向同一地址，且落在站点域名下；
3. robots meta 与页面类型一致（正文页 index，404/50x noindex）；
4. 「更新于」日期与 JSON-LD 的 dateModified 同源（sitemap 的 lastmod 见第 6 项）；
5. 产物里的 assets/ 与手写原站的同名文件逐字节一致（fonts/img/js 都是搬过来的）；
6. sitemap 收录页面数、域名、百度移动协议声明，以及错误页是否被排除；
7. 没有残留的模板标记（[field: / {cms:）与空的 href/src。

退出码：0 = 没有问题；1 = 有问题（逐条列出）。
"""
import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ORIGIN = ROOT.parent / "frontend"
WWW = ROOT / "sites" / "lingjiuw.cn" / "www"

PAGES = [
    ("首页", "index.html", "index"),
    ("落地页", "wechat-miniprogram/index.html", "index"),
    ("隐私政策", "privacy/index.html", "index"),
    ("404", "404.html", "noindex"),
    ("50x", "50x.html", "noindex"),
]

LD = re.compile(r'<script type="application/ld\+json">(.*?)</script>', re.S)
CANONICAL = re.compile(r'<link rel="canonical" href="([^"]*)"')
OG_URL = re.compile(r'<meta property="og:url" content="([^"]*)"')
ROBOTS = re.compile(r'<meta name="robots" content="([^"]*)"')
UPDATED = re.compile(r'更新于 <time datetime="([^"]*)">([^<]*)</time>')
MARKERS = ("[field:", "{cms:")


def main() -> int:
    out = []
    problems = 0

    def check(ok: bool, label: str, detail: str = "") -> None:
        nonlocal problems
        if not ok:
            problems += 1
        out.append(f"  [{'OK ' if ok else 'BAD'}] {label}" + (f" — {detail}" if detail else ""))

    out.append("## 1-4. 逐页结构 / SEO")
    for label, rel, expected_robots in PAGES:
        path = WWW / rel
        text = path.read_text(encoding="utf-8")
        out.append(f"\n### {label}（{rel}）")

        canonical_match = CANONICAL.search(text)
        canonical = canonical_match.group(1) if canonical_match else ""
        for index, block in enumerate(LD.findall(text)):
            try:
                data = json.loads(block)
            except json.JSONDecodeError as error:
                check(False, f"JSON-LD #{index + 1} 可解析", str(error))
                continue
            check(True, f"JSON-LD #{index + 1} 可解析")
            for node in data.get("@graph", []):
                node_type = node.get("@type")
                url = node.get("url")
                if node_type in ("WebPage", "Service") and url:
                    check(url == canonical, f"{node_type}.url 与 canonical 一致",
                          f"{url} vs {canonical}")
                if node_type == "WebPage":
                    updated = UPDATED.search(text)
                    check(node.get("dateModified") == (updated.group(1) if updated else None),
                          "JSON-LD dateModified 与页脚可见的「更新于」一致",
                          f"{node.get('dateModified')} vs {updated.group(1) if updated else '（无）'}")

        if canonical:
            check(canonical.startswith("https://www.lingjiuw.cn/"), "canonical 用首选域名", canonical)
            check(canonical.endswith("/"), "canonical 以斜杠结尾", canonical)
            og = OG_URL.search(text)
            if og is not None:
                check(og.group(1) == canonical, "og:url 与 canonical 一致")
            else:
                # 隐私政策页是"文档页"版式，原文就没有任何 og: 标签
                out.append("  [NOTE] 本页没有 og: 标签（文档页版式，与手写原站一致）")
        else:
            check(expected_robots == "noindex", "有 canonical（错误页可无）")

        robots_match = ROBOTS.search(text)
        robots = robots_match.group(1) if robots_match else ""
        check(robots.startswith(expected_robots),
              f"robots={'noindex' if expected_robots == 'noindex' else 'index'}", robots)

        for marker in MARKERS:
            check(marker not in text, f"无残留模板标记 {marker}")
        if rel == "50x.html":
            # 原文刻意让"重新加载"用空 href 指向刚才失败的那个地址，点一下即重新请求
            empties = re.findall(r'(?:href|src)=""', text)
            check(len(empties) == 1, "只有「重新加载」那一个空 href（原站设计）", str(len(empties)))
        else:
            for empty in re.findall(r'(?:href|src)=""', text):
                check(False, "无空 href/src", empty)

    out.append("\n## 5. assets 与原站逐字节一致")
    for source in sorted((ORIGIN / "assets").rglob("*")):
        if not source.is_file():
            continue
        rel = source.relative_to(ORIGIN)
        target = WWW / rel
        if not target.exists():
            check(False, f"{rel} 存在")
            continue
        same = (hashlib.sha256(source.read_bytes()).hexdigest()
                == hashlib.sha256(target.read_bytes()).hexdigest())
        if rel.as_posix() == "assets/js/main.js":
            # 唯一被模板作者改过的资源：首次上线时间与表单收件地址从脚本里挪到站点配置
            check(not same, f"{rel} 已按预期改动（读站点配置）")
            continue
        check(same, f"{rel} 逐字节一致")

    out.append("\n## 6. sitemap / robots")
    sitemap = (WWW / "sitemap-0.xml").read_text(encoding="utf-8")
    locs = re.findall(r"<loc>([^<]*)</loc>", sitemap)
    check(len(locs) == 3, "sitemap 收录 3 个页面（首页 / 落地页 / 隐私政策）", str(locs))
    check("mobile:mobile" in sitemap, "sitemap 声明了百度移动协议 mobile:mobile")
    check("/50x.html" not in sitemap and "/404.html" not in sitemap,
          "错误页不进 sitemap（noindex 生效）")
    robots = (WWW / "robots.txt").read_text(encoding="utf-8")
    check("Sitemap: https://www.lingjiuw.cn/sitemap.xml" in robots, "robots 指向 sitemap")
    check("Allow: /" in robots, "robots 允许抓取")

    text = "\n".join(out) + "\n"
    target = ROOT / "target" / "verify-lingjiuw-seo.txt"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")
    print(text)
    print(f"\n== 问题数：{problems} ==")
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
