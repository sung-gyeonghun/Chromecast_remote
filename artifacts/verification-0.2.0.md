# 리모콘 0.2.0 검증

2026-10-15 (Asia/Seoul)

- APK: `remocon-0.2.0.apk`, 3,750,552 bytes
- SHA-256: `630ef9ea9fb517aa3993cdcaf62c9adce343998ef1389fec4b5ee75d2f4ad84b`
- 버전 코드 2, 기존 0.1.0과 동일한 개발용 서명으로 업데이트 설치 가능
- Gradle assembleDebug / testDebugUnitTest / lintDebug 성공
- JUnit: 기존 3개 + IME 2개 = 5개 성공. 한글 문자열의 UTF-8 전송, TV의 IME/field counter 보존, 빈 문자열 거부 검사
- Lint 오류 0, 경고 5 (SDK/라이브러리 버전 및 TLS trust manager 관련, 이전 버전과 같은 분류)
- apksigner verify 성공 (APK Signature Scheme v2)

## UI 확인

Android 10 에뮬레이터에서 기존 버전 위에 업데이트 설치 및 실행 성공.

- 파란색 라이트/다크 화면을 직접 캡처해 확인
- 우측 상단 물음표 아래 다크 토글 정상 동작
- 앱 종료/재실행 후 다크 선택 유지
- 연결 중 테마 변경 후 연결 유지 및 키보드 입력 버튼 유지
- 연결 전 키보드 버튼 비활성화
- 키보드 입력창과 휴대폰 소프트 키보드 표시, 파란색 테마의 대화상자 확인

`remocon-light.png`, `remocon-dark.png`는 연결 대기 화면입니다. `remocon-keyboard.png`는 로컬 테스트 서버에 연결한 상태의 입력창입니다.

## 키보드 통신 확인

공개 Android TV Remote v2 구현의 IME 기능과 protobuf 필드를 참고해 구현했습니다. Google 앱을 REA로 분석한 결과는 아닙니다.

- 제어 연결에서 IME(feature 4) 협상
- TV가 전달한 IME counter/field counter를 사용해 RemoteImeBatchEdit 전송
- TV 입력 정보가 없거나 IME 미지원이면 전송하지 않고 입력창에서 안내
- 테스트 전용 MockTv 서버가 전송된 counter 17 / 42와 문자열 위치를 검증
- ‘검색 / Enter’ UI에서 검색어 `Chromecast` 전송 → 서버 `TEXT=Chromecast`, 이어서 `KEY=66` 확인
- ‘텍스트 전송’ UI에서 문자열만 전송 → 서버 `TEXT=Chromecast` 확인
- 최종 APK에서도 연결 중 테마 변경, 검색 / Enter 및 텍스트 전송 확인

TV의 검색창을 먼저 선택해야 합니다. 검색어는 기존 입력 내용을 바꾸는 방식이며 Enter 처리와 입력 지원은 TV 앱별로 다릅니다. 실제 사용자의 Chromecast/휴대폰에서는 아직 검증하지 않았습니다.

이전 `rea-evidence.json`과 `verification.md`는 **0.1.0 APK** 검사 기록입니다. 이번 변경은 소스 구현과 Android 빌드/에뮬레이터 검증으로 수행했습니다.
