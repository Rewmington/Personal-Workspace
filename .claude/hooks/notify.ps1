#Requires -Version 5.1
param(
    [Parameter(Mandatory = $true)][string]$Title,
    [string]$Message = "",
    [ValidateSet("info", "success", "warning", "confirm")][string]$Type = "info"
)

$tempDir = "$env:TEMP\claude-hooks"
if (-not (Test-Path $tempDir)) { New-Item -ItemType Directory -Path $tempDir -Force | Out-Null }

$exe = "$tempDir\cc-notify.exe"
$csFile = "$tempDir\notify.cs"
$logFile = "$tempDir\notify.log"

$timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss.fff"
"[$timestamp] PID=$PID Type=$Type Title=$Title Message=$Message" | Add-Content -Path $logFile -Encoding UTF8

$cs = @'
using System;
using System.Drawing;
using System.Windows.Forms;

class Notifier {
    [STAThread]
    static void Main(string[] args) {
        string title = args[0];
        string message = args.Length > 1 ? args[1] : "";
        string type = args.Length > 2 ? args[2] : "info";

        ToolTipIcon icon;
        switch (type) {
            case "warning":
            case "confirm":
                icon = ToolTipIcon.Warning;
                break;
            default:
                icon = ToolTipIcon.Info;
                break;
        }

        var notify = new NotifyIcon();
        notify.Icon = SystemIcons.Information;
        notify.Visible = true;

        var timer = new System.Windows.Forms.Timer();
        timer.Interval = 9000;
        timer.Tick += (s, e) => {
            timer.Stop();
            notify.Visible = false;
            notify.Dispose();
            Application.ExitThread();
        };

        notify.BalloonTipShown += (s, e) => { timer.Start(); };
        notify.ShowBalloonTip(8000, title, message, icon);

        Application.Run();
    }
}
'@

$cs | Set-Content -Path $csFile -Encoding UTF8

Add-Type -TypeDefinition $cs `
    -ReferencedAssemblies "System.Windows.Forms", "System.Drawing" `
    -OutputAssembly $exe `
    -OutputType ConsoleApplication `
    -ErrorAction Stop

& $exe $Title $Message $Type
