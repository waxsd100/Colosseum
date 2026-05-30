# Colosseum (コロシアム)

Colosseum は、Minecraft サーバー内で手軽にカジノや闘技場の賭け（ベッティング）を楽しめるように開発された、プラグインの詰め合わせ（スイート）です。

ゲーム内アイテムである「カーペット」を物理的なカジノチップに見立てて、プレイヤー同士で遊べるユニークなシステムを提供しています。

---

## プロジェクトの構成

このプロジェクトは、役割ごとに以下の 3 つのモジュールに分かれています。

*   **[ChipLib](file:///D:/Users/wax100/Documents/workspace/IdeaProjects/Colosseum/ChipLib)**  
    チップの基本機能をまとめたライブラリです。アドベンチャーモードのプレイヤーでも特定のブロックの上にチップ（カーペット）を設置・回収できるように、裏側で CanPlaceOn NBT タグなどを制御しています。
*   **[CasinoCore](file:///D:/Users/wax100/Documents/workspace/IdeaProjects/Colosseum/CasinoCore)**  
    サーバー全体でのカジノイベントを管理するコアプラグインです。カジノモードを開始すると、参加プレイヤーを一時的にアドベンチャーモードにし、カーペット回収用のハサミ（カジノシザース）を配って、不正を防ぎながら遊べる環境を整えます。データの保存処理は非同期で行われるため、サーバーの動作も軽量です。
*   **[ArenaCore](file:///D:/Users/wax100/Documents/workspace/IdeaProjects/Colosseum/ArenaCore)**  
    闘技場でのベッティングを行うためのプラグインです。プレイヤーは、あらかじめ設定されたエリアに物理的にチップ（カーペット）を置くことで、好きなチームに賭けることができます。「やっぱり賭けをやめたい」という時は、配布されたハサミでカーペットを壊すだけで、いつでも手元にチップが戻ってきます。

---

## チップと額面のルール

チップ（カーペット）の色ごとに額面（E 単位）が決まっています。

*   茶色 (BROWN_CARPET): 1 E
*   紫色 (PURPLE_CARPET): 5 E
*   青色 (BLUE_CARPET): 10 E
*   水色 (LIGHT_BLUE_CARPET): 50 E
*   浅葱色 (MAGENTA_CARPET): 100 E
*   緑色 (GREEN_CARPET): 500 E
*   黄緑色 (LIME_CARPET): 1,000 E
*   黄色 (YELLOW_CARPET): 5,000 E
*   橙色 (ORANGE_CARPET): 10,000 E
*   桃色 (PINK_CARPET): 50,000 E
*   赤色 (RED_CARPET): 100,000 E
*   白色 (WHITE_CARPET): 500,000 E
*   黒色 (BLACK_CARPET): 1,000,000 E

---

## 使えるコマンド

### カジノの管理とチップの売買

管理者の方は /casino コマンドでカジノ全体の操作を行います。

*   /casino on : カジノを開始します。プレイヤーのゲームモード変更やハサミの配布が自動で行われます。
*   /casino off : カジノを終了します。その場にいるプレイヤーの手持ちチップを自動で回収・換金し、ゲームモードを元に戻します。
*   /casino status : 現在カジノが動いているか確認します。
*   /casino ranking : プレイヤーの累計損益ランキングを表示します。

一般プレイヤーは /chip コマンドを使ってチップのやり取りをします。

*   /chip <額面> <枚数> : 指定したチップを指定枚数購入します。
*   /chip <金額> : 所持金を入力すると、一番枚数が少なくなるような組み合わせでチップを自動分割して購入します。
*   /chip info : チップの額面と色の対応表をゲーム内に表示します。
*   /chip balance : 自分が今持っているチップの内訳と合計額を表示します。
*   /chip cashout : 手持ちのチップをすべて回収し、所持金に戻します。

### 闘技場（アリーナ）の運営とオッズ確認

アリーナの運営は /arena コマンドで行います（管理者用）。

1.  /arena create <アリーナ名> <チーム名1> <チーム名2>... でセッションを開設。
2.  /arena team add <チーム名> <プレイヤー> で戦闘員をチームに追加。
3.  WorldEdit で選択した範囲を、/arena region bet <チーム名> で賭けエリア、/arena region team <チーム名> で全滅判定用のチームエリアとして設定します。
4.  /arena open で賭けの受付を開始し、試合開始時に /arena start で締め切ります。
5.  決着がついたら /arena win <勝利チーム名> を宣言すると、オッズに応じた配当金が自動でチップとして配布されます（途中で中止したい場合は /arena cancel で全額返金されます）。

観客のプレイヤーは /bet コマンドで情報を確認できます。

*   /bet odds : 各チームの現在のオッズや賭けプールの総額を表示します。
*   /bet info : 自分がどのチームにいくら賭けているか、的中した場合の予想配当などの詳細を表示します。

---

## システム設定について

### カジノの設定 (plugins/CasinoCore/config.yml)
*   max-buy: 一度に購入できるチップの最大合計額（デフォルト: 1000000）
*   ranking-size: ランキングに表示するプレイヤー数（デフォルト: 10）

### 闘技場の設定 (plugins/ArenaCore/config.yml)
*   payout-method: 配当の計算方法。pari-mutuel (パリミュチュエル方式), fixed-odds (賭けた時点のオッズで固定する方式), simple (単純再分配) から選択できます。
*   win-condition: 勝利の判定基準。last-team-standing (エリア内のチーム全滅で自動判定), manual (管理者の手動宣言), score (スコア制) から選択できます。
*   house-edge: 運営の手数料（デフォルト 0.1 = 10%）
*   entry-fee: 戦闘員の参加費（デフォルト 100 E）
*   odds-broadcast-interval: 賭け受付中に、現在のオッズをチャット欄に自動でお知らせする間隔（秒単位）

---

## 動作に必要なもの

*   Minecraft: 1.20.1 以上
*   Java: Java 17 以上
*   前提プラグイン:
    *   [Vault](https://github.com/MilkBowl/Vault)
    *   Vault に対応した経済プラグイン（EmeraldBank など）
*   おすすめプラグイン:
    *   [WorldEdit](https://dev.bukkit.org/projects/worldedit) （アリーナのエリア設定を行うために必要です）

---

## 開発とビルド

プロジェクトのルートディレクトリで以下の Gradle コマンドを実行して、ビルドやテストを行うことができます。

```bash
# プラグインのビルド（CasinoCore, ArenaCore の jar ファイルが生成されます）
./gradlew build

# 単体テストの実行
./gradlew test
```

ビルドが成功すると、それぞれのモジュールの build/libs/ フォルダ内に導入用の jar ファイルが作成されます。
*   CasinoCore/build/libs/CasinoCore-1.0-SNAPSHOT.jar
*   ArenaCore/build/libs/ArenaCore-1.0-SNAPSHOT.jar
