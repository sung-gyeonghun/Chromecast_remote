"""Emulator-only UI smoke flow against scripts/MockTv.java (never a real TV).
Usage: python scripts/ui_smoke.py <adb path> pair|code|keys [six-hex-code]
"""
import re
import subprocess
import sys
import os
import time
import xml.etree.ElementTree as ET

ADB = sys.argv[1]
DEVICE = os.environ.get("REMOCON_TEST_DEVICE", "emulator-5554")
def adb(*args):
    return subprocess.check_output([ADB, "-s", DEVICE, *args], timeout=15).decode("utf-8")
def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/remocon-smoke.xml")
    return list(ET.fromstring(adb("shell", "cat", "/sdcard/remocon-smoke.xml")).iter("node"))
def find(text=None, desc=None, cls=None):
    for _ in range(5):
        for n in nodes():
            a = n.attrib
            if text is not None and a.get("text") != text: continue
            if desc is not None and a.get("content-desc") != desc: continue
            if cls is not None and a.get("class") != cls: continue
            return n
        time.sleep(.2)
    raise AssertionError(f"UI item missing: {text or desc or cls}")
def tap(text=None, desc=None, cls=None):
    n=find(text,desc,cls)
    if n.get("enabled") != "true": raise AssertionError("Disabled UI control")
    x1,y1,x2,y2=map(int,re.findall(r"\d+",n.get("bounds")))
    adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))
def type_text(value): adb("shell","input","text",value)
def clear_input():
    adb("shell","input","keyevent","123")
    adb("shell","input","keyevent",*(["67"]*32))

if sys.argv[2] == "pair":
    try: tap(text="기기 연결")
    except AssertionError:
        tap(text="연결 관리")
        tap(text="기기 변경")
    tap(text="IP 직접 입력")
    tap(cls="android.widget.EditText")
    clear_input()
    type_text("10.0.2.2")
    tap(text="연결")
    find(text="TV와 페어링")
    print("PAIRING_UI_OK")
elif sys.argv[2] == "code":
    tap(cls="android.widget.EditText")
    type_text(sys.argv[3])
    tap(text="페어링")
    time.sleep(.5)
    find(text="●  연결됨")
    print("CONNECTED_UI_OK")
elif sys.argv[2] == "wrong-code":
    tap(cls="android.widget.EditText")
    clear_input()
    type_text(sys.argv[3])
    tap(text="페어링")
    find(text="페어링 코드가 맞지 않습니다. TV의 코드를 다시 확인하세요.")
    find(cls="android.widget.EditText")
    print("WRONG_CODE_RETRY_UI_OK")
elif sys.argv[2] == "keys":
    find(text="●  연결됨")
    for desc in ["위", "왼쪽", "확인", "오른쪽", "아래", "뒤로", "홈", "재생 / 일시정지", "볼륨 줄이기", "음소거", "볼륨 높이기", "전원 전환"]:
        tap(desc=desc)
    print("TWELVE_KEYS_UI_OK")
elif sys.argv[2] == "apps":
    find(text="●  연결됨")
    tap(desc="YouTube 실행")
    tap(desc="Netflix 실행")
    find(text="●  연결됨")
    print("TWO_APP_SHORTCUTS_UI_OK")
elif sys.argv[2] == "layout":
    all_nodes=nodes()
    app_nodes=[n for n in all_nodes if n.get("package")=="com.morluto.remocon"]
    assert not any(n.get("class")=="android.widget.ScrollView" or n.get("scrollable")=="true" for n in app_nodes),"Main screen must not scroll"
    content=next(n for n in app_nodes if n.get("resource-id")=="android:id/content")
    left,top,right,bottom=map(int,re.findall(r"\d+",content.get("bounds")))
    controls=["사용 안내","다크 모드 설정","YouTube 실행","Netflix 실행","위","아래","왼쪽","오른쪽","확인","뒤로","홈","재생 / 일시정지","볼륨 줄이기","음소거","볼륨 높이기","전원 전환"]
    for desc in controls:
        n=next(n for n in app_nodes if n.get("content-desc")==desc)
        x1,y1,x2,y2=map(int,re.findall(r"\d+",n.get("bounds")))
        assert left<=x1<x2<=right and top<=y1<y2<=bottom,(desc,n.get("bounds"),content.get("bounds"))
    assert not any(n.get("content-desc")=="키보드 입력" for n in app_nodes)
    assert any(n.get("text") in ["기기 연결","연결 관리"] for n in app_nodes)
    print("ALL_17_CONTROLS_VISIBLE_NO_SCROLL_OK")
