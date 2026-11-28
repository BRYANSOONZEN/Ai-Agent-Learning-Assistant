# 用本机 Word 把 md2docx.py 生成的 HTML 存成 .docx：图片嵌进文档、[[TOC]] 换成自动目录、A4 纸、页码
param([string]$src, [string]$dst)
$ErrorActionPreference = 'Stop'
$word = New-Object -ComObject Word.Application
$word.Visible = $false
try {
  $doc = $word.Documents.Open($src, $false, $false)
  $doc.ActiveWindow.View.Type = 3   # 页面视图
  # 图片：从链接改成嵌入
  foreach ($s in @($doc.InlineShapes)) {
    if ($s.LinkFormat -ne $null) {
      $s.LinkFormat.SavePictureWithDocument = $true
      $s.LinkFormat.BreakLink()
    }
  }
  # 页面：A4，上下 2.54 厘米、左右 3.17 厘米
  $ps = $doc.PageSetup
  $ps.PaperSize = 7
  $ps.TopMargin = 72; $ps.BottomMargin = 72; $ps.LeftMargin = 90; $ps.RightMargin = 90
  # 目录
  $r = $doc.Content
  $f = $r.Find
  if ($f.Execute('[[TOC]]')) {
    $r.Text = ''
    $toc = $doc.TablesOfContents.Add($r, $true, 2, 3)
    $toc.Update()
  }
  # 页脚居中页码
  $footer = $doc.Sections.Item(1).Footers.Item(1)
  $footer.PageNumbers.Add(1, $true) | Out-Null
  if (Test-Path $dst) { Remove-Item $dst -Force }
  $doc.SaveAs2($dst, 16)
  $doc.Close($false)
} finally {
  $word.Quit()
}
