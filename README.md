# Now Playing Screensaver (갤럭시탭 S9+ 용)

유튜브 뮤직(또는 다른 미디어 앱)이 재생 중일 때, 앨범아트 + 재생바 + 이전/재생-일시정지/다음
버튼을 보여주는 화면입니다. **일반 앱**으로도 실행되고, **태블릿 화면보호기(Daydream)**
로도 등록해서 쓸 수 있습니다.

## 🔥 태블릿/휴대폰만으로 빌드하기 (컴퓨터 없이)
Android Studio 없이 브라우저만으로 APK를 만들 수 있어요. `.github/workflows/build-apk.yml`
파일이 이미 포함되어 있어서, GitHub에 올리기만 하면 자동으로 빌드됩니다.

1. GitHub 앱 또는 모바일 브라우저로 github.com 접속 → 로그인 (계정 없으면 가입)
2. 오른쪽 위 `+` → **New repository** → 이름 아무거나 (예: now-playing) → Create
3. 방금 만든 저장소 페이지에서 **Add file > Upload files**
4. 압축 푼 `NowPlayingScreensaver` 폴더 안의 내용물을 전부 선택해서 업로드
   (폴더째로 드래그가 안 되면, 파일 탐색기 앱에서 폴더를 다시 zip으로 묶은 뒤
   업로드 화면에 그 zip을 끌어놓으면 GitHub가 자동으로 풀어서 올려줍니다)
5. 업로드 후 커밋(Commit changes)
6. 저장소 상단 **Actions** 탭 클릭 → "Build Debug APK" 워크플로가 자동 실행 중일 거예요
   (몇 분 걸림)
7. 초록 체크가 뜨면 그 실행 결과를 눌러서 맨 아래 **Artifacts > app-debug-apk** 다운로드
8. 다운로드된 zip 안에 `app-debug.apk`가 있음 → 갤럭시탭에서 설치
   (알 수 없는 출처 설치 허용 필요)

이 방법이면 컴퓨터도 Android Studio도 전혀 필요 없이 갤럭시탭이나 휴대폰만으로 끝까지
진행할 수 있어요.

## (참고) 컴퓨터로 빌드하는 경우
컴퓨터가 있다면 Android Studio에서 직접 여는 방법도 가능합니다.

## 왜 여기서 바로 APK를 못 만들어줬나요?
제 작업 환경에는 Android SDK/Gradle과 인터넷 접속이 없어서 이 자리에서 컴파일은 불가능해요.
대신 Android Studio에서 바로 열어서 빌드만 누르면 되는 완성된 프로젝트를 만들어 드렸습니다.

> 참고: 이 프로젝트에는 Gradle Wrapper 파일이 포함되어 있지 않습니다(제가 만든 환경에
> 인터넷이 안 돼서 미리 받아둘 수 없었어요). Android Studio에서 열면 자동으로
> "Gradle wrapper가 없습니다, 생성할까요?" 라는 안내가 뜨는데 그냥 승인하시면 됩니다.

## 빌드 방법
1. [Android Studio](https://developer.android.com/studio) 설치 (Hedgehog 이상 권장)
2. 압축 푼 `NowPlayingScreensaver` 폴더를 **Open** (File > Open)
3. Gradle 동기화가 끝날 때까지 대기 (처음 한 번은 인터넷에서 라이브러리를 받습니다)
4. 상단 메뉴 `Build > Build Bundle(s) / APK(s) > Build APK(s)`
5. 빌드가 끝나면 `app/build/outputs/apk/debug/app-debug.apk` 생성됨
6. 이 apk를 갤럭시탭 S9+로 옮겨서 설치 (출처를 알 수 없는 앱 설치 허용 필요)

## 설치 후 설정 (필수)
1. 앱 최초 실행 시 "알림 접근 권한 허용하기" 버튼을 누르면 설정 화면으로 이동합니다.
   - 안드로이드가 다른 앱(유튜브 뮤직)의 재생 정보를 읽으려면 이 권한이 반드시 필요합니다.
   - 설정에서 **Now Playing Screensaver**를 찾아 켜주세요.
2. 유튜브 뮤직에서 아무 노래나 재생하면 화면에 앨범아트/곡정보/재생바가 나타납니다.

## 화면보호기(Daydream)로 등록하기
1. 설정 > 디스플레이 > 화면 보호기(스크린 세이버) 이동
   (S9+ 기본 UI 기준: 설정 > 디스플레이 > 화면 보호기)
2. **Now Playing Screensaver** 선택
3. "지금 미리보기"로 테스트 가능
4. "충전 중일 때 시작" 등의 옵션을 켜두면 거치대에 꽂아둘 때 자동으로 켜집니다

## 구조 설명
- `MainActivity.kt` : 앱 아이콘을 눌러 실행했을 때의 화면
- `NowPlayingDreamService.kt` : 화면보호기로 등록됐을 때 실행되는 화면
- `NowPlayingController.kt` : 위 두 화면이 공통으로 쓰는 로직
  (미디어세션 연결, 앨범아트/재생바 갱신, 버튼 클릭 처리)
- `MediaNotificationListenerService.kt` : 다른 앱의 재생 정보를 읽기 위해
  꼭 있어야 하는 최소한의 알림 리스너 (알림 자체는 사용하지 않음)

## 빠른 설정(퀵패널)에 추가하기
화면 위에서 아래로 두 번 쓸어내려 빠른 설정 패널을 열고, 편집(연필 아이콘) 버튼을 누른 뒤
**Now Playing Screensaver** 타일을 원하는 위치로 끌어다 놓으면, 다음부터는 패널에서
탭 한 번으로 앱이 바로 열립니다.

## 참고 / 한계
- 유튜브 뮤직뿐 아니라 안드로이드 표준 미디어세션(MediaSession)을 쓰는 모든 앱(스포티파이,
  삼성뮤직 등)에서 동작합니다. 여러 앱이 동시에 재생 중이면 유튜브 뮤직을 우선 표시합니다.
- 일부 기기 제조사(특히 삼성)는 배터리 최적화 때문에 백그라운드에서 알림 리스너 서비스가
  종료될 수 있습니다. 이럴 경우 설정 > 배터리 > Now Playing Screensaver를
  "제한 없음"으로 바꿔주세요.
- 아이콘은 임시로 생성한 기본 아이콘입니다. `app/src/main/res/mipmap-*/ic_launcher.png`를
  원하는 이미지로 교체하시면 됩니다.
