# ArenaCore

Minecraft サーバー上で闘技場の賭け（ベッティング）を運営するためのプラグインです。
観客はカーペットチップを物理的に設置して好きなチームに賭け、勝利チームの支持者にオッズに応じた配当が自動配布されます。

---

## 概要

- アリーナセッションの作成・管理（チーム編成 → 賭け受付 → 試合 → 勝者宣言）
- 物理カーペット設置によるベッティングシステム
- 3 種類の配当方式（パリミュチュエル / 固定オッズ / 単純再分配）
- 3 種類の勝利条件（全滅方式 / 手動宣言 / スコア制）
- 待機場システム: プレイヤーと Mob の混合許容。エリア内で自動参加/離脱
- **アリーナ終了時の自動換金**: 勝者宣言後に全プレイヤーのチップを自動換金、アリーナランキングに記録
- 3段階の地形復元システム + クラッシュ復旧
- プリセットによる設定の保存・復元
- 待機場・戦闘エリアの永続化と再利用

---

## 依存関係

| 種別 | 名前 | 用途 |
|------|------|------|
| 必須プラグイン | **CasinoCore** | チップ管理の取得元 |
| 必須プラグイン | [Vault](https://github.com/MilkBowl/Vault) | 経済（Economy）API |
| 必須プラグイン | Vault 対応経済プラグイン | 所持金の管理 |
| 推奨プラグイン | [WorldEdit](https://dev.bukkit.org/projects/worldedit) | エリア設定・地形復元に必要 |

依存関係チェーン:

```
ChipLib（プラグイン） ← CasinoCore ← ArenaCore
```

---

## 全体フロー

### 初回開催

```
① セッション作成        /arena create 闘技場
        ↓
② チーム追加            /arena team add Warriors
                        /arena team add Monsters
        ↓
③ 戦闘エリア設定        /arena field set          ← WE で範囲選択してから
                        /arena field set 大闘技場   ← or 保存済みフィールド名を指定
        ↓
④ 待機場設定            /arena team area Warriors  ← WE で範囲選択してから
                        /arena team area Monsters
        ↓
⑤ 賭けエリア設定        /arena region Warriors     ← WE で範囲選択してから
                        /arena region Monsters
        ↓
⑥ プリセット保存        /arena preset save 闘技場  ← 次回から ⑦ だけでOK
        ↓
⑦ 賭け受付開始          /arena open
        ↓
⑧ 試合開始              /arena start               ← 待機場のプレイヤー/Mob 自動登録
        ↓
⑨ 勝者宣言              /arena win Warriors         ← 配当自動配布・自動換金・地形復元
```

### 2回目以降（プリセット使用）

```
/arena preset load 闘技場   ← ①〜⑤ を一発復元
        ↓
/arena open                 ← 賭け受付
        ↓
/arena start                ← 試合開始
        ↓
/arena win Warriors         ← 勝者宣言
```

---

## コマンド一覧

### 管理者コマンド `/arena`

#### セッション管理

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/arena create <名前>` | アリーナセッションを作成 | `arena.admin` |
| `/arena open` | 賭けの受付を開始 | `arena.admin` |
| `/arena start` | 試合を開始（賭け締切・待機場から自動登録） | `arena.admin` |
| `/arena win <チーム名>` | 勝者を宣言し配当を配布 | `arena.admin` |
| `/arena cancel` | 試合をキャンセル（全額返金） | `arena.admin` |
| `/arena status` | セッションの状態を確認 | `arena.admin` |

#### チーム管理

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/arena team add <チーム名>` | チームを追加 | `arena.admin` |
| `/arena team list` | 全チームの一覧（メンバー・Mob数表示） | `arena.admin` |
| `/arena team area <チーム> [待機場名]` | WE選択範囲 or 保存済み待機場を待機場に設定 | `arena.admin` |
| `/arena team dest <チーム名>` | 現在地をチームのTP先に設定 | `arena.admin` |
| `/arena team color <チーム名> <色>` | チームカラーを設定（Scoreboard Team 連携） | `arena.admin` |

> **💡 Mob チームについて**
> Mob チームの待機場設定も `/arena team area` で統一されています。待機場に Mob を配置すると自動的に Mob チームとして認識されます。

#### エリア設定（WorldEdit 必須）

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/arena region <チーム名>` | WE 選択範囲を賭けエリアとして設定 | `arena.admin` |
| `/arena field set [名前]` | WE 選択範囲 or 保存済みフィールドを戦闘エリアとして設定 | `arena.admin` |
| `/arena field info` | 戦闘エリアの座標・ブロック数を表示 | `arena.admin` |

#### 待機場の永続化

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/arena area save <名前>` | WE 選択範囲を待機場として保存 | `arena.admin` |
| `/arena area save <名前> dest` | 保存済み待機場の TP 先を現在地に設定 | `arena.admin` |
| `/arena area list` | 保存済み待機場の一覧 | `arena.admin` |
| `/arena area info <名前>` | 保存済み待機場の詳細情報 | `arena.admin` |
| `/arena area delete <名前>` | 保存済み待機場を削除 | `arena.admin` |

#### 戦闘エリアの永続化

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/arena field save <名前>` | 現在の戦闘エリアを名前付きで保存 | `arena.admin` |
| `/arena field list` | 保存済み戦闘エリアの一覧 | `arena.admin` |
| `/arena field delete <名前>` | 保存済み戦闘エリアを削除 | `arena.admin` |

#### プリセット

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/arena preset save [名前]` | 現在の設定をプリセットとして保存（省略時はセッション名） | `arena.admin` |
| `/arena preset load <名前>` | プリセットからセッションを復元 | `arena.admin` |
| `/arena preset list` | 保存済みプリセットの一覧 | `arena.admin` |
| `/arena preset delete <名前>` | プリセットを削除 | `arena.admin` |

### 観客コマンド `/bet`

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/bet odds` | 各チームのオッズ・賭けプール総額を表示 | `arena.bet` |
| `/bet info` | 自分の賭け状況・予想配当を表示 | `arena.bet` |

> **📝 賭け方法**
> 賭けは `/bet` コマンドではなく、各チームの賭けエリアにチップ（カーペット）を物理的に設置して行います。
> 賭けを取り消す場合は、設置したカーペットをハサミで壊すとチップが手元に戻ります。

---

## 権限（パーミッション）

| パーミッション | 説明 | デフォルト |
|--------------|------|----------|
| `arena.admin` | `/arena` コマンドの使用 | OP |
| `arena.bet` | `/bet` コマンドの使用 | 全員 |

---

## 設定ファイル

`plugins/ArenaCore/config.yml`

```yaml
# 配当方式: pari-mutuel / fixed-odds / simple
payout-method: pari-mutuel

# 勝利条件: last-team-standing / manual / score
win-condition: last-team-standing

# 運営手数料率 (0.0 〜 0.99, 推奨: 0.05 〜 0.15)
house-edge: 0.10

# 戦闘員の参加費（0 = 無料, Vault経済から徴収）
entry-fee: 0

# スコア制の場合の勝利キル数（0 = /arena end で手動集計）
score-target: 0

# 賭け受付中にオッズを定期表示する間隔（秒, 0 = 無効）
odds-broadcast-interval: 30

# ── 地形復元 ──
terrain-restore:
  enabled: true
  during-match-delay: 300       # 試合中: ブロック破壊からN tick後に自動復元
  post-match-delay: 60          # 試合後: 高速復元開始までの遅延 (tick)
  post-match-blocks-per-tick: 10  # 試合後: 1tickあたりの復元ブロック数
  effects: true                 # 復元エフェクト (パーティクル + 効果音)
```

| 設定キー | 説明 | デフォルト値 |
|---------|------|------------|
| `payout-method` | 配当方式（`pari-mutuel` / `fixed-odds` / `simple`） | `pari-mutuel` |
| `win-condition` | 勝利条件（`last-team-standing` / `manual` / `score`） | `last-team-standing` |
| `house-edge` | 運営手数料率（0.0〜0.99） | `0.10` |
| `entry-fee` | 戦闘員の参加費（0 = 無料） | `0` |
| `score-target` | スコア制の勝利キル数（0 = 手動） | `0` |
| `odds-broadcast-interval` | オッズ自動通知間隔（秒） | `30` |
| `terrain-restore.enabled` | 地形復元機能の有効/無効 | `true` |
| `terrain-restore.during-match-delay` | 試合中の復元遅延（tick） | `300` |
| `terrain-restore.post-match-delay` | 試合後の復元開始遅延（tick） | `60` |
| `terrain-restore.post-match-blocks-per-tick` | 試合後の1tick あたり復元ブロック数 | `10` |
| `terrain-restore.effects` | 復元エフェクトの有効/無効 | `true` |

---

## 待機場システム

待機場はプレイヤーと Mob を混合して許容します。

- **自動参加/離脱**: 待機場エリアに入ると即座にチームに追加、出ると自動離脱
- **TABリスト連携**: Minecraft バニラの Scoreboard Team に自動登録（チームカラー表示・Friendly Fire 無効化）
- **サーバー参加時チェック**: ログイン時に待機場エリア内にいれば自動的にチームに参加
- **試合開始時の自動登録**: `/arena start` 実行時に各待機場内のプレイヤー/Mob を自動でチームメンバーとして登録

---

## 地形復元システム

試合中に破壊されたブロックを自動で元に戻す **3段階の地形復元システム** です。

### 復元の流れ

1. **Stage 1 — 試合中のゆっくり復元**: 破壊されたブロックを一定tick後に1つずつ復元
2. **Stage 2 — 試合後の高速復元**: 記録されたブロック変更を高速で一気に復元
3. **Stage 3 — Schematic 完全置換**: 戦闘エリア全体を Schematic で完全ペースト

### クラッシュ復旧

- 試合開始時に `.active` マーカーファイルを作成、正常終了時に削除
- サーバー再起動時にファイルが残っていれば Schematic で自動復旧

---

## 配当計算

### パリミュチュエル方式（`pari-mutuel`）— デフォルト

全チームの賭け金をプールし、手数料を引いた残りを勝利チーム支持者に分配。

### 固定オッズ方式（`fixed-odds`）

賭けた時点のオッズで配当が確定。

### シンプル再分配方式（`simple`）

負けチームの賭け金を勝利チーム支持者に按分。

---

## 勝利条件

| 条件 | 説明 |
|------|------|
| `last-team-standing` | チーム全員が死亡/ログアウトで敗北。最後に残ったチームが勝利 |
| `manual` | 管理者が `/arena win <チーム名>` で手動宣言 |
| `score` | スコア制。`score-target` で目標キル数を設定 |

---

## ビルド方法

プロジェクトルートで以下のコマンドを実行します:

```bash
# ArenaCore のみビルド
./gradlew :ArenaCore:build

# ArenaCore のテスト実行
./gradlew :ArenaCore:test

# テストサーバーで実行（Paper）
./gradlew :ArenaCore:runServer
```

ビルド成果物: `ArenaCore/build/libs/ArenaCore-3.2.0.jar`

> **📝 備考**
> - CasinoCore と ChipLib は `compileOnly` 依存のため、サーバーに別途配置が必要です。
> - Java 17 以上が必要です。
