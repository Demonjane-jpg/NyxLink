. "$PSScriptRoot/Device-UI.ps1"
function Set-DeviceCustomShortcut([int]$Slot,[string]$Value){
    if($Slot -lt 1 -or $Slot -gt 3){throw 'Expected a custom shortcut slot from 1 to 3.'}
    Invoke-DeviceMenuChoice ACTIONS 'Configure custom buttons'
    $ui=Read-DeviceUI;Tap-DeviceNode ($ui.SelectSingleNode("//node[@text='Custom $Slot']"))
    $ui=Read-DeviceUI;$edit=$ui.SelectSingleNode('//node[@class="android.widget.EditText"]')
    if(!$edit){throw 'Custom shortcut editor was not found.'}
    $original=[string]$edit.text
    Tap-DeviceNode $edit
    if($Value -notmatch '^[A-Za-z0-9+ ]+$'){throw 'Unexpected shortcut characters; no text was sent.'}
    & $script:BedMirrorAdb -s $script:BedMirrorSerial shell input keyevent 123
    $deleteKeys=@('67')*([Math]::Min(256,$original.Length+1))
    & $script:BedMirrorAdb -s $script:BedMirrorSerial shell input keyevent $deleteKeys
    & $script:BedMirrorAdb -s $script:BedMirrorSerial shell input text ($Value.Replace(' ','%s'))
    $ui=Read-DeviceUI
    if($ui.SelectSingleNode('//node[@class="android.widget.EditText"]').text -cne $Value){throw 'Shortcut text verification failed; no preference was saved.'}
    Tap-DeviceNode ($ui.SelectSingleNode('//node[@text="SAVE"]'))
    return $original
}
