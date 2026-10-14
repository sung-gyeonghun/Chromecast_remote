# 리모콘 0.3.0 검증

2026-10-15 (Asia/Seoul)

- APK: `remocon-0.3.0.apk`, 3,750,580 bytes
- SHA-256: `61540ca510458d65ab05a8fe2cc14b384e0a8f4187aef9afd171e8b2d87c46ac`
- 버전 코드 3, 기존 버전과 같은 개발용 서명으로 업데이트 설치 가능
- assembleDebug / testDebugUnitTest / lintDebug 통과
- JUnit: 기존 5개 + 앱 링크 2개 = 7개 통과
- APK v2 서명 검증 통과

## 한 화면 레이아웃

메인 화면에서 ScrollView를 제거했습니다. 키보드·YouTube·Netflix를 한 행으로 구성하고, 나머지 영역은 현재 화면 높이를 나눠 사용합니다. 방향 패드는 가용 너비와 높이에 맞춰 크기를 바꾸고 버튼 텍스트는 자동 조절합니다. 전원·볼륨 관련 안내는 기존 도움말에서 확인할 수 있습니다.

에뮬레이터에서 다음 조건을 검사했습니다.

- 기본 1080×2400 / 420 dpi (약 411×914 dp)
- 720×1280 / 320 dpi (360×640 dp)
- 640×1136 / 320 dpi (320×568 dp)
- 1280×720 / 320 dpi (640×360 dp 가로 화면)
- 360×640 dp에서 글꼴 배율 1.3

기기 연결 버튼을 포함한 조작 컨트롤 18개가 화면 경계 안에 표시되고 메인 화면에 스크롤 가능한 뷰가 없는 것을 확인했습니다. 360×640 dp 화면은 스크린샷으로도 확인했습니다. 라이트/다크 미리보기는 새 레이아웃으로 교체했습니다.

## 앱 실행 프로토콜

TV의 app-link feature(512)와 지원 기능을 협상한 뒤 `RemoteAppLinkLaunchRequest`(field 90)의 `app_link`(field 1)를 전송합니다.

- YouTube: `https://www.youtube.com`
- Netflix: `netflix://`

링크는 공개 구현 [androidtvremote2 demo.py](https://github.com/tronikos/androidtvremote2/blob/main/src/demo.py)의 앱 실행 예제를 참고했습니다. 추가된 테스트는 앱 실행 필드 및 문자열 인코딩, 허용하지 않은 링크 거부를 확인합니다.

로컬 TLS 테스트 서버에서 두 버튼의 실행 링크 전송과 기존 키보드 검색 동작을 확인했습니다. 버튼 안내는 ‘실행 요청을 보냈어요’로 표시하며 실제 TV 앱 실행 성공을 단정하지 않습니다.

실제 Chromecast의 YouTube/Netflix 실행은 아직 검증하지 않았습니다. TV에 앱이 설치되어 있고 TV의 Remote Service가 app-link를 지원해야 합니다. Google 앱이나 실제 기기 통신을 REA로 분석한 결과가 아닙니다.
