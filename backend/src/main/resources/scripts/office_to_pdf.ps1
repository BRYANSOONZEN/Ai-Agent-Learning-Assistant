# 把 PPT / Word 转成 PDF（本机装了 Microsoft Office 时用）：OfficeToPdf.java 调用，参数是源文件和输出 PDF 的完整路径。
# 32 = PowerPoint 的 ppSaveAsPDF，17 = Word 的 wdFormatPDF。
param([string]$src, [string]$dst)
$ErrorActionPreference = 'Stop'
$ext = [System.IO.Path]::GetExtension($src).ToLower()
if ($ext -eq '.ppt' -or $ext -eq '.pptx') {
  $app = New-Object -ComObject PowerPoint.Application
  try {
    $p = $app.Presentations.Open($src, $true, $false, $false)
    $p.SaveAs($dst, 32)
    $p.Close()
  } finally { $app.Quit() }
} else {
  $app = New-Object -ComObject Word.Application
  $app.Visible = $false
  try {
    $d = $app.Documents.Open($src, $false, $true)
    $d.SaveAs2($dst, 17)
    $d.Close($false)
  } finally { $app.Quit() }
}
