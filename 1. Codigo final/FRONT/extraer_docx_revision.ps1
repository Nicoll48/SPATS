$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$entrada = (Resolve-Path 'Documento\20223270_NicollTone_EderQuispe_E1 (12).docx').Path
$salida = Join-Path (Get-Location) 'Entregables\revision_documento'
New-Item -ItemType Directory -Force -Path $salida | Out-Null
$zip = [System.IO.Compression.ZipFile]::OpenRead($entrada)
try {
  function Leer-Entrada([string]$nombre) {
    $entry = $zip.GetEntry($nombre)
    if (-not $entry) { return $null }
    $reader = [System.IO.StreamReader]::new($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
  }
  [xml]$doc = Leer-Entrada 'word/document.xml'
  $ns = [System.Xml.XmlNamespaceManager]::new($doc.NameTable)
  $ns.AddNamespace('w', 'http://schemas.openxmlformats.org/wordprocessingml/2006/main')
  $lineas = [System.Collections.Generic.List[string]]::new()
  $estructura = [System.Collections.Generic.List[object]]::new()
  $numeroTabla = 0
  foreach ($nodo in $doc.SelectNodes('//w:body/*', $ns)) {
    if ($nodo.LocalName -eq 'p') {
      $texto = (($nodo.SelectNodes('.//w:t', $ns) | ForEach-Object { $_.InnerText }) -join '').Trim()
      $estiloNodo = $nodo.SelectSingleNode('./w:pPr/w:pStyle', $ns)
      $estilo = if ($estiloNodo) { $estiloNodo.GetAttribute('val', $ns.LookupNamespace('w')) } else { '' }
      if ($texto) {
        $lineas.Add($texto)
        $estructura.Add([pscustomobject]@{ tipo='parrafo'; estilo=$estilo; texto=$texto })
      }
    } elseif ($nodo.LocalName -eq 'tbl') {
      $numeroTabla++
      $filas = [System.Collections.Generic.List[object]]::new()
      foreach ($tr in $nodo.SelectNodes('./w:tr', $ns)) {
        $celdas = @()
        foreach ($tc in $tr.SelectNodes('./w:tc', $ns)) {
          $celdas += ((($tc.SelectNodes('.//w:t', $ns) | ForEach-Object { $_.InnerText }) -join ' ').Trim())
        }
        $filas.Add($celdas)
      }
      $estructura.Add([pscustomobject]@{ tipo='tabla'; numero=$numeroTabla; filas=$filas })
      $lineas.Add("`n[TABLA $numeroTabla]")
      foreach ($fila in $filas) { $lineas.Add(($fila -join ' | ')) }
    }
  }
  $comentarios = @()
  $commentsXml = Leer-Entrada 'word/comments.xml'
  if ($commentsXml) {
    [xml]$comments = $commentsXml
    $cns = [System.Xml.XmlNamespaceManager]::new($comments.NameTable)
    $cns.AddNamespace('w', 'http://schemas.openxmlformats.org/wordprocessingml/2006/main')
    foreach ($c in $comments.SelectNodes('//w:comment', $cns)) {
      $comentarios += [pscustomobject]@{ autor=$c.GetAttribute('author', $cns.LookupNamespace('w')); fecha=$c.GetAttribute('date', $cns.LookupNamespace('w')); texto=(($c.SelectNodes('.//w:t', $cns) | ForEach-Object { $_.InnerText }) -join '') }
    }
  }
  $propiedades = @{}
  $appXml = Leer-Entrada 'docProps/app.xml'
  if ($appXml) { [xml]$app = $appXml; foreach ($hijo in $app.DocumentElement.ChildNodes) { $propiedades[$hijo.LocalName] = $hijo.InnerText } }
  $resumen = [ordered]@{
    propiedades=$propiedades
    parrafos=($estructura | Where-Object tipo -eq 'parrafo').Count
    tablas=$numeroTabla
    imagenes=($zip.Entries | Where-Object FullName -like 'word/media/*').Count
    comentarios=$comentarios
    inserciones_control_cambios=$doc.SelectNodes('//w:ins', $ns).Count
    eliminaciones_control_cambios=$doc.SelectNodes('//w:del', $ns).Count
  }
  [System.IO.File]::WriteAllLines((Join-Path $salida 'texto_completo.txt'), $lineas, [System.Text.UTF8Encoding]::new($false))
  $estructura | ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8 (Join-Path $salida 'estructura.json')
  $resumen | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $salida 'resumen.json')
  $resumen | ConvertTo-Json -Depth 8
} finally { $zip.Dispose() }
