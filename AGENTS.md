# AGENTS.md — AI アシスタント向けプロジェクトルール

このファイルは AI コーディングアシスタントが本プロジェクトで作業する際に
遵守すべきルールと規約を定義する。

---

## バージョン管理

### バージョン形式

本プロジェクトは **日付ベースバージョニング** を採用している。

| 形式 | 用途 | 例 |
|---|---|---|
| `YY.M.D` | その日の最初のリリース | `26.6.14` |
| `YY.M.D.N` | 同日の追加リリース（N=1,2,...） | `26.6.14.1` |

- `YY` = 西暦下2桁、`M` = 月（ゼロ埋めなし）、`D` = 日（ゼロ埋めなし）
- 月・日にゼロパディングは **しない**（`06` ではなく `6`）
- バージョンは `gradle.properties` の各モジュール変数で管理する:
  - `chipLibVersion`
  - `casinoCoreVersion`
  - `arenaCoreVersion`

### リリースタグ形式

```
v{VERSION}-{module}
```

| module 名 | 対象プロジェクト |
|---|---|
| `arena` | ArenaCore |
| `casino` | CasinoCore |
| `chiplib` | ChipLib |

**例:**
- `v26.6.14-arena` — ArenaCore のリリース
- `v26.6.14.1-casino` — CasinoCore の同日2回目リリース

### リリース手順

1. `gradle.properties` のバージョンを更新する
2. 変更をコミットする
3. `git tag -a v{VERSION}-{module} -m "メッセージ"` でアノテーションタグを作成する
4. `git push origin {branch} --follow-tags` で push する
5. GitHub Actions (`release.yml`) が自動でビルド・テスト・リリースを作成する

---

## ビルド構成

### Shadow JAR とリロケーション

**ChipLib** は以下の依存を shadow JAR にバンドルし、リロケートしている:

```
redis.clients       → io.wax100.chipLib.redis
org.apache.commons.pool2 → io.wax100.chipLib.pool2
org.json             → io.wax100.chipLib.json
de.tr7zw.changeme.nbtapi → io.wax100.chipLib.nbtapi
```

> [!CAUTION]
> ChipLib のリロケート済みクラスを使用するモジュール（ArenaCore 等）が
> 同じライブラリをバンドルする場合、**必ず同一のリロケーションルールを適用すること**。
> リロケーションが不一致だと `NoSuchMethodError` / `NoClassDefFoundError` が発生する。

### 依存スコープの注意点

- サーバー（Bukkit/Spigot/Mohist）が提供する API → `compileOnly`
- shadow JAR にバンドルする必要があるライブラリ → `implementation`
- 他のサブプロジェクト（ChipLib, CasinoCore） → `compileOnly`（プラグインとして別途ロードされるため）

---

## プロジェクト構成

```
Colosseum/
├── ChipLib/      — 共通ライブラリ（チップ経済、Redis接続管理）
├── CasinoCore/   — カジノプラグイン
├── ArenaCore/    — 闘技場プラグイン
├── build.gradle  — ルートビルドスクリプト（共通設定）
└── gradle.properties — バージョン・依存関係の一元管理
```

---

## 言語・コーディング規約

- Java 17 ターゲット
- Javadoc は日本語で記述する
- コミットメッセージは日本語で記述する（Conventional Commits 形式推奨: `fix:`, `feat:` 等）
