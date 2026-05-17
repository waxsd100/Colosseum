# 🎰 CasinoCore

Minecraft サーバー用カジノプラグイン。サーバー全体のカジノモードを切り替え、チップの売買・換金・損益ランキングを管理します。

## ✨ 機能

### カジノモード管理
- `/casino on` — カジノモードを開始（全員に通知、アドベンチャーモード適用）
- `/casino off` — カジノモードを終了し、全プレイヤーのチップを自動換金
- `/casino status` — 現在の状態を確認
- `/casino ranking` — 累計損益ランキングを表示

### チップ購入・換金
- `/chip <額面> <枚数>` — 指定額面のチップを枚数分購入
- `/chip <金額>` — 金額を自動分割して最適なチップの組み合わせで購入
- `/chip info` — チップの色・額面一覧を表示
- `/chip balance` — 手持ちチップの詳細と合計を表示
- `/chip cashout` — 手持ちチップを個別に換金する

### チップ一覧

| 色 | カーペット | 額面 |
|:---:|:---|---:|
| 茶 | BROWN_CARPET | 1 E |
| 紫 | PURPLE_CARPET | 5 E |
| 青 | BLUE_CARPET | 10 E |
| 水 | LIGHT_BLUE_CARPET | 50 E |
| 浅葱 | MAGENTA_CARPET | 100 E |
| 緑 | GREEN_CARPET | 500 E |
| 黄緑 | LIME_CARPET | 1,000 E |
| 黄 | YELLOW_CARPET | 5,000 E |
| 橙 | ORANGE_CARPET | 10,000 E |
| 桃 | PINK_CARPET | 50,000 E |
| 赤 | RED_CARPET | 100,000 E |
| 白 | WHITE_CARPET | 500,000 E |
| 黒 | BLACK_CARPET | 1,000,000 E |

### 換金システム
- **個別換金:** `/chip cashout` でカジノモード中にいつでも換金可能
- **一括換金:** `/casino off` 時に全オンラインプレイヤーのチップを自動回収
- **コマンドブロック対応:** `execute as @p run chip cashout` で換金カウンターを設置可能
- 額面別の売却内訳を表示
- 購入額との差分から **勝ち/負け** を判定
- 累計損益をランキングに反映

### セッション管理
- カジノ中はプレイヤーをアドベンチャーモードに変更（管理者は除外）
- keepInventory を自動で ON にし、終了時に元の設定へ復元
- サーバー再起動・`/reload` 時もゲームモード・keepInventory を安全に復元
- カジノシザース（カーペット回収用ハサミ）を自動配布・回収

## 📋 必要環境

| 項目 | バージョン |
|:---|:---|
| Minecraft | 1.20+ |
| Java | 17+ |
| [Vault](https://github.com/MilkBowl/Vault) | 必須 |
| 経済プラグイン | [EmeraldBank](https://www.spigotmc.org/resources/emeraldbank.90498/) 等 |

## 📦 インストール

1. [Releases](https://github.com/waxsd100/CasinoCore/releases) から最新の `CasinoCore-x.x.x.jar` をダウンロード
2. サーバーの `plugins/` フォルダに配置
3. サーバーを再起動

## ⚙️ 設定

`plugins/CasinoCore/config.yml`

```yaml
# チップの最大購入額（自動分割モード時）
max-buy: 1000000

# ランキング表示件数
ranking-size: 10
```

## 🏗️ ビルド

```bash
git clone https://github.com/waxsd100/CasinoCore.git
cd CasinoCore
./gradlew build -Pversion=1.0.1
```

成果物: `build/libs/CasinoCore-*.jar`

## 📜 リリース

`v*` タグをプッシュすると GitHub Actions が自動でビルド・リリースを作成します。

```bash
git tag v1.0.1
git push origin v1.0.1
```

## 📄 ライセンス

MIT License
