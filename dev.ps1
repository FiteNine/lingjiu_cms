#Requires -Version 5.1
<#
.SYNOPSIS
    凌久 CMS 一键：清理缓存 -> 编译（admin-ui + 后端）-> 运行。

.DESCRIPTION
    依次做四件事：

    1. 释放端口。上一次 mvn spring-boot:run 被 Ctrl+C 或被杀掉后，它拉起的子进程
       java 常常残留并继续占用 8081，下一次启动就会报
       "Web server failed to start. Port 8081 was already in use." 并以退出码 1 结束
       （Maven 只打印 "Process terminated with exit code: 1"，看不到真正原因）。
    2. 清理缓存：target/、前端构建产物、Vite 依赖预构建缓存与 esbuild 临时目录。
    3. 编译 admin-ui（产物输出到 src/main/resources/static），随后 Maven 编译后端并
       把这些静态资源一起打进 target/classes。
    4. 前台运行 mvn spring-boot:run，日志直接打在终端，Ctrl+C 停止。

    端口同时写在两处，改的时候要一起改：src/main/resources/application.yml 的
    server.port，以及 admin-ui/vite.config.ts 的 proxy target。

.EXAMPLE
    在 backend 目录下双击 dev.cmd，或在终端执行 .\dev.cmd
    （本机策略会拒绝运行未签名的 .ps1，所以统一从 dev.cmd 进）
#>

$Port = 8081  # 与 src/main/resources/application.yml 的 server.port 一致
$Root = $PSScriptRoot
$UiDir = Join-Path $Root 'admin-ui'
$StaticDir = Join-Path $Root 'src\main\resources\static'

function Write-Step([string]$Text) {
    Write-Host ''
    Write-Host "==> $Text" -ForegroundColor Cyan
}

# 取监听指定端口的进程；端口空闲时返回空数组
function Get-PortProcess([int]$Port) {
    @(
        Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique |
            ForEach-Object { Get-Process -Id $_ -ErrorAction SilentlyContinue }
    )
}

function Clear-Port([int]$Port) {
    for ($i = 1; $i -le 20; $i++) {
        $procs = Get-PortProcess -Port $Port
        if ($procs.Count -eq 0) {
            Write-Host "    端口 $Port 空闲"
            return
        }
        foreach ($p in $procs) {
            # 只结束 Java 进程（mvn 与 spring-boot:run 拉起的都是 java.exe），
            # 端口上是别的服务时直接停下，交给人工判断，避免误杀。
            if ($p.ProcessName -notmatch '^java(w)?$') {
                throw "端口 $Port 被非 Java 进程 $($p.ProcessName) (PID $($p.Id)) 占用，请先自行处理后再运行。"
            }
            Write-Host "    结束残留进程 $($p.ProcessName) (PID $($p.Id))"
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        }
        # 反复确认而不是睡固定时长：个别情况下 java 进程被结束后端口仍被占住，
        # 直接往下走就会撞上 "Port 8081 was already in use"。
        Start-Sleep -Milliseconds 500
    }
    throw "端口 $Port 在 10 秒内始终未能释放，请手动检查占用进程。"
}

function Remove-Cache([string]$Path) {
    if (Test-Path -LiteralPath $Path) {
        Remove-Item -LiteralPath $Path -Recurse -Force
        Write-Host "    已删除 $Path"
    }
}

Push-Location $Root
try {
    Write-Step "释放端口 $Port"
    Clear-Port -Port $Port

    Write-Step '清理缓存'
    Remove-Cache (Join-Path $Root 'target')
    Remove-Cache $StaticDir
    Remove-Cache (Join-Path $UiDir 'node_modules\.vite')
    Remove-Cache (Join-Path $UiDir 'node_modules\.tmp')

    Write-Step '编译 admin-ui（产物 -> src/main/resources/static）'
    Push-Location $UiDir
    try {
        if (-not (Test-Path -LiteralPath (Join-Path $UiDir 'node_modules'))) {
            Write-Host '    未安装依赖，先执行 npm install'
            npm install
            if ($LASTEXITCODE -ne 0) { throw "npm install 失败（exit $LASTEXITCODE）" }
        }
        npm run build
        if ($LASTEXITCODE -ne 0) { throw "admin-ui 构建失败（exit $LASTEXITCODE）" }
    }
    finally {
        Pop-Location
    }

    # 编译前端要十几秒，这段时间里端口可能被别的残留进程抢走，所以启动前再确认一次。
    Clear-Port -Port $Port

    Write-Step '运行后端（mvn spring-boot:run，Ctrl+C 停止）'
    mvn spring-boot:run
}
finally {
    Pop-Location
    # 正常停止时端口已释放，这里是兜底：mvn 被中断时子进程 java 可能残留。
    try { Clear-Port -Port $Port } catch { }
}
