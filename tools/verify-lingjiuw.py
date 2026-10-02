"""把重新生成的静态站与手写原站做**可见文案**逐字比对。

用法：cd backend; python tools/verify-lingjiuw.py
输出：target/verify-lingjiuw.txt（UTF-8），stdout 同步打印

口径：
- 只比"页面上看得见的文字"：去掉 <script>/<style>/HTML 注释之后再剥标签；
- 空白折叠成单个空格（原文与模板的缩进必然不同，缩进不是内容）；
- 基准取 `../frontend`（官网源码子模块，与改造前 sites/lingjiuw.cn/www 逐字节相同）。

退出码：0 = 全部页面逐字一致；1 = 有差异（差异逐条列出）。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ORIGIN = ROOT.parent / "frontend"
GENERATED = ROOT / "sites" / "lingjiuw.cn" / "www"

PAGES = [
    ("首页", "/index.html"),
    ("微信小程序落地页", "/wechat-miniprogram/index.html"),
    ("隐私政策", "/privacy/index.html"),
    ("404", "/404.html"),
    ("50x", "/50x.html"),
]

SCRIPT = re.compile(r"<script\b.*?</script>", re.S | re.I)
STYLE = re.compile(r"<style\b.*?</style>", re.S | re.I)
COMMENT = re.compile(r"<!--.*?-->", re.S)
TAG = re.compile(r"<[^>]*>", re.S)
WS = re.compile(r"\s+")


def visible_text(html: str) -> str:
    html = SCRIPT.sub(" ", html)
    html = STYLE.sub(" ", html)
    html = COMMENT.sub(" ", html)
    text = TAG.sub(" ", html)
    text = text.replace("&nbsp;", " ")
    for entity, char in (("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"),
                         ("&quot;", '"'), ("&#39;", "'")):
        text = text.replace(entity, char)
    return WS.sub(" ", text).strip()


def tokens(text: str) -> list:
    """按标点与空白切成"文案片段"，用来定位缺了哪一句。"""
    return [part for part in re.split(r"(?<=[。！？；：，、])|\s+", text) if part.strip()]


def main() -> int:
    out = []
    failures = 0
    for label, rel in PAGES:
        source = ORIGIN / rel.lstrip("/")
        target = GENERATED / rel.lstrip("/")
        if not source.exists():
            out.append(f"## {label} ({rel})\n原文件不存在：{source}\n")
            continue
        old = visible_text(source.read_text(encoding="utf-8"))
        new = visible_text(target.read_text(encoding="utf-8")) if target.exists() else ""
        same = old == new
        out.append(f"## {label} ({rel})")
        out.append(f"- 原文可见文字 {len(old)} 字符 / 生成 {len(new)} 字符："
                   f"{'完全一致' if same else '有差异'}")
        if not same:
            failures += 1
            old_tokens = tokens(old)
            new_tokens = tokens(new)
            new_set = set(new_tokens)
            missing = [t for t in old_tokens if t not in new_set]
            old_set = set(old_tokens)
            extra = [t for t in new_tokens if t not in old_set]
            if missing:
                out.append(f"- **原文有、生成里没有**（{len(missing)} 段）：")
                for token in missing[:40]:
                    out.append(f"    - {token}")
            if extra:
                out.append(f"- **生成里有、原文没有**（{len(extra)} 段）：")
                for token in extra[:40]:
                    out.append(f"    - {token}")
        out.append("")

    text = "\n".join(out) + "\n"
    target = ROOT / "target" / "verify-lingjiuw.txt"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")
    print(text)
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
