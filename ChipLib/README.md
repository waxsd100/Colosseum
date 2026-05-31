# ChipLib

Minecraft サーバーでカジノチップ（カーペットアイテム）の管理を行うプラグインです。
チップの購入・換金・残高確認や、カテゴリ別ランキングシステムを提供します。

Colosseum プラグインスイートの基盤モジュールとして、CasinoCore および ArenaCore から利用されます。

---

## 概要

- チップ（カーペット）の生成・管理（CanPlaceOn NBT 付きでアドベンチャーモードでも設置可能）
- `/chip` コマンドによるチップの購入・確認・換金
- `/ranking` コマンドによるカテゴリ別ランキング（カジノ／アリーナ／総合）
- **チップ購入制限**: カジノモード中 or アリーナ賭け受付中のみ購入可能
- **アドベンチャーモード強制**: チップ購入時にアドベンチャーモードに自動切替、換金時に元のモードに復元
- 外部プラグイン向け API（`allowPlayer` / `disallowPlayer` / `cashoutPlayer` / `setPurchaseListener`）

---

## 依存関係

| 種別 | 名前 | 用途 |
|------|------|------|
| 必須プラグイン | [Vault](https://github.com/MilkBowl/Vault) | 経済（Economy）API |
| 必須プラグイン | Vault 対応経済プラグイン | 所持金の管理 |
| ライブラリ | [NBT-API](https://github.com/tr7zw/Item-NBT-API) | CanPlaceOn NBT タグの操作（Shadow JAR に同梱） |

ChipLib は他のプラグインに依存されます:

```
ChipLib（プラグイン） ← CasinoCore ← ArenaCore
```

---

## チップ一覧

| 色 | マテリアル | 額面 |
|---|----------|------|
| 茶色 | BROWN_CARPET | 1 E |
| 紫色 | PURPLE_CARPET | 5 E |
| 青色 | BLUE_CARPET | 10 E |
| 水色 | LIGHT_BLUE_CARPET | 50 E |
| 浅葱色 | MAGENTA_CARPET | 100 E |
| 緑色 | GREEN_CARPET | 500 E |
| 黄緑色 | LIME_CARPET | 1,000 E |
| 黄色 | YELLOW_CARPET | 5,000 E |
| 橙色 | ORANGE_CARPET | 10,000 E |
| 桃色 | PINK_CARPET | 50,000 E |
| 赤色 | RED_CARPET | 100,000 E |
| 白色 | WHITE_CARPET | 500,000 E |
| 黒色 | BLACK_CARPET | 1,000,000 E |

---

## コマンド一覧

### `/chip` — チップの購入・情報コマンド

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/chip <額面> <枚数>` | 指定額面のチップを指定枚数購入 | `chip.use` |
| `/chip <金額>` | 金額分のチップを最少枚数で自動分割購入 | `chip.use` |
| `/chip info` | チップの額面・色対応表を表示 | `chip.use` |
| `/chip balance` | 手持ちチップの内訳と合計額を表示 | `chip.use` |
| `/chip cashout` | 手持ちチップを全額換金し所持金に戻す | `chip.use` |

> **⚠ 購入制限**
> - チップの購入（`/chip <額面> <枚数>` および `/chip <金額>`）はカジノモード中、またはアリーナの賭け受付中のみ使用可能です。
> - `info` / `balance` / `cashout` はいつでも使用できます。

> **💡 アドベンチャーモード強制**
> - チップ購入成功時、プレイヤーのゲームモードが自動的にアドベンチャーモードに変更されます（元のモードは保存）。
> - `/chip cashout` 実行時に元のゲームモードに自動復元されます。

#### 使用例

```
# 10,000 E チップを 5 枚購入（合計 50,000 E）
/chip 10000 5

# 25,000 E 分のチップを自動分割で購入
/chip 25000

# チップ一覧を表示
/chip info

# 手持ちチップの残高を確認
/chip balance

# 手持ちチップを全て換金
/chip cashout
```

### `/ranking` — ランキング表示コマンド

| コマンド | 説明 | 権限 |
|---------|------|------|
| `/ranking` | 総合ランキングを表示 | `ranking.use` |
| `/ranking total` | 総合ランキングを表示 | `ranking.use` |
| `/ranking casino` | カジノランキングを表示 | `ranking.use` |
| `/ranking arena` | アリーナランキングを表示 | `ranking.use` |
| `/ranking reset <casino\|arena\|all>` | ランキングをリセット | OP |

#### 使用例

```
# 総合ランキング（カジノ + アリーナの合計損益）
/ranking

# カジノでの損益ランキング
/ranking casino

# アリーナでの損益ランキング
/ranking arena

# カジノランキングのみリセット（OP権限必要）
/ranking reset casino

# 全ランキングをリセット（OP権限必要）
/ranking reset all
```

---

## 権限（パーミッション）

| パーミッション | 説明 | デフォルト |
|--------------|------|----------|
| `chip.use` | `/chip` コマンドの使用 | 全員 |
| `ranking.use` | `/ranking` コマンドの使用 | 全員 |

---

## 設定ファイル

`plugins/ChipLib/config.yml`

```yaml
# チップの最大購入額（自動分割モード時, 0 = 無制限）
max-buy: 1000000
```

| 設定キー | 説明 | デフォルト値 |
|---------|------|------------|
| `max-buy` | `/chip <金額>` での一度の最大購入額。0 で無制限 | `1000000` |

### ランキングデータ

`plugins/ChipLib/ranking_data.yml` にカテゴリ別の累計損益が自動保存されます（手動編集は非推奨）。

---

## ビルド方法

プロジェクトルートで以下のコマンドを実行します:

```bash
# ChipLib のみビルド
./gradlew :ChipLib:build

# ChipLib のテスト実行
./gradlew :ChipLib:test
```

ビルド成果物: `ChipLib/build/libs/ChipLib-2.0.1.jar`

> **📝 備考**
> - Shadow JAR プラグインにより NBT-API が `io.wax100.chipLib.nbtapi` にリロケートされて同梱されます。
> - Java 17 以上が必要です。
