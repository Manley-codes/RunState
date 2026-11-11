[CmdletBinding()]
param(
    [switch] $Live,
    [string] $Maven = 'mvn'
)

# Test-source launcher. The default run uses fake providers, even when credentials are present.
$sampleMode = if ($Live) { '--live' } else { '--offline' }
$previousMavenOptions = $env:MAVEN_OPTS
Push-Location -LiteralPath $PSScriptRoot
try {
    $env:MAVEN_OPTS = "$previousMavenOptions -Djdk.httpclient.redirects.retrylimit=1".Trim()
    & $Maven -B --no-transfer-progress -o test-compile 'org.codehaus.mojo:exec-maven-plugin:3.5.0:java' `
        '-Dexec.classpathScope=test' '-Dexec.cleanupDaemonThreads=false' '-Dexec.mainClass=com.runstate.reflection.SampleRunCostComparison' `
        "-Dexec.args=$sampleMode"
    if ($LASTEXITCODE -ne 0) { throw "Sample comparison failed (Maven exit $LASTEXITCODE)." }
} finally {
    $env:MAVEN_OPTS = $previousMavenOptions
    Pop-Location
}
