# CarTube

スマホで指定した YouTube を、Android Auto の車の画面に出して見るアプリです。CarStream と同じように、再生は車載ディスプレイ側で行います。

Google Play には出していません。APK をダウンロードして、スマホに直接インストールします。想定しているのは Xiaomi 14 Ultra（Android 14 / HyperOS）で、車の Android Auto にサイドロードして使う使い方です。

YouTube も Android Auto も、それぞれの会社の名前です。CarTube は公式アプリではありません。経路案内もしません。車の画面いっぱいにページを出すために、Android Auto の描画領域を使っているだけです。

## 安全について

**駐車中に使うか、同乗者が操作するためのものです。運転中に画面を操作したり、映像を視聴したりしないでください。**

走っているときに視線をそらしたり、画面を触ったりすると事故になります。車を停めてから使ってください。運転中の使用で起きたことについて、このアプリの作者は責任を負いません。

## 必要なもの

- Android 8.0 以降のスマホ。動作の想定は Xiaomi 14 Ultra（Android 14 / HyperOS）です
- スマホに入っている Android Auto
- Android Auto 対応の車、またはディスプレイオーディオ

中国本土向けの HyperOS には、Google の Android Auto 自体が入らないことがあります。その状態では、このアプリを入れても車の画面には出せません。

## インストール

### 1. APK を入れる

1. [Releases](https://github.com/haratak/car-tube/releases) から `car-tube-<バージョン>.apk` をスマホにダウンロードする。
2. ダウンロードした APK を開く。
3. 「提供元不明のアプリ」や「この提供元を許可」と出たら、許可してインストールする。設定から入れる場合は、APK を開いたアプリ（Chrome やファイルマネージャーなど）に対して「不明なアプリのインストール」を許可します。

ここはスマホ本体のインストール許可です。Android Auto 側の許可は、このあと別で行います。

### 2. Android Auto の開発者モード

車の画面ではなく、スマホの Android Auto アプリで設定します。

1. Android Auto の設定を開く。
2. 一番下までスクロールして、「バージョン」を 10 回タップする。
3. 開発者モードになったら、右上のメニュー（⋮）から「開発者向け設定」を開く。
4. 「提供元不明のアプリ」をオンにする。バージョンによっては「不明なソースからのアプリ」という表示です。
5. いったん車との接続を切り、もう一度つなぐ。

これをしないと、CarTube は車のランチャーに出てきません。

Android Auto 17 は、ナビ（カテゴリが maps）として宣言したサイドロードアプリを、このスイッチがオンでも隠します。CarTube はアプリの種別を game にしてあります。[AABrowser](https://github.com/kododake/AABrowser) と同じ扱いで、提供元不明のアプリがオンなら一覧に出ます。出るのは駐車中だけです。車が動き出すと画面はグレーになり、操作できなくなります。運転中に見ない、というこのアプリの前提と同じです。

Android 15 以降のスマホでは、車のランチャーが駐車中の画面として CarTube を開きます。ページをそのまま触れます。それより前の Android や、デスクトップヘッドユニットでは、従来どおりテンプレートの画面が開きます。そちらはパンのボタンを押してからドラッグします。アイコンが2つ出たときは、どちらでも再生できます。

### ランチャーにまだ出ないとき

上までやってもアイコンが出ない車があります。Android Auto 17 以降は、GitHub から入れた APK だと `InstallSourceInfo` の initiating package を見て、テンプレート系アプリを隠すことがあります。CarTube 0.2.0 は `android:appCategory="game"` と駐車専用の `CAR_LAUNCHER` で、多くの環境ではこの問題を避けられます。それでも出ないときは、Play ストア経由のインストール記録を付け直します。

**CarTube Installer（同梱・別 APK）**

1. [Releases](https://github.com/haratak/car-tube/releases) から `car-tube-installer-*.apk` を入れる。
2. 下の Shizuku 手順で Shizuku を起動する。
3. CarTube Installer で `car-tube-*.apk` を選び、「Play ストア扱いでインストール」を実行する。
4. 画面下の診断で `installer=com.android.vending` になっているか確認する。
5. Android Auto の「提供元不明のアプリ」がオンであることを確認し、車との接続を切り直す。

**KingInstaller（代替）**

[KingInstaller](https://github.com/fcaronte/KingInstaller) でも同じ目的のインストールができます。Shizuku 方式が使えない場合は KingInstaller の Root 方式が、HyperOS ではより確実なことがあります。

あとから普通の APK で上書きインストールすると、Play ストア扱いの記録が消えます。そのときは、また CarTube Installer か KingInstaller で入れ直してください。

#### Xiaomi 14 Ultra（HyperOS）で Shizuku を起動する

ルート不要です。Developer options と Wireless debugging を使います。

1. **開発者向けオプション**: 設定 → 端末情報 → OS バージョン（または MIUI バージョン）を 7 回タップ → 設定 → 追加設定 → 開発者向けオプション。
2. **USB デバッグ** と **ワイヤレスデバッグ** をオンにする。
3. 開発者向けオプション → **ワイヤレスデバッグ** を開く → **ペア設定コードによるデバイスのペア設定**。
4. 表示された 6 桁コードとポートを、PC から `adb pair <ip>:<pair-port>` でペアする（PC がなくても Shizuku アプリ内の案内に従えば同じ操作ができます）。
5. ペア後、Shizuku アプリを開き **ワイヤレスデバッグで起動** をタップする。通知に「Shizuku 正在运行」と出れば OK。
6. CarTube Installer を開き **Shizuku の権限を許可** する。

HyperOS では Shizuku 経由の入れ替えがメーカー制限で失敗することがあります。そのときは KingInstaller の Root 方式、または Root 環境での `pm install -i com.android.vending` が必要になることがあります。

### 3. Xiaomi / HyperOS で落ちるとき

HyperOS は、画面を消すとアプリを止めがちです。車の画面が真っ黒になる、音だけ消える、というときはここを確認してください。

- **自動起動**: 設定 → アプリ → アプリを管理 → CarTube → 自動起動をオン
- **バッテリー**: 同じアプリ情報のバッテリー（電池）で「制限なし」にする。省電力の対象に入れない
- アプリ内の「バッテリー制限を解除」「自動起動の設定を開く」からも、システムの画面を開けます。機種によってはアプリ情報に飛ぶだけなので、その場合は上の項目を手で開いてください

車の画面を出したままスマホをロックして止まるなら、ほぼバッテリー制限です。

## 使い方

1. 車と Android Auto で接続する。
2. 車のランチャーから CarTube を開く。
3. スマホの CarTube に、YouTube の URL を貼るか、見たい言葉を入力する。
4. 「車の画面で開く」を押す。映像は車の画面に出ます。

車の画面をタップすると、再生や一時停止などの操作がページに伝わります。テンプレートの画面でリストをスクロールしたいときは、画面のパン（地図を動かすときのボタン）を押してからドラッグしてください。駐車中の画面として開いているときは、ページをそのまま触れます。車の戻る操作は、ページの履歴を戻します。

次のような入力が使えます。

- `https://www.youtube.com/watch?v=...`
- `https://youtu.be/...`
- `https://www.youtube.com/shorts/...`
- 11文字の動画 ID
- 検索したい言葉（車の画面で検索結果を開きます）

YouTube 以外のサイトの URL は開きません。

YouTube 側の都合で、埋め込み再生が拒否されたり、画だけ出なかったりすることがあります。そのときはページを開き直すか、検索から動画をタップしてください。

## 開発

デバッグ用 APK とテスト:

```bash
./gradlew testDebugUnitTest assembleDebug
```

署名鍵はこのリポジトリに入っていません。`v*` のタグを push すると、GitHub Actions がリポジトリシークレットで署名し、`car-tube-<バージョン>.apk` を Releases に載せます。車の画面にどう描いているかは [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) に書いてあります。
