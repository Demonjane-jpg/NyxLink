$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$listener=[Net.HttpListener]::new();$listener.Prefixes.Add('http://127.0.0.1:48765/');$listener.Start()
try{while($listener.IsListening){$context=$listener.GetContext();try{
 if($context.Request.HttpMethod -eq 'POST' -and $context.Request.Url.AbsolutePath -eq '/state'){$reader=[IO.StreamReader]::new($context.Request.InputStream);$body=$reader.ReadToEnd();$reader.Dispose();$null=$body|ConvertFrom-Json;[IO.File]::WriteAllText("$root/artifacts/extended-tests/browser-state.json",$body);$bytes=[Text.Encoding]::UTF8.GetBytes('ok');$context.Response.ContentType='text/plain'}
 else{$bytes=[IO.File]::ReadAllBytes("$root/tests/browser/input.html");$context.Response.ContentType='text/html; charset=utf-8'}
 $context.Response.ContentLength64=$bytes.Length;$context.Response.OutputStream.Write($bytes,0,$bytes.Length)
 }finally{$context.Response.Close()}}}finally{$listener.Stop();$listener.Close()}
