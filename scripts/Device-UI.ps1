param([string]$Serial = '')
$script:BedMirrorAdb = Join-Path (Split-Path $PSScriptRoot -Parent) '.tools/android/platform-tools/adb.exe'
if(!$Serial){$connected=@(& $script:BedMirrorAdb devices | Where-Object {$_ -match '^\S+\s+device$'});if($connected.Count -ne 1){throw 'Specify the one test phone serial explicitly.'};$Serial=($connected[0]-split '\s+')[0]}
$script:BedMirrorSerial = $Serial
function Read-DeviceUI {
    $remote='/sdcard/nyxlink-ui-'+[Guid]::NewGuid().ToString('N')+'.xml'
    try {
        $dump=(& $script:BedMirrorAdb -s $script:BedMirrorSerial shell uiautomator dump $remote) -join "`n"
        if($LASTEXITCODE -ne 0 -or $dump -notmatch 'dumped to:'){throw 'Android did not return a fresh UI snapshot. No input was sent.'}
        $xml = (& $script:BedMirrorAdb -s $script:BedMirrorSerial shell cat $remote) -join "`n"
        if($LASTEXITCODE -ne 0){throw 'Could not read the fresh Android UI snapshot.'}
        [xml]$document = $xml
        return $document
    } finally { & $script:BedMirrorAdb -s $script:BedMirrorSerial shell rm -f $remote | Out-Null }
}
function Tap-DeviceNode($Node) {
    if (!$Node) { throw 'UI element was not found.' }
    $numbers = [regex]::Matches($Node.bounds,'\d+') | ForEach-Object { [int]$_.Value }
    $nav=$Node.OwnerDocument.SelectSingleNode('//node[@resource-id="android:id/navigationBarBackground"]')
    if($nav){$v=[regex]::Matches($nav.bounds,'\d+')|ForEach-Object{[int]$_.Value};if($v[0] -gt $numbers[0] -and $v[1] -eq 0){$numbers[2]=[Math]::Min($numbers[2],$v[0])}}
    if($numbers[2] -le $numbers[0]){throw 'Control is covered by the Android navigation bar; scroll it into view.'}
    & $script:BedMirrorAdb -s $script:BedMirrorSerial shell input tap ([int](($numbers[0]+$numbers[2])/2)) ([int](($numbers[1]+$numbers[3])/2))
}
function Invoke-DeviceMenuChoice([string]$Menu,[string]$Choice) {
    $ui=Read-DeviceUI
    $menuNode=$ui.SelectSingleNode("//node[@text='$Menu']")
    if(!$menuNode){
        $row=$ui.SelectSingleNode('//node[@class="android.widget.HorizontalScrollView"]')
        if(!$row){throw 'Expected the streaming toolbar; no input was sent.'}
        $v=[regex]::Matches($row.bounds,'\d+')|ForEach-Object{[int]$_.Value}
        $y=[int](($v[1]+$v[3])/2)
        & $script:BedMirrorAdb -s $script:BedMirrorSerial shell input swipe ($v[2]-50) $y ($v[0]+80) $y 450
        $ui=Read-DeviceUI;$menuNode=$ui.SelectSingleNode("//node[@text='$Menu']")
    }
    Tap-DeviceNode $menuNode
    for($attempt=0;$attempt -lt 10;$attempt++){
        $ui=Read-DeviceUI
        $node=$ui.SelectSingleNode("//node[@text='$Choice']")
        if($node){Tap-DeviceNode $node;return}
        $list=$ui.SelectSingleNode('//node[@class="android.widget.ListView"]')
        if(!$list){throw 'Expected a scrollable app menu; no input was sent.'}
        $v=[regex]::Matches($list.bounds,'\d+')|ForEach-Object{[int]$_.Value}
        $x=[int](($v[0]+$v[2])/2)
        & $script:BedMirrorAdb -s $script:BedMirrorSerial shell input swipe $x ($v[3]-45) $x ($v[1]+45) 650
    }
    throw "Menu choice was not found: $Choice"
}
function DoubleTap-DeviceSurface($Ui) {
    $node=$Ui.SelectSingleNode('//node[starts-with(@content-desc,"Remote desktop.")]')
    if(!$node){$node=$Ui.SelectSingleNode('//node[@class="android.view.SurfaceView"]')}
    if(!$node){$node=$Ui.SelectSingleNode('/hierarchy/node[@package="dev.bedmirror.android.debug" and @class="android.widget.FrameLayout"]')}
    if(!$node){throw 'Video gesture surface is unavailable.'}
    $numbers=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
    $top=$numbers[1];$bottom=$numbers[3]
    $toolbar=$Ui.SelectSingleNode('//node[@text="KEYBOARD"]')
    if($toolbar){$v=[regex]::Matches($toolbar.bounds,'\d+');$top=[Math]::Max($top,[int]$v[3].Value+12)}
    $keys=$Ui.SelectSingleNode('//node[@text="ESC"]')
    if($keys){$v=[regex]::Matches($keys.bounds,'\d+');$bottom=[Math]::Min($bottom,[int]$v[1].Value-12)}
    if($bottom-$top -lt 40){throw 'Close the Android IME before injecting a controls gesture; the toolbar covers the video.'}
    $x=[int](($numbers[0]+$numbers[2])/2);$y=[int](($top+$bottom)/2)
    & $script:BedMirrorAdb -s $script:BedMirrorSerial shell input tap $x $y
    & $script:BedMirrorAdb -s $script:BedMirrorSerial shell input tap $x $y
}
