<div align="center">

<img src="docs/assets/banner.svg" alt="REMOCON — Your TV, in your hand" width="100%" />

# 리모콘

**Chromecast 4세대를 내 휴대폰으로.**<br>
연결 유지와 파란색 라이트·다크 테마를 갖춘 Android Wi-Fi 리모컨

![Version](https://img.shields.io/badge/version-0.6.0-246BFD?style=flat-square)
![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![Connection](https://img.shields.io/badge/connection-local_Wi--Fi-58708E?style=flat-square)

### [⬇ Android APK 다운로드](https://github.com/sung-gyeonghun/Chromecast_remote/raw/refs/heads/main/artifacts/remocon-0.6.0.apk)

**v0.6.0 · 약 3.6 MB · Android 8.0 이상**<br>
[설치 방법](#-설치와-연결) · [주요 기능](#-주요-기능) · [연결 유지](#-연결-유지와-해제) · [직접 빌드](#build)

</div>

---

## ✨ 화면 미리보기

<table>
  <tr>
    <th align="center">Light</th>
    <th align="center">Dark</th>
  </tr>
  <tr>
    <td width="50%"><img src="artifacts/remocon-light.png" alt="파란색 라이트 모드의 리모컨 화면" width="100%" /></td>
    <td width="50%"><img src="artifacts/remocon-dark.png" alt="파란색 다크 모드의 리모컨 화면" width="100%" /></td>
  </tr>
</table>

> 화면은 로컬 테스트 서버에 연결한 Android 에뮬레이터에서 촬영했습니다.

## 📲 설치와 연결

**휴대폰에서 위의 [APK 다운로드](https://github.com/sung-gyeonghun/Chromecast_remote/raw/refs/heads/main/artifacts/remocon-0.6.0.apk) 링크를 열면 설치 파일을 받을 수 있습니다.**

1. 다운로드한 `remocon-0.6.0.apk`를 엽니다.
2. Android가 요청하면 다운로드에 사용한 브라우저 또는 파일 관리자에 **알 수 없는 앱 설치**를 허용합니다.
3. 휴대폰과 Chromecast를 **같은 Wi-Fi**에 연결합니다.
4. 앱에서 **기기 연결**을 누르고 Chromecast를 선택합니다.
5. **TV에 표시된 6자리 코드**를 입력하면 연결됩니다.

자동 검색이 안 되면 **IP 직접 입력**을 이용하세요. Chromecast의 설정 → 네트워크에서 IPv4 주소를 확인할 수 있습니다. 기존 0.1.0 사용자는 앱을 삭제하지 않고 업데이트 설치할 수 있습니다.

| 준비물 | 요구 사항 |
| :--- | :--- |
| 휴대폰 | Android 8.0 이상 |
| 기기 | Chromecast with Google TV, 4세대 4K/HD 대상 |
| 네트워크 | 휴대폰과 Chromecast가 통신할 수 있는 동일 Wi-Fi |
| 별도 설정 | Google 계정 로그인, ADB 디버깅, TV에 별도 앱 설치 불필요 |

> [!IMPORTANT]
> 현재 APK는 **개발용 서명 버전**입니다. 0.6.0 수정 사항은 에뮬레이터와 테스트 서버에서 검증했으며 실제 Chromecast에서 추가 확인이 필요합니다. Google 공식 앱이 아닙니다.

## 🎛 주요 기능

| 기능 | 사용할 수 있는 것 |
| :--- | :--- |
| **기기 연결** | 자동 검색, IP 직접 입력, TV 코드 페어링 |
| **탐색** | 위·아래·왼쪽·오른쪽, 확인, 뒤로, 홈 |
| **미디어** | 재생 / 일시정지, 볼륨 조절, 음소거, 전원 키 |
| **앱 바로가기** | YouTube · Netflix 실행 요청 |
| **테마** | 파란색 라이트·다크 모드, 선택 저장 |
| **연결 유지** | 앱을 닫아도 유지, 네트워크 장애 시 자동 재연결, 직접 해제 |

**테마 전환:** 오른쪽 위 **물음표 왼쪽 ‘다크’ 토글**을 켜면 다크 모드, 끄면 라이트 모드입니다. 처음 실행할 때는 휴대폰 시스템 테마를 따르며, 변경한 선택은 저장됩니다.

**한 화면 구성:** 메인 화면에 스크롤을 사용하지 않습니다. YouTube·Netflix를 한 줄로 배치하고, 방향 패드와 버튼 영역은 화면 크기에 맞춰 조절합니다.

**전원 버튼:** 좌측 상단의 빨간 전원 아이콘을 누릅니다. 상단 제목·영문 문구와 하단 전원 버튼을 제거해 화면을 간결하게 구성했습니다.

**YouTube / Netflix:** TV에 해당 앱이 설치된 상태에서 버튼을 누르면 TV 앱에 실행 요청을 보냅니다. 휴대폰의 YouTube / Netflix 앱을 실행하는 기능이 아닙니다. TV의 앱 바로가기 지원 여부와 실제 설치 상태에 따라 결과가 달라질 수 있습니다.

## 🔗 연결 유지와 해제

연결 후 앱을 닫거나 다른 앱으로 전환해도 **연결 유지 서비스**가 동작합니다. 연결 유지에 시간 제한을 두지 않으며, 일시적인 Wi-Fi 장애나 서버 종료 시 자동으로 재연결합니다. Android 13 이상에서 알림 권한을 요청하면 허용하는 것을 권장합니다.

- **직접 해제:** 앱의 ‘연결 관리 → 연결 해제’, 또는 연결 유지 알림의 ‘연결 해제’
- **기기 변경:** ‘연결 관리 → 기기 변경’
- **검색:** Remote v2 / Google Cast mDNS, DIAL 및 저장된 IP 확인을 함께 사용

네트워크 차단, 기기 전원 종료, Android의 앱 강제 종료 상태에서 물리적인 접속 자체를 보장할 수는 없습니다. 네트워크 장애에는 재연결을 계속 시도하며, 인증서 변경이나 페어링 오류에는 확인을 요청합니다. 기기 IP가 바뀌면 검색 또는 IP 재입력이 필요할 수 있습니다.

## 🔊 볼륨 조절

볼륨 버튼은 TV 키 누름/해제 이벤트를 보내고 기기 볼륨 변경을 확인합니다. 변경을 확인할 수 없으면 **Cast 수신기 볼륨을 직접 제어**합니다. 실패해도 리모컨 연결을 끊지 않습니다.

TV 볼륨이 **적외선 리모컨 전용** 또는 **고정 볼륨**으로 설정되어 있으면 휴대폰의 Wi-Fi 명령만으로 TV 자체 음량을 바꾸지 못할 수 있습니다. Google TV 설정의 리모컨/볼륨 제어를 Chromecast 또는 HDMI-CEC로 변경해야 할 수 있습니다. 고정 볼륨이나 요청 거부는 안내로 표시합니다.

키보드 버튼과 텍스트 입력 기능은 0.6.0에서 제거했습니다.

## 💡 연결이 안 될 때

| 증상 | 확인할 것 |
| :--- | :--- |
| 기기를 찾지 못함 | 같은 Wi-Fi인지 확인하고 IP 직접 입력 시도 |
| 연결 시간이 초과됨 | TV 전원, VPN, 게스트 Wi-Fi / AP 격리 확인 |
| 코드가 맞지 않음 | TV에 현재 표시된 6자리 코드 재입력 |
| 인증서가 변경됐다고 표시됨 | 기기를 확인한 뒤 다시 페어링 |
| 전원·볼륨이 반응하지 않음 | TV / Chromecast의 HDMI-CEC 및 볼륨 제어 설정 확인 |

전원은 TV의 HDMI-CEC 설정에 따라 동작합니다. **음성 입력, TV 입력 전환, 키보드 입력, 장시간 누르기**는 현재 지원하지 않습니다.

<details>
<summary id="build"><strong>🔧 직접 빌드하기</strong></summary>

### 준비

- Java 17 이상
- Android SDK 플랫폼 35 및 빌드 도구
- Gradle Wrapper 8.11.1, Android Gradle Plugin 8.9.1 — 프로젝트에 설정 포함

프로젝트 루트에 `local.properties`를 만들고 자신의 Android SDK 경로를 지정합니다.

```properties
sdk.dir=C:/Users/사용자/AppData/Local/Android/Sdk
```

### Windows PowerShell

```powershell
git clone https://github.com/sung-gyeonghun/Chromecast_remote.git
cd Chromecast_remote
.\scripts\build.ps1
```

스크립트는 APK 빌드, 단위 테스트, Lint를 실행한 뒤 결과를 `artifacts/remocon-0.6.0.apk`에 복사합니다. 한글 경로에서 Gradle 테스트 워커가 클래스를 찾지 못하는 문제를 피하기 위해 빌드 출력은 `%LOCALAPPDATA%/RemoconBuild`에 저장합니다.

### macOS / Linux

```bash
bash ./gradlew assembleDebug testDebugUnitTest lintDebug
```

결과는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

</details>

<details>
<summary><strong>🧪 검증 범위와 구현 근거</strong></summary>

- 프로토콜 프레이밍, 오류 데이터, 페어링 해시, 앱 실행 메시지의 **단위 테스트 5개 통과**
- **Android Lint 오류 0개** 및 APK 서명 검사 통과
- 에뮬레이터에서 테마 전환·선택 저장·업데이트 설치 확인
- 로컬 TLS 테스트 서버에서 페어링, 핑, 12개 리모컨 키, 볼륨 직접 제어 확인

[0.6.0 검증 기록](artifacts/verification-0.6.0.md) · [APK SHA-256](artifacts/remocon-0.6.0.sha256)

통신 구현은 공개 [androidtvremote2](https://github.com/tronikos/androidtvremote2/tree/e664a8e1f14ee458b14c646bfb3ea46f10367051)의 프로토콜 구현과 스키마를 참고해 Java로 작성했습니다. Google TV / Google Home 앱이나 실제 기기 트래픽을 REA로 분석해서 만든 것은 아닙니다. REA는 이전 0.1.0 APK의 패키지 구조 검사에 사용했습니다.

**테스트 서버 검증은 실제 Chromecast 및 TV 앱별 호환성을 보증하지 않습니다.**

</details>

<details>
<summary><strong>🔒 인증 정보와 사용 라이브러리</strong></summary>

TV 코드를 두 TLS 인증서의 RSA 공개키에 결합해 검증한 후 TV 공개키를 저장합니다. 이후 제어 연결에서 TV 공개키가 달라지면 거부합니다. 인증서와 개인키는 휴대폰의 앱 내부 저장소에 보관하며 백업 / 기기 전송에서 제외합니다.

로컬 네트워크 통신은 페어링 `6467/TCP`, 제어 `6466/TCP`, Cast 볼륨 `8009/TCP`, 검색 mDNS 및 DIAL SSDP `1900/UDP`를 사용합니다. 기기 IP가 바뀌면 새로운 주소로 연결 / 페어링이 필요할 수 있습니다.

Cast 볼륨 채널은 페어링 채널과 별개의 TLS 인증서를 사용합니다. 이미 페어링한 IP에서 처음 확인한 Cast 공개키를 저장하고 이후 키 변경을 거부합니다.

인증서 생성에는 Bouncy Castle 1.78.1을 사용합니다. [라이선스](app/src/main/assets/licenses/bouncycastle-license.html)는 APK에도 포함되어 있습니다.

</details>

---

<div align="center">

**휴대폰 하나로, TV를 조금 더 편하게.**<br>
[APK 다운로드](https://github.com/sung-gyeonghun/Chromecast_remote/raw/refs/heads/main/artifacts/remocon-0.6.0.apk) · [문제 제보](https://github.com/sung-gyeonghun/Chromecast_remote/issues)

</div>
