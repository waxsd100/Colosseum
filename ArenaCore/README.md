# ArenaCore

Minecraft サーバー上で闘技場のベッティングを運営するためのプラグインです。
観客はカーペットチップを物理的に設置して好きなチームにベットし、勝利チームの支持者にオッズに応じた配当が自動配布されます。

---

## 概要

- アリーナセッションの作成・管理（チーム編成 → ベット受付 → 試合 → 勝者宣言）
- 物理カーペット設置によるベッティングシステム
- **パリミュチュエル方式（天引き分配）** による配当計算
- 3 種類の勝利条件（全滅方式 / 手動宣言 / スコア制）
- 待機場システム: プレイヤーと Mob の混合許容。エリア内で自動参加/離脱
- **デスマッチモード**: 闘技者同士がチップを賭けて戦う高額試合
- **ブラインドフェーズ**: ベット締切前にオッズを非公開にする駆け引き要素
- **ジャックポットシステム**: 大穴勝利時にプール金を放出
- 闘技者への最低保証金（固定給）
- **アリーナ終了時の自動換金**: 勝者宣言後に全プレイヤーのチップを自動換金、アリーナランキングに記録
- 3段階の地形復元システム + クラッシュ復旧
- プリセットによる全設定の一括保存・復元

---

## 依存関係

| 種別 | 名前 | 用途 |
|------|------|------|
| 必須プラグイン | **ChipLib** | チップ管理基盤 |
| 必須プラグイン | **CasinoCore** | カジノ統合・セッション管理 |
| 必須プラグイン | [Vault](https://github.com/MilkBowl/Vault) | 経済（Economy）API |
| 必須プラグイン | Vault 対応経済プラグイン | 所持金の管理 |
| 推奨プラグイン | [WorldEdit](https://dev.bukkit.org/projects/worldedit) | エリア設定・地形復元に必要 |

```
ChipLib ← CasinoCore ← ArenaCore
```

---

## 状態遷移

```
SETUP → RECRUITING → BETTING → BLIND → CLOSED → ACTIVE → FINISHED
  │                     │                │
  └── cancel ──────────→└── cancel ─────→└── cancel (引き分け・全額返金)
```

| 状態 | 説明 |
|------|------|
| `SETUP` | セッション作成〜チーム/エリア設定 |
| `RECRUITING` | `/arena open` 後、参加者募集中 |
| `BETTING` | `/arena lock` 後、参加者確定 & ベット受付中 |
| `BLIND` | ベット締切 N 秒前、オッズ非公開でベット継続 |
| `CLOSED` | ベット締切済み、試合開始待ち |
| `ACTIVE` | `/arena start` 後、試合中 |
| `FINISHED` | `/arena win` 後、配当完了 |

---

## 全体フロー

### 初回開催

```
① セッション作成        /arena create 闘技場
        ↓
② チーム追加            /arena team add Warriors
                        /arena team add Monsters
        ↓
③ 待機場設定            /arena team area Warriors  ← WE で範囲選択してから
                        /arena team area Monsters
        ↓
④ TP先設定              /arena team dest Warriors
                        /arena team dest Monsters
        ↓
⑤ ベットエリア設定      /arena region Warriors     ← WE で範囲選択してから
                        /arena region Monsters
        ↓
⑥ 戦闘エリア設定        /arena field set            ← WE で範囲選択してから
        ↓
⑦ プリセット保存        /arena preset save 闘技場   ← 次回から ⑧ だけでOK
        ↓
⑧ 参加者募集            /arena open [秒数]
        ↓
⑨ 参加者ロック          /arena lock [秒数]          ← ベット受付開始
        ↓
⑩ 試合開始              /arena start
        ↓
⑪ 勝者宣言              /arena win Warriors         ← 配当自動配布・自動換金・地形復元
```

### 2回目以降（プリセット使用）

```
/arena preset load 闘技場   ← ①〜⑦ を一発復元
        ↓
/arena open                 ← 参加者募集
        ↓
/arena lock                 ← ベット受付開始
        ↓
/arena start                ← 試合開始
        ↓
/arena win Warriors         ← 勝者宣言
```

---

## コマンド一覧

### 管理者コマンド `/arena`

| コマンド | 説明 |
|---------|------|
| `/arena create <名前>` | セッションを新規作成 |
| `/arena team add <チーム名>` | チームを追加 |
| `/arena team list` | チーム一覧（メンバー・Mob数表示） |
| `/arena team area <チーム>` | WE選択範囲を待機場に設定 |
| `/arena team dest <チーム>` | 現在地をTP先に設定 |
| `/arena team color <チーム> <色>` | チームカラーを設定（Scoreboard Team 連携） |
| `/arena region <チーム名>` | WE選択範囲をベットエリアに設定 |
| `/arena field set` | WE選択範囲を戦闘エリアに設定 |
| `/arena field info` | 戦闘エリアの座標・ブロック数を表示 |
| `/arena preset save [名前]` | 全設定をプリセット保存（省略時はセッション名） |
| `/arena preset load <名前>` | プリセットからセッション復元 |
| `/arena preset list` | プリセット一覧 |
| `/arena preset delete <名前>` | プリセット削除 |
| `/arena open [秒数]` | 参加者募集開始（秒数指定で自動ロック） |
| `/arena lock [秒数]` | 参加者確定 & ベット受付開始（秒数指定で自動締切） |
| `/arena close` | ベット締切（試合は開始しない） |
| `/arena start` | 試合開始（ベットを自動締切） |
| `/arena win <チーム名>` | 勝者宣言 → 配当処理 |
| `/arena cancel` | セッション中止（試合中なら引き分け・全額返金） |
| `/arena deathmatch <参加費>` | デスマッチを提案（闘技者の投票制） |
| `/arena deathmatch info` | デスマッチ投票状況を表示 |
| `/arena deathmatch cancel` | デスマッチ提案をキャンセル |
| `/arena status` | 現在のセッション状態を表示 |

> **💡 プリセットの保存内容**: チーム構成・待機場・TP先・ベットエリア・戦闘エリア・チームカラーがすべて一括で保存されます。

### 観客コマンド `/bet`

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/bet odds` | 各チームのオッズ・ベットプール総額を表示 | `arena.bet` |
| `/bet info` | 自分のベット状況・予想配当を表示 | `arena.bet` |
| `/bet <チーム> <金額>` | コマンドからベット（エリア設置の代替） | `arena.bet` |

> **📝 ベット方法**
> ベットは各チームのベットエリアにチップ（カーペット）を物理的に設置して行います。
> `/bet <チーム> <金額>` コマンドからも可能です。
> ベットを取り消す場合は、設置したカーペットをハサミで壊すとチップが手元に戻ります。

---

## 権限（パーミッション）

| パーミッション | 説明 | デフォルト |
|--------------|------|----------|
| `arena.admin` | `/arena` コマンドの使用 | OP |
| `arena.bet` | `/bet` コマンドの使用 | 全員 |

---

## 配当システム

### パリミュチュエル方式（天引き分配）

全ベット額をプールし、以下の順序で分配します：

```
全ベット額（ベッタープール）
  ├── 敗者闘技者還元  (デフォルト 1%)
  ├── 勝者闘技者還元  (デフォルト 10%)
  ├── 運営手数料      (デフォルト 5%) → ジャックポット積立
  └── 観客配当        (残り 84%)     → 勝利チームのベッター按分
```

加えて、全闘技者に **最低保証金**（デフォルト 100 E）が支給されます。

### ジャックポットシステム

- 毎試合の運営手数料がジャックポットに積立
- 勝利チームのベット比率が閾値未満（大穴勝利）の場合にジャックポットが発動
- ジャックポット全額 + 当該試合の手数料がベッター配当に加算

### デスマッチモード

- ベット締切後、闘技者がチップを賭けて戦う特別モード
- `/arena deathmatch <参加費>` で提案 → 闘技者の投票で決定
  - **5人以下**: 全員一致で成立
  - **6人以上**: 過半数（ceil(n/2)）で成立
- デスマッチプールは勝利チームの闘技者に分配

### ブラインドフェーズ

- ベット締切の N 秒前にオッズが非公開になる
- ベット自体は引き続き可能（最後の駆け引き）
- オッズ表示は `???` になる

---

## 設定ファイル

`plugins/ArenaCore/config.yml`

```yaml
# 勝利条件: last-team-standing / manual / score
win-condition: last-team-standing

# 戦闘員の参加費（0 = 無料, Vault経済から徴収）
entry-fee: 0

# スコア制の場合の勝利キル数（0 = 手動集計）
score-target: 0

# ベット受付中にオッズを定期表示する間隔（秒, 0 = 無効）
odds-broadcast-interval: 30

# ── 天引き分配率 ──
distribution:
  loser-fighter-share: 0.01     # 敗者闘技者への還元率 (1%)
  winner-fighter-share: 0.10    # 勝者闘技者への還元率 (10%)
  house-fee: 0.05               # 運営手数料 → ジャックポット積立 (5%)
  # 残り (84%) が観客配当

# ── ジャックポット ──
jackpot:
  enabled: true
  trigger-threshold: 0.10       # 勝利チームのベット比率がこれ未満で発動

# ── 闘技者の最低保証金 ──
fighter-guarantee: 100          # 勝敗に関係なく全闘技者に支払う固定給

# ── ブラインドフェーズ ──
blind:
  enabled: true                 # ベット終了前にオッズ非公開期間を設ける
  seconds-before-close: 30      # 締切何秒前にブラインドに移行するか

# ── デスマッチ ──
deathmatch:
  max-proposals: 2              # 1セッション中の最大DM提案回数
  house-fee-enabled: false      # DMプールにも運営手数料をかけるか

# ── ベットタイマー ──
default-betting-duration: 0     # /arena lock 時のデフォルトベット制限時間（秒, 0 = 無制限）

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
| `win-condition` | 勝利条件 | `last-team-standing` |
| `entry-fee` | 戦闘員の参加費（0 = 無料） | `0` |
| `score-target` | スコア制の勝利キル数（0 = 手動） | `0` |
| `odds-broadcast-interval` | オッズ自動通知間隔（秒） | `30` |
| `distribution.loser-fighter-share` | 敗者闘技者還元率 | `0.01` |
| `distribution.winner-fighter-share` | 勝者闘技者還元率 | `0.10` |
| `distribution.house-fee` | 運営手数料率 | `0.05` |
| `jackpot.enabled` | ジャックポット有効/無効 | `true` |
| `jackpot.trigger-threshold` | ジャックポット発動閾値 | `0.10` |
| `fighter-guarantee` | 闘技者の最低保証金 | `100` |
| `blind.enabled` | ブラインドフェーズ有効/無効 | `true` |
| `blind.seconds-before-close` | ブラインド移行秒数 | `30` |
| `deathmatch.max-proposals` | DM最大提案回数 | `2` |
| `deathmatch.house-fee-enabled` | DMプール手数料 | `false` |
| `default-betting-duration` | デフォルトベット制限時間 | `0` |
| `terrain-restore.enabled` | 地形復元有効/無効 | `true` |
| `terrain-restore.during-match-delay` | 試合中復元遅延 (tick) | `300` |
| `terrain-restore.post-match-delay` | 試合後復元遅延 (tick) | `60` |
| `terrain-restore.post-match-blocks-per-tick` | 1tick あたり復元ブロック数 | `10` |
| `terrain-restore.effects` | 復元エフェクト | `true` |

---

## 待機場システム

待機場はプレイヤーと Mob を混合して許容します。

- **自動参加/離脱**: 待機場エリアに入ると即座にチームに追加、出ると自動離脱
- **TABリスト連携**: Minecraft バニラの Scoreboard Team に自動登録（チームカラー表示・Friendly Fire 無効化）
- **サーバー参加時チェック**: ログイン時に待機場エリア内にいれば自動的にチームに参加
- **ロック後の固定**: `/arena lock` 後は参加者が確定し、エリアを離れてもチームから抜けない

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

## プレイヤーの接続管理

途中ログイン・ログアウト・キックに対して安全なクリーンアップ処理が行われます。

| イベント | 動作 |
|---------|------|
| **ログアウト（SETUP/RECRUITING中）** | チーム離脱・Scoreboard Team 除去 |
| **ログアウト（BETTING/BLIND/CLOSED中）** | ベットはそのまま有効 |
| **ログアウト（ACTIVE中・戦闘員）** | 死亡扱い → 脱落処理・勝利条件チェック |
| **ログアウト（ACTIVE中・観客）** | 配当はオフラインでも受け取れる |
| **キック** | 上記と同一の処理を実行 |
| **途中ログイン** | ベット受付中ならチップ購入許可、待機場エリア内なら自動チーム参加 |

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
./gradlew :ArenaCore:shadowJar

# ArenaCore のテスト実行
./gradlew :ArenaCore:test
```

ビルド成果物: `ArenaCore/build/libs/ArenaCore-3.2.0.jar`

> **📝 備考**
> - CasinoCore と ChipLib は `compileOnly` 依存のため、サーバーに別途配置が必要です。
> - Java 17 以上が必要です。
