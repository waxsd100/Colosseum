# Colosseum (コロシアム)

Colosseum は、Minecraft サーバー内で手軽にカジノや闘技場の賭け（ベッティング）を楽しめるように開発された、プラグインの詰め合わせ（スイート）です。

ゲーム内アイテムである「カーペット」を物理的なカジノチップに見立てて、プレイヤー同士で遊べるユニークなシステムを提供しています。

---

## プロジェクトの構成

このプロジェクトは、役割ごとに以下の 3 つのモジュールに分かれています。

*   **ChipLib**  
    チップの基本機能をまとめたライブラリです。アドベンチャーモードのプレイヤーでも特定のブロックの上にチップ（カーペット）を設置・回収できるように、裏側で CanPlaceOn NBT タグなどを制御しています。
*   **CasinoCore**  
    サーバー全体でのカジノイベントを管理するコアプラグインです。カジノモードを開始すると、参加プレイヤーを一時的にアドベンチャーモードにし、カーペット回収用のハサミ（カジノシザース）を配って、不正を防ぎながら遊べる環境を整えます。データの保存処理は非同期で行われるため、サーバーの動作も軽量です。
*   **ArenaCore**  
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

*   /chip \<額面\> \<枚数\> : 指定したチップを指定枚数購入します。
*   /chip \<金額\> : 所持金を入力すると、一番枚数が少なくなるような組み合わせでチップを自動分割して購入します。
*   /chip info : チップの額面と色の対応表をゲーム内に表示します。
*   /chip balance : 自分が今持っているチップの内訳と合計額を表示します。
*   /chip cashout : 手持ちのチップをすべて回収し、所持金に戻します。

### 闘技場（アリーナ）の運営とオッズ確認

アリーナの運営は /arena コマンドで行います（管理者用）。

1.  /arena create \<アリーナ名\> \<チーム名1\> \<チーム名2\>... でセッションを開設。
2.  /arena team add \<チーム名\> \<プレイヤー\> で戦闘員をチームに追加。
3.  WorldEdit で選択した範囲を、/arena region bet \<チーム名\> で賭けエリア、/arena region team \<チーム名\> で全滅判定用のチームエリアとして設定します。
4.  /arena open で賭けの受付を開始し、試合開始時に /arena start で締め切ります。
5.  決着がついたら /arena win \<勝利チーム名\> を宣言すると、オッズに応じた配当金が自動でチップとして配布されます（途中で中止したい場合は /arena cancel で全額返金されます）。

### 戦闘エリアの設定

WorldEdit で範囲を選択し、/arena field コマンドで戦闘エリアを設定・確認できます。

*   /arena field set : WorldEdit の選択範囲を戦闘エリアとして登録します。登録時に Schematic ファイルが自動保存され、地形復元に使用されます。
*   /arena field info : 現在の戦闘エリアの座標・ブロック数・ワールド名を表示します。

### プリセット（アリーナ設定の保存・復元）

一度設定したアリーナの構成を保存しておき、次回以降はワンコマンドで復元できます。

*   /arena preset save \[名前\] : 現在のセッション設定をプリセットとして保存します。名前を省略するとセッション名が使われます。
*   /arena preset load \<名前\> : 保存済みプリセットからセッションを復元します。
*   /arena preset list : 保存済みプリセットの一覧を表示します。
*   /arena preset delete \<名前\> : 保存済みプリセットを削除します。

観客のプレイヤーは /bet コマンドで情報を確認できます。

*   /bet odds : 各チームの現在のオッズや賭けプールの総額を表示します。
*   /bet info : 自分がどのチームにいくら賭けているか、的中した場合の予想配当などの詳細を表示します。

---

## 地形復元システム

ArenaCore には、試合中に破壊されたブロックを自動で元に戻す **3段階の地形復元システム** が搭載されています。

### 復元の流れ

1. **Stage 1 — 試合中のゆっくり復元**  
   試合中に破壊されたブロックを、一定tick経過後に1ブロックずつ自動復元します。砂の落下や爆発で壊れたブロックも対象です。

2. **Stage 2 — 試合後の高速復元**  
   試合終了（勝者宣言 or キャンセル）後、記録されたブロック変更を高速で一気に復元します。1tickあたりの復元ブロック数は設定で調整可能です。

3. **Stage 3 — Schematic による完全置換**  
   Stage 2 完了後、戦闘エリア設定時に保存した Schematic ファイルを使い、エリア全体を完全にペーストします。砂や水流の取りこぼしを防ぎ、確実に元の地形に戻します。

### クラッシュ復旧

試合中にサーバーがクラッシュした場合に備え、`.active` マーカーファイルを使った自動復旧機構があります。

*   試合開始時に `.active` ファイルが作成され、正常終了時に削除されます。
*   サーバー再起動時にこのファイルが残っていた場合、自動的に Schematic ペーストで地形を復旧します。

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

### 地形復元の設定 (plugins/ArenaCore/config.yml)
*   terrain-restore.enabled: 地形復元機能の有効/無効（デフォルト: true）
*   terrain-restore.during-match-delay: 試合中の復元遅延tick数（デフォルト: 60 = 3秒）
*   terrain-restore.post-match-delay: 試合後の復元開始遅延tick数（デフォルト: 20 = 1秒）
*   terrain-restore.post-match-blocks-per-tick: 試合後に1tickあたり復元するブロック数（デフォルト: 50）
*   terrain-restore.effects: 復元時のパーティクル・効果音の有効/無効（デフォルト: true）

---

## 動作に必要なもの

*   Minecraft: 1.20.1 以上
*   Java: Java 17 以上
*   前提プラグイン:
    *   [Vault](https://github.com/MilkBowl/Vault)
    *   Vault に対応した経済プラグイン（EmeraldBank など）
*   おすすめプラグイン:
    *   [WorldEdit](https://dev.bukkit.org/projects/worldedit) （アリーナのエリア設定・地形復元に必要です）

---

## 開発とビルド

プロジェクトのルートディレクトリで以下の Gradle コマンドを実行して、ビルドやテストを行うことができます。

```bash
# プラグインのビルド（CasinoCore, ArenaCore の jar ファイルが生成されます）
./gradlew build

# 全モジュールのテスト実行
./gradlew test

# 特定モジュールのテスト実行
./gradlew :ArenaCore:test
./gradlew :CasinoCore:test
./gradlew :ChipLib:test
```

ビルドが成功すると、それぞれのモジュールの build/libs/ フォルダ内に導入用の jar ファイルが作成されます。
*   CasinoCore/build/libs/CasinoCore-3.0.0.jar
*   ArenaCore/build/libs/ArenaCore-1.0.0.jar

### テスト構成

全 3 モジュールで 415 件のテストが実行されます。

| モジュール | テスト内容 |
|-----------|----------|
| **ChipLib** (48件) | Chip enum, ChipManager (formatAmount, breakdownAmount, calculateSlotsNeeded) |
| **CasinoCore** (113件) | CasinoManager, ChipManager統合, PlayerStats, CashoutMessageFormatter, CasinoDataStore永続化, チップ購入フローIT |
| **ArenaCore** (254件) | ArenaSession, ArenaFieldConfig, ArenaPresetStore, Bet, 配当計算, 勝利条件, 地形復元ライフサイクルIT, プリセット復元IT |
