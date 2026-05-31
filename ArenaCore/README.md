# ArenaCore

Minecraft サーバー上で闘技場の賭け（ベッティング）を運営するためのプラグインです。  
観客はカーペットチップを物理的に設置して好きなチームに賭け、勝利チームの支持者にオッズに応じた配当が自動配布されます。

---

## 目次

- [導入方法](#導入方法)
- [クイックスタート（はじめてのアリーナ開催）](#クイックスタートはじめてのアリーナ開催)
- [コマンド一覧](#コマンド一覧)
- [権限（パーミッション）](#権限パーミッション)
- [設定ファイル](#設定ファイル)
- [アリーナの運営フロー](#アリーナの運営フロー)
- [待機場・戦闘エリアの永続化](#待機場戦闘エリアの永続化)
- [プリセット（設定の保存・復元）](#プリセット設定の保存復元)
- [地形復元システム](#地形復元システム)
- [配当計算の仕組み](#配当計算の仕組み)
- [勝利条件](#勝利条件)
- [FAQ・トラブルシューティング](#faqトラブルシューティング)

---

## 導入方法

### 前提条件

| 項目 | バージョン |
|------|----------|
| Minecraft | 1.20.1 以上 |
| Java | 17 以上 |
| [Vault](https://github.com/MilkBowl/Vault) | 必須 |
| Vault対応経済プラグイン | 必須 (例: EmeraldBank) |
| [WorldEdit](https://dev.bukkit.org/projects/worldedit) | 推奨（エリア設定・地形復元に必要） |

### インストール

1. `ArenaCore.jar` を `plugins/` フォルダに配置
2. サーバーを起動（`plugins/ArenaCore/config.yml` が自動生成されます）
3. 必要に応じて `config.yml` を編集し、`/reload confirm` で反映

---

## クイックスタート（はじめてのアリーナ開催）

### Step 1 — アリーナを作成する

```
/arena create 闘技場
```

「闘技場」という名前のアリーナセッションが開設されます。

### Step 2 — チームを追加する

```
/arena team add Warriors
/arena team add Monsters
```

> **💡 チームカラーの設定（任意）**
> ```
> /arena team color Warriors red
> /arena team color Monsters green
> ```
> Minecraft バニラの Scoreboard Team と連携し、名前の色表示や Friendly Fire 無効化が適用されます。

### Step 3 — 戦闘エリアを設定する（WorldEdit 必須）

WorldEdit の木の斧で戦闘エリアの2点を選択してから：

```
/arena field set
```

この時点で **地形の Schematic が自動保存** されます。試合後の地形復元に使用されます。

設定内容を確認するには：

```
/arena field info
```

### Step 4 — 待機場を設定する

戦闘員をチームに登録する仕組みの中核です。  
**`/arena start` 実行時に待機場内にいるプレイヤーが自動的にチームメンバーとして登録されます。**

```
（Warriors の待機場を WE で選択）
/arena team area Warriors

（Monsters の待機場を WE で選択）
/arena team area Monsters
```

> **💡 保存済み待機場をリンクする場合**
> あらかじめ `/arena area save` で保存した待機場を名前で指定することもできます：
> ```
> /arena team area Warriors 赤チーム待機場
> ```

> **💡 TP先の設定（任意）**  
> 試合開始時に待機場のプレイヤーを戦闘エリアにテレポートさせたい場合：
> ```
> （戦闘エリア内の好きな場所に立って）
> /arena team dest Warriors
> ```

#### Mob チームの場合

Mob（ゾンビ、スケルトンなど）で構成するチームは、待機場にMobを配置しておくだけです：

```
（Mob の待機場を WE で選択）
/arena mob area Monsters
```

これで待機場が設定され、自動的に **Mob チーム** としてマークされます。  
`/arena start` 時に待機場内のMobが自動でチーム登録・追跡されます。

### Step 5 — 賭けエリアを設定する（WorldEdit 必須）

各チームの賭けエリア（観客がカーペットを置く場所）を設定します：

```
//wand          ← WorldEdit の選択ツール
（エリアの2点を選択）
/arena region Warriors

（別のエリアの2点を選択）
/arena region Monsters
```

### Step 6 — 賭けの受付を開始する

```
/arena open
```

観客は賭けエリアにチップ（カーペット）を物理的に設置して賭けます。  
現在のオッズは定期的にチャットで自動通知されます。

> **⚠ 設定漏れ警告**  
> 待機場やTP先、戦闘エリアが未設定のチームがあれば、open 時に警告が表示されます。

### Step 7 — 試合を開始する

```
/arena start
```

以下が自動で行われます：

1. 賭けが締め切られる
2. **各待機場にいるプレイヤーが自動的にチームメンバーとして登録**
3. **Mob チームの待機場にいるMobが自動追跡開始**
4. TP先が設定されている場合、プレイヤー/Mobを戦闘エリアにテレポート
5. 地形の変更追跡（ブロック破壊の記録）が開始

### Step 8 — 勝者を宣言する

```
/arena win Warriors
```

以下が自動で行われます：

1. 配当金をオッズに応じて計算
2. 勝利チームに賭けた観客にチップを配布
3. 戦闘員の参加費を勝利チームに返還
4. 地形を3段階で自動復元

### 試合をキャンセルする場合

```
/arena cancel
```

全額返金され、地形が復元されます。

---

## コマンド一覧

### 管理者コマンド `/arena`

#### セッション管理

| コマンド | 説明 |
|---------|------|
| `/arena create <名前>` | アリーナセッションを作成 |
| `/arena open` | 賭けの受付を開始 |
| `/arena start` | 試合を開始（賭け締切） |
| `/arena win <チーム名>` | 勝者を宣言し配当を配布 |
| `/arena cancel` | 試合をキャンセル（全額返金） |
| `/arena status` | セッションの状態を確認 |

#### チーム管理

| コマンド | 説明 |
|---------|------|
| `/arena team add <チーム名>` | チームを追加 |
| `/arena team list` | 全チームの一覧 |
| `/arena team area <チーム> [待機場名]` | WE選択範囲 or 保存済み待機場をプレイヤー待機場に設定 |
| `/arena team dest <チーム名>` | 現在地をTP先に設定 |
| `/arena team color <チーム名> <色>` | チームカラーを設定（Scoreboard連携） |

#### Mob チーム管理

| コマンド | 説明 |
|---------|------|
| `/arena mob area <チーム> [待機場名]` | WE選択範囲 or 保存済み待機場をMob待機場に設定 |
| `/arena mob dest <チーム名>` | 現在地をMobのTP先に設定 |
| `/arena mob list <チーム名>` | 待機場のMob一覧を表示 |

> **💡 チーム登録の仕組み**  
> `/arena start` 実行時に、各待機場内のプレイヤー・Mobが**自動的にチームメンバーとして登録**されます。  
> 同時にMinecraftバニラの **Scoreboard Team** にも登録され、チームカラーの名前表示とFriendly Fire無効化が適用されます。

#### エリア設定（WorldEdit 必須）

| コマンド | 説明 |
|---------|------|
| `/arena region <チーム名>` | 選択範囲を賭けエリアとして設定 |
| `/arena field set` | 選択範囲を戦闘エリアとして設定（Schematic自動保存） |
| `/arena field info` | 戦闘エリアの情報を表示 |

#### 待機場・戦闘エリアの永続化

| コマンド | 説明 |
|---------|------|
| `/arena area save <名前>` | WE選択範囲を待機場として保存 |
| `/arena area save <名前> dest` | 保存済み待機場のTP先を現在地に設定 |
| `/arena area list` | 保存済み待機場の一覧 |
| `/arena area info <名前>` | 保存済み待機場の情報 |
| `/arena area delete <名前>` | 保存済み待機場を削除 |
| `/arena field save <名前>` | 現在の戦闘エリアを名前付きで保存 |
| `/arena field load <名前>` | 保存済み戦闘エリアを読み込み |
| `/arena field list` | 保存済み戦闘エリアの一覧 |
| `/arena field delete <名前>` | 保存済み戦闘エリアを削除 |

#### プリセット

| コマンド | 説明 |
|---------|------|
| `/arena preset save [名前]` | 現在の設定をプリセットとして保存 |
| `/arena preset load <名前>` | プリセットからセッションを復元 |
| `/arena preset list` | 保存済みプリセットの一覧 |
| `/arena preset delete <名前>` | プリセットを削除 |

### 観客コマンド `/bet`

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/bet odds` | 各チームのオッズ・賭けプール総額を表示 | `arena.bet` |
| `/bet info` | 自分の賭け状況・予想配当を表示 | `arena.bet` |

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
# ── 配当計算 ──

# 配当方式: pari-mutuel / fixed-odds / simple
payout-method: pari-mutuel

# 運営手数料（0.1 = 10%）。0未満または1.0以上はデフォルト値 0.1 に補正
house-edge: 0.1

# ── 勝利条件 ──

# 勝利判定: last-team-standing / manual / score
win-condition: manual

# スコア制の目標キル数（0 = 手動集計）
score-target: 0

# ── 参加費 ──

# 戦闘員の参加費 (E)。0 = 無料
entry-fee: 100

# ── 賭けの制限 ──

# 最低賭け額
min-bet: 1

# 最大賭け額（0 = 無制限）
max-bet: 0

# ── オッズ通知 ──

# 賭け受付中のオッズ自動通知間隔（秒）。0以下はデフォルト値 200 に補正
odds-broadcast-interval: 30

# ── 地形復元 ──

terrain-restore:
  # 地形復元機能の有効/無効
  enabled: true

  # 試合中の復元遅延（tick数、300 = 15秒）。負の値は 0 に補正
  during-match-delay: 300

  # 試合後の復元開始遅延（tick数）
  post-match-delay: 60

  # 試合後に1tickあたり復元するブロック数。0以下は 1 に補正
  post-match-blocks-per-tick: 10

  # 復元時のパーティクル・効果音
  effects: true
```

---

## アリーナの運営フロー

アリーナセッションは以下の **5 つの状態** を順番に遷移します：

```
SETUP → BETTING → ACTIVE → FINISHED → (クリア)
  │                           │
  └────── cancel ─────────────┘
```

| 状態 | 説明 | できること |
|------|------|----------|
| **SETUP** | 初期設定中 | チーム追加、エリア設定、プリセット保存 |
| **BETTING** | 賭け受付中 | 観客がチップを設置して賭ける |
| **ACTIVE** | 試合中 | 地形の変更を追跡、賭けは締切 |
| **FINISHED** | 試合終了 | 配当配布、地形復元中 |

---

## 待機場・戦闘エリアの永続化

待機場と戦闘エリアは名前を付けて保存し、複数のアリーナで再利用できます。

### 待機場の保存と活用

```
（WE で範囲を選択）
/arena area save 赤チーム待機場

（TP先を設定）
/arena area save 赤チーム待機場 dest

（チームにリンク）
/arena team area Warriors 赤チーム待機場
```

### 戦闘エリアの保存と活用

```
/arena field set               ← 現在のセッションに設定
/arena field save 大闘技場     ← 名前を付けて保存
/arena field load 大闘技場     ← 別セッションで読み込み
```

保存先：
- 待機場: `plugins/ArenaCore/areas/<名前>.yml`
- 戦闘エリア: `plugins/ArenaCore/fields/<名前>.yml`

---

## プリセット（設定の保存・復元）

一度設定したアリーナの構成（チーム名、チームカラー、戦闘エリア、待機場、賭けエリア、Mobチーム設定）を保存し、次回ワンコマンドで復元できます。

### チュートリアル：プリセットの活用

#### 1. 初回設定後に保存

```
/arena create 闘技場
/arena team add Warriors
/arena team add Monsters
/arena team color Warriors red
/arena team color Monsters green
/arena field set
/arena region Warriors
/arena region Monsters
/arena preset save 闘技場
```

`plugins/ArenaCore/presets/闘技場.yml` にYAMLファイルとして保存されます。

#### 2. 次回は一発で復元

```
/arena preset load 闘技場
```

チーム構成・チームカラー・全エリア設定・Mobチーム設定が即座に復元されます。  
あとは `/arena open` で賭けを開始するだけです。

#### 3. プリセットの管理

```
/arena preset list        ← 保存済み一覧を表示
/arena preset delete 闘技場  ← 不要なプリセットを削除
```

---

## 地形復元システム

試合中に破壊されたブロックを自動で元に戻す **3段階の地形復元システム** です。

### 復元の流れ

```
試合開始 (/arena start)
    │
    ▼
┌─ Stage 1: 試合中のゆっくり復元 ─────────────────┐
│ 破壊されたブロックを一定tick後に1つずつ自動復元    │
│ 砂の落下・爆発で壊れたブロックも対象              │
│ 設定: during-match-delay (デフォルト 300tick = 15秒)│
└──────────────────────────────────────────────────┘
    │
    ▼  勝者宣言 (/arena win) or キャンセル (/arena cancel)
    │
┌─ Stage 2: 試合後の高速復元 ─────────────────────┐
│ 記録されたブロック変更を高速で一気に復元           │
│ 設定: post-match-blocks-per-tick (デフォルト 10)  │
└──────────────────────────────────────────────────┘
    │
    ▼
┌─ Stage 3: Schematic 完全置換 ───────────────────┐
│ /arena field set 時に保存した Schematic で        │
│ 戦闘エリア全体を完全にペースト                    │
│ 砂や水流の取りこぼしを確実に防止                  │
└──────────────────────────────────────────────────┘
```

### クラッシュ復旧

試合中にサーバーがクラッシュした場合に備え、`.active` マーカーファイルで自動復旧します。

- 試合開始時: `plugins/ArenaCore/arenas/<名前>.active` を作成
- 正常終了時: `.active` ファイルを削除
- サーバー再起動時: `.active` が残っていれば **Schematic ペーストで自動復旧**

---

## 配当計算の仕組み

3つの配当方式から選択できます。

### 1. パリミュチュエル方式（`pari-mutuel`）— デフォルト

全チームの賭け金をプールし、運営手数料を引いた残りを勝利チーム支持者に分配します。

```
配当倍率 = プール合計 × (1 − 手数料) ÷ 勝利チームの賭け合計

例）
  プール合計: 100,000 E
  手数料: 10%
  Warriors への賭け合計: 30,000 E
  
  配当倍率 = 100,000 × 0.9 ÷ 30,000 = 3.0 倍
  1,000 E 賭けたプレイヤー → 3,000 E の配当
```

### 2. 固定オッズ方式（`fixed-odds`）

賭けた **時点** のオッズで配当が確定します。後から他のプレイヤーが賭けてもオッズは変わりません。

### 3. シンプル再分配方式（`simple`）

負けチームの賭け金を勝利チーム支持者に按分します。手数料はプール全体ではなく再分配額にのみ適用されます。

---

## 勝利条件

### `manual`（デフォルト）

管理者が `/arena win <チーム名>` で手動宣言します。

### `last-team-standing`

チームメンバーが全員死亡またはログアウトしたチームが敗北します。最後に残ったチームが自動的に勝者となります。

### `score`

スコア制。管理者がスコアを管理し、条件達成で勝者が決定します。

---

## FAQ・トラブルシューティング

### Q: 賭けエリアが設定できない

- WorldEdit がインストールされているか確認してください
- 先に WorldEdit の木の斧で範囲を選択してから `/arena region <チーム名>` を実行してください

### Q: 地形が復元されない

- `config.yml` の `terrain-restore.enabled` が `true` になっているか確認してください
- WorldEdit がインストールされているか確認してください
- `/arena field set` で戦闘エリアが設定されているか確認してください

### Q: 賭けた金額が返ってこない

- `/arena cancel` を実行すると全額返金されます
- `/arena win` 実行後は、負けチームに賭けた分は配当に使用されるため返金されません

### Q: Mob チームの全滅判定が動かない

- `win-condition: last-team-standing` が設定されているか確認してください
- `/arena mob area <チーム名>` でMobチームとして設定されているか確認してください

### Q: プリセットをロードしたら賭けエリアが復元されない

- プリセット保存時に賭けエリアが設定されていなかった可能性があります
- 保存前にすべてのエリア設定を完了してから `/arena preset save` を実行してください

### Q: 2回目以降の試合で `/arena create` が失敗する

- 前のセッションが残っている可能性があります。`/arena cancel` でクリアしてから再作成してください
- プリセットを使う場合は `/arena preset load <名前>` で直接復元できます
