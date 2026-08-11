# 将 xuri-rpc 发布（install）到本地 Maven 仓库 (~/.m2/repository)
# 用法: 在项目根目录执行  .\publish-local.ps1
# 可选: .\publish-local.ps1 -SkipTests  跳过测试快速发布

param(
    [switch]$SkipTests
)

$pomPath = Join-Path $PSScriptRoot "pom.xml"

if ($SkipTests) {
    mvn clean install -DskipTests -f $pomPath
} else {
    mvn clean install -f $pomPath
}

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n已发布到本地 Maven 仓库: $env:USERPROFILE\.m2\repository\com\xuri\xuri-rpc\" -ForegroundColor Green
} else {
    Write-Host "`n发布失败，请检查上方 Maven 输出。" -ForegroundColor Red
    exit $LASTEXITCODE
}
